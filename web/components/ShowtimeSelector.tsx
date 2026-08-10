"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { money, shortDate, timeOfDay, weekday } from "@/lib/format";
import type { Showtime } from "@/lib/types";

/**
 * Bo chon suat chieu dung chung.
 *
 * Thay the ba ban cai dat gan giong nhau cua ban JSP:
 *   - assets/js/seat-map.js        (loadDatesForFilm + filterShowtimes)
 *   - WEB-INF/views/film/detail.jsp (initDateTabs + renderShowtimeGrid + selectShowtime)
 *   - WEB-INF/views/showtime/list.jsp (initDateTabs + openShowtimePanel)
 *
 * Ban JSP con mot loi that: deselectShowtime() goi selectedSeats.clear() tren mot Array
 * va goi hai ham khong ton tai (updateSummary, renderSeatMap). O day trang thai do React
 * quan ly nen lop loi do bien mat hoan toan.
 */
export function ShowtimeSelector({ showtimes }: { showtimes: Showtime[] }) {
  const dates = useMemo(
    () => [...new Set(showtimes.map((s) => s.startTime.slice(0, 10)))].sort(),
    [showtimes],
  );
  const cinemas = useMemo(() => {
    const map = new Map<number, string>();
    showtimes.forEach((s) => map.set(s.cinemaId, s.cinemaName ?? `Rạp ${s.cinemaId}`));
    return [...map.entries()].map(([id, name]) => ({ id, name }));
  }, [showtimes]);

  const [activeDate, setActiveDate] = useState(dates[0] ?? "");
  const [activeCinema, setActiveCinema] = useState<number | null>(null);

  const visible = useMemo(
    () =>
      showtimes
        .filter((s) => s.startTime.slice(0, 10) === activeDate)
        .filter((s) => activeCinema === null || s.cinemaId === activeCinema)
        .sort((a, b) => a.startTime.localeCompare(b.startTime)),
    [showtimes, activeDate, activeCinema],
  );

  // Gom theo rap roi theo dinh dang, giong cach trinh bay cua ban JSP.
  const grouped = useMemo(() => {
    const byCinema = new Map<number, { name: string; groups: Map<string, Showtime[]> }>();
    for (const s of visible) {
      if (!byCinema.has(s.cinemaId)) {
        byCinema.set(s.cinemaId, { name: s.cinemaName ?? "", groups: new Map() });
      }
      const entry = byCinema.get(s.cinemaId)!;
      const key = s.formatVersionDisplay ?? "2D";
      if (!entry.groups.has(key)) entry.groups.set(key, []);
      entry.groups.get(key)!.push(s);
    }
    return [...byCinema.values()];
  }, [visible]);

  if (showtimes.length === 0) {
    return (
      <section>
        <h2 className="mb-4 text-xl font-extrabold text-ink">Lịch chiếu</h2>
        <p className="rounded-lg bg-surface p-6 text-sm text-muted">
          Phim này hiện chưa có lịch chiếu.
        </p>
      </section>
    );
  }

  return (
    <section>
      <h2 className="mb-4 text-xl font-extrabold text-ink">Lịch chiếu</h2>

      <div className="mb-4 flex gap-2 overflow-x-auto pb-1">
        {dates.map((d) => {
          const active = d === activeDate;
          return (
            <button
              key={d}
              type="button"
              onClick={() => setActiveDate(d)}
              aria-pressed={active}
              className={`shrink-0 rounded-lg px-4 py-2 text-center transition ${
                active ? "bg-primary text-white" : "bg-surface text-muted hover:bg-line"
              }`}
            >
              <span className="block text-[11px] font-medium">{weekday(d)}</span>
              <span className="block text-sm font-bold">{shortDate(d)}</span>
            </button>
          );
        })}
      </div>

      {cinemas.length > 1 && (
        <div className="mb-4 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setActiveCinema(null)}
            className={`rounded-full px-3 py-1 text-xs font-semibold ${
              activeCinema === null ? "bg-secondary text-white" : "bg-surface text-muted"
            }`}
          >
            Tất cả rạp
          </button>
          {cinemas.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => setActiveCinema(c.id)}
              className={`rounded-full px-3 py-1 text-xs font-semibold ${
                activeCinema === c.id ? "bg-secondary text-white" : "bg-surface text-muted"
              }`}
            >
              {c.name}
            </button>
          ))}
        </div>
      )}

      {grouped.length === 0 ? (
        <p className="rounded-lg bg-surface p-6 text-sm text-muted">
          Không có suất chiếu phù hợp với bộ lọc này.
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          {grouped.map((cinema) => (
            <article key={cinema.name} className="rounded-lg bg-surface p-4 shadow-[var(--shadow-card)]">
              <h3 className="text-base font-bold text-ink">{cinema.name}</h3>
              {[...cinema.groups.entries()].map(([format, list]) => (
                <div key={format} className="mt-3">
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">
                    {format}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {list.map((s) => (
                      <Link
                        key={s.id}
                        href={`/dat-ve?showtimeId=${s.id}`}
                        title={`${timeOfDay(s.startTime)} — ${money(s.basePrice)}`}
                        className="rounded-md border border-line px-4 py-2 text-sm font-bold text-ink transition hover:border-primary hover:bg-primary hover:text-white"
                      >
                        {timeOfDay(s.startTime)}
                      </Link>
                    ))}
                  </div>
                </div>
              ))}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
