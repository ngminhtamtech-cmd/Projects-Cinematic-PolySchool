import "server-only";
import { cookies } from "next/headers";
import { assetUrlForBase } from "./asset-url";
import { validatedHttpBase } from "./booking-handoff";
import type { ApiEnvelope, PageMeta } from "./types";

/**
 * Client goi API Java tu phia SERVER (Server Component, Route Handler, Server Action).
 *
 * Day la nua sau cua mo hinh BFF: trinh duyet khong bao gio biet dia chi Tomcat.
 * Cookie JSESSIONID duoc doc tu request hien tai va forward thu cong, vi fetch phia
 * server khong tu dinh kem cookie nhu trong trinh duyet.
 */

const DEFAULT_APP_BASE = "http://localhost:8080/Website-ban-ve-xem-phim";
const BASE = validatedHttpBase(
  process.env.CINEBOOK_API_BASE ?? `${DEFAULT_APP_BASE}/api/v1`,
);
const BACKEND_TIMEOUT_MS = 5_000;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type RequestOptions = {
  /** Giay. Bo qua = khong cache (mac dinh an toan cho du lieu phu thuoc phien dang nhap). */
  revalidate?: number;
  /** Gui kem cookie phien. Bat buoc cho endpoint yeu cau dang nhap. */
  withSession?: boolean;
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
};

async function sessionHeaders(writeRequest: boolean): Promise<Record<string, string>> {
  const jar = await cookies();
  const raw = jar
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");
  const result: Record<string, string> = raw ? { Cookie: raw } : {};
  const csrf = jar.get("XSRF-TOKEN")?.value;
  if (writeRequest && csrf) result["X-CSRF-Token"] = decodeURIComponent(csrf);
  return result;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<{ data: T; meta?: PageMeta }> {
  const { revalidate, withSession = false, method = "GET", body, headers = {} } = options;

  const finalHeaders: Record<string, string> = {
    Accept: "application/json",
    ...headers,
    ...(withSession ? await sessionHeaders(!["GET", "HEAD"].includes(method.toUpperCase())) : {}),
  };
  if (body !== undefined) {
    finalHeaders["Content-Type"] = "application/json";
  }

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers: finalHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
      // Endpoint phu thuoc phien khong duoc cache; endpoint cong khai dung ISR theo revalidate.
      cache: withSession || revalidate === undefined ? "no-store" : undefined,
      next: !withSession && revalidate !== undefined ? { revalidate } : undefined,
    });
  } catch {
    throw new ApiError(
      503,
      "BACKEND_UNAVAILABLE",
      "Không thể kết nối dịch vụ CineBook. Vui lòng thử lại sau ít phút.",
    );
  }

  let payload: ApiEnvelope<T> | null = null;
  try {
    payload = (await res.json()) as ApiEnvelope<T>;
  } catch {
    throw new ApiError(res.status, "INVALID_RESPONSE", "Backend tra ve du lieu khong phai JSON.");
  }

  if (!res.ok || payload.error) {
    throw new ApiError(
      res.status,
      payload.error?.code ?? "UNKNOWN",
      payload.error?.message ?? `Loi ${res.status}`,
    );
  }
  return { data: payload.data as T, meta: payload.meta };
}

export const api = {
  get: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method">) =>
    request<T>(path, { ...options, method: "POST", body }),
};

/** Bien duong dan anh tuong doi tu API thanh URL day du. */
export function assetUrl(path?: string | null): string | null {
  return assetUrlForBase(path, process.env.NEXT_PUBLIC_ASSET_BASE ?? DEFAULT_APP_BASE);
}
