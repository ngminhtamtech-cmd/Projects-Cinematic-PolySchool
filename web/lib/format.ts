/** Dinh dang hien thi theo chuan Viet Nam, dung chung cho toan bo giao dien. */

const VND = new Intl.NumberFormat("vi-VN");

export function money(value: number | string | null | undefined): string {
  const n = Number(value ?? 0);
  return `${VND.format(Math.max(0, n))} đ`;
}

export function shortDate(iso?: string | null): string {
  if (!iso) return "";
  const [y, m, d] = iso.slice(0, 10).split("-");
  return d && m && y ? `${d}/${m}/${y}` : iso;
}

export function timeOfDay(iso?: string | null): string {
  if (!iso) return "";
  return iso.slice(11, 16);
}

const WEEKDAYS = ["Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"];

export function weekday(iso?: string | null): string {
  if (!iso) return "";
  const date = new Date(iso.slice(0, 10));
  return Number.isNaN(date.getTime()) ? "" : WEEKDAYS[date.getDay()];
}

export function duration(minutes?: number | null): string {
  if (!minutes) return "";
  return `${minutes} phút`;
}

export const STATUS_LABEL: Record<string, string> = {
  showing: "Đang chiếu",
  coming: "Sắp chiếu",
  ended: "Ngừng chiếu",
};
