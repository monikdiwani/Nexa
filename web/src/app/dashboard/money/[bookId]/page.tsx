"use client";

import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import {
  doc, onSnapshot, collection, query, orderBy,
  addDoc, updateDoc, deleteDoc, increment, getDoc, writeBatch, getDocs, where
} from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion, AnimatePresence } from "framer-motion";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowLeft, Plus, TrendingUp, TrendingDown, Copy, Check,
  Trash2, Filter, Loader2, Users, DollarSign, Shield, Eye, Pencil, X, Search, Download, Calculator, FileText, CheckCircle2, History, Camera
} from "lucide-react";
import jsPDF from "jspdf";
import "jspdf-autotable";
import Tesseract from "tesseract.js";
import { simplifyDebts, DebtEdge } from "@/lib/DebtSimplifier";

interface LedgerBook {
  id: string; name: string; currency: string; ownerId: string;
  inviteCode: string; totalCashIn: number; totalCashOut: number;
  netBalance: number; members: Record<string, string>;
}

interface Entry {
  id: string; date: number; particulars: string; type: string;
  medium: string; amount: number; category: string; note: string;
  createdByName?: string; createdBy?: string; createdAt: number;
  splits?: Record<string, number>;
  paidBy?: string;
  isSettlement?: boolean;
  settledWith?: string;
  isRecurring?: boolean;
  recurringPattern?: string;
}

interface LogEntry {
  id: string; actionType: string; actorName: string;
  particulars: string; transactionType: string; amount: number;
  timestamp: number; details: string;
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
  const [search, setSearch] = useState("");
  const [showMembersPanel, setShowMembersPanel] = useState(false);
  const [showLogsPanel, setShowLogsPanel] = useState(false);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [roleUpdating, setRoleUpdating] = useState<string | null>(null);
  const [memberNames, setMemberNames] = useState<Record<string, string>>({});
  const [simplifiedDebts, setSimplifiedDebts] = useState<DebtEdge[]>([]);
  const [settling, setSettling] = useState<string | null>(null);
  
  const [filterType, setFilterType] = useState("ALL");
  const [filterMedium, setFilterMedium] = useState("ALL");
  const [filterTime, setFilterTime] = useState("ALL");
  const [scanning, setScanning] = useState(false);

  const [form, setForm] = useState({
    date: new Date().toISOString().split("T")[0],
    particulars: "", type: "CASH_IN", medium: "CASH",
    amount: "", category: "Other", note: "",
    isRecurring: false, recurringPattern: "MONTHLY"
  });

  useEffect(() => {
    if (!user || !bookId) return;
    const bookUnsub = onSnapshot(doc(db, "cashbooks", bookId), (snap) => {
      if (snap.exists()) setBook({ id: snap.id, ...snap.data() } as LedgerBook);
    });
    const entriesUnsub = onSnapshot(
      query(collection(db, "cashbooks", bookId, "entries"), orderBy("date", "desc")),
      (snap) => {
        const data = snap.docs.map(d => ({ id: d.id, ...d.data() } as Entry));
        setEntries(data);
        setLoading(false);
        
        // Calculate debts
        const edges: DebtEdge[] = [];
        data.forEach(e => {
          if (e.isSettlement && e.createdBy && e.settledWith) {
            edges.push({ from: e.createdBy, to: e.settledWith, amount: e.amount });
          } else if (e.splits && e.paidBy) {
            for (const [uid, amt] of Object.entries(e.splits)) {
              if (uid !== e.paidBy) edges.push({ from: uid, to: e.paidBy, amount: amt as number });
            }
          }
        });
        setSimplifiedDebts(simplifyDebts(edges));
      }
    );
    return () => { bookUnsub(); entriesUnsub(); };
  }, [user, bookId]);

