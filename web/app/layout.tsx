import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Grassroot Advocacy for Tinubu · GAT 2027",
  description: "Mobilising every polling unit for Tinubu 2027 — register, refer, and report from the grassroots.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full">
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
