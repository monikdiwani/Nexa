"use client";
import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, onSnapshot, orderBy, addDoc, updateDoc, doc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion } from "framer-motion";
import { Plus, Bell, CheckCircle, Clock, Loader2 } from "lucide-react";

interface Reminder { id: string; title: string; message: string; triggerTime: number; priority: string; category: string; isCompleted: boolean; }

const catEmoji: Record<string,string> = { BILL:"📄", MEETING:"👥", TASK:"✅", MEDICINE:"💊", SHOPPING:"🛒", CUSTOM:"⏰" };

export default function RemindersPage() {
  const { user } = useAuth();
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [filter, setFilter] = useState("PENDING");
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ title:"", message:"", triggerDate:"", triggerTime:"", priority:"MEDIUM", category:"CUSTOM" });

  useEffect(() => {
    if (!user) return;
    const q = query(collection(db, "users", user.uid, "reminders"), orderBy("triggerTime", "asc"));
    return onSnapshot(q, snap => { setReminders(snap.docs.map(d=>({id:d.id,...d.data()} as Reminder))); setLoading(false); });
  }, [user]);

  const addReminder = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    setSaving(true);
    const dt = new Date(`${form.triggerDate}T${form.triggerTime || "00:00"}`);
    await addDoc(collection(db, "users", user.uid, "reminders"), {
      title: form.title, message: form.message, triggerTime: dt.getTime(),
      priority: form.priority, category: form.category, isCompleted: false,
      isSnoozed: false, createdAt: Date.now(), recurringPattern: "NONE", isRecurring: false
    });
    setShowForm(false); setSaving(false);
    setForm({ title:"", message:"", triggerDate:"", triggerTime:"", priority:"MEDIUM", category:"CUSTOM" });
  };

  const markDone = async (r: Reminder) => {
    if (!user) return;
    await updateDoc(doc(db, "users", user.uid, "reminders", r.id), { isCompleted: true, completedAt: Date.now() });
  };

  const now = Date.now();
  const filtered = reminders.filter(r => {
    if (filter === "PENDING") return !r.isCompleted && r.triggerTime >= now;
    if (filter === "COMPLETED") return r.isCompleted;
    if (filter === "MISSED") return !r.isCompleted && r.triggerTime < now;
    return true;
  });

  const fmtTime = (ms: number) => {
    const d = ms - now;
    if (d < 0) return `Overdue by ${Math.abs(Math.round(d/3600000))}h`;
    if (d < 3600000) return `In ${Math.round(d/60000)} min`;
    if (d < 86400000) return `In ${Math.round(d/3600000)}h`;
    return `In ${Math.round(d/86400000)} days`;
  };

  return (
    <div className="p-4 md:p-6 max-w-3xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{background:"linear-gradient(135deg,#26C6DA,#00838F)"}}>
            <Bell className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-xl font-bold" style={{color:"var(--text-primary)"}}>Reminders</h1>
            <p className="text-sm" style={{color:"var(--text-secondary)"}}>{reminders.filter(r=>!r.isCompleted&&r.triggerTime>=now).length} upcoming</p>
          </div>
        </div>
        <button onClick={()=>setShowForm(!showForm)} className="btn-primary px-4 py-2 text-sm flex items-center gap-2">
          <Plus className="w-4 h-4" /> Add
        </button>
      </div>

      {showForm && (
        <motion.form initial={{opacity:0,y:-8}} animate={{opacity:1,y:0}} onSubmit={addReminder}
          className="rounded-2xl p-5 mb-5 space-y-3" style={{background:"var(--surface)",boxShadow:"var(--shadow-card)"}}>
          <h3 className="font-semibold" style={{color:"var(--text-primary)"}}>New Reminder</h3>
          <input required value={form.title} onChange={e=>setForm({...form,title:e.target.value})} placeholder="Title"
            className="w-full nexa-input px-4 py-2.5 text-sm" />
          <input value={form.message} onChange={e=>setForm({...form,message:e.target.value})} placeholder="Note (optional)"
            className="w-full nexa-input px-4 py-2.5 text-sm" />
          <div className="grid grid-cols-2 gap-2">
            <input type="date" required value={form.triggerDate} onChange={e=>setForm({...form,triggerDate:e.target.value})}
              className="nexa-input px-3 py-2.5 text-sm" />
            <input type="time" value={form.triggerTime} onChange={e=>setForm({...form,triggerTime:e.target.value})}
              className="nexa-input px-3 py-2.5 text-sm" />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <select value={form.priority} onChange={e=>setForm({...form,priority:e.target.value})}
              className="nexa-input px-3 py-2.5 text-sm">
              <option value="HIGH">🔴 High</option>
              <option value="MEDIUM">🟡 Medium</option>
              <option value="LOW">🟢 Low</option>
            </select>
            <select value={form.category} onChange={e=>setForm({...form,category:e.target.value})}
              className="nexa-input px-3 py-2.5 text-sm">
              {Object.keys(catEmoji).map(c=><option key={c} value={c}>{catEmoji[c]} {c}</option>)}
            </select>
          </div>
          <div className="flex gap-2">
            <button type="submit" disabled={saving} className="btn-primary px-4 py-2 text-sm">
              {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : "Save Reminder"}
            </button>
            <button type="button" onClick={()=>setShowForm(false)} className="btn-secondary px-4 py-2 text-sm">Cancel</button>
          </div>
        </motion.form>
      )}

      <div className="flex gap-2 mb-4">
        {["PENDING","MISSED","COMPLETED"].map(f=>(
          <button key={f} onClick={()=>setFilter(f)} className="chip text-xs cursor-pointer"
            style={filter===f?{background:"var(--primary)",color:"white"}:{background:"var(--surface)",color:"var(--text-secondary)",border:"1px solid var(--divider)"}}>           {f}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="space-y-2">{[...Array(3)].map((_,i)=><div key={i} className="h-16 rounded-xl shimmer" />)}</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16" style={{color:"var(--text-hint)"}}><Bell className="w-12 h-12 mx-auto mb-3 opacity-30" /><p>No reminders in this category.</p></div>
      ) : (
        <div className="space-y-2">
          {filtered.map((r,i)=>(
            <motion.div key={r.id} initial={{opacity:0,y:8}} animate={{opacity:1,y:0}} transition={{delay:i*0.04}}
              className="flex items-center gap-3 p-4 rounded-xl" style={{background:"var(--surface)",boxShadow:"var(--shadow-card)"}}>
              <div className="w-10 h-10 rounded-xl flex items-center justify-center text-xl flex-shrink-0" style={{background:"var(--primary-surface)"}}>
                {catEmoji[r.category]??"⏰"}
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-medium text-sm truncate" style={{color:"var(--text-primary)"}}>{r.title}</p>
                <p className="text-xs flex items-center gap-1" style={{color: r.triggerTime<now ? "var(--negative)" : "var(--warning)"}}>
                  <Clock className="w-3 h-3" />{fmtTime(r.triggerTime)} · {new Date(r.triggerTime).toLocaleString("en-IN",{month:"short",day:"numeric",hour:"2-digit",minute:"2-digit"})}
                </p>
              </div>
              <span className={`chip text-xs ${r.priority==="HIGH"?"priority-high":r.priority==="LOW"?"priority-low":"priority-medium"}`}>{r.priority}</span>
              {!r.isCompleted && (
                <button onClick={()=>markDone(r)} className="p-2 rounded-xl transition-colors" style={{background:"var(--cash-in-bg)",color:"var(--positive)"}} title="Mark done">
                  <CheckCircle className="w-4 h-4" />
                </button>
              )}
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}