  // Resolve member UIDs to display names
  useEffect(() => {
    if (!book?.members) return;
    const uids = Object.keys(book.members).filter(uid => uid !== user?.uid);
    if (uids.length === 0) return;
    const newNames: Record<string, string> = {};
    Promise.all(
      uids.map(async (uid) => {
        try {
          const snap = await getDoc(doc(db, "users", uid));
          if (snap.exists()) {
            const data = snap.data() as any;
            newNames[uid] = data.displayName || data.name || data.email || `User (${uid.slice(0,6)})`;
          } else {
            newNames[uid] = `User (${uid.slice(0,6)})`;
          }
        } catch {
          newNames[uid] = `User (${uid.slice(0,6)})`;
        }
      })
    ).then(() => setMemberNames(newNames));
  }, [book?.members, user?.uid]);

  const myRole = book?.members?.[user?.uid ?? ""] ?? "VIEWER";
  const isAdmin = myRole === "ADMIN";
  const canEdit = myRole === "ADMIN" || myRole === "EDITOR"; // Feature 19: EDITOR can add entries

  const loadLogs = async () => {
    setShowLogsPanel(true);
    const snap = await getDocs(query(collection(db, "cashbooks", bookId, "logs"), orderBy("timestamp", "desc")));
    setLogs(snap.docs.map(d => ({ id: d.id, ...d.data() } as LogEntry)));
  };

  const settleUp = async (debt: DebtEdge) => {
    if (!user || !book || !canEdit) return;
    setSettling(debt.from + debt.to);
    
    // Create a settlement entry
    await addDoc(collection(db, "cashbooks", bookId, "entries"), {
      date: Date.now(), particulars: "Settlement", type: "CASH_OUT",
      medium: "CASH", amount: debt.amount, category: "Settlement",
      note: "Settle Up", createdBy: debt.from,
      createdByName: memberNames[debt.from] ?? "Member",
      createdAt: Date.now(), lastModifiedAt: Date.now(),
      isSettlement: true, settledWith: debt.to
    });
    
    await addDoc(collection(db, "cashbooks", bookId, "logs"), {
      actionType: "SETTLE", actorId: user.uid,
      actorName: user.displayName ?? "Unknown",
      particulars: "Settlement", transactionType: "SETTLE",
      amount: debt.amount, timestamp: Date.now(), details: "Debt simplified settlement"
    });
    
    setSettling(null);
  };

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
      isRecurring: form.isRecurring, recurringPattern: form.isRecurring ? form.recurringPattern : null,
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

