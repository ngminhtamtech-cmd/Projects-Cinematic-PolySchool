import Link from "next/link";
import type { Metadata } from "next";
import { FilmCard } from "@/components/FilmCard";
import { ApiError, api } from "@/lib/api-server";
import type { FilmSummary, PageMeta } from "@/lib/types";

export const metadata: Metadata = {
  title: "Danh mục phim",
  description:
    "Toàn bộ phim đang chiếu và sắp chiếu tại hệ thống rạp CineBook. Tìm theo tên phim, diễn viên và đặt vé ngay.",
};

// The admin catalogue is written by the Java app, outside Next cache invalidation.
export const dynamic = "force-dynamic";

const TABS = [
  { key: "", label: "Tất cả" },
  { key: "showing", label: "Đang chiếu" },
  { key: "coming", label: "Sắp chiếu" },
];

type SearchParams = Promise<{ q?: string; status?: string; page?: string }>;

export default async function FilmListPage({ searchParams }: { searchParams: SearchParams }) {
  const { q = "", status = "", page = "1" } = await searchParams;

  const query = new URLSearchParams();
  if (q) query.set("q", q);
  if (status) query.set("status", status);
  query.set("page", page);
  query.set("size", "20");

  let films: FilmSummary[];
  let meta: PageMeta | undefined;
  try {
    const result = await api.get<FilmSummary[]>(`/films?${query}`);
    films = result.data;
    meta = result.meta;
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    const retryHref = `/phim?${query}`;
    return (
      <main className="mx-auto flex min-h-[60vh] w-full max-w-2xl items-center px-4 py-12">
        <section
          role="alert"
          className="w-full rounded-xl border border-line bg-surface p-8 text-center shadow-sm"
        >
          <h1 className="text-2xl font-extrabold text-ink">Chưa thể tải danh mục phim</h1>
          <p className="mt-3 text-sm leading-6 text-muted">
            {error.message} Hệ thống chưa thực hiện thao tác đặt vé nào.
          </p>
          <Link
            href={retryHref}
            className="mt-6 inline-flex rounded-md bg-primary px-5 py-2.5 text-sm font-bold text-white"
          >
            Thử lại
          </Link>
        </section>
      </main>
    );
  }

  const hrefFor = (tab: string) => {
    const p = new URLSearchParams();
    if (q) p.set("q", q);
    if (tab) p.set("status", tab);
    const s = p.toString();
    return s ? `/phim?${s}` : "/phim";
  };

  return (
    <main className="mx-auto w-full max-w-[1280px] px-4 py-8">
      <header className="mb-6">
        <h1 className="text-2xl font-extrabold text-ink md:text-3xl">Danh mục phim</h1>
        <p className="mt-1 text-sm text-muted">
          {meta?.total ?? films.length} phim{q && ` khớp với “${q}”`}
        </p>
      </header>

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <nav className="flex gap-2">
          {TABS.map((tab) => {
            const active = tab.key === status;
            return (
              <Link
                key={tab.key || "all"}
                href={hrefFor(tab.key)}
                className={`rounded-full px-4 py-1.5 text-sm font-semibold transition ${
                  active
                    ? "bg-primary text-white"
                    : "bg-surface text-muted hover:bg-line"
                }`}
              >
                {tab.label}
              </Link>
            );
          })}
        </nav>

        <form action="/phim" className="ml-auto flex gap-2">
          {status && <input type="hidden" name="status" value={status} />}
          <input
            type="search"
            name="q"
            defaultValue={q}
            placeholder="Tìm phim, diễn viên…"
            className="h-10 w-56 rounded-md border border-line bg-surface px-3 text-sm outline-none focus:border-primary"
          />
          <button
            type="submit"
            className="h-10 rounded-md bg-secondary px-4 text-sm font-semibold text-white hover:opacity-90"
          >
            Tìm
          </button>
        </form>
      </div>

      {films.length === 0 ? (
        <p className="rounded-lg bg-surface p-10 text-center text-muted">
          Không tìm thấy phim phù hợp.
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          {films.map((film) => (
            <FilmCard key={film.id} film={film} />
          ))}
        </div>
      )}

      {meta && meta.totalPages > 1 && (
        <nav className="mt-8 flex justify-center gap-2">
          {Array.from({ length: meta.totalPages }, (_, i) => i + 1).map((n) => {
            const p = new URLSearchParams();
            if (q) p.set("q", q);
            if (status) p.set("status", status);
            p.set("page", String(n));
            return (
              <Link
                key={n}
                href={`/phim?${p}`}
                className={`h-9 w-9 rounded-md text-center text-sm leading-9 font-semibold ${
                  n === meta.page ? "bg-primary text-white" : "bg-surface text-muted hover:bg-line"
                }`}
              >
                {n}
              </Link>
            );
          })}
        </nav>
      )}
    </main>
  );
}
