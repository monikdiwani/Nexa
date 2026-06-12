"use client";
import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, onSnapshot, where, addDoc, serverTimestamp } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion } from "framer-motion";
import { DollarSign, TrendingUp, TrendingDown, Plus, Loader2 } from "lucide-react";
import Link from "next/link";

interface LedgerBook { id: string; name: string; currency: string; ownerId: string; totalCashIn: number; totalCashOut: number; netBalance: number; }

export default function MoneyPage() {
  const { user } = useAuth();
  const [books, setBooks] = useState<LedgerBook[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");

  useEffect(() => {
    if (!user) return;
    const q = query(collection(db, "cashbooks"), where(`members.${user.uid}`, "!=", null));
    return onSnapshot(q, snap => { setBooks(snap.docs.map(d=>({id:d.id,...d.data()} as LedgerBook))); setLoading(false); });
  }, [user]);

  const createBook = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !name.trim()) return;
    setCreating(true);
    const code = Math.random().toString(36).substring(2, 8).toUpperCase();
    const ref = await addDoc(collection(db, "cashbooks"), {
      name: name.trim(), currency: "INR", ownerId: user.uid, inviteCode: code,
      members: { [user.uid]: "ADMIN" }, totalCashIn: 0, totalCashOut: 0, netBalance: 0,
      createdAt: Date.now()
    });
    await addDoc(collection(db, "invite_codes"), { code, bookId: ref.id });
    setName(""); setShowForm(false); setCreating(false);
  };

  const totalIn = books.reduce((s,b)=>s+b.totalCashIn,0);
  const totalOut = books.reduce((s,b)=>s+b.totalCashOut,0);
  const fmt = (n: number) => `₹${Math.abs(n).toLocaleString("en-IN",{minimumFractionDigits:0})}`;

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{background:"linear-gradient(135deg,#7C83F7,#5C6BC0)"}}>
            <DollarSign className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-xl font-bold" style={{color:"var(--text-primary)"}}>Money</h1>
            <p className="text-sm" style={{color:"var(--text-secondary)"}}>{books.length} ledger books</p>
          </div>
        </div>
        <button onClick={()=>setShowForm(!showForm)} className="btn-primary px-4 py-2 text-sm flex items-center gap-2">
          <Plus className="w-4 h-4" /> New Book
        </button>
      </div>

      {/* Summary */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        {[
          {label:"Total In",val:totalIn,color:"var(--positive)",bg:"var(--cash-in-bg)",Icon:TrendingUp},
          {label:"Total Out",val:totalOut,color:"var(--negative)",bg:"var(--cash-out-bg)",Icon:TrendingDown},
          {label:"Net",val:totalIn-totalOut,color:(totalIn-totalOut)>=0?"var(--positive)":"var(--negative)",bg:(totalIn-totalOut)>=0?"var(--cash-in-bg)":"var(--cash-out-bg)",Icon:DollarSign}
        ].map(({label,val,color,bg,Icon})=>(
          <div key={label} className="rounded-2xl p-4 flex items-center gap-3" style={{background:"var(--surface)",boxShadow:"var(--shadow-card)"}}>
            <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0" style={{background:bg}}>
              <Icon className="w-4 h-4" style={{color}} />
            </div>
            <div>
              <p className="text-xs" style={{color:"var(--text-secondary)"}}>{label}</p>
              <p className="font-bold text-sm" style={{color}}>{fmt(val)}</p>
            </div>
          </div>
        ))}
      </div>

      {showForm && (
        <motion.form initial={{opacity:0,y:-8}} animate={{opacity:1,y:0}} onSubmit={createBook}
          className="rounded-2xl p-5 mb-5 space-y-3" style={{background:"var(--surface)",boxShadow:"var(--shadow-card)"}}>
          <h3 className="font-semibold" style={{color:"var(--text-primary)"}}>Create Ledger Book</h3>
          <input required value={name} onChange={e=>setName(e.target.value)} placeholder="Book name (e.g. Home Expenses)"
            className="w-full nexa-input px-4 py-2.5 text-sm" />
          <div className="flex gap-2">
            <button type="submit" disabled={creating} className="btn-primary px-4 py-2 text-sm">
              {creating?<Loader2 className="w-4 h-4 animate-spin" />:"Create Book"}
            </button>
            <button type="button" onClick={()=>setShowForm(false)} className="btn-secondary px-4 py-2 text-sm">Cancel</button>
          </div>
        </motion.form>
      )}

      {loading ? (
        <div className="space-y-3">{[...Array(3)].map((_,i)=><div key={i} className="h-20 rounded-2xl shimmer" />)}</div>
      ) : books.length === 0 ? (
        <div className="text-center py-16" style={{color:"var(--text-hint)"}}><DollarSign className="w-12 h-12 mx-auto mb-3 opacity-30" /><p>No ledger books yet.</p></div>
      ) : (
        <div className="space-y-3">
          {books.map((book,i)=>(
            <motion.div key={book.id} initial={{opacity:0,y:8}} animate={{opacity:1,y:0}} transition={{delay:i*0.06}}>
              <Link href={`/dashboard/money/${book.id}`}
                className="flex items-center gap-4 p-4 rounded-2xl card-hover block" style={{background:"var(--surface)",boxShadow:"var(--shadow-card)"}}>
                <div className="w-11 h-11 rounded-xl flex items-center justify-center text-white font-bold text-base flex-shrink-0 nexa-gradient">
                  {book.name[0].toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="font-semibold" style={{color:"var(--text-primary)"}}>{book.name}</p>
                  <div className="flex gap-3 mt-0.5 text-xs">
                    <span style={{color:"var(--positive)"}}>In: {fmt(book.totalCashIn)}</span>
                    <span style={{color:"var(--negative)"}}>Out: {fmt(book.totalCashOut)}</span>
                  </div>
                </div>
                <div className="text-right">
                  <p className="font-bold" style={{color:book.netBalance>=0?"var(--positive)":"var(--negative)"}}>{fmt(book.netBalance)}</p>
                  <p className="text-xs" style={{color:"var(--text-hint)"}}>Net</p>
                </div>
              </Link>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}
