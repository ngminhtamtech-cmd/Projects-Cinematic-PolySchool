import { validatedHttpBase } from "./booking-handoff.ts";

/** Join an API asset path without duplicating a legacy servlet context prefix. */
export function assetUrlForBase(path: string | null | undefined, rawBase: string): string | null {
  if (!path) return null;
  const value = path.trim();
  if (!value) return null;
  if (value.startsWith("http://") || value.startsWith("https://")) return value;

  const base = validatedHttpBase(rawBase);
  const baseUrl = new URL(base);
  if (value.startsWith("//")) return `${baseUrl.protocol}${value}`;

  let assetPath = value.startsWith("/") ? value : `/${value}`;
  const contextPath = baseUrl.pathname.replace(/\/$/, "");
  if (contextPath && (assetPath === contextPath || assetPath.startsWith(`${contextPath}/`))) {
    assetPath = assetPath.slice(contextPath.length) || "/";
  }
  return `${base}${assetPath}`;
}
