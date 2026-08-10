import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { ShowtimeSelector } from "@/components/ShowtimeSelector";
import { ApiError, api, assetUrl } from "@/lib/api-server";
import { duration, shortDate, STATUS_LABEL } from "@/lib/format";
import type { FilmDetail } from "@/lib/types";

// Java admin writes cannot invalidate Next's data cache, so film state is live.
export const dynamic = "force-dynamic";

type Params = Promise<{ id: string }>;

async function loadFilm(id: string): Promise<FilmDetail | null> {
  try {
    const { data } = await api.get<FilmDetail>(`/films/${id}`);
    return data;
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 400)) return null;
    throw error;
  }
}

/** SEO: day la ly do chinh chon Next.js thay vi React SPA thuan. */
export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { id } = await params;
  const detail = await loadFilm(id);
  if (!detail) return { title: "Không tìm thấy phim" };

  const { film } = detail;
  const poster = assetUrl(film.thumbnail);
  const description =
    film.description?.slice(0, 160) ??
    `Lịch chiếu và đặt vé phim ${film.title} tại hệ thống rạp CineBook.`;

  return {
    title: film.title,
    description,
    openGraph: {
      title: `${film.title} | CineBook`,
      description,
      type: "video.movie",
      images: poster ? [poster] : undefined,
    },
  };
}

export default async function FilmDetailPage({ params }: { params: Params }) {
  const { id } = await params;
  const detail = await loadFilm(id);
  if (!detail) notFound();

  const { film, showtimes, comments } = detail;
  const poster = assetUrl(film.thumbnail);
  const banner = assetUrl(film.banner);

  const facts: Array<[string, string | undefined]> = [
    ["Thể loại", film.categories?.join(", ") || film.format],
    ["Thời lượng", duration(film.durationMinutes)],
    ["Quốc gia", film.country],
    ["Khởi chiếu", shortDate(film.releaseDate)],
    // EX-01: chi hien khi phim co gioi han ngay ket thuc.
    ["Chiếu đến hết", film.endDate ? shortDate(film.endDate) : undefined],
    ["Đạo diễn", film.directors],
    ["Diễn viên", film.actors],
    ["Ngôn ngữ", film.language],
    ["Phân loại", film.ageRating],
  ];

  return (
    <main>
      <section className="relative isolate overflow-hidden bg-dark-bg">
        {banner && (
          <Image
            src={banner}
            alt=""
            fill
            priority
            sizes="100vw"
            className="object-cover opacity-30"
          />
        )}
        <div className="relative mx-auto flex w-full max-w-[1280px] flex-col gap-6 px-4 py-10 md:flex-row">
          <div className="relative aspect-2/3 w-44 shrink-0 overflow-hidden rounded-lg shadow-[var(--shadow-strong)] md:w-56">
            {poster && (
              <Image
                src={poster}
                alt={film.title}
                fill
                priority
                sizes="224px"
                className="object-cover"
              />
            )}
          </div>

          <div className="flex flex-col gap-3 text-white">
            <div className="flex flex-wrap items-center gap-2">
              {film.ageRating && (
                <span className="rounded bg-primary px-2 py-0.5 text-xs font-bold">
                  {film.ageRating}
                </span>
              )}
              {film.status && (
                <span className="rounded bg-white/15 px-2 py-0.5 text-xs font-semibold">
                  {STATUS_LABEL[film.status] ?? film.status}
                </span>
              )}
              {typeof film.rating === "number" && film.rating > 0 && (
                <span className="text-sm font-bold text-gold">⭐ {film.rating.toFixed(1)}</span>
              )}
              {/* EX-01: cung rule voi tang JSP — trang thai do backend tinh theo ngay cua
                  CSDL, tang nay chi hien thi lai. */}
              {film.availability === "EXPIRING_SOON" && (
                <span className="rounded bg-red-600 px-2 py-0.5 text-xs font-bold">
                  Phim sắp hết chiếu!! Đặt vé liền tay
                </span>
              )}
            </div>

            <h1 className="text-3xl font-extrabold md:text-4xl">{film.title}</h1>
            {film.otherTitles && <p className="text-sm text-white/60">{film.otherTitles}</p>}
            {film.description && (
              <p className="max-w-3xl text-sm leading-7 text-white/85">{film.description}</p>
            )}

            <dl className="mt-2 grid grid-cols-1 gap-x-8 gap-y-1 text-sm sm:grid-cols-2">
              {facts
                .filter(([, value]) => value)
                .map(([label, value]) => (
                  <div key={label} className="flex gap-2">
                    <dt className="shrink-0 text-white/50">{label}:</dt>
                    <dd className="font-medium">{value}</dd>
                  </div>
                ))}
            </dl>

            {film.trailerUrl && (
              <a
                href={film.trailerUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="mt-3 w-fit rounded-md bg-primary px-5 py-2.5 text-sm font-bold text-white hover:bg-primary-dark"
              >
                ▶ Xem trailer
              </a>
            )}
          </div>
        </div>
      </section>

      <div className="mx-auto w-full max-w-[1280px] px-4 py-8">
        <ShowtimeSelector showtimes={showtimes} />

        <section className="mt-10">
          <h2 className="mb-4 text-xl font-extrabold text-ink">
            Đánh giá từ khán giả ({comments.length})
          </h2>
          {comments.length === 0 ? (
            <p className="rounded-lg bg-surface p-6 text-sm text-muted">
              Chưa có đánh giá nào cho phim này.
            </p>
          ) : (
            <ul className="flex flex-col gap-3">
              {comments.map((c) => (
                <li key={c.id} className="rounded-lg bg-surface p-4 shadow-[var(--shadow-card)]">
                  <div className="flex items-center gap-2">
                    <strong className="text-sm text-ink">{c.userFullName ?? "Ẩn danh"}</strong>
                    <span className="text-xs text-gold">{"★".repeat(c.rate)}</span>
                    <span className="ml-auto text-xs text-muted">{shortDate(c.createdAt)}</span>
                  </div>
                  {c.content && <p className="mt-1.5 text-sm text-ink/80">{c.content}</p>}
                </li>
              ))}
            </ul>
          )}
        </section>

        <Link href="/phim" className="mt-8 inline-block text-sm font-semibold text-secondary">
          ← Quay lại danh mục phim
        </Link>
      </div>
    </main>
  );
}