    setForm({ date: new Date().toISOString().split("T")[0], particulars: "", type: "CASH_IN", medium: "CASH", amount: "", category: "Other", note: "", isRecurring: false, recurringPattern: "MONTHLY" });
    setShowForm(false);
    setSaving(false);
  };

  const scanReceipt = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setScanning(true);
    try {
      const { data: { text } } = await Tesseract.recognize(file, 'eng');
      
      // Simple regex to find the largest currency-like number
      const amounts = text.match(/\$?\s?\d+[\.,]\d{2}/g);
      let maxAmt = 0;
      if (amounts) {
        amounts.forEach(a => {
          const num = parseFloat(a.replace(/[^0-9\.]/g, ''));
          if (num > maxAmt) maxAmt = num;
        });
      }
      
      if (maxAmt > 0) {
        setForm(f => ({ ...f, amount: maxAmt.toString() }));
      } else {
        alert("Could not automatically detect amount from receipt.");
      }
    } catch (err) {
      alert("Failed to scan receipt.");
    }
    setScanning(false);
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

  const deleteBook = async () => {
    if (!isAdmin || !window.confirm(`Are you sure you want to permanently delete '${book?.name}' and all its entries?`)) return;
    try {
      const batch = writeBatch(db);
      const entriesSnap = await getDocs(collection(db, "cashbooks", bookId, "entries"));
      entriesSnap.forEach(docSnap => batch.delete(docSnap.ref));
      batch.delete(doc(db, "cashbooks", bookId));
      await batch.commit();
      router.push("/dashboard/money");
    } catch (e) {
      console.error("Error deleting book:", e);
      alert("Failed to delete the cashbook.");
    }
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

  const exportPDF = () => {
    if (!book) return;
    const doc = new jsPDF();
    doc.setFontSize(18);
    doc.text(`Ledger Report: ${book.name}`, 14, 22);
    doc.setFontSize(11);
    doc.text(`Total In: ${fmt(book.totalCashIn)} | Total Out: ${fmt(book.totalCashOut)} | Net: ${fmt(book.netBalance)}`, 14, 30);
    
    const tableData = filtered.map(e => [
      new Date(e.date).toLocaleDateString("en-IN"),
      e.particulars,
      e.type === "CASH_IN" ? "IN" : "OUT",
      fmt(e.amount),
      e.category,
      e.createdByName || ""
    ]);

    (doc as any).autoTable({
      startY: 40,
      head: [["Date", "Particulars", "Type", "Amount", "Category", "Added By"]],
      body: tableData,
      theme: 'grid',
      headStyles: { fillColor: [92, 107, 192] }
    });
    
    doc.save(`${book.name.replace(/[^a-z0-9]/gi, '_').toLowerCase()}_report.pdf`);
  };

  const filtered = entries.filter(e => {
    const matchesSearch = !search || e.particulars.toLowerCase().includes(search.toLowerCase()) ||
      e.note?.toLowerCase().includes(search.toLowerCase()) ||
      e.category?.toLowerCase().includes(search.toLowerCase());
      
    const matchesType = filterType === "ALL" || e.type === filterType;
    const matchesMedium = filterMedium === "ALL" || e.medium === filterMedium;
    
    let matchesTime = true;
    if (filterTime !== "ALL") {
      const msDay = 86400000;
      const now = Date.now();
      if (filterTime === "TODAY") matchesTime = (now - e.date) < msDay;
      else if (filterTime === "WEEK") matchesTime = (now - e.date) < msDay * 7;
      else if (filterTime === "MONTH") matchesTime = (now - e.date) < msDay * 30;
    }
    
    return matchesSearch && matchesType && matchesMedium && matchesTime;
  });

  // Compute running balance (newest to oldest = subtract/add as we go from total)
  const runningBalances: Record<string, number> = {};
  let running = (book?.netBalance ?? 0);
  // entries are ordered newest first
  entries.forEach(e => {
    runningBalances[e.id] = running;
    // go backwards: subtract what this entry contributed
    running = e.type === "CASH_IN" ? running - e.amount : running + e.amount;
  });

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
          {isAdmin && (
            <button onClick={deleteBook} className="btn btn-ghost btn-sm flex items-center gap-1.5" style={{ color: "var(--negative)" }}>
              <Trash2 size={15} /> Delete
            </button>
          )}
          <button onClick={() => router.push(`/dashboard/money/${bookId}/cash-counter`)} className="btn btn-ghost btn-sm flex items-center gap-1.5" style={{ color: "var(--text-secondary)" }}>
            <Calculator size={15} /> Cash Counter
          </button>
          <button onClick={loadLogs} className="btn btn-ghost btn-sm flex items-center gap-1.5" style={{ color: "var(--text-secondary)" }}>
            <History size={15} /> Logs
          </button>
          <button onClick={exportPDF} className="btn btn-ghost btn-sm flex items-center gap-1.5" style={{ color: "var(--primary)" }}>
            <FileText size={15} /> Export PDF
          </button>
          {canEdit && (
            <button onClick={() => router.push(`/dashboard/money/${bookId}/add-shared`)} className="btn btn-ghost btn-sm flex items-center gap-1.5" style={{ color: "var(--primary)" }}>
              <Users size={15} /> Add Shared
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
                  const displayName = isSelf ? (user?.displayName || "You") : (memberNames[uid] || `User (${uid.slice(0,6)})`);
                  const nameInitials = displayName.split(" ").map((n: string) => n[0]).join("").toUpperCase().slice(0,2);
                  return (
                    <div key={uid} className="flex items-center gap-3 p-3 rounded-xl"
                      style={{ background: "var(--bg)" }}>
                      <div className="w-8 h-8 rounded-lg nexa-gradient flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                        {nameInitials || uid.slice(0,2).toUpperCase()}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold truncate" style={{ color: "var(--text-primary)" }}>
                          {isSelf ? `${displayName} (You)` : displayName}
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

      {/* Activity Logs Panel */}
      <AnimatePresence>
        {showLogsPanel && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }} transition={{ duration: 0.2 }}
            className="overflow-hidden mb-5">
            <div className="card p-5">
              <div className="flex items-center justify-between mb-4">
                <h3 style={{ color: "var(--text-primary)" }}>Activity Logs</h3>
                <button onClick={() => setShowLogsPanel(false)} className="btn-icon"><X size={16} /></button>
              </div>
              <div className="space-y-3 max-h-64 overflow-y-auto pr-2">
                {logs.length === 0 && <p className="text-sm text-center py-4" style={{ color: "var(--text-hint)" }}>No logs found</p>}
                {logs.map(log => (
                  <div key={log.id} className="flex gap-3 text-sm pb-2 border-b" style={{ borderColor: "var(--divider)" }}>
                    <div className="w-6 h-6 rounded flex items-center justify-center flex-shrink-0" style={{ background: "var(--primary-surface)" }}>
                      <History size={12} style={{ color: "var(--primary)" }} />
                    </div>
                    <div>
                      <p style={{ color: "var(--text-primary)" }}>
                        <span className="font-bold">{log.actorName}</span> {log.actionType.toLowerCase()}{" "}
                        <span className="font-semibold">{log.particulars}</span>
                      </p>
                      <p className="text-xs" style={{ color: "var(--text-hint)" }}>
                        {new Date(log.timestamp).toLocaleString()} • {fmt(log.amount)}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
      
      {/* Debt Simplifier Banner */}
      {simplifiedDebts.length > 0 && (
        <div className="card mb-5 overflow-hidden" style={{ borderColor: "var(--primary)" }}>
          <div className="px-4 py-2.5 text-sm font-bold flex items-center gap-2" style={{ background: "var(--primary-surface)", color: "var(--primary)" }}>
            <DollarSign size={16} /> Pending Settlements
          </div>
          <div className="p-4 space-y-3">
            {simplifiedDebts.map(d => {
              const fromName = memberNames[d.from] || (user?.uid === d.from ? "You" : "Member");
              const toName = memberNames[d.to] || (user?.uid === d.to ? "You" : "Member");
              const amIOwing = user?.uid === d.from;
              const amIReceiving = user?.uid === d.to;
              
              return (
                <div key={d.from+d.to} className="flex items-center justify-between p-3 rounded-xl" style={{ background: "var(--bg)" }}>
                  <div>
                    <p className="text-sm" style={{ color: "var(--text-primary)" }}>
                      <span className="font-bold">{fromName}</span> owes <span className="font-bold">{toName}</span>
                    </p>
                    <p className="font-black text-base" style={{ color: amIOwing ? "var(--negative)" : amIReceiving ? "var(--positive)" : "var(--text-secondary)" }}>
                      {fmt(d.amount)}
                    </p>
                  </div>
                  {canEdit && (amIOwing || amIReceiving || isAdmin) && (
                    <button onClick={() => settleUp(d)} disabled={settling === d.from+d.to} className="btn btn-sm" style={{ background: "var(--cash-in-bg)", color: "var(--positive)", border: "none" }}>
                      {settling === d.from+d.to ? <Loader2 size={14} className="animate-spin" /> : <CheckCircle2 size={14} />} Mark Paid
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

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
                <div className="relative">
                  <input type="file" accept="image/*" id="receiptScan" hidden onChange={scanReceipt} />
                  <label htmlFor="receiptScan" className="btn btn-outline w-full h-full text-sm flex items-center justify-center gap-1.5 cursor-pointer">
                    {scanning ? <Loader2 size={15} className="animate-spin" /> : <Camera size={15} />}
                    {scanning ? "Scanning..." : "Scan Receipt"}
                  </label>
                </div>
              </div>

              <div>
                <label className="text-xs font-medium mb-1.5 block" style={{ color: "var(--text-secondary)" }}>Category</label>
                <div className="flex gap-2 flex-wrap max-h-24 overflow-y-auto">
                  {categories.map(c => (
                    <button key={c} type="button" onClick={() => setForm({ ...form, category: c })}
                      className="px-2 py-1 text-xs rounded-md border font-medium"
                      style={{ 
                        background: form.category === c ? "var(--primary)" : "var(--bg)", 
                        color: form.category === c ? "white" : "var(--text-primary)",
                        borderColor: form.category === c ? "var(--primary)" : "var(--divider)"
                      }}>
                      {c}
                    </button>
                  ))}
                </div>
              </div>

              <input value={form.note} onChange={e => setForm({ ...form, note: e.target.value })}
                placeholder="Note (optional)" className="input" />

              <div className="flex items-center gap-3 py-2">
                <label className="flex items-center gap-2 cursor-pointer text-sm font-medium" style={{ color: "var(--text-primary)" }}>
                  <input type="checkbox" checked={form.isRecurring} onChange={e => setForm({ ...form, isRecurring: e.target.checked })}
                    className="w-4 h-4 rounded" style={{ accentColor: "var(--primary)" }} />
                  Make this recurring
                </label>
                {form.isRecurring && (
                  <select value={form.recurringPattern} onChange={e => setForm({ ...form, recurringPattern: e.target.value })} className="input text-sm flex-1">
                    <option value="DAILY">Daily</option>
                    <option value="WEEKLY">Weekly</option>
                    <option value="MONTHLY">Monthly</option>
                    <option value="YEARLY">Yearly</option>
                  </select>
                )}
              </div>

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

      {/* Search + Filter */}
      <div className="relative mb-3">
        <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2" style={{ color: "var(--text-hint)" }} />
        <input value={search} onChange={e => setSearch(e.target.value)}
          placeholder="Search entries..." className="input pr-8 text-sm" style={{ paddingLeft: "36px" }} />
        {search && (
          <button onClick={() => setSearch("")} className="absolute right-3 top-1/2 -translate-y-1/2 btn-icon" style={{ padding: "2px" }}>
            <X size={13} />
          </button>
        )}
      </div>

      {/* Filter chips */}
      <div className="flex items-center gap-2 mb-4 flex-wrap">
        <Filter size={14} style={{ color: "var(--text-hint)" }} />
        {["ALL", "CASH_IN", "CASH_OUT"].map(f => (
          <button key={f} onClick={() => setFilterType(f)} className="chip cursor-pointer text-xs"
            style={filterType === f
              ? { background: "var(--primary)", color: "white" }
              : { background: "var(--surface)", color: "var(--text-secondary)", border: "1px solid var(--divider)" }}>
            {f === "ALL" ? "All Type" : f === "CASH_IN" ? "↑ In" : "↓ Out"}
          </button>
        ))}
        {["ALL", "CASH", "BANK"].map(f => (
          <button key={f} onClick={() => setFilterMedium(f)} className="chip cursor-pointer text-xs"
            style={filterMedium === f
              ? { background: "var(--primary)", color: "white" }
              : { background: "var(--surface)", color: "var(--text-secondary)", border: "1px solid var(--divider)" }}>
            {f === "ALL" ? "All Media" : f === "CASH" ? "💵 Cash" : "🏦 Bank"}
          </button>
        ))}
        {["ALL", "TODAY", "WEEK", "MONTH"].map(f => (
          <button key={f} onClick={() => setFilterTime(f)} className="chip cursor-pointer text-xs"
            style={filterTime === f
              ? { background: "var(--primary)", color: "white" }
              : { background: "var(--surface)", color: "var(--text-secondary)", border: "1px solid var(--divider)" }}>
            {f === "ALL" ? "All Time" : f === "TODAY" ? "Today" : f === "WEEK" ? "7 Days" : "30 Days"}
          </button>
        ))}
        <span className="ml-auto text-xs font-semibold" style={{ color: "var(--text-hint)" }}>{filtered.length} entries</span>
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
                    {entry.isRecurring && (
                      <span className="text-xs font-semibold px-1.5 py-0.5 rounded-md" style={{ background: "rgba(92,107,192,0.12)", color: "var(--primary)" }}>
                        ↻ {entry.recurringPattern}
                      </span>
                    )}
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
                  {runningBalances[entry.id] !== undefined && (
                    <p className="text-xs mt-0.5" style={{ color: "var(--text-hint)" }}>
                      bal: {runningBalances[entry.id] >= 0 ? "" : "-"}{fmt(runningBalances[entry.id])}
                    </p>
                  )}
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
