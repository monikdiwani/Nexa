"use client";

import { useAuth } from "@/context/AuthContext";
import { useRouter, usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  LayoutDashboard, DollarSign, StickyNote, CheckSquare,
  Bell, PieChart, Settings, LogOut, Search, Menu, X,
  Wallet, Sun, Moon, ChevronRight
} from "lucide-react";

const navItems = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/dashboard/money", label: "Money", icon: DollarSign },
  { href: "/dashboard/notes", label: "Notes", icon: StickyNote },
  { href: "/dashboard/tasks", label: "Tasks", icon: CheckSquare },
  { href: "/dashboard/reminders", label: "Reminders", icon: Bell },
  { href: "/dashboard/reports", label: "Reports", icon: PieChart },
  { href: "/dashboard/settings", label: "Settings", icon: Settings },
];

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const { user, loading, signOut } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [dark, setDark] = useState(false);

  useEffect(() => {
    if (!loading && !user) router.push("/login");
  }, [user, loading, router]);

  useEffect(() => {
    if (dark) document.documentElement.classList.add("dark");
    else document.documentElement.classList.remove("dark");
  }, [dark]);

  if (loading || !user) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: "var(--bg)" }}>
        <div className="flex flex-col items-center gap-4">
          <div className="w-12 h-12 rounded-2xl nexa-gradient flex items-center justify-center shadow-lg">
            <span className="text-xl font-black text-white">N</span>
          </div>
          <div className="flex gap-1">
            {[0,1,2].map(i => (
              <div key={i} className="w-2 h-2 rounded-full pulse-dot"
                style={{ background: "var(--primary)", animationDelay: `${i * 0.2}s` }} />
            ))}
          </div>
        </div>
      </div>
    );
  }

  const initials = user.displayName
    ? user.displayName.split(" ").map(n => n[0]).join("").toUpperCase().slice(0, 2)
    : user.email?.[0].toUpperCase() ?? "N";

  const Sidebar = ({ mobile = false }) => (
    <div className={`flex flex-col h-full ${mobile ? "" : "w-60"}`}
      style={{ background: "var(--surface)", borderRight: "1px solid var(--divider)" }}>
      {/* Logo */}
      <div className="flex items-center gap-3 px-5 py-5 border-b" style={{ borderColor: "var(--divider)" }}>
        <div className="w-9 h-9 rounded-xl nexa-gradient flex items-center justify-center shadow-md flex-shrink-0">
          <span className="text-base font-black text-white">N</span>
        </div>
        <div>
          <div className="font-bold text-base" style={{ color: "var(--text-primary)" }}>Nexa</div>
          <div className="text-xs" style={{ color: "var(--text-hint)" }}>Web App</div>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
        {navItems.map(({ href, label, icon: Icon }) => {
          const active = pathname === href || (href !== "/dashboard" && pathname.startsWith(href));
          return (
            <Link key={href} href={href} onClick={() => setSidebarOpen(false)}
              className={`nav-item ${active ? "active" : ""}`}>
              <Icon className="w-4.5 h-4.5 flex-shrink-0" style={{ width: 18, height: 18 }} />
              <span className="flex-1">{label}</span>
              {active && <ChevronRight className="w-3.5 h-3.5 opacity-40" />}
            </Link>
          );
        })}
      </nav>

      {/* Bottom user card */}
      <div className="p-3 border-t" style={{ borderColor: "var(--divider)" }}>
        <div className="flex items-center gap-3 p-2 rounded-xl" style={{ background: "var(--bg)" }}>
          <div className="w-8 h-8 rounded-full nexa-gradient flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
            {initials}
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-xs font-semibold truncate" style={{ color: "var(--text-primary)" }}>
              {user.displayName ?? "Nexa User"}
            </div>
            <div className="text-xs truncate" style={{ color: "var(--text-hint)" }}>{user.email}</div>
          </div>
          <button onClick={signOut} className="p-1.5 rounded-lg hover:bg-red-50 text-red-400 transition-colors flex-shrink-0" title="Sign out">
            <LogOut className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </div>
  );

  return (
    <div className="flex h-screen overflow-hidden" style={{ background: "var(--bg)" }}>
      {/* Desktop Sidebar */}
      <div className="hidden md:flex flex-col h-full flex-shrink-0">
        <Sidebar />
      </div>

      {/* Mobile Sidebar Overlay */}
      <AnimatePresence>
        {sidebarOpen && (
          <>
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              className="fixed inset-0 bg-black/50 z-40 md:hidden" onClick={() => setSidebarOpen(false)} />
            <motion.div initial={{ x: -280 }} animate={{ x: 0 }} exit={{ x: -280 }}
              transition={{ type: "spring", damping: 25, stiffness: 300 }}
              className="fixed left-0 top-0 bottom-0 w-72 z-50 md:hidden shadow-2xl">
              <Sidebar mobile />
            </motion.div>
          </>
        )}
      </AnimatePresence>

      {/* Main content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top bar */}
        <header className="flex items-center gap-3 px-4 md:px-6 py-3 border-b flex-shrink-0"
          style={{ background: "var(--surface)", borderColor: "var(--divider)" }}>
          <button className="md:hidden p-2 rounded-xl transition-colors hover:bg-gray-100" style={{ color: "var(--text-secondary)" }}
            onClick={() => setSidebarOpen(true)}>
            <Menu className="w-5 h-5" />
          </button>

          {/* Search */}
          <div className="flex-1 max-w-md relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4" style={{ color: "var(--text-hint)" }} />
            <input placeholder="Search everything..."
              className="w-full pl-9 pr-4 py-2 text-sm rounded-xl border"
              style={{ background: "var(--bg)", borderColor: "var(--divider)", color: "var(--text-primary)" }}
              onKeyDown={(e) => e.key === "Enter" && router.push("/dashboard/search")} />
          </div>

          <div className="flex items-center gap-2 ml-auto">
            {/* Dark mode */}
            <button onClick={() => setDark(!dark)} className="p-2 rounded-xl transition-colors hover:bg-gray-100"
              style={{ color: "var(--text-secondary)" }}>
              {dark ? <Sun className="w-4.5 h-4.5" style={{width:18,height:18}} /> : <Moon className="w-4.5 h-4.5" style={{width:18,height:18}} />}
            </button>
            {/* Avatar */}
            <div className="w-8 h-8 rounded-full nexa-gradient flex items-center justify-center text-white text-xs font-bold cursor-pointer"
              onClick={() => router.push("/dashboard/settings")}>
              {initials}
            </div>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-y-auto">
          <div className="page-enter">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
