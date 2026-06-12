"use client";

import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import {
  doc, onSnapshot, collection, query, orderBy,
  addDoc, updateDoc, deleteDoc, increment
} from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion } from "framer-motion";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowLeft, Plus, TrendingUp, TrendingDown, Copy, Check,
  Trash2, Filter, Loader2, Users, DollarSign
} from "lucide-react";
import Link from "next/link";

interface LedgerBook {
  id: string; name: string; currency: string; ownerId: string;
  inviteCode: string; totalCashIn: number; totalCashOut: number;
  netBalance: number; members: Record<string, string>;
}

interface Entry {
  id: string; date: number; particulars: string; type: string;
  medium: string; amount: number; category: string; note: string;
  createdByName: string; createdAt: number;
}

const categories = ["Sales", "Rent", "Salary", "Office", "Personal", "Food", "Transport", "Shopping", "Bills", "Other"];

export default function LedgerDetailPage() {
  const { user } = useAuth();
  const params = useParams();
  const router = useRouter();
  const bookId = params.bookId as string;

  const [book, setBook] = useState<LedgerBook | null>(null);
  const [entries, setEntries] = useState<Entry[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [copied, setCopied] = useState(false);
  const [filterType, setFilterType] = useState("ALL");
  const [form, setForm] = useState({
    date: new Date().toISOString().split("T")[0],
    particulars: "", type: "CASH_IN", medium: "CASH",
    amount: "", category: "Other", note: ""
  });

  useEffect(() => {
    if (!user || !bookId) return;
    // Listen to book
    const bookUnsub = onSnapshot(doc(db, "cashbooks", bookId), (snap) => {
      if (snap.exists()) setBook({ id: snap.id, ...snap.data() } as LedgerBook);
    });
    // Listen to entries
    const entriesUnsub = onSnapshot(
      query(collection(db, "cashbooks", bookId, "entries"), orderBy("date", "desc")),
      (snap) => {
        setEntries(snap.docs.map(d => ({ id: d.id, ...d.data() } as Entry)));
        setLoading(false);
      }
    );
    return () => { bookUnsub(); entriesUnsub(); };
  }, [user, bookId]);

  const isAdmin = book?.members?.[user?.uid ?? ""] === "ADMIN";

  const addEntry = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !book) return;
    setSaving(true);
    const amt = parseFloat(form.amount);
    const dateMs = new Date(form.date).getTime();

    await addDoc(collection(db, "cashbooks", bookId, "entries"), {
      date: dateMs, particulars: form.particulars, type: form.type,
      medium: form.medium, amount: amt, category: form.category,
      note: form.note, createdBy: user.uid,
      createdByName: user.displayName ?? user.email ?? "Unknown",
      createdAt: Date.now(), lastModifiedAt: Date.now(),
      billImageUrl: null, contactName: null, contactPhone: null,
      isRecurring: false, recurringPattern: null,
    });

    // Update book totals
    const delta = form.type === "CASH_IN" ? amt : -amt;
    await updateDoc(doc(db, "cashbooks", bookId), {
      totalCashIn: form.type === "CASH_IN" ? increment(amt) : increment(0),
      totalCashOut: form.type === "CASH_OUT" ? increment(amt) : increment(0),
      netBalance: increment(delta),
    });

    // Audit log
    await addDoc(collection(db, "cashbooks", bookId, "logs"), {
      actionType: "CREATE", actorId: user.uid,
      actorName: user.displayName ?? "Unknown",
      particulars: form.particulars, transactionType: form.type,
      amount: amt, timestamp: Date.now(), details: form.note
    });

    setForm({ date: new Date().toISOString().split("T")[0], particulars: "", type: "CASH_IN", medium: "CASH", amount: "", category: "Other", note: "" });
    setShowForm(false);
    setSaving(false);
  };

  const deleteEntry = async (entry: Entry) => {
    if (!user || !isAdmin) return;
    await deleteDoc(doc(db, "cashbooks", bookId, "entries", entry.id));
    const delta = entry.type === "CASH_IN" ? -entry.amount : entry.amount;
    await updateDoc(doc(db, "cashbooks", bookId), {
      totalCashIn: entry.type === "CASH_IN" ? increment(-entry.amount) : increment(0),
      totalCashOut: entry.type === "CASH_OUT" ? increment(-entry.amount) : increment(0),
      netBalance: increment(delta),
    });
  };

  const copyCode = () => {
    if (!book) return;
    navigator.clipboard.writeText(book.inviteCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const filtered = entries.filter(e => filterType === "ALL" || e.type === filterType);
  const fmt = (n: number) => `₹${Math.abs(n).toLocaleString("en-IN", { minimumFractionDigits: 0 })}`;

  if (!book) return (
    <div className="p-6 flex items-center justify-center">
      <Loader2 className="w-8 h-8 animate-spin" style={{ color: "var(--primary)" }} />
    </div>
  );

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <button onClick={() => router.back()} className="p-2 rounded-xl transition-colors hover:bg-gray-100"
          style={{ color: "var(--text-secondary)" }}>
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div className="flex-1 min-w-0">
          <h1 className="text-xl font-bold truncate" style={{ color: "var(--text-primary)" }}>{book.name}</h1>
          <div className="flex items-center gap-2 mt-0.5">
            <span className="text-xs" style={{ color: "var(--text-hint)" }}>Code:</span>
            <button onClick={copyCode} className="flex items-center gap-1 text-xs font-mono font-semibold px-2 py-0.5 rounded-lg"
              style={{ background: "var(--primary-surface)", color: "var(--primary)" }}>
              {book.inviteCode}
              {copied ? <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />}
            </button>
            {isAdmin && <span className="chip text-xs priority-low">ADMIN</span>}
          </div>
        </div>
        {isAdmin && (
          <button onClick={() => setShowForm(!showForm)} className="btn-primary px-4 py-2 text-sm flex items-center gap-2">
            <Plus className="w-4 h-4" /> Add Entry
          </button>
        )}
      </div>

      {/* Balance summary */}
      <div className="grid grid-cols-3 gap-3 mb-5">
        {[
          { label: "Cash In", val: book.totalCashIn, color: "var(--positive)", bg: "var(--cash-in-bg)", Icon: TrendingUp },
          { label: "Cash Out", val: book.totalCashOut, color: "var(--negative)", bg: "var(--cash-out-bg)", Icon: TrendingDown },
          { label: "Net Balance", val: book.netBalance, color: book.netBalance >= 0 ? "var(--positive)" : "var(--negative)", bg: book.netBalance >= 0 ? "var(--cash-in-bg)" : "var(--cash-out-bg)", Icon: DollarSign },
        ].map(({ label, val, color, bg, Icon }) => (
          <div key={label} className="rounded-2xl p-4 flex items-center gap-3"
            style={{ background: "var(--surface)", boxShadow: "var(--shadow-card)" }}>
            <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: bg }}>
              <Icon className="w-4 h-4" style={{ color }} />
            </div>
            <div>
              <p className="text-xs" style={{ color: "var(--text-secondary)" }}>{label}</p>
              <p className="font-bold text-sm" style={{ color }}>{fmt(val)}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Add Entry Form */}
      {showForm && (
        <motion.form initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} onSubmit={addEntry}
          className="rounded-2xl p-5 mb-5 space-y-3" style={{ background: "var(--surface)", boxShadow: "var(--shadow-card)" }}>
          <h3 className="font-semibold" style={{ color: "var(--text-primary)" }}>New Entry</h3>

          {/* Type toggle */}
          <div className="flex rounded-xl overflow-hidden border" style={{ borderColor: "var(--divider)" }}>
            {["CASH_IN", "CASH_OUT"].map(t => (
              <button key={t} type="button" onClick={() => setForm({ ...form, type: t })}
                className="flex-1 py-2.5 text-sm font-semibold transition-all"
                style={{
                  background: form.type === t ? (t === "CASH_IN" ? "var(--positive)" : "var(--negative)") : "var(--bg)",
                  color: form.type === t ? "white" : "var(--text-secondary)"
                }}>
                {t === "CASH_IN" ? "↑ Cash In" : "↓ Cash Out"}
              </button>
            ))}
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-xs font-medium mb-1 block" style={{ color: "var(--text-secondary)" }}>Date</label>
              <input type="date" required value={form.date} onChange={e => setForm({ ...form, date: e.target.value })}
                className="w-full nexa-input px-3 py-2.5 text-sm" />
            </div>
            <div>
              <label className="text-xs font-medium mb-1 block" style={{ color: "var(--text-secondary)" }}>Amount (₹)</label>
              <input type="number" required min="0.01" step="0.01" value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })}
                placeholder="0.00" className="w-full nexa-input px-3 py-2.5 text-sm" />
            </div>
          </div>

          <input required value={form.particulars} onChange={e => setForm({ ...form, particulars: e.target.value })}
            placeholder="Particulars (e.g. Salary, Rent...)" className="w-full nexa-input px-4 py-2.5 text-sm" />

          <div className="grid grid-cols-2 gap-2">
            <select value={form.medium} onChange={e => setForm({ ...form, medium: e.target.value })}
              className="nexa-input px-3 py-2.5 text-sm">
              <option value="CASH">💵 Cash</option>
              <option value="BANK">🏦 Bank</option>
            </select>
            <select value={form.category} onChange={e => setForm({ ...form, category: e.target.value })}
              className="nexa-input px-3 py-2.5 text-sm">
              {categories.map(c => <option key={c}>{c}</option>)}
            </select>
          </div>

          <input value={form.note} onChange={e => setForm({ ...form, note: e.target.value })}
            placeholder="Note (optional)" className="w-full nexa-input px-4 py-2.5 text-sm" />

          <div className="flex gap-2">
            <button type="submit" disabled={saving} className="btn-primary px-4 py-2 text-sm">
              {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : "Save Entry"}
            </button>
            <button type="button" onClick={() => setShowForm(false)} className="btn-secondary px-4 py-2 text-sm">Cancel</button>
          </div>
        </motion.form>
      )}

      {/* Filter */}
      <div className="flex items-center gap-2 mb-4">
        <Filter className="w-4 h-4" style={{ color: "var(--text-hint)" }} />
        {["ALL", "CASH_IN", "CASH_OUT"].map(f => (
          <button key={f} onClick={() => setFilterType(f)} className="chip text-xs cursor-pointer"
            style={filterType === f ? { background: "var(--primary)", color: "white" } : { background: "var(--surface)", color: "var(--text-secondary)", border: "1px solid var(--divider)" }}>
            {f === "ALL" ? "All" : f === "CASH_IN" ? "↑ In" : "↓ Out"}
          </button>
        ))}
        <span className="ml-auto text-xs" style={{ color: "var(--text-hint)" }}>{filtered.length} entries</span>
      </div>

      {/* Entries list */}
      {loading ? (
        <div className="space-y-2">{[...Array(5)].map((_, i) => <div key={i} className="h-16 rounded-xl shimmer" />)}</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16" style={{ color: "var(--text-hint)" }}>
          <DollarSign className="w-12 h-12 mx-auto mb-3 opacity-30" />
          <p>No entries yet. {isAdmin && "Add the first entry!"}</p>
        </div>
      ) : (
        <div className="space-y-2">
          {filtered.map((entry, i) => (
            <motion.div key={entry.id} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.03 }}
              className="flex items-center gap-3 p-4 rounded-xl group"
              style={{ background: "var(--surface)", boxShadow: "var(--shadow-card)" }}>
              {/* Type indicator */}
              <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
                style={{ background: entry.type === "CASH_IN" ? "var(--cash-in-bg)" : "var(--cash-out-bg)" }}>
                {entry.type === "CASH_IN"
                  ? <TrendingUp className="w-4 h-4" style={{ color: "var(--positive)" }} />
                  : <TrendingDown className="w-4 h-4" style={{ color: "var(--negative)" }} />
                }
              </div>

              <div className="flex-1 min-w-0">
                <p className="font-medium text-sm truncate" style={{ color: "var(--text-primary)" }}>{entry.particulars}</p>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className="text-xs" style={{ color: "var(--text-hint)" }}>
                    {new Date(entry.date).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "2-digit" })}
                  </span>
                  <span className="text-xs px-1.5 py-0.5 rounded" style={{ background: "var(--bg)", color: "var(--text-hint)" }}>
                    {entry.medium}
                  </span>
                  <span className="text-xs" style={{ color: "var(--text-hint)" }}>{entry.category}</span>
                </div>
                {entry.createdByName && (
                  <p className="text-xs mt-0.5 flex items-center gap-1" style={{ color: "var(--text-hint)" }}>
                    <Users className="w-3 h-3" /> by {entry.createdByName}
                  </p>
                )}
              </div>

              <div className="text-right flex-shrink-0">
                <p className="font-bold text-sm" style={{ color: entry.type === "CASH_IN" ? "var(--positive)" : "var(--negative)" }}>
                  {entry.type === "CASH_IN" ? "+" : "-"}{fmt(entry.amount)}
                </p>
              </div>

              {isAdmin && (
                <button onClick={() => deleteEntry(entry)}
                  className="opacity-0 group-hover:opacity-100 transition-opacity p-1.5 rounded-lg"
                  style={{ color: "var(--negative)", background: "rgba(220,53,69,0.08)" }}>
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              )}
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}
