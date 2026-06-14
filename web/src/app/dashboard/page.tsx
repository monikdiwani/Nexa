"use client";

import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import {
  collection, query, orderBy, onSnapshot, limit, where
} from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion } from "framer-motion";
import Link from "next/link";
import {
  CheckSquare, Bell, DollarSign, StickyNote,
  TrendingUp, TrendingDown, Plus, ChevronRight,
  Star, Clock, Zap
} from "lucide-react";

function getGreeting(name: string) {
  const h = new Date().getHours();
  const g = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
  return `${g}, ${name?.split(" ")[0] ?? "there"} 👋`;
}

interface Task { id: string; title: string; priority: string; isCompleted: boolean; dueDate: number | null; isImportant: boolean; }
interface Reminder { id: string; title: string; triggerTime: number; priority: string; isCompleted: boolean; category: string; }

const PRIORITY_COLOR: Record<string, string> = {
  HIGH: "var(--priority-high)", MEDIUM: "var(--priority-medium)", LOW: "var(--priority-low)"
};
const PRIORITY_BG: Record<string, string> = {
  HIGH: "rgba(229,57,53,0.10)", MEDIUM: "rgba(255,149,0,0.10)", LOW: "rgba(40,167,69,0.10)"
};
const CAT_EMOJI: Record<string, string> = {
  BILL: "📄", MEETING: "👥", TASK: "✅", MEDICINE: "💊", SHOPPING: "🛒", CUSTOM: "⏰"
};

