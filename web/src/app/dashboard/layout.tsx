"use client";

import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  LayoutDashboard, DollarSign, StickyNote, CheckSquare,
  Bell, BarChart2, Settings, Menu, X, ChevronRight,
  Sun, Moon, LogOut, Search
} from "lucide-react";

const NAV = [
  { href: "/dashboard", icon: LayoutDashboard, label: "Dashboard" },
  { href: "/dashboard/money", icon: DollarSign, label: "Money" },
  { href: "/dashboard/notes", icon: StickyNote, label: "Notes" },
  { href: "/dashboard/tasks", icon: CheckSquare, label: "Tasks" },
  { href: "/dashboard/reminders", icon: Bell, label: "Reminders" },
  { href: "/dashboard/reports", icon: BarChart2, label: "Reports" },
  { href: "/dashboard/settings", icon: Settings, label: "Settings" },
];

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const { user, signOut } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [dark, setDark] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Auth guard
  useEffect(() => {
    if (!user) router.push("/login");
  }, [user, router]);

  // Persist dark mode
  useEffect(() => {
    const saved = localStorage.getItem("nexa-dark");
    if (saved === "true") {
      setDark(true);
      document.documentElement.classList.add("dark");
    }
  }, []);

  const toggleDark = () => {
    const next = !dark;
    setDark(next);
    if (next) document.documentElement.classList.add("dark");
    else document.documentElement.classList.remove("dark");
    localStorage.setItem("nexa-dark", String(next));
  };

  const handleSignOut = async () => {
    await signOut();
    router.push("/");
  };

  const initials = user?.displayName
    ?.split(" ").map(n => n[0]).join("").toUpperCase().slice(0, 2) ?? "N";

  const isActive = (href: string) =>
    href === "/dashboard" ? pathname === "/dashboard" : pathname.startsWith(href);

  const NavLinks = ({ onClose }: { onClose?: () => void }) => (
    <nav className="flex flex-col gap-0.5">
      {NAV.map(({ href, icon: Icon, label }) => {
        const active = isActive(href);
        return (
          <Link key={href} href={href}
            onClick={onClose}
            className="nav-item"
            style={active ? {
              background: "var(--primary-surface)",
              color: "var(--primary)",
              fontWeight: 600
            } : {}}>
            {/* Active indicator */}
            {active && (
              <span className="absolute left-0 top-2 bottom-2 w-0.5 rounded-r-full"
                style={{ background: "var(--primary)" }} />
            )}
            <Icon size={18} strokeWidth={active ? 2.2 : 1.8} />
            <span>{label}</span>
          </Link>
        );
      })}
    </nav>
  );

  if (!user) return null;

  return (
    <div className="flex h-screen overflow-hidden" style={{ background: "var(--bg)" }}>

      {/* ── Desktop Sidebar ── */}
      <aside className="hidden md:flex flex-col w-60 flex-shrink-0 border-r"
        style={{ background: "var(--surface)", borderColor: "var(--divider)" }}>

        {/* Logo */}
        <div className="flex items-center gap-3 px-5 py-5 border-b"
          style={{ borderColor: "var(--divider)" }}>
          <div className="w-8 h-8 rounded-xl overflow-hidden flex items-center justify-center bg-white shadow-sm">
            <img src="/nexa-logo.svg" alt="Nexa Logo" className="w-full h-full object-cover" />
          </div>
          <div>
            <p className="font-black text-base leading-tight" style={{ color: "var(--text-primary)" }}>Nexa</p>
            <p className="text-xs" style={{ color: "var(--text-hint)" }}>Web App</p>
          </div>
        </div>

        {/* Nav */}
        <div className="flex-1 overflow-y-auto px-3 py-4">
          <NavLinks />
        </div>

        {/* User section */}
        <div className="border-t p-3 space-y-1" style={{ borderColor: "var(--divider)" }}>
          {/* Dark toggle */}
          <button onClick={toggleDark}
            className="nav-item w-full text-left">
            {dark ? <Sun size={18} strokeWidth={1.8} /> : <Moon size={18} strokeWidth={1.8} />}
            <span>{dark ? "Light Mode" : "Dark Mode"}</span>
            <div className="ml-auto relative w-9 h-5 rounded-full transition-colors"
              style={{ background: dark ? "var(--primary)" : "var(--stroke)" }}>
              <span className="absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-all"
                style={{ left: dark ? "17px" : "2px" }} />
            </div>
          </button>

          {/* User pill */}
          <div className="flex items-center gap-2.5 p-2.5 rounded-xl"
            style={{ background: "var(--bg)" }}>
            <div className="w-8 h-8 rounded-xl nexa-gradient flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
              {initials}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold truncate" style={{ color: "var(--text-primary)" }}>
                {user.displayName ?? "Nexa User"}
              </p>
              <p className="text-xs truncate" style={{ color: "var(--text-hint)" }}>{user.email}</p>
            </div>
            <button onClick={handleSignOut} className="btn-icon flex-shrink-0" title="Sign out">
              <LogOut size={15} />
            </button>
          </div>
        </div>
      </aside>

      {/* ── Mobile Sidebar Overlay ── */}
      <AnimatePresence>
        {sidebarOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              className="fixed inset-0 z-40 md:hidden"
              style={{ background: "rgba(0,0,0,0.4)" }}
              onClick={() => setSidebarOpen(false)}
            />
            <motion.aside
              initial={{ x: -280 }} animate={{ x: 0 }} exit={{ x: -280 }}
              transition={{ type: "spring", damping: 28, stiffness: 300 }}
              className="fixed left-0 top-0 bottom-0 w-72 z-50 flex flex-col md:hidden border-r"
              style={{ background: "var(--surface)", borderColor: "var(--divider)" }}>
              <div className="flex items-center justify-between px-5 py-5 border-b"
                style={{ borderColor: "var(--divider)" }}>
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-xl overflow-hidden flex items-center justify-center bg-white shadow-sm">
                    <img src="/nexa-logo.svg" alt="Nexa Logo" className="w-full h-full object-cover" />
                  </div>
                  <p className="font-black text-base" style={{ color: "var(--text-primary)" }}>Nexa</p>
                </div>
                <button onClick={() => setSidebarOpen(false)} className="btn-icon">
                  <X size={20} />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto px-3 py-4">
                <NavLinks onClose={() => setSidebarOpen(false)} />
              </div>

              <div className="border-t p-3" style={{ borderColor: "var(--divider)" }}>
                <div className="flex items-center gap-2.5 p-2.5 rounded-xl"
                  style={{ background: "var(--bg)" }}>
                  <div className="w-8 h-8 rounded-xl nexa-gradient flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                    {initials}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold truncate" style={{ color: "var(--text-primary)" }}>
                      {user.displayName ?? "Nexa User"}
                    </p>
                    <p className="text-xs truncate" style={{ color: "var(--text-hint)" }}>{user.email}</p>
                  </div>
                </div>
              </div>
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      {/* ── Main Content ── */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">

        {/* Top bar */}
        <header className="flex items-center gap-3 px-4 py-3 border-b flex-shrink-0"
          style={{ background: "var(--surface)", borderColor: "var(--divider)" }}>

          {/* Mobile menu button */}
          <button onClick={() => setSidebarOpen(true)}
            className="btn-icon md:hidden">
            <Menu size={20} />
          </button>

          {/* Page title - shown from pathname */}
          <div className="flex-1">
            <p className="font-bold text-base hidden md:block" style={{ color: "var(--text-primary)" }}>
              {NAV.find(n => isActive(n.href))?.label ?? "Dashboard"}
            </p>
            {/* Mobile: show logo */}
            <div className="flex items-center gap-2 md:hidden">
              <div className="w-6 h-6 rounded-lg overflow-hidden flex items-center justify-center bg-white shadow-sm">
                <img src="/nexa-logo.svg" alt="Nexa Logo" className="w-full h-full object-cover" />
              </div>
              <span className="font-black text-sm" style={{ color: "var(--text-primary)" }}>Nexa</span>
            </div>
          </div>

          {/* Right actions */}
          <div className="flex items-center gap-1">
            <button onClick={toggleDark} className="btn-icon hidden md:flex">
              {dark ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <div className="w-8 h-8 rounded-xl nexa-gradient flex items-center justify-center text-white text-xs font-bold cursor-pointer"
              onClick={() => router.push("/dashboard/settings")}>
              {initials}
            </div>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-y-auto">
          {children}
        </main>

        {/* Mobile bottom nav */}
        <nav className="md:hidden flex border-t flex-shrink-0"
          style={{ background: "var(--surface)", borderColor: "var(--divider)" }}>
          {NAV.slice(0, 5).map(({ href, icon: Icon, label }) => {
            const active = isActive(href);
            return (
              <Link key={href} href={href}
                className="flex-1 flex flex-col items-center gap-0.5 py-2.5 transition-colors"
                style={{ color: active ? "var(--primary)" : "var(--text-hint)" }}>
                <Icon size={20} strokeWidth={active ? 2.2 : 1.8} />
                <span className="text-xs font-medium">{label.split(" ")[0]}</span>
              </Link>
            );
          })}
        </nav>
      </div>
    </div>
  );
}

