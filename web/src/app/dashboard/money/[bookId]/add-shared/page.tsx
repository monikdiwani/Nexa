"use client";

import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { doc, getDoc, collection, addDoc, updateDoc, increment } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { useRouter, useParams } from "next/navigation";
import { ArrowLeft, Check, Loader2, Users } from "lucide-react";

export default function AddSharedExpensePage() {
  const { user } = useAuth();
  const router = useRouter();
  const params = useParams();
  const bookId = params.bookId as string;

  const [book, setBook] = useState<any>(null);
  const [memberNames, setMemberNames] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [form, setForm] = useState({
    particulars: "",
    amount: "",
    category: "Food",
    date: new Date().toISOString().split("T")[0],
    paidBy: user?.uid || "",
    splitMethod: "EQUAL" // EQUAL, PERCENTAGE, EXACT
  });

  const [splits, setSplits] = useState<Record<string, number>>({});
  const [percentages, setPercentages] = useState<Record<string, number>>({});

  useEffect(() => {
    if (!user || !bookId) return;
    setForm(f => ({ ...f, paidBy: user.uid }));
    
    getDoc(doc(db, "cashbooks", bookId)).then(async snap => {
      if (!snap.exists()) return router.push("/dashboard/money");
      const data = snap.data();
      setBook(data);
      
      const newNames: Record<string, string> = {};
      const uids = Object.keys(data.members || {});
      
      // Init equal splits initially
      const initialSplits: Record<string, number> = {};
      const initialPercents: Record<string, number> = {};
      
      await Promise.all(uids.map(async uid => {
        try {
          const uSnap = await getDoc(doc(db, "users", uid));
          if (uSnap.exists()) newNames[uid] = uSnap.data().displayName || `User (${uid.slice(0,6)})`;
          else newNames[uid] = `User (${uid.slice(0,6)})`;
        } catch {
          newNames[uid] = `User (${uid.slice(0,6)})`;
        }
        initialSplits[uid] = 0;
        initialPercents[uid] = Math.round((100 / uids.length) * 100) / 100;
      }));
      setMemberNames(newNames);
      setSplits(initialSplits);
      setPercentages(initialPercents);
      setLoading(false);
    });
  }, [user, bookId, router]);

  const handleAmountChange = (val: string) => {
    setForm(f => ({ ...f, amount: val }));
    const amt = parseFloat(val) || 0;
    
    if (form.splitMethod === "EQUAL" && book) {
      const uids = Object.keys(book.members || {});
      const splitAmt = Math.round((amt / uids.length) * 100) / 100;
      const newSplits: Record<string, number> = {};
      uids.forEach(u => newSplits[u] = splitAmt);
      setSplits(newSplits);
    } else if (form.splitMethod === "PERCENTAGE" && book) {
      const uids = Object.keys(book.members || {});
      const newSplits: Record<string, number> = {};
      uids.forEach(u => {
        newSplits[u] = Math.round((amt * (percentages[u] || 0) / 100) * 100) / 100;
      });
      setSplits(newSplits);
    }
  };

  const handleSplitMethod = (method: string) => {
    setForm(f => ({ ...f, splitMethod: method }));
    const amt = parseFloat(form.amount) || 0;
    const uids = Object.keys(book?.members || {});
    
    if (method === "EQUAL") {
      const splitAmt = Math.round((amt / uids.length) * 100) / 100;
      const newSplits: Record<string, number> = {};
      uids.forEach(u => newSplits[u] = splitAmt);
      setSplits(newSplits);
    } else if (method === "PERCENTAGE") {
      const newSplits: Record<string, number> = {};
      uids.forEach(u => {
        newSplits[u] = Math.round((amt * (percentages[u] || 0) / 100) * 100) / 100;
      });
      setSplits(newSplits);
    }
  };

  const handlePercentageChange = (uid: string, p: number) => {
    const newP = { ...percentages, [uid]: p };
    setPercentages(newP);
    const amt = parseFloat(form.amount) || 0;
    setSplits(s => ({ ...s, [uid]: Math.round((amt * p / 100) * 100) / 100 }));
  };

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !book) return;
    
    const amt = parseFloat(form.amount);
    if (!amt || amt <= 0) return alert("Enter valid amount");
    
    const totalSplit = Object.values(splits).reduce((a, b) => a + b, 0);
    if (Math.abs(totalSplit - amt) > 1) {
      return alert(`Splits (${totalSplit}) do not match total amount (${amt})`);
    }

    setSaving(true);
    
    const dateMs = new Date(form.date).getTime();
    
    // Save entry as CASH_OUT, but with splits mapping
    await addDoc(collection(db, "cashbooks", bookId, "entries"), {
      date: dateMs, particulars: form.particulars, type: "CASH_OUT",
      medium: "CASH", amount: amt, category: form.category,
      note: `Split: ${form.splitMethod}`, createdBy: user.uid,
      createdByName: user.displayName ?? user.email ?? "Unknown",
      createdAt: Date.now(), lastModifiedAt: Date.now(),
      paidBy: form.paidBy, splits: splits
    });

    // We only update the netBalance of the book if we want it to reflect total cash out.
    // In a pure shared ledger, netBalance might just be sum of all cash out, 
    // but the actual debts are handled by DebtSimplifier.
    await updateDoc(doc(db, "cashbooks", bookId), {
      totalCashOut: increment(amt),
      netBalance: increment(-amt),
    });

    await addDoc(collection(db, "cashbooks", bookId, "logs"), {
      actionType: "CREATE", actorId: user.uid,
      actorName: user.displayName ?? "Unknown",
      particulars: form.particulars, transactionType: "SHARED_EXPENSE",
      amount: amt, timestamp: Date.now(), details: `Paid by ${memberNames[form.paidBy]}`
    });

    router.push(`/dashboard/money/${bookId}`);
  };

  if (loading) return <div className="p-10 text-center"><Loader2 className="animate-spin mx-auto text-blue-500" /></div>;

  return (
    <div className="p-4 md:p-6 max-w-xl mx-auto pb-10">
      <div className="flex items-center gap-3 mb-6">
        <button onClick={() => router.back()} className="btn-icon"><ArrowLeft size={20} /></button>
        <h1 className="text-xl font-bold">Add Shared Expense</h1>
      </div>

      <form onSubmit={save} className="card p-5 space-y-4">
        <div>
          <label className="text-xs font-semibold mb-1 block">Description</label>
          <input required value={form.particulars} onChange={e => setForm({...form, particulars: e.target.value})} className="input" placeholder="e.g. Dinner, Uber" />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="text-xs font-semibold mb-1 block">Amount (₹)</label>
            <input required type="number" min="1" step="0.01" value={form.amount} onChange={e => handleAmountChange(e.target.value)} className="input" placeholder="0.00" />
          </div>
          <div>
            <label className="text-xs font-semibold mb-1 block">Date</label>
            <input required type="date" value={form.date} onChange={e => setForm({...form, date: e.target.value})} className="input" />
          </div>
        </div>

        <div>
          <label className="text-xs font-semibold mb-1 block">Paid By</label>
          <select value={form.paidBy} onChange={e => setForm({...form, paidBy: e.target.value})} className="input">
            {Object.keys(book.members || {}).map(uid => (
              <option key={uid} value={uid}>{uid === user?.uid ? "You" : memberNames[uid]}</option>
            ))}
          </select>
        </div>

        <div className="border-t border-b py-3 border-gray-200 dark:border-gray-800">
          <label className="text-xs font-semibold mb-2 block">Split Method</label>
          <div className="flex gap-2">
            {["EQUAL", "PERCENTAGE", "EXACT"].map(m => (
              <button type="button" key={m} onClick={() => handleSplitMethod(m)} 
                className={`flex-1 py-1.5 text-xs font-semibold rounded-lg ${form.splitMethod === m ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'}`}>
                {m}
              </button>
            ))}
          </div>
        </div>

        <div className="space-y-3">
          {Object.keys(book.members || {}).map(uid => (
            <div key={uid} className="flex items-center gap-3">
              <div className="flex-1 flex items-center gap-2">
                <div className="w-6 h-6 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center text-xs font-bold">
                  {memberNames[uid]?.[0]?.toUpperCase()}
                </div>
                <p className="text-sm font-medium">{uid === user?.uid ? "You" : memberNames[uid]}</p>
              </div>
              
              {form.splitMethod === "PERCENTAGE" && (
                <div className="w-20 flex items-center gap-1">
                  <input type="number" value={percentages[uid] || 0} onChange={e => handlePercentageChange(uid, parseFloat(e.target.value)||0)} className="input text-xs py-1 px-2" />
                  <span className="text-xs">%</span>
                </div>
              )}
              
              <div className="w-24 relative">
                <span className="absolute left-2 top-1/2 -translate-y-1/2 text-xs text-gray-500">₹</span>
                <input type="number" step="0.01" 
                  disabled={form.splitMethod === "EQUAL"}
                  value={splits[uid] || ""} 
                  onChange={e => setSplits({...splits, [uid]: parseFloat(e.target.value)||0})} 
                  className="input text-xs py-1 pl-5" />
              </div>
            </div>
          ))}
        </div>

        <button type="submit" disabled={saving} className="btn btn-primary w-full mt-2">
          {saving ? <Loader2 className="animate-spin" size={16} /> : <Check size={16} />} 
          Save Expense
        </button>
      </form>
    </div>
  );
}
