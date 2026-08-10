import type { Metadata } from "next";
import { Be_Vietnam_Pro, Montserrat } from "next/font/google";
import "./globals.css";

/*
 * Ban JSP nap font bang @import trong CSS - mot request chan render.
 * next/font tu host font, khong con round-trip toi Google va khong bi layout shift.
 */
const beVietnam = Be_Vietnam_Pro({
  variable: "--font-be-vietnam",
  subsets: ["latin", "vietnamese"],
  weight: ["400", "500", "600", "700", "800"],
  display: "swap",
});

const montserrat = Montserrat({
  variable: "--font-montserrat",
  subsets: ["latin", "vietnamese"],
  weight: ["700", "800"],
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "CineBook - Đặt vé xem phim trực tuyến",
    template: "%s | CineBook",
  },
  description:
    "Đặt vé xem phim trực tuyến tại hệ thống rạp CineBook: chọn ghế, chọn combo và thanh toán chỉ trong vài bước.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html
      lang="vi"
      className={`${beVietnam.variable} ${montserrat.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
