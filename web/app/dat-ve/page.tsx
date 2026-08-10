import Link from "next/link";
import { redirect } from "next/navigation";
import { ApiError, api } from "@/lib/api-server";
import {
  bookingUrl,
  parseShowtimeId,
} from "@/lib/booking-handoff";
import type { BookingEligibility } from "@/lib/types";

type SearchParams = Promise<{ showtimeId?: string | string[] }>;

const DEFAULT_JSP_BASE = "http://localhost:8080/Website-ban-ve-xem-phim";

function HandoffMessage({ title, message }: { title: string; message: string }) {
  return (
    <main className="mx-auto flex min-h-[60vh] w-full max-w-2xl items-center px-4 py-12">
      <section className="w-full rounded-xl border border-line bg-surface p-8 text-center shadow-sm">
        <h1 className="text-2xl font-extrabold text-ink">{title}</h1>
        <p className="mt-3 text-sm leading-6 text-muted">{message}</p>
        <Link
          href="/phim"
          className="mt-6 inline-flex rounded-md bg-primary px-5 py-2.5 text-sm font-bold text-white"
        >
          Quay lại danh mục phim
        </Link>
      </section>
    </main>
  );
}

export default async function BookingHandoffPage({ searchParams }: { searchParams: SearchParams }) {
  const { showtimeId: rawShowtimeId } = await searchParams;
  const showtimeId = parseShowtimeId(rawShowtimeId);
  if (showtimeId === null) {
    return (
      <HandoffMessage
        title="Mã suất chiếu không hợp lệ"
        message="Vui lòng chọn lại một suất chiếu từ trang phim."
      />
    );
  }

  let eligibility: BookingEligibility;
  try {
    ({ data: eligibility } = await api.get<BookingEligibility>(
      `/showtimes/${showtimeId}/booking-eligibility`,
    ));
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <HandoffMessage
          title="Không tìm thấy suất chiếu"
          message="Suất chiếu có thể đã bị xóa hoặc không còn được phục vụ."
        />
      );
    }
    return (
      <HandoffMessage
        title="Chưa thể kết nối hệ thống đặt vé"
        message="Dịch vụ đang tạm gián đoạn. Vui lòng thử lại sau, hệ thống chưa tạo giữ ghế."
      />
    );
  }

  if (!eligibility.eligible) {
    return (
      <HandoffMessage
        title="Suất chiếu không còn nhận đặt vé"
        message={eligibility.message || "Vui lòng chọn một suất chiếu khác."}
      />
    );
  }

  let target: string;
  try {
    target = bookingUrl(
      showtimeId,
      process.env.NEXT_PUBLIC_JSP_BASE ?? DEFAULT_JSP_BASE,
    );
  } catch {
    return (
      <HandoffMessage
        title="Cấu hình đặt vé chưa sẵn sàng"
        message="Địa chỉ hệ thống đặt vé không hợp lệ. Vui lòng báo cho quản trị viên."
      />
    );
  }
  redirect(target);
}
