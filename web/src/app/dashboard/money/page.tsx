"use client";
import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, onSnapshot, where, addDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion, AnimatePresence } from "framer-motion";
import { DollarSign, TrendingUp, TrendingDown, Plus, Loader2, BookOpen, ChevronRight } from "lucide-react";
import Link from "next/link";

interface LedgerBook {
  id: string; name: string; currency: string; ownerId: string;
  totalCashIn: number; totalCashOut: number; netBalance: number;
  createdAt: number;
}

export default function MoneyPage() {
  const { user } = useAuth();
  const [books, setBooks] = useState<LedgerBook[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");

  useEffect(() => {
    if (!user) return;
    // cashbooks is a top-level collection keyed by membership — where is required here
    const q = query(collection(db, "cashbooks"), where(`members.${user.uid}`, "!=", null));
    return onSnapshot(q, snap => {
      setBooks(snap.docs.map(d=>({id:d.id,...d.data()} as LedgerBook)));
      setLoading(false);
    });
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
  const net = totalIn - totalOut;
  const fmt = (n: number) => `₹${Math.abs(n).toLocaleString("en-IN",{minimumFractionDigits:0})}`;

  const BOOK_GRADIENTS = [
    "linear-gradient(135deg,#7C83F7,#5C6BC0)",
    "linear-gradient(135deg,#26C6DA,#00897B)",
    "linear-gradient(135deg,#FF8A65,#E53935)",
    "linear-gradient(135deg,#AB47BC,#7B1FA2)",
    "linear-gradient(135deg,#FFA726,#F57C00)",
    "linear-gradient(135deg,#66BB6A,#388E3C)",
  ];

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto pb-10">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold" style={{color:"var(--text-primary)"}}>Money</h1>
          <p className="text-sm mt-0.5" style={{color:"var(--text-secondary)"}}>
            {books.length} ledger {books.length===1?"book":"books"}
          </p>
        </div>
        <button onClick={()=>setShowForm(!showForm)} className="btn btn-primary btn-sm">
          <Plus size={15}/> New Book
        </button>
      </div>

      {/* Summary bar */}
      <div className="grid grid-cols-3 gap-3 mb-6">
        {[
          {label:"Total In", val:totalIn, color:"var(--positive)", bg:"var(--cash-in-bg)", Icon:TrendingUp},
          {label:"Total Out", val:totalOut, color:"var(--negative)", bg:"var(--cash-out-bg)", Icon:TrendingDown},
          {label:"Net", val:net, color:net>=0?"var(--positive)":"var(--negative)", bg:net>=0?"var(--cash-in-bg)":"var(--cash-out-bg)", Icon:DollarSign},
        ].map(({label,val,color,bg,Icon})=>(
          <motion.div key={label} initial={{opacity:0,y:10}} animate={{opacity:1,y:0}}
            className="card p-3.5 md:p-4 flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0" style={{background:bg}}>
              <Icon size={16} style={{color}}/>
            </div>
            <div className="min-w-0">
              <p className="text-xs font-medium truncate" style={{color:"var(--text-secondary)"}}>{label}</p>
              <p className="font-bold text-sm md:text-base truncate" style={{color}}>{fmt(val)}</p>
            </div>
          </motion.div>
        ))}
      </div>

      {/* Create Form */}
      <AnimatePresence>
        {showForm && (
          <motion.div initial={{opacity:0,height:0}} animate={{opacity:1,height:"auto"}}
            exit={{opacity:0,height:0}} transition={{duration:0.2}} className="overflow-hidden mb-5">
            <form onSubmit={createBook} className="card p-5 space-y-3">
              <h3 className="font-semibold" style={{color:"var(--text-primary)"}}>Create Ledger Book</h3>
              <input required value={name} onChange={e=>setName(e.target.value)}
                placeholder="Book name (e.g. Home Expenses)" className="input" />
              <div className="flex gap-2">
                <button type="submit" disabled={creating} className="btn btn-primary btn-sm">
                  {creating ? <Loader2 size={14} className="animate-spin"/> : <BookOpen size={14}/>} Create Book
                </button>
                <button type="button" onClick={()=>setShowForm(false)} className="btn btn-ghost btn-sm">Cancel</button>
              </div>
            </form>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Book List */}
      {loading ? (
        <div className="space-y-3">
          {[...Array(3)].map((_,i)=><div key={i} className="shimmer h-20 rounded-2xl"/>)}
        </div>
      ) : books.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">
            <DollarSign size={28} style={{color:"var(--primary)"}}/>
          </div>
          <p className="font-semibold" style={{color:"var(--text-primary)"}}>No ledger books yet</p>
          <p className="text-sm" style={{color:"var(--text-secondary)"}}>Create your first book to start tracking money.</p>
        </div>
      ) : (
        <div className="space-y-3">
          <AnimatePresence>
            {books.map((book,i)=>(
              <motion.div key={book.id} initial={{opacity:0,y:8}} animate={{opacity:1,y:0}}
                exit={{opacity:0}} transition={{delay:i*0.04,duration:0.2}}>
                <Link href={`/dashboard/money/${book.id}`}
                  className="card p-4 flex items-center gap-4 card-hover block group">
                  {/* Book icon */}
                  <div className="w-12 h-12 rounded-xl flex items-center justify-center text-white font-black text-lg flex-shrink-0"
                    style={{background: BOOK_GRADIENTS[i % BOOK_GRADIENTS.length]}}>
                    {book.name[0]?.toUpperCase() ?? "B"}
                  </div>

                  <div className="flex-1 min-w-0">
                    <p className="font-semibold truncate" style={{color:"var(--text-primary)"}}>{book.name}</p>
                    <div className="flex gap-3 mt-1 flex-wrap">
                      <span className="text-xs flex items-center gap-0.5" style={{color:"var(--positive)"}}>
                        <TrendingUp size={11}/> {fmt(book.totalCashIn)}
                      </span>
                      <span className="text-xs flex items-center gap-0.5" style={{color:"var(--negative)"}}>
                        <TrendingDown size={11}/> {fmt(book.totalCashOut)}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-3 flex-shrink-0">
                    <div className="text-right">
                      <p className="font-bold text-base" style={{color:book.netBalance>=0?"var(--positive)":"var(--negative)"}}>
                        {book.netBalance < 0 ? "-" : ""}{fmt(book.netBalance)}
                      </p>
                      <p className="text-xs" style={{color:"var(--text-hint)"}}>Net Balance</p>
                    </div>
                    <ChevronRight size={16} className="opacity-0 group-hover:opacity-100 transition-opacity" style={{color:"var(--text-hint)"}}/>
                  </div>
                </Link>
              </motion.div>
            ))}
          </AnimatePresence>
        </div>
      )}
    </div>
  );
}
