import Image from "next/image";
import Link from "next/link";
import { assetUrl } from "@/lib/api-server";
import { duration, shortDate, STATUS_LABEL } from "@/lib/format";
import type { FilmSummary } from "@/lib/types";

/**
 * The phim dung chung cho trang danh muc, trang chu va ket qua tim kiem.
 * Ban JSP lap lai markup nay o home.jsp, film/list.jsp va public-header.jspf.
 */
export function FilmCard({ film }: { film: FilmSummary }) {
  const poster = assetUrl(film.thumbnail);

  return (
    <Link
      href={`/phim/${film.id}`}
      className="group flex flex-col overflow-hidden rounded-lg bg-surface shadow-[var(--shadow-card)] transition hover:-translate-y-1 hover:shadow-[var(--shadow-strong)]"
    >
      <div className="relative aspect-2/3 overflow-hidden bg-surface-dark">
        {poster ? (
          <Image
            src={poster}
            alt={film.title}
            fill
            sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 20vw"
            className="object-cover transition duration-300 group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-white/60">
            Chưa có poster
          </div>
        )}

        {film.ageRating && (
          <span className="absolute left-2 top-2 rounded bg-primary px-1.5 py-0.5 text-[11px] font-bold text-white">
            {film.ageRating}
          </span>
        )}
        {typeof film.rating === "number" && film.rating > 0 && (
          <span className="absolute right-2 top-2 rounded bg-black/70 px-1.5 py-0.5 text-[11px] font-bold text-gold">
            ⭐ {film.rating.toFixed(1)}
          </span>
        )}
      </div>

      <div className="flex flex-1 flex-col gap-1 p-3">
        <h3 className="line-clamp-2 text-sm font-bold text-ink group-hover:text-primary">
          {film.title}
        </h3>
        <p className="text-xs text-muted">
          {[duration(film.durationMinutes), film.format].filter(Boolean).join(" · ")}
        </p>
        <p className="mt-auto pt-1 text-xs text-muted">
          {film.status === "coming"
            ? `Khởi chiếu ${shortDate(film.releaseDate)}`
            : STATUS_LABEL[film.status ?? ""] ?? ""}
        </p>
      </div>
    </Link>
  );
}
