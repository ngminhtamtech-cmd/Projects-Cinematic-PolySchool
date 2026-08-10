import type { NextConfig } from "next";

const defaultAssetBase = "http://localhost:8080/Website-ban-ve-xem-phim";
const assetBase = new URL(process.env.NEXT_PUBLIC_ASSET_BASE ?? defaultAssetBase);
if (!["http:", "https:"].includes(assetBase.protocol)) {
  throw new Error("NEXT_PUBLIC_ASSET_BASE must use HTTP(S).");
}
const assetPath = assetBase.pathname.replace(/\/$/, "");
const assetHostIsLocal = ["localhost", "127.0.0.1", "::1"].includes(
  assetBase.hostname,
);

const nextConfig: NextConfig = {
  // Co package-lock.json khac o thu muc home nguoi dung; chot goc workspace vao day
  // de Next khong suy dien nham.
  turbopack: { root: __dirname },
  images: {
    // Next 16 blocks loopback/private upstreams even when they match
    // remotePatterns. Permit that only for the explicitly configured local
    // Tomcat bridge used by this hybrid deployment; remote deployments keep
    // the safer default.
    dangerouslyAllowLocalIP: assetHostIsLocal,
    remotePatterns: [
      // Anh upload van do Tomcat phuc vu trong giai doan qua do.
      {
        protocol: assetBase.protocol.slice(0, -1) as "http" | "https",
        hostname: assetBase.hostname,
        port: assetBase.port,
        pathname: `${assetPath}/assets/**`,
      },
      {
        protocol: assetBase.protocol.slice(0, -1) as "http" | "https",
        hostname: assetBase.hostname,
        port: assetBase.port,
        pathname: `${assetPath}/uploads/**`,
      },
      // Anh minh hoa cua noi dung bien tap (CineTag, Goc Dien Anh...).
      { protocol: "https", hostname: "images.unsplash.com" },
    ],
  },
};

export default nextConfig;
