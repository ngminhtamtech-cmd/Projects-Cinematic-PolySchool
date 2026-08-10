"use client";

import type { ApiEnvelope, PageMeta } from "./types";

/**
 * Client goi API tu phia TRINH DUYET. Luon di qua BFF proxy same-origin
 * (/api/proxy/...) nen cookie JSESSIONID duoc gui tu dong va khong can CORS.
 */

const PROXY = "/api/proxy";

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

/** Doc CSRF token tu cookie XSRF-TOKEN do CsrfFilter phat ra (co y khong HttpOnly). */
function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

async function ensureCsrf(): Promise<string> {
  const existing = csrfToken();
  if (existing) return existing;
  // Chua co token: goi endpoint an toan de server gieo token vao session + cookie.
  const res = await fetch(`${PROXY}/auth/csrf`, { headers: { Accept: "application/json" } });
  const body = (await res.json()) as ApiEnvelope<{ csrfToken: string }>;
  return body.data?.csrfToken ?? csrfToken() ?? "";
}

async function request<T>(
  path: string,
  init: { method?: string; body?: unknown } = {},
): Promise<{ data: T; meta?: PageMeta }> {
  const method = init.method ?? "GET";
  const headers: Record<string, string> = { Accept: "application/json" };

  if (!["GET", "HEAD"].includes(method)) {
    headers["Content-Type"] = "application/json";
    headers["X-CSRF-Token"] = await ensureCsrf();
  }

  const res = await fetch(`${PROXY}${path}`, {
    method,
    headers,
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });

  let payload: ApiEnvelope<T>;
  try {
    payload = (await res.json()) as ApiEnvelope<T>;
  } catch {
    throw new ApiError(res.status, "INVALID_RESPONSE", "Máy chủ trả về dữ liệu không hợp lệ.");
  }

  if (!res.ok || payload.error) {
    throw new ApiError(
      res.status,
      payload.error?.code ?? "UNKNOWN",
      payload.error?.message ?? `Lỗi ${res.status}`,
    );
  }
  return { data: payload.data as T, meta: payload.meta };
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: "POST", body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: "PUT", body }),
  del: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};
