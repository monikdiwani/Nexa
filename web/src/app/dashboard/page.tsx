"use client";

import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, orderBy, onSnapshot, limit, where } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion } from "framer-motion";
import Link from "next/link";
import {
  CheckSquare, Bell, DollarSign, StickyNote, TrendingUp,
  TrendingDown, Plus, ChevronRight, ArrowUpRight, BarChart2,
  Star, Clock, Target
} from "lucide-react";

function getGreeting(name: string) {
  const h = new Date().getHours();
  const g = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
  return `${g}, ${name?.split(" ")[0] ?? "there"} 👋`;
}

interface Task { id: string; title: string; priority: string; isCompleted: boolean; dueDate: number | null; }
interface Reminder { id: string; title: string; triggerTime: number; priority: string; isCompleted: boolean; category: string; }
interface Entry { id: string; type: string; amount: number; particulars: string; date: number; }

const categoryEmoji: Record<string, string> = {
  BILL: "📄", MEETING: "👥", TASK: "✅", MEDICINE: "💊", SHOPPING: "🛒", CUSTOM: "⏰"
};

export default function DashboardPage() {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [entries, setEntries] = useState<Entry[]>([]);
  const [loadingData, setLoadingData] = useState(true);

  useEffect(() => {
    if (!user) return;
    const uid = user.uid;

    // Tasks listener
    const tUnsub = onSnapshot(
      query(collection(db, "users", uid, "tasks"), where("isCompleted", "==", false), orderBy("createdAt", "desc"), limit(5)),
      (snap) => setTasks(snap.docs.map(d => ({ id: d.id, ...d.data() } as Task)))
    );

    // Reminders listener
    const rUnsub = onSnapshot(
      query(collection(db, "users", uid, "reminders"), where("isCompleted", "==", false), orderBy("triggerTime", "asc"), limit(5)),
      (snap) => { setReminders(snap.docs.map(d => ({ id: d.id, ...d.data() } as Reminder))); setLoadingData(false); }
    );

    return () => { tUnsub(); rUnsub(); };
  }, [user]);

  // Compute totals
  const cashIn = entries.filter(e => e.type === "CASH_IN").reduce((s, e) => s + e.amount, 0);
  const cashOut = entries.filter(e => e.type === "CASH_OUT").reduce((s, e) => s + e.amount, 0);
  const net = cashIn - cashOut;

  const pendingTasks = tasks.filter(t => !t.isCompleted);
  const pendingReminders = reminders.filter(r => !r.isCompleted && r.triggerTime > Date.now());

  const card = ({ children, className = "" }: { children: React.ReactNode; className?: string }) => (
    <div className={`rounded-2xl p-5 ${className}`} style={{ background: "var(--surface)", boxShadow: "var(--shadow-card)" }}>
      {children}
    </div>
  );

  const fadeIn = (delay = 0) => ({
    initial: { opacity: 0, y: 16 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: 0.35, delay, ease: "easeOut" as const }
  });

  if (loadingData) {
    return (
      <div className="p-6 space-y-4">
        {[...Array(4)].map((_, i) => (
          <div key={i} className="h-24 rounded-2xl shimmer" />
        ))}
      </div>
    );
  }

  return (
    <div className="p-4 md:p-6 max-w-7xl mx-auto space-y-5 pb-10">

      {/* Greeting Banner */}
      <motion.div {...fadeIn(0)}
        className="relative overflow-hidden rounded-2xl p-6"
        style={{ background: "linear-gradient(135deg, #3949AB 0%, #1A237E 100%)" }}>
        <div className="absolute top-0 right-0 w-40 h-40 rounded-full opacity-10" style={{ background: "white", transform: "translate(30%, -30%)" }} />
        <div className="absolute bottom-0 left-0 w-28 h-28 rounded-full opacity-10" style={{ background: "white", transform: "translate(-30%, 30%)" }} />
        <div className="relative z-10">
          <h1 className="text-xl md:text-2xl font-bold text-white mb-1">{getGreeting(user?.displayName ?? "")}</h1>
          <p className="text-blue-200 text-sm">Here's your overview for today</p>
          <div className="flex flex-wrap gap-4 mt-4">
            <div className="flex items-center gap-2 text-white">
              <CheckSquare className="w-4 h-4 text-blue-300" />
              <span className="text-sm font-medium">{pendingTasks.length} tasks pending</span>
            </div>
            <div className="flex items-center gap-2 text-white">
              <Bell className="w-4 h-4 text-blue-300" />
              <span className="text-sm font-medium">{pendingReminders.length} reminders upcoming</span>
            </div>
          </div>
        </div>
      </motion.div>

      {/* Balance Cards */}
      <motion.div {...fadeIn(0.05)} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {[
          { label: "Cash In", value: cashIn, icon: TrendingUp, color: "var(--positive)", bg: "var(--cash-in-bg)" },
          { label: "Cash Out", value: cashOut, icon: TrendingDown, color: "var(--negative)", bg: "var(--cash-out-bg)" },
          { label: "Net Balance", value: net, icon: BarChart2, color: net >= 0 ? "var(--positive)" : "var(--negative)", bg: net >= 0 ? "var(--cash-in-bg)" : "var(--cash-out-bg)" },
        ].map(({ label, value, icon: Icon, color, bg }) => (
          <div key={label} className="rounded-2xl p-5 flex items-center gap-4"
            style={{ background: "var(--surface)", boxShadow: "var(--shadow-card)" }}>
            <div className="w-11 h-11 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: bg }}>
              <Icon className="w-5 h-5" style={{ color }} />
            </div>
            <div>
              <div className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>{label}</div>
              <div className="text-xl font-bold" style={{ color }}>
                ₹{Math.abs(value).toLocaleString("en-IN", { minimumFractionDigits: 0 })}
              </div>
            </div>
          </div>
        ))}
      </motion.div>

      {/* Quick Actions */}
      <motion.div {...fadeIn(0.1)}>
        <h2 className="text-sm font-semibold mb-3" style={{ color: "var(--text-secondary)" }}>QUICK ACTIONS</h2>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {[
            { label: "Add Expense", icon: DollarSign, href: "/dashboard/money", gradient: "linear-gradient(135deg,#7C83F7,#5C6BC0)" },
            { label: "New Task", icon: CheckSquare, href: "/dashboard/tasks", gradient: "linear-gradient(135deg,#43A047,#2E7D32)" },
            { label: "New Note", icon: StickyNote, href: "/dashboard/notes", gradient: "linear-gradient(135deg,#FFA726,#E65100)" },
            { label: "Set Reminder", icon: Bell, href: "/dashboard/reminders", gradient: "linear-gradient(135deg,#26C6DA,#00838F)" },
          ].map(({ label, icon: Icon, href, gradient }) => (
            <Link key={label} href={href}
              className="card-hover rounded-2xl p-4 flex flex-col items-center justify-center gap-2 text-center text-white font-semibold text-sm"
              style={{ background: gradient, boxShadow: "0 4px 15px rgba(0,0,0,0.15)" }}>
              <Icon className="w-6 h-6" />
              {label}
            </Link>
          ))}
        </div>
      </motion.div>

      {/* Tasks + Reminders */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* Pending Tasks */}
        <motion.div {...fadeIn(0.15)}>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-semibold" style={{ color: "var(--text-primary)" }}>Pending Tasks</h2>
            <Link href="/dashboard/tasks" className="text-xs font-medium flex items-center gap-1" style={{ color: "var(--primary)" }}>
              View all <ChevronRight className="w-3.5 h-3.5" />
            </Link>
          </div>
          <div className="space-y-2">
            {pendingTasks.length === 0 ? (
              <div className="rounded-2xl p-8 text-center" style={{ background: "var(--surface)" }}>
                <Target className="w-8 h-8 mx-auto mb-2" style={{ color: "var(--text-hint)" }} />
                <p className="text-sm" style={{ color: "var(--text-secondary)" }}>All caught up! No pending tasks.</p>
                <Link href="/dashboard/tasks" className="inline-flex items-center gap-1 mt-3 text-sm font-semibold" style={{ color: "var(--primary)" }}>
                  <Plus className="w-4 h-4" /> Add a task
                </Link>
              </div>
            ) : pendingTasks.slice(0, 4).map(task => (
              <Link key={task.id} href="/dashboard/tasks"
                className="flex items-center gap-3 p-3 rounded-xl card-hover"
                style={{ background: "var(--surface)", boxShadow: "var(--shadow-card)" }}>
                <div className="w-5 h-5 rounded-full border-2 flex-shrink-0" style={{ borderColor: "var(--stroke)" }} />
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium truncate" style={{ color: "var(--text-primary)" }}>{task.title}</div>
                  {task.dueDate && (
                    <div className="text-xs flex items-center gap-1 mt-0.5" style={{ color: "var(--text-hint)" }}>
                      <Clock className="w-3 h-3" />
                      {new Date(task.dueDate).toLocaleDateString("en-IN", { month: "short", day: "numeric" })}
                    </div>
                  )}
                </div>
                <span className={`chip text-xs ${task.priority === "HIGH" ? "priority-high" : task.priority === "LOW" ? "priority-low" : "priority-medium"}`}>
                  {task.priority}
                </span>
              </Link>
            ))}
          </div>
        </motion.div>

        {/* Upcoming Reminders */}
        <motion.div {...fadeIn(0.2)}>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-semibold" style={{ color: "var(--text-primary)" }}>Upcoming Reminders</h2>
            <Link href="/dashboard/reminders" className="text-xs font-medium flex items-center gap-1" style={{ color: "var(--primary)" }}>
              View all <ChevronRight className="w-3.5 h-3.5" />
            </Link>
          </div>
          <div className="space-y-2">
            {pendingReminders.length === 0 ? (
              <div className="rounded-2xl p-8 text-center" style={{ background: "var(--surface)" }}>
                <Bell className="w-8 h-8 mx-auto mb-2" style={{ color: "var(--text-hint)" }} />
                <p className="text-sm" style={{ color: "var(--text-secondary)" }}>No upcoming reminders.</p>
                <Link href="/dashboard/reminders" className="inline-flex items-center gap-1 mt-3 text-sm font-semibold" style={{ color: "var(--primary)" }}>
                  <Plus className="w-4 h-4" /> Add reminder
                </Link>
              </div>
            ) : pendingReminders.slice(0, 4).map(r => (
              <Link key={r.id} href="/dashboard/reminders"
                className="flex items-center gap-3 p-3 rounded-xl card-hover"
                style={{ background: "var(--surface)", boxShadow: "var(--shadow-card)" }}>
                <div className="w-9 h-9 rounded-xl flex items-center justify-center text-lg flex-shrink-0"
                  style={{ background: "var(--primary-surface)" }}>
                  {categoryEmoji[r.category] ?? "⏰"}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium truncate" style={{ color: "var(--text-primary)" }}>{r.title}</div>
                  <div className="text-xs" style={{ color: r.triggerTime < Date.now() ? "var(--negative)" : "var(--warning)" }}>
                    {new Date(r.triggerTime).toLocaleString("en-IN", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}
                  </div>
                </div>
                <ArrowUpRight className="w-4 h-4 flex-shrink-0" style={{ color: "var(--text-hint)" }} />
              </Link>
            ))}
          </div>
        </motion.div>
      </div>
    </div>
  );
}