export default function DashboardPage() {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [notesCount, setNotesCount] = useState(0);
  const [booksCount, setBooksCount] = useState(0);
  const [loadingData, setLoadingData] = useState(true);

  useEffect(() => {
    if (!user) return;
    const uid = user.uid;
    let done1 = false, done2 = false;
    const finish = () => { if (done1 && done2) setLoadingData(false); };

    // Tasks — now using proper index: (isCompleted ASC, createdAt DESC)
    const tUnsub = onSnapshot(
      query(
        collection(db, "users", uid, "tasks"),
        where("isCompleted", "==", false),
        orderBy("createdAt", "desc"),
        limit(5)
      ),
      (snap) => {
        setTasks(snap.docs.map(d => ({ id: d.id, ...d.data() } as Task)));
        done1 = true; finish();
      },
      () => {
        // Fallback if index not ready yet
        const fallback = query(collection(db, "users", uid, "tasks"), orderBy("createdAt", "desc"), limit(10));
        onSnapshot(fallback, snap => {
          setTasks(snap.docs.map(d => ({ id: d.id, ...d.data() } as Task)).filter(t => !t.isCompleted).slice(0, 5));
          done1 = true; finish();
        });
      }
    );

    // Reminders — now using proper index: (isCompleted ASC, triggerTime ASC)
    const rUnsub = onSnapshot(
      query(
        collection(db, "users", uid, "reminders"),
        where("isCompleted", "==", false),
        orderBy("triggerTime", "asc"),
        limit(5)
      ),
      (snap) => {
        setReminders(snap.docs.map(d => ({ id: d.id, ...d.data() } as Reminder)));
        done2 = true; finish();
      },
      () => {
        const fallback = query(collection(db, "users", uid, "reminders"), orderBy("triggerTime", "asc"), limit(10));
        onSnapshot(fallback, snap => {
          setReminders(snap.docs.map(d => ({ id: d.id, ...d.data() } as Reminder)).filter(r => !r.isCompleted).slice(0, 5));
          done2 = true; finish();
        });
      }
    );

    // Notes count
    const nUnsub = onSnapshot(
      query(collection(db, "users", uid, "notes")),
      (snap) => setNotesCount(snap.docs.filter(d => !d.data().isDeleted && !d.data().isArchived).length)
    );

    // Cashbooks count
    const bUnsub = onSnapshot(
      query(collection(db, "cashbooks"), where(`members.${uid}`, "!=", null)),
      (snap) => setBooksCount(snap.size)
    );

    return () => { tUnsub(); rUnsub(); nUnsub(); bUnsub(); };
  }, [user]);

  const now = Date.now();
  const pendingTasks = tasks.filter(t => !t.isCompleted);
  const upcomingReminders = reminders.filter(r => !r.isCompleted && r.triggerTime >= now);
  const overdueReminders = reminders.filter(r => !r.isCompleted && r.triggerTime < now);

  const fadeUp = (delay = 0) => ({
    initial: { opacity: 0, y: 14 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: 0.3, delay, ease: "easeOut" as const }
  });

  const QuickCard = ({
    to, icon: Icon, gradient, title, count, label
  }: {
    to: string; icon: React.ElementType; gradient: string;
    title: string; count: number; label: string;
  }) => (
    <Link href={to} className="card card-hover p-4 flex flex-col gap-3 block no-underline">
      <div className="w-10 h-10 rounded-xl flex items-center justify-center"
        style={{ background: gradient }}>
        <Icon size={20} color="white" />
      </div>
      <div>
        <p className="text-2xl font-black" style={{ color: "var(--text-primary)" }}>{count}</p>
        <p className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>{label}</p>
      </div>
      <p className="text-sm font-semibold" style={{ color: "var(--text-primary)" }}>{title}</p>
    </Link>
  );

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto pb-10">

      {/* Greeting Banner */}
      <motion.div {...fadeUp(0)}
        className="relative overflow-hidden rounded-2xl p-6 mb-6"
        style={{ background: "linear-gradient(135deg, #5C6BC0 0%, #3949AB 50%, #1A237E 100%)" }}>
        <div className="absolute -top-6 -right-6 w-40 h-40 rounded-full opacity-10"
          style={{ background: "white" }} />
        <div className="absolute -bottom-8 right-16 w-28 h-28 rounded-full opacity-8"
          style={{ background: "white" }} />
        <div className="relative z-10">
          <p className="text-white text-xl font-bold mb-1" style={{ color: "white" }}>
            {getGreeting(user?.displayName ?? "there")}
          </p>
          <p className="text-sm" style={{ color: "rgba(255,255,255,0.85)" }}>
            {pendingTasks.length > 0
              ? `You have ${pendingTasks.length} pending task${pendingTasks.length > 1 ? "s" : ""} today`
              : "All tasks done! Great work 🎉"}
          </p>
          {overdueReminders.length > 0 && (
            <div className="mt-3 inline-flex items-center gap-2 px-3 py-1.5 rounded-lg"
              style={{ background: "rgba(255,255,255,0.15)" }}>
              <span className="pulse-dot w-1.5 h-1.5 rounded-full bg-red-300" />
              <span className="text-xs text-white font-medium">
                {overdueReminders.length} reminder{overdueReminders.length > 1 ? "s" : ""} overdue
              </span>
            </div>
          )}
        </div>
      </motion.div>

      {/* Quick stats grid */}
      <motion.div {...fadeUp(0.05)} className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
        <QuickCard to="/dashboard/tasks" icon={CheckSquare}
          gradient="linear-gradient(135deg,#66BB6A,#2E7D32)"
          title="Tasks" count={pendingTasks.length} label="pending" />
        <QuickCard to="/dashboard/reminders" icon={Bell}
          gradient="linear-gradient(135deg,#26C6DA,#00838F)"
          title="Reminders" count={upcomingReminders.length} label="upcoming" />
        <QuickCard to="/dashboard/notes" icon={StickyNote}
          gradient="linear-gradient(135deg,#FFA726,#E65100)"
          title="Notes" count={notesCount} label="total" />
        <QuickCard to="/dashboard/money" icon={DollarSign}
          gradient="linear-gradient(135deg,#7C83F7,#5C6BC0)"
          title="Money" count={booksCount} label="books" />
      </motion.div>

      {/* Two column layout */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">

        {/* Pending Tasks */}
        <motion.div {...fadeUp(0.1)} className="card p-5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-bold" style={{ color: "var(--text-primary)" }}>
              Pending Tasks
            </h2>
            <Link href="/dashboard/tasks"
              className="text-xs font-semibold flex items-center gap-1 no-underline"
              style={{ color: "var(--primary)" }}>
              View all <ChevronRight size={13} />
            </Link>
          </div>

          {loadingData ? (
            <div className="space-y-2">
              {[...Array(3)].map((_, i) => <div key={i} className="shimmer h-10" />)}
            </div>
          ) : pendingTasks.length === 0 ? (
            <div className="empty-state py-8">
              <CheckSquare size={32} style={{ color: "var(--positive)", opacity: 0.5 }} />
              <p className="text-sm font-medium" style={{ color: "var(--text-secondary)" }}>All done! 🎉</p>
            </div>
          ) : (
            <div className="space-y-2">
              {pendingTasks.map(task => (
                <div key={task.id} className="flex items-center gap-2.5 p-2.5 rounded-xl"
                  style={{ background: "var(--bg)" }}>
                  <div className="w-2 h-2 rounded-full flex-shrink-0"
                    style={{ background: PRIORITY_COLOR[task.priority] }} />
                  <p className="text-sm flex-1 truncate" style={{ color: "var(--text-primary)" }}>
                    {task.title}
                  </p>
                  <div className="flex items-center gap-1.5">
                    {task.isImportant && <Star size={12} style={{ color: "var(--warning)" }} fill="currentColor" />}
                    {task.dueDate && task.dueDate < now && (
                      <span className="text-xs" style={{ color: "var(--negative)" }}>Overdue</span>
                    )}
                    <span className="chip text-xs" style={{
                      background: PRIORITY_BG[task.priority],
                      color: PRIORITY_COLOR[task.priority],
                      padding: "1px 7px"
                    }}>
                      {task.priority[0]}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}

          <Link href="/dashboard/tasks"
            className="mt-4 flex items-center justify-center gap-2 w-full py-2 rounded-xl text-sm font-semibold no-underline transition-colors"
            style={{ background: "var(--primary-surface)", color: "var(--primary)" }}>
            <Plus size={15} /> Add Task
          </Link>
        </motion.div>

        {/* Upcoming Reminders */}
        <motion.div {...fadeUp(0.12)} className="card p-5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-bold" style={{ color: "var(--text-primary)" }}>
              Reminders
            </h2>
            <Link href="/dashboard/reminders"
              className="text-xs font-semibold flex items-center gap-1 no-underline"
              style={{ color: "var(--primary)" }}>
              View all <ChevronRight size={13} />
            </Link>
          </div>

          {loadingData ? (
            <div className="space-y-2">
              {[...Array(3)].map((_, i) => <div key={i} className="shimmer h-12" />)}
            </div>
          ) : reminders.length === 0 ? (
            <div className="empty-state py-8">
              <Bell size={32} style={{ color: "var(--primary)", opacity: 0.5 }} />
              <p className="text-sm font-medium" style={{ color: "var(--text-secondary)" }}>No reminders set</p>
            </div>
          ) : (
            <div className="space-y-2">
              {reminders.slice(0, 4).map(r => {
                const isOverdue = r.triggerTime < now;
                const d = r.triggerTime - now;
                const timeLabel = isOverdue
                  ? "Overdue"
                  : d < 3600000 ? `${Math.round(d / 60000)}m`
                    : d < 86400000 ? `${Math.round(d / 3600000)}h`
                      : `${Math.round(d / 86400000)}d`;
                return (
                  <div key={r.id} className="flex items-center gap-2.5 p-2.5 rounded-xl"
                    style={{ background: isOverdue ? "var(--cash-out-bg)" : "var(--bg)" }}>
                    <span className="text-lg">{CAT_EMOJI[r.category] ?? "⏰"}</span>
                    <p className="text-sm flex-1 truncate" style={{ color: "var(--text-primary)" }}>
                      {r.title}
                    </p>
                    <span className="text-xs font-semibold flex-shrink-0"
                      style={{ color: isOverdue ? "var(--negative)" : "var(--warning)" }}>
                      <Clock size={11} className="inline mr-0.5" />{timeLabel}
                    </span>
                  </div>
                );
              })}
            </div>
          )}

          <Link href="/dashboard/reminders"
            className="mt-4 flex items-center justify-center gap-2 w-full py-2 rounded-xl text-sm font-semibold no-underline transition-colors"
            style={{ background: "var(--primary-surface)", color: "var(--primary)" }}>
            <Plus size={15} /> Add Reminder
          </Link>
        </motion.div>
      </div>

      {/* Quick actions */}
      <motion.div {...fadeUp(0.18)} className="card p-5 mt-5">
        <h2 className="text-base font-bold mb-4" style={{ color: "var(--text-primary)" }}>
          Quick Actions
        </h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {[
            { to: "/dashboard/money", icon: TrendingUp, label: "Add Income", color: "var(--positive)", bg: "var(--cash-in-bg)" },
            { to: "/dashboard/money", icon: TrendingDown, label: "Add Expense", color: "var(--negative)", bg: "var(--cash-out-bg)" },
            { to: "/dashboard/notes", icon: StickyNote, label: "New Note", color: "var(--warning)", bg: "rgba(255,149,0,0.10)" },
            { to: "/dashboard/reports", icon: Zap, label: "View Reports", color: "var(--primary)", bg: "var(--primary-surface)" },
          ].map(({ to, icon: Icon, label, color, bg }) => (
            <Link key={label} href={to}
              className="flex flex-col items-center gap-2 p-3 rounded-xl text-center card-hover no-underline"
              style={{ background: bg }}>
              <Icon size={20} style={{ color }} />
              <span className="text-xs font-semibold" style={{ color }}>{label}</span>
            </Link>
          ))}
        </div>
      </motion.div>
    </div>
  );
}
