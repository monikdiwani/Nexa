import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/context/AuthContext";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
});

export const metadata: Metadata = {
  title: "Nexa — Your Life, Organized",
  description:
    "Nexa is your all-in-one productivity companion. Manage money, notes, tasks, and reminders — all in one beautiful app that syncs instantly across your devices.",
  keywords: ["Nexa", "productivity", "cashbook", "notes", "tasks", "reminders", "finance tracker"],
  authors: [{ name: "Nexa" }],
  openGraph: {
    title: "Nexa — Your Life, Organized",
    description: "Manage money, notes, tasks, and reminders — all in one place.",
    type: "website",
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${inter.variable} font-sans antialiased`}>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
