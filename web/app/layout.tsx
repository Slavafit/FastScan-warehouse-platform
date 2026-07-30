import type { Metadata } from "next";
import "./globals.css";

const siteUrl =
  process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: "FastScan Warehouse Platform",
  description:
    "Warehouse operations, client accounts, analytics, and notifications in one FastScan workspace.",
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
  openGraph: {
    title: "FastScan Warehouse Platform",
    description:
      "Warehouse operations, client accounts, analytics, and notifications in one workspace.",
    images: [{ url: "/og.png", width: 1672, height: 939 }],
    type: "website",
  },
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ru">
      <body>{children}</body>
    </html>
  );
}
