"use client";
import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, onSnapshot, orderBy, where, getDocs } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion } from "framer-motion";
import { TrendingUp, TrendingDown, DollarSign, Calendar, BarChart2, CheckSquare } from "lucide-react";

interface Entry { id: string; type: string; amount: number; date: number; category: string; particulars: string; }
interface Task { id: string; isCompleted: boolean; priority: string; }
interface Book { id: string; name: string; members: Record<string, string>; }

const CATEGORIES = ["Sales","Rent","Salary","Office","Personal","Food","Transport","Shopping","Bills","Other"];
const CAT_COLORS = ["#7C83F7","#EF5350","#66BB6A","#FFA726","#26C6DA","#FF8A65","#AB47BC","#42A5F5","#EC407A","#8D6E63"];

export default function ReportsPage() {
  const { user } = useAuth();
  const [entries, setEntries] = useState<Entry[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [range, setRange] = useState<"7"|"30"|"90">("30");

  useEffect(() => {
    if (!user) return;
    let tasksDone = false, entriesDone = false;
    const check = () => { if (tasksDone && entriesDone) setLoading(false); };

    // ─── 1. Load tasks ───────────────────────────────────────────
    const tUnsub = onSnapshot(
      query(collection(db, "users", user.uid, "tasks"), orderBy("createdAt", "desc")),
      (snap) => {
        setTasks(snap.docs.map(d => ({ id: d.id, ...d.data() } as Task)));
        tasksDone = true;
        check();
      }
    );

    // ─── 2. Load ALL cashbook entries across all books ────────────
    // First get all cashbooks the user is a member of
    const fetchAllEntries = async () => {
      try {
        const booksSnap = await getDocs(
          query(collection(db, "cashbooks"), where(`members.${user.uid}`, "!=", null))
        );
        const books = booksSnap.docs as any[];
        if (books.length === 0) {
          entriesDone = true;
          check();
          return;
        }

        // For each book, get its entries
        let allEntries: Entry[] = [];
        let pending = books.length;

        books.forEach((bookDoc) => {
          getDocs(
            query(collection(db, "cashbooks", bookDoc.id, "entries"), orderBy("date", "desc"))
          ).then((eSnap) => {
            const bookEntries = eSnap.docs.map(d => ({ id: d.id, ...d.data() } as Entry));
            allEntries = [...allEntries, ...bookEntries];
            pending--;
            if (pending === 0) {
              setEntries(allEntries);
              entriesDone = true;
              check();
            }
          }).catch(() => {
            pending--;
            if (pending === 0) {
              setEntries(allEntries);
              entriesDone = true;
              check();
            }
          });
        });
      } catch {
        entriesDone = true;
        check();
      }
    };

    fetchAllEntries();
    return () => tUnsub();
  }, [user]);

  const now = Date.now();
  const days = parseInt(range);
  const from = now - days * 86400000;

  const rangeEntries = entries.filter(e => e.date >= from);
  const totalIn = rangeEntries.filter(e => e.type === "CASH_IN").reduce((s, e) => s + e.amount, 0);
  const totalOut = rangeEntries.filter(e => e.type === "CASH_OUT").reduce((s, e) => s + e.amount, 0);
  const net = totalIn - totalOut;
  const avgPerDay = days > 0 && totalOut > 0 ? totalOut / days : 0;

  // Daily spend bar chart (last 7 days)
  const last7 = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(now - (6 - i) * 86400000);
    const dayStart = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
    const dayEnd = dayStart + 86400000;
    const spent = entries
      .filter(e => e.type === "CASH_OUT" && e.date >= dayStart && e.date < dayEnd)
      .reduce((s, e) => s + e.amount, 0);
    const earned = entries
      .filter(e => e.type === "CASH_IN" && e.date >= dayStart && e.date < dayEnd)
      .reduce((s, e) => s + e.amount, 0);
    return { label: d.toLocaleDateString("en-IN", { weekday: "short" }), spent, earned };
  });
  const maxDay = Math.max(...last7.map(d => Math.max(d.spent, d.earned)), 1);

  // Category breakdown
  const catData = CATEGORIES.map((cat, i) => {
    const amt = rangeEntries.filter(e => e.type === "CASH_OUT" && e.category === cat).reduce((s, e) => s + e.amount, 0);
    return { cat, amt, color: CAT_COLORS[i] };
  }).filter(c => c.amt > 0).sort((a, b) => b.amt - a.amt);
  const maxCat = catData[0]?.amt || 1;

  // Task stats
  const totalTasks = tasks.length;
  const completedTasks = tasks.filter(t => t.isCompleted).length;
  const pendingTasks = totalTasks - completedTasks;
  const completionPct = totalTasks > 0 ? Math.round((completedTasks / totalTasks) * 100) : 0;
  const circumference = 2 * Math.PI * 36;
  const dashOffset = circumference * (1 - completionPct / 100);

  const fmt = (n: number) => n >= 1000 ? `₹${(n / 1000).toFixed(1)}k` : `₹${Math.round(n)}`;
  const hasData = entries.length > 0;

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto pb-10">
      <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: "var(--text-primary)" }}>Reports</h1>
          <p className="text-sm mt-0.5" style={{ color: "var(--text-secondary)" }}>
            {entries.length} entries across all cashbooks
          </p>
        </div>
        <div className="flex gap-1 p-1 rounded-xl" style={{ background: "var(--surface)", border: "1px solid var(--divider)" }}>
          {(["7", "30", "90"] as const).map(r => (
            <button key={r} onClick={() => setRange(r)}
              className="px-3 py-1.5 rounded-lg text-sm font-semibold transition-all"
              style={range === r
                ? { background: "var(--primary)", color: "white" }
                : { background: "transparent", color: "var(--text-secondary)" }
              }>{r}d</button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="space-y-4">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {[...Array(4)].map((_, i) => <div key={i} className="shimmer h-24 rounded-2xl" />)}
          </div>
          <div className="shimmer h-48 rounded-2xl" />
        </div>
      ) : (
        <>
          {/* Stats cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-5">
            {[
              { label: "Cash In", val: totalIn, Icon: TrendingUp, color: "var(--positive)", bg: "var(--cash-in-bg)" },
              { label: "Cash Out", val: totalOut, Icon: TrendingDown, color: "var(--negative)", bg: "var(--cash-out-bg)" },
              { label: "Net Balance", val: net, Icon: DollarSign, color: net >= 0 ? "var(--positive)" : "var(--negative)", bg: net >= 0 ? "var(--cash-in-bg)" : "var(--cash-out-bg)" },
              { label: "Avg/Day", val: avgPerDay, Icon: Calendar, color: "var(--info)", bg: "rgba(0,122,255,0.08)" },
            ].map(({ label, val, Icon, color, bg }, idx) => (
              <motion.div key={label} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
                transition={{ delay: idx * 0.05 }} className="card p-4 flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: bg }}>
                  <Icon size={18} style={{ color }} />
                </div>
                <div className="min-w-0">
                  <p className="text-xs font-medium" style={{ color: "var(--text-secondary)" }}>{label}</p>
                  <p className="font-bold text-base" style={{ color }}>{fmt(Math.abs(val))}</p>
                </div>
              </motion.div>
            ))}
          </div>

          {/* Bar chart — last 7 days */}
          <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }} className="card p-5 mb-5">
            <div className="flex items-center justify-between mb-1">
              <h2 style={{ color: "var(--text-primary)" }}>Daily Activity</h2>
              <div className="flex items-center gap-3 text-xs" style={{ color: "var(--text-hint)" }}>
                <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full inline-block" style={{ background: "var(--positive)" }} />In</span>
                <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full inline-block" style={{ background: "var(--negative)" }} />Out</span>
              </div>
            </div>
            <p className="text-sm mb-4" style={{ color: "var(--text-secondary)" }}>Last 7 days</p>
            {!hasData ? (
              <p className="text-center py-8 text-sm" style={{ color: "var(--text-hint)" }}>No cashbook data yet — add entries in Money section</p>
            ) : (
              <div className="flex items-end gap-2 h-32">
                {last7.map((d, i) => (
                  <div key={i} className="flex-1 flex flex-col items-center gap-1">
                    <div className="w-full flex gap-0.5 items-end" style={{ height: "100px" }}>
                      <div className="flex-1 rounded-t-md transition-all duration-700" style={{
                        height: `${d.earned > 0 ? Math.max((d.earned / maxDay) * 100, 6) : 0}%`,
                        background: "var(--positive)", opacity: 0.75, minHeight: d.earned > 0 ? "6px" : "0"
                      }} />
                      <div className="flex-1 rounded-t-md transition-all duration-700" style={{
                        height: `${d.spent > 0 ? Math.max((d.spent / maxDay) * 100, 6) : 0}%`,
                        background: "var(--negative)", opacity: 0.75, minHeight: d.spent > 0 ? "6px" : "0"
                      }} />
                    </div>
                    <span className="text-xs" style={{ color: "var(--text-hint)" }}>{d.label}</span>
                  </div>
                ))}
              </div>
            )}
          </motion.div>

          {/* Category + Task side-by-side */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-5">
            {/* Category breakdown */}
            <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.15 }} className="card p-5">
              <h2 className="mb-4" style={{ color: "var(--text-primary)" }}>Spending by Category</h2>
              {catData.length === 0 ? (
                <p className="text-sm text-center py-8" style={{ color: "var(--text-hint)" }}>
                  {hasData ? "No outgoing transactions in this period" : "No cashbook data yet"}
                </p>
              ) : (
                <div className="space-y-3">
                  {catData.slice(0, 7).map(({ cat, amt, color }) => (
                    <div key={cat}>
                      <div className="flex justify-between mb-1">
                        <span className="text-sm font-medium" style={{ color: "var(--text-primary)" }}>{cat}</span>
                        <span className="text-sm font-bold" style={{ color: "var(--negative)" }}>{fmt(amt)}</span>
                      </div>
                      <div className="h-2 rounded-full overflow-hidden" style={{ background: "var(--divider)" }}>
                        <motion.div initial={{ width: 0 }} animate={{ width: `${(amt / maxCat) * 100}%` }}
                          transition={{ duration: 0.8, delay: 0.2 }}
                          className="h-full rounded-full" style={{ background: color }} />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </motion.div>

            {/* Task completion */}
            <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2 }} className="card p-5">
              <h2 className="mb-4" style={{ color: "var(--text-primary)" }}>Task Completion</h2>
              <div className="flex items-center gap-6">
                <div className="relative flex-shrink-0">
                  <svg width="96" height="96" viewBox="0 0 88 88">
                    <circle cx="44" cy="44" r="36" fill="none" stroke="var(--divider)" strokeWidth="8" />
                    <motion.circle cx="44" cy="44" r="36" fill="none"
                      stroke="var(--primary)" strokeWidth="8" strokeLinecap="round"
                      strokeDasharray={circumference}
                      initial={{ strokeDashoffset: circumference }}
                      animate={{ strokeDashoffset: dashOffset }}
                      transition={{ duration: 1, delay: 0.3 }}
                      transform="rotate(-90 44 44)" />
                  </svg>
                  <div className="absolute inset-0 flex items-center justify-center">
                    <span className="text-xl font-black" style={{ color: "var(--primary)" }}>{completionPct}%</span>
                  </div>
                </div>
                <div className="space-y-3">
                  <div>
                    <p className="text-xs" style={{ color: "var(--text-secondary)" }}>Completed</p>
                    <p className="text-2xl font-bold" style={{ color: "var(--positive)" }}>{completedTasks}</p>
                  </div>
                  <div>
                    <p className="text-xs" style={{ color: "var(--text-secondary)" }}>Pending</p>
                    <p className="text-2xl font-bold" style={{ color: "var(--warning)" }}>{pendingTasks}</p>
                  </div>
                </div>
              </div>
              {totalTasks === 0 && (
                <p className="text-sm text-center mt-4" style={{ color: "var(--text-hint)" }}>Add tasks to see stats</p>
              )}
            </motion.div>
          </div>

          {/* Top transactions */}
          {rangeEntries.length > 0 && (
            <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.25 }} className="card p-5">
              <h2 className="mb-4" style={{ color: "var(--text-primary)" }}>Recent Transactions</h2>
              <div className="space-y-2">
                {rangeEntries.slice(0, 8).map((e) => (
                  <div key={e.id} className="flex items-center gap-3 py-2 border-b last:border-0"
                    style={{ borderColor: "var(--divider)" }}>
                    <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0"
                      style={{ background: e.type === "CASH_IN" ? "var(--cash-in-bg)" : "var(--cash-out-bg)" }}>
                      {e.type === "CASH_IN"
                        ? <TrendingUp size={14} style={{ color: "var(--positive)" }} />
                        : <TrendingDown size={14} style={{ color: "var(--negative)" }} />}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate" style={{ color: "var(--text-primary)" }}>{e.particulars}</p>
                      <p className="text-xs" style={{ color: "var(--text-hint)" }}>{e.category} · {new Date(e.date).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}</p>
                    </div>
                    <p className="font-bold text-sm flex-shrink-0"
                      style={{ color: e.type === "CASH_IN" ? "var(--positive)" : "var(--negative)" }}>
                      {e.type === "CASH_IN" ? "+" : "-"}{fmt(e.amount)}
                    </p>
                  </div>
                ))}
              </div>
            </motion.div>
          )}
        </>
      )}
    </div>
  );
}
