"use client";

import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import {
  doc, onSnapshot, collection, query, orderBy,
  addDoc, updateDoc, deleteDoc, increment
} from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion, AnimatePresence } from "framer-motion";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowLeft, Plus, TrendingUp, TrendingDown, Copy, Check,
  Trash2, Filter, Loader2, Users, DollarSign, ChevronDown, Shield, Eye, Pencil, X
} from "lucide-react";

interface LedgerBook {
  id: string; name: string; currency: string; ownerId: string;
  inviteCode: string; totalCashIn: number; totalCashOut: number;
  netBalance: number; members: Record<string, string>;
}

interface Entry {
  id: string; date: number; particulars: string; type: string;
  medium: string; amount: number; category: string; note: string;
  createdByName?: string; createdBy?: string; createdAt: number;
}

const categories = ["Sales", "Rent", "Salary", "Office", "Personal", "Food", "Transport", "Shopping", "Bills", "Other"];
const ROLE_ICONS: Record<string, React.ElementType> = { ADMIN: Shield, EDITOR: Pencil, VIEWER: Eye };
const ROLE_COLORS: Record<string, string> = {
  ADMIN: "var(--primary)", EDITOR: "var(--warning)", VIEWER: "var(--text-secondary)"
};
const ROLE_BG: Record<string, string> = {
  ADMIN: "var(--primary-surface)", EDITOR: "rgba(255,149,0,0.10)", VIEWER: "var(--bg)"
};

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
  const [showMembersPanel, setShowMembersPanel] = useState(false);
  const [roleUpdating, setRoleUpdating] = useState<string | null>(null);
  const [form, setForm] = useState({
    date: new Date().toISOString().split("T")[0],
    particulars: "", type: "CASH_IN", medium: "CASH",
    amount: "", category: "Other", note: ""
  });

  useEffect(() => {
    if (!user || !bookId) return;
    const bookUnsub = onSnapshot(doc(db, "cashbooks", bookId), (snap) => {
      if (snap.exists()) setBook({ id: snap.id, ...snap.data() } as LedgerBook);
    });
    const entriesUnsub = onSnapshot(
      query(collection(db, "cashbooks", bookId, "entries"), orderBy("date", "desc")),
      (snap) => {
        setEntries(snap.docs.map(d => ({ id: d.id, ...d.data() } as Entry)));
        setLoading(false);
      }
    );
    return () => { bookUnsub(); entriesUnsub(); };
  }, [user, bookId]);

  const myRole = book?.members?.[user?.uid ?? ""] ?? "VIEWER";
  const isAdmin = myRole === "ADMIN";
  const canEdit = myRole === "ADMIN" || myRole === "EDITOR"; // Feature 19: EDITOR can add entries

  const addEntry = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !book || !canEdit) return;
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

    const delta = form.type === "CASH_IN" ? amt : -amt;
    await updateDoc(doc(db, "cashbooks", bookId), {
      totalCashIn: form.type === "CASH_IN" ? increment(amt) : increment(0),
      totalCashOut: form.type === "CASH_OUT" ? increment(amt) : increment(0),
      netBalance: increment(delta),
    });

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
    if (!user || !canEdit) return;
    await deleteDoc(doc(db, "cashbooks", bookId, "entries", entry.id));
    const delta = entry.type === "CASH_IN" ? -entry.amount : entry.amount;
    await updateDoc(doc(db, "cashbooks", bookId), {
      totalCashIn: entry.type === "CASH_IN" ? increment(-entry.amount) : increment(0),
      totalCashOut: entry.type === "CASH_OUT" ? increment(-entry.amount) : increment(0),
      netBalance: increment(delta),
    });
  };

  // Feature 21 — Only ADMIN can copy/share invite code
  const copyCode = () => {
    if (!book || !isAdmin) return;
    navigator.clipboard.writeText(book.inviteCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Feature 19 — Change member role (admin only)
  const changeMemberRole = async (uid: string, newRole: string) => {
    if (!isAdmin) return;
    setRoleUpdating(uid);
    await updateDoc(doc(db, "cashbooks", bookId), {
      [`members.${uid}`]: newRole
    });
    setRoleUpdating(null);
  };

  // Feature 19 — Remove member (admin only)
  const removeMember = async (uid: string) => {
    if (!isAdmin || !window.confirm("Remove this member from the cashbook?")) return;
    const { deleteField } = await import("firebase/firestore");
    await updateDoc(doc(db, "cashbooks", bookId), {
      [`members.${uid}`]: deleteField()
    });
  };

  const filtered = entries.filter(e => filterType === "ALL" || e.type === filterType);
  const fmt = (n: number) => `₹${Math.abs(n).toLocaleString("en-IN", { minimumFractionDigits: 0 })}`;

  const memberCount = book ? Object.keys(book.members ?? {}).length : 0;

  if (!book) return (
    <div className="p-6 flex items-center justify-center h-64">
      <Loader2 size={32} className="animate-spin" style={{ color: "var(--primary)" }} />
    </div>
  );

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto pb-10">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <button onClick={() => router.back()} className="btn-icon">
          <ArrowLeft size={20} />
        </button>
        <div className="flex-1 min-w-0">
          <h1 className="text-xl font-bold truncate" style={{ color: "var(--text-primary)" }}>{book.name}</h1>
          <div className="flex items-center gap-2 mt-0.5 flex-wrap">
            {/* Feature 21: Invite code only visible to ADMIN */}
            {isAdmin ? (
              <button onClick={copyCode} className="flex items-center gap-1 text-xs font-mono font-semibold px-2 py-0.5 rounded-lg transition-all"
                style={{ background: "var(--primary-surface)", color: "var(--primary)" }}
                title="Copy invite code">
                {book.inviteCode}
                {copied ? <Check size={11} /> : <Copy size={11} />}
              </button>
            ) : (
              <span className="text-xs" style={{ color: "var(--text-hint)" }}>Invite: Admin only</span>
            )}
            {/* Role badge */}
            <span className="chip text-xs" style={{
              background: ROLE_BG[myRole] ?? "var(--bg)",
              color: ROLE_COLORS[myRole] ?? "var(--text-secondary)"
            }}>
              {myRole}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {/* Feature 19: Members panel — admin only */}
          {isAdmin && (
            <button onClick={() => setShowMembersPanel(!showMembersPanel)}
              className="btn btn-ghost btn-sm flex items-center gap-1.5">
              <Users size={15} /> {memberCount}
            </button>
          )}
          {canEdit && (
            <button onClick={() => setShowForm(!showForm)} className="btn btn-primary btn-sm">
              <Plus size={15} /> Add Entry
            </button>
          )}
        </div>
      </div>

      {/* Feature 19: Members management panel */}
      <AnimatePresence>
        {showMembersPanel && isAdmin && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }} transition={{ duration: 0.2 }}
            className="overflow-hidden mb-5">
            <div className="card p-5">
              <div className="flex items-center justify-between mb-4">
                <h3 style={{ color: "var(--text-primary)" }}>Members ({memberCount})</h3>
                <button onClick={() => setShowMembersPanel(false)} className="btn-icon"><X size={16} /></button>
              </div>
              <div className="space-y-2">
                {Object.entries(book.members ?? {}).map(([uid, role]) => {
                  const RoleIcon = ROLE_ICONS[role] ?? Eye;
                  const isSelf = uid === user?.uid;
                  return (
                    <div key={uid} className="flex items-center gap-3 p-3 rounded-xl"
                      style={{ background: "var(--bg)" }}>
                      <div className="w-8 h-8 rounded-lg nexa-gradient flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                        {uid.slice(0, 2).toUpperCase()}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold truncate" style={{ color: "var(--text-primary)" }}>
                          {isSelf ? "You" : `Member (${uid.slice(0, 8)}...)`}
                        </p>
                        <div className="flex items-center gap-1 mt-0.5">
                          <RoleIcon size={11} style={{ color: ROLE_COLORS[role] }} />
                          <span className="text-xs font-medium" style={{ color: ROLE_COLORS[role] }}>{role}</span>
                        </div>
                      </div>
                      {/* Role change — only for other members */}
                      {!isSelf && (
                        <div className="flex items-center gap-1.5 flex-shrink-0">
                          <div className="relative">
                            <select
                              value={role}
                              disabled={roleUpdating === uid}
                              onChange={e => changeMemberRole(uid, e.target.value)}
                              className="input text-xs py-1 px-2 pr-6 rounded-lg cursor-pointer"
                              style={{ fontSize: "12px" }}>
                              <option value="VIEWER">👁 Viewer</option>
                              <option value="EDITOR">✏️ Editor</option>
                              <option value="ADMIN">🛡 Admin</option>
                            </select>
                            {roleUpdating === uid && (
                              <Loader2 size={12} className="animate-spin absolute right-1.5 top-1/2 -translate-y-1/2"
                                style={{ color: "var(--primary)" }} />
                            )}
                          </div>
                          <button onClick={() => removeMember(uid)}
                            className="btn-icon" style={{ color: "var(--negative)" }}
                            title="Remove member">
                            <X size={14} />
                          </button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
              <p className="text-xs mt-4" style={{ color: "var(--text-hint)" }}>
                💡 Share the invite code with others. They choose Viewer or Editor when joining.
              </p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Balance summary */}
      <div className="grid grid-cols-3 gap-3 mb-5">
        {[
          { label: "Cash In", val: book.totalCashIn, color: "var(--positive)", bg: "var(--cash-in-bg)", Icon: TrendingUp },
          { label: "Cash Out", val: book.totalCashOut, color: "var(--negative)", bg: "var(--cash-out-bg)", Icon: TrendingDown },
          { label: "Net Balance", val: book.netBalance, color: book.netBalance >= 0 ? "var(--positive)" : "var(--negative)", bg: book.netBalance >= 0 ? "var(--cash-in-bg)" : "var(--cash-out-bg)", Icon: DollarSign },
        ].map(({ label, val, color, bg, Icon }) => (
          <div key={label} className="card p-3 md:p-4 flex items-center gap-2 md:gap-3">
            <div className="w-8 h-8 md:w-9 md:h-9 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: bg }}>
              <Icon size={16} style={{ color }} />
            </div>
            <div className="min-w-0">
              <p className="text-xs truncate" style={{ color: "var(--text-secondary)" }}>{label}</p>
              <p className="font-bold text-sm" style={{ color }}>{fmt(val ?? 0)}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Add Entry Form */}
      <AnimatePresence>
        {showForm && canEdit && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }} transition={{ duration: 0.2 }} className="overflow-hidden mb-5">
            <form onSubmit={addEntry} className="card p-5 space-y-3">
              <h3 style={{ color: "var(--text-primary)" }}>New Entry</h3>

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
                    className="input text-sm" />
                </div>
                <div>
                  <label className="text-xs font-medium mb-1 block" style={{ color: "var(--text-secondary)" }}>Amount (₹)</label>
                  <input type="number" required min="0.01" step="0.01" value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })}
                    placeholder="0.00" className="input text-sm" />
                </div>
              </div>

              <input required value={form.particulars} onChange={e => setForm({ ...form, particulars: e.target.value })}
                placeholder="Particulars (e.g. Salary, Rent...)" className="input" />

              <div className="grid grid-cols-2 gap-2">
                <select value={form.medium} onChange={e => setForm({ ...form, medium: e.target.value })} className="input text-sm">
                  <option value="CASH">💵 Cash</option>
                  <option value="BANK">🏦 Bank</option>
                </select>
                <select value={form.category} onChange={e => setForm({ ...form, category: e.target.value })} className="input text-sm">
                  {categories.map(c => <option key={c}>{c}</option>)}
                </select>
              </div>

              <input value={form.note} onChange={e => setForm({ ...form, note: e.target.value })}
                placeholder="Note (optional)" className="input" />

              <div className="flex gap-2">
                <button type="submit" disabled={saving} className="btn btn-primary btn-sm">
                  {saving ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />} Save Entry
                </button>
                <button type="button" onClick={() => setShowForm(false)} className="btn btn-ghost btn-sm">Cancel</button>
              </div>
            </form>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Filter */}
      <div className="flex items-center gap-2 mb-4 flex-wrap">
        <Filter size={14} style={{ color: "var(--text-hint)" }} />
        {["ALL", "CASH_IN", "CASH_OUT"].map(f => (
          <button key={f} onClick={() => setFilterType(f)} className="chip cursor-pointer text-xs"
            style={filterType === f
              ? { background: "var(--primary)", color: "white" }
              : { background: "var(--surface)", color: "var(--text-secondary)", border: "1px solid var(--divider)" }}>
            {f === "ALL" ? "All" : f === "CASH_IN" ? "↑ In" : "↓ Out"}
          </button>
        ))}
        <span className="ml-auto text-xs" style={{ color: "var(--text-hint)" }}>{filtered.length} entries</span>
      </div>

      {/* Entries list */}
      {loading ? (
        <div className="space-y-2">{[...Array(5)].map((_, i) => <div key={i} className="shimmer h-16" />)}</div>
      ) : filtered.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon"><DollarSign size={28} style={{ color: "var(--primary)" }} /></div>
          <p className="font-semibold" style={{ color: "var(--text-primary)" }}>No entries yet</p>
          <p className="text-sm" style={{ color: "var(--text-secondary)" }}>
            {canEdit ? "Add the first entry to get started!" : "Entries added by members will appear here."}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {filtered.map((entry, i) => {
            const isMyEntry = entry.createdBy === user?.uid;
            return (
              <motion.div key={entry.id}
                initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.03 }}
                className="card p-3.5 flex items-center gap-3 group">
                {/* Type indicator */}
                <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
                  style={{ background: entry.type === "CASH_IN" ? "var(--cash-in-bg)" : "var(--cash-out-bg)" }}>
                  {entry.type === "CASH_IN"
                    ? <TrendingUp size={16} style={{ color: "var(--positive)" }} />
                    : <TrendingDown size={16} style={{ color: "var(--negative)" }} />
                  }
                </div>

                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-sm truncate" style={{ color: "var(--text-primary)" }}>
                    {entry.particulars}
                  </p>
                  <div className="flex items-center gap-2 mt-0.5 flex-wrap">
                    <span className="text-xs" style={{ color: "var(--text-hint)" }}>
                      {new Date(entry.date).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "2-digit" })}
                    </span>
                    <span className="text-xs font-medium" style={{ color: "var(--text-hint)" }}>{entry.medium}</span>
                    <span className="text-xs" style={{ color: "var(--text-hint)" }}>{entry.category}</span>
                    {/* Feature 20: Show who added */}
                    {entry.createdByName && (
                      <span className="text-xs flex items-center gap-0.5" style={{ color: "var(--text-hint)" }}>
                        <Users size={10} /> {isMyEntry ? "by You" : `by ${entry.createdByName}`}
                      </span>
                    )}
                  </div>
                </div>

                <div className="text-right flex-shrink-0">
                  <p className="font-bold text-sm" style={{ color: entry.type === "CASH_IN" ? "var(--positive)" : "var(--negative)" }}>
                    {entry.type === "CASH_IN" ? "+" : "-"}{fmt(entry.amount)}
                  </p>
                </div>

                {/* Delete — EDITOR can delete their own; ADMIN can delete any */}
                {canEdit && (isAdmin || isMyEntry) && (
                  <button onClick={() => deleteEntry(entry)}
                    className="opacity-0 group-hover:opacity-100 transition-opacity p-1.5 rounded-lg"
                    style={{ color: "var(--negative)", background: "rgba(220,53,69,0.08)" }}>
                    <Trash2 size={14} />
                  </button>
                )}
              </motion.div>
            );
          })}
        </div>
      )}
    </div>
  );
}
