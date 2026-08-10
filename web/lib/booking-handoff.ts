export function parseShowtimeId(raw: string | string[] | undefined): number | null {
  if (typeof raw !== "string" || !/^[1-9]\d*$/.test(raw)) return null;
  const value = Number(raw);
  return Number.isSafeInteger(value) ? value : null;
}

export function validatedHttpBase(raw: string): string {
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new Error("Cấu hình URL CineBook không hợp lệ.");
  }
  if (!["http:", "https:"].includes(url.protocol)
      || url.username
      || url.password
      || url.search
      || url.hash) {
    throw new Error("Cấu hình URL CineBook phải là HTTP(S) base an toàn.");
  }
  return url.toString().replace(/\/$/, "");
}

export function bookingUrl(showtimeId: number, jspBase: string): string {
  if (!Number.isSafeInteger(showtimeId) || showtimeId <= 0) {
    throw new Error("showtimeId phải là số nguyên dương.");
  }
  return `${validatedHttpBase(jspBase)}/booking?showtimeId=${showtimeId}`;
}
