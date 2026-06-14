"use client";

import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, onSnapshot, addDoc, deleteDoc, updateDoc, doc, getDocs } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion, AnimatePresence } from "framer-motion";
import { PieChart, Plus, Trash2, Pencil, Save, X, Loader2 } from "lucide-react";

interface Budget {
  id: string;
  category: string;
  limitAmount: number;
}

interface Cashbook {
  id: string;
  members: Record<string, string>;
}

export default function BudgetsPage() {
  const { user } = useAuth();
  const [budgets, setBudgets] = useState<Budget[]>([]);
  const [spentByCategory, setSpentByCategory] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ category: "Food", limitAmount: "" });
  const [saving, setSaving] = useState(false);

  const categories = ["Food", "Transport", "Shopping", "Bills", "Personal", "Office", "Rent", "Other"];

  useEffect(() => {
    if (!user) return;

    // 1. Fetch budgets
    const unsubBudgets = onSnapshot(query(collection(db, "users", user.uid, "budgets")), (snap) => {
      setBudgets(snap.docs.map(d => ({ id: d.id, ...d.data() } as Budget)));
    });

    // 2. Fetch spending for current month across all cashbooks
    const fetchSpending = async () => {
      const now = new Date();
      const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
      const endOfMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59).getTime();

      let spending: Record<string, number> = {};
      
      const booksSnap = await getDocs(collection(db, "cashbooks"));
      for (const book of booksSnap.docs) {
        const data = book.data();
        if (data.ownerId === user.uid || (data.members && data.members[user.uid])) {
          // fetch entries
          const entriesSnap = await getDocs(collection(db, "cashbooks", book.id, "entries"));
          entriesSnap.forEach(entryDoc => {
            const e = entryDoc.data();
            if (e.type === "CASH_OUT" && e.date >= startOfMonth && e.date <= endOfMonth) {
              const cat = e.category || "Other";
              spending[cat] = (spending[cat] || 0) + e.amount;
            }
          });
        }
      }
      setSpentByCategory(spending);
      setLoading(false);
    };

    fetchSpending();

    return () => {
      unsubBudgets();
    };
  }, [user]);

  const addBudget = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    setSaving(true);
    await addDoc(collection(db, "users", user.uid, "budgets"), {
      category: form.category,
      limitAmount: parseFloat(form.limitAmount),
      createdAt: Date.now()
    });
    setForm({ category: "Food", limitAmount: "" });
    setShowForm(false);
    setSaving(false);
  };

  const deleteBudget = async (id: string) => {
    if (!user || !window.confirm("Delete this budget?")) return;
    await deleteDoc(doc(db, "users", user.uid, "budgets", id));
  };

  const fmt = (n: number) => `₹${n.toLocaleString("en-IN")}`;

  if (loading) return <div className="p-6 flex justify-center"><Loader2 className="animate-spin" style={{ color: "var(--primary)" }} /></div>;

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto pb-10">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2" style={{ color: "var(--text-primary)" }}>
            <PieChart size={24} style={{ color: "var(--primary)" }} /> Monthly Budgets
          </h1>
          <p className="text-sm mt-0.5" style={{ color: "var(--text-secondary)" }}>Track spending limits</p>
        </div>
        <button onClick={() => setShowForm(!showForm)} className="btn btn-primary btn-sm">
          <Plus size={15} /> New Budget
        </button>
      </div>

      <AnimatePresence>
        {showForm && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} className="overflow-hidden mb-6">
            <form onSubmit={addBudget} className="card p-5 space-y-3">
              <h3 style={{ color: "var(--text-primary)" }}>Set Budget Limit</h3>
              <div className="grid grid-cols-2 gap-3">
                <select value={form.category} onChange={e => setForm({ ...form, category: e.target.value })} className="input text-sm">
                  {categories.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
                <input type="number" required min="1" value={form.limitAmount} onChange={e => setForm({ ...form, limitAmount: e.target.value })}
                  placeholder="Limit Amount (₹)" className="input text-sm" />
              </div>
              <div className="flex gap-2 pt-2">
                <button type="submit" disabled={saving} className="btn btn-primary btn-sm">
                  {saving ? <Loader2 size={14} className="animate-spin" /> : "Save"}
                </button>
                <button type="button" onClick={() => setShowForm(false)} className="btn btn-ghost btn-sm">Cancel</button>
              </div>
            </form>
          </motion.div>
        )}
      </AnimatePresence>

      {budgets.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon"><PieChart size={28} style={{ color: "var(--primary)" }} /></div>
          <p className="font-semibold" style={{ color: "var(--text-primary)" }}>No budgets set</p>
          <p className="text-sm" style={{ color: "var(--text-secondary)" }}>Create a budget to track your spending.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {budgets.map(b => {
            const spent = spentByCategory[b.category] || 0;
            const percentage = Math.min((spent / b.limitAmount) * 100, 100);
            const isExceeded = spent > b.limitAmount;
            return (
              <div key={b.id} className="card p-5">
                <div className="flex justify-between items-center mb-2">
                  <h3 className="font-bold text-lg" style={{ color: "var(--text-primary)" }}>{b.category}</h3>
                  <button onClick={() => deleteBudget(b.id)} className="btn-icon" style={{ color: "var(--negative)" }}>
                    <Trash2 size={15} />
                  </button>
                </div>
                <div className="flex justify-between text-sm mb-2 font-medium">
                  <span style={{ color: isExceeded ? "var(--negative)" : "var(--text-secondary)" }}>Spent: {fmt(spent)}</span>
                  <span style={{ color: "var(--text-primary)" }}>Limit: {fmt(b.limitAmount)}</span>
                </div>
                <div className="h-3 w-full rounded-full overflow-hidden" style={{ background: "var(--stroke)" }}>
                  <div className="h-full rounded-full transition-all duration-500"
                    style={{ width: `${percentage}%`, background: isExceeded ? "var(--negative)" : "var(--primary)" }} />
                </div>
                {isExceeded && (
                  <p className="text-xs font-bold mt-2" style={{ color: "var(--negative)" }}>
                    Over budget by {fmt(spent - b.limitAmount)}
                  </p>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
