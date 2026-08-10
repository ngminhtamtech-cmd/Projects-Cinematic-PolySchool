import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { assetUrlForBase } from "../lib/asset-url.ts";
import {
  bookingUrl,
  parseShowtimeId,
  validatedHttpBase,
} from "../lib/booking-handoff.ts";

test("showtimeId accepts one positive canonical integer", () => {
  assert.equal(parseShowtimeId("3"), 3);
  assert.equal(parseShowtimeId(undefined), null);
  assert.equal(parseShowtimeId("0"), null);
  assert.equal(parseShowtimeId("03"), null);
  assert.equal(parseShowtimeId("3.0"), null);
  assert.equal(parseShowtimeId(["3", "4"]), null);
});

test("booking handoff uses a configured HTTP(S) JSP base and normalized ID", () => {
  const base = "http://localhost:8080/Website-ban-ve-xem-phim/";

  assert.equal(
    bookingUrl(3, base),
    "http://localhost:8080/Website-ban-ve-xem-phim/booking?showtimeId=3",
  );
  assert.equal(validatedHttpBase("https://cinebook.example/app/"), "https://cinebook.example/app");
  assert.throws(() => validatedHttpBase("javascript:alert(1)"));
  assert.throws(() => bookingUrl(0, base));
});

test("uploaded assets never duplicate the configured servlet context", () => {
  const base = "http://localhost:8080/Website-ban-ve-xem-phim";

  assert.equal(
    assetUrlForBase("/Website-ban-ve-xem-phim/uploads/poster.png", base),
    `${base}/uploads/poster.png`,
  );
  assert.equal(
    assetUrlForBase("/uploads/poster.png", base),
    `${base}/uploads/poster.png`,
  );
  assert.equal(assetUrlForBase("/assets/img/default-film.jpg", base),
    `${base}/assets/img/default-film.jpg`);
});

test("booking handoff delegates eligibility to the Java authority", () => {
  const page = readFileSync(new URL("../app/dat-ve/page.tsx", import.meta.url), "utf8");

  assert.match(page, /\/showtimes\/\$\{showtimeId\}\/booking-eligibility/);
  assert.doesNotMatch(page, /isBookableShowtime|new Date\s*\(/);
});

test("backend calls are bounded and outages remain actionable", () => {
  const serverApi = readFileSync(new URL("../lib/api-server.ts", import.meta.url), "utf8");
  const proxy = readFileSync(new URL("../app/api/proxy/[...path]/route.ts", import.meta.url), "utf8");
  const films = readFileSync(new URL("../app/phim/page.tsx", import.meta.url), "utf8");

  assert.match(serverApi, /AbortSignal\.timeout/);
  assert.match(serverApi, /BACKEND_UNAVAILABLE/);
  assert.match(proxy, /AbortSignal\.timeout/);
  assert.match(proxy, /status:\s*503/);
  assert.match(films, /ApiError/);
  assert.match(films, /Thử lại/);
});

test("admin catalogue writes are visible immediately on Next public pages", () => {
  const films = readFileSync(new URL("../app/phim/page.tsx", import.meta.url), "utf8");
  const detail = readFileSync(new URL("../app/phim/[id]/page.tsx", import.meta.url), "utf8");

  for (const source of [films, detail]) {
    assert.doesNotMatch(source, /export const revalidate\s*=\s*[1-9]/);
    assert.doesNotMatch(source, /api\.get[^;]+revalidate:\s*[1-9]/s);
  }
});
