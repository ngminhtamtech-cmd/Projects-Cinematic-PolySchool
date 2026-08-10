import { cookies } from "next/headers";
import { NextRequest } from "next/server";
import { validatedHttpBase } from "@/lib/booking-handoff";

/**
 * BFF proxy: cau noi duy nhat giua trinh duyet va REST API Java.
 *
 * Vi sao can lop nay thay vi goi thang Tomcat tu trinh duyet:
 *  - Next.js (:3000) va Tomcat (:8080) khac origin, nen cookie JSESSIONID se khong duoc
 *    gui kem neu khong bat CORS + SameSite=None; Secure (phien toai va yeu hon).
 *  - Dia chi backend khong bao gio lo ra internet.
 *
 * Trinh duyet goi /api/proxy/... voi cookie same-origin; handler nay forward cookie
 * sang Java, roi tra nguoc ca Set-Cookie ve domain cua Next.js.
 */

const BASE = validatedHttpBase(
  process.env.CINEBOOK_API_BASE
    ?? "http://localhost:8080/Website-ban-ve-xem-phim/api/v1",
);
const BACKEND_TIMEOUT_MS = 5_000;

/** Header khong duoc phep forward nguyen ven vi thuoc ve tang van chuyen. */
const HOP_BY_HOP = new Set([
  "connection",
  "keep-alive",
  "transfer-encoding",
  "upgrade",
  "host",
  "content-length",
]);

async function handle(request: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const search = request.nextUrl.search;
  const target = `${BASE}/${path.join("/")}${search}`;

  const jar = await cookies();
  const cookieHeader = jar
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");

  const headers = new Headers();
  request.headers.forEach((value, key) => {
    if (!HOP_BY_HOP.has(key.toLowerCase())) headers.set(key, value);
  });
  if (cookieHeader) headers.set("cookie", cookieHeader);
  headers.set("accept", "application/json");

  const hasBody = !["GET", "HEAD"].includes(request.method);

  let upstream: Response;
  try {
    upstream = await fetch(target, {
      method: request.method,
      headers,
      body: hasBody ? await request.arrayBuffer() : undefined,
      redirect: "manual",
      cache: "no-store",
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
    });
  } catch {
    return Response.json(
      {
        error: {
          code: "BACKEND_UNAVAILABLE",
          message: "Không thể kết nối dịch vụ CineBook. Vui lòng thử lại sau ít phút.",
        },
      },
      {
        status: 503,
        headers: { "Cache-Control": "no-store", "Retry-After": "3" },
      },
    );
  }

  const outHeaders = new Headers();
  upstream.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (HOP_BY_HOP.has(lower) || lower === "content-encoding") return;
    // set-cookie xu ly rieng ben duoi: Headers.forEach gop nhieu Set-Cookie
    // thanh mot chuoi noi bang dau phay, lam hong cookie.
    if (lower === "set-cookie") return;
    outHeaders.append(key, value);
  });

  // Tomcat phat cookie voi Path cua ung dung. Trinh duyet chi nhin thay
  // Next.js o :3000 nen cookie do se KHONG bao gio duoc gui lai cho /api/proxy/... .
  // Phai viet lai Path ve "/" thi phien JSESSIONID va XSRF-TOKEN moi song sot qua BFF.
  for (const cookie of upstream.headers.getSetCookie()) {
    const rewritten = cookie
      .replace(/;\s*Path=[^;]*/i, "; Path=/")
      .replace(/;\s*Domain=[^;]*/i, "");
    outHeaders.append("set-cookie", /;\s*Path=/i.test(cookie) ? rewritten : `${rewritten}; Path=/`);
  }

  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: outHeaders,
  });
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;

// Proxy luon phu thuoc request hien tai, khong duoc prerender.
export const dynamic = "force-dynamic";
