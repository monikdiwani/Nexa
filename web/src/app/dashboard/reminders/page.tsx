"use client";
import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, orderBy, onSnapshot, addDoc, updateDoc, doc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion, AnimatePresence } from "framer-motion";
import { Plus, Bell, CheckCircle2, Clock, Loader2, AlarmClock, Trash2, X, AlarmClockOff } from "lucide-react";

interface Reminder {
  id: string; title: string; message: string;
  triggerTime: number; priority: string; category: string;
  isCompleted: boolean; isSnoozed: boolean;
}

const CAT_EMOJI: Record<string,string> = {
  BILL:"📄", MEETING:"👥", TASK:"✅", MEDICINE:"💊", SHOPPING:"🛒", CUSTOM:"⏰"
};
const CAT_COLOR: Record<string,string> = {
  BILL:"#EF6C00", MEETING:"#1565C0", TASK:"#2E7D32", MEDICINE:"#AD1457", SHOPPING:"#6A1B9A", CUSTOM:"#37474F"
};
const CAT_BG: Record<string,string> = {
  BILL:"#FFF3E0", MEETING:"#E3F2FD", TASK:"#E8F5E9", MEDICINE:"#FCE4EC", SHOPPING:"#F3E5F5", CUSTOM:"#ECEFF1"
};

export default function RemindersPage() {
  const { user } = useAuth();
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [filter, setFilter] = useState("PENDING");
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    title:"", message:"", triggerDate:"", triggerTime:"",
    priority:"MEDIUM", category:"CUSTOM"
  });

  useEffect(() => {
    if (!user) return;
    const q = query(collection(db,"users",user.uid,"reminders"), orderBy("triggerTime","asc"));
    return onSnapshot(q, snap => {
      setReminders(snap.docs.map(d=>({id:d.id,...d.data()} as Reminder)));
      setLoading(false);
    });
  }, [user]);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    setSaving(true);
    const dt = new Date(`${form.triggerDate}T${form.triggerTime||"09:00"}`);
    await addDoc(collection(db,"users",user.uid,"reminders"), {
      title:form.title, message:form.message, triggerTime:dt.getTime(),
      priority:form.priority, category:form.category,
      isCompleted:false, isSnoozed:false, createdAt:Date.now(),
      recurringPattern:"NONE", isRecurring:false
    });
    setShowForm(false); setSaving(false);
    setForm({title:"",message:"",triggerDate:"",triggerTime:"",priority:"MEDIUM",category:"CUSTOM"});
  };

  const markDone = async (r: Reminder) => {
    if (!user) return;
    await updateDoc(doc(db,"users",user.uid,"reminders",r.id), {isCompleted:true,completedAt:Date.now()});
  };

  const snoozeReminder = async (r: Reminder, hours: number) => {
    if (!user) return;
    const newTime = Date.now() + hours * 3600000;
    await updateDoc(doc(db,"users",user.uid,"reminders",r.id), {
      triggerTime: newTime, isSnoozed: true
    });
  };

  const deleteReminder = async (id: string) => {
    if (!user) return;
    const { deleteDoc: del } = await import("firebase/firestore");
    await del(doc(db,"users",user.uid,"reminders",id));
  };

  const now = Date.now();

  const fmtCountdown = (ms: number) => {
    const d = ms - now;
    if (d < 0) {
      const h = Math.abs(Math.round(d/3600000));
      return h < 24 ? `${h}h overdue` : `${Math.round(h/24)}d overdue`;
    }
    if (d < 3600000) return `In ${Math.round(d/60000)}m`;
    if (d < 86400000) return `In ${Math.round(d/3600000)}h`;
    return `In ${Math.round(d/86400000)}d`;
  };

  const FILTERS = [
    {id:"PENDING", label:"Upcoming"},
    {id:"MISSED", label:"Missed"},
    {id:"COMPLETED", label:"Done"},
  ];

  const filtered = reminders.filter(r => {
    if (filter==="PENDING") return !r.isCompleted && r.triggerTime >= now;
    if (filter==="MISSED") return !r.isCompleted && r.triggerTime < now;
    if (filter==="COMPLETED") return r.isCompleted;
    return true;
  });

  const counts = {
    PENDING: reminders.filter(r=>!r.isCompleted&&r.triggerTime>=now).length,
    MISSED: reminders.filter(r=>!r.isCompleted&&r.triggerTime<now).length,
    COMPLETED: reminders.filter(r=>r.isCompleted).length,
  };

  return (
    <div className="p-4 md:p-6 max-w-2xl mx-auto pb-10">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold" style={{color:"var(--text-primary)"}}>Reminders</h1>
          <p className="text-sm mt-0.5" style={{color:"var(--text-secondary)"}}>
            {counts.PENDING} upcoming{counts.MISSED>0 ? ` · ${counts.MISSED} missed` : ""}
          </p>
        </div>
        <button onClick={()=>setShowForm(!showForm)} className="btn btn-primary btn-sm">
          <Plus size={15}/> Add
        </button>
      </div>

      {/* Form */}
      <AnimatePresence>
        {showForm && (
          <motion.div initial={{opacity:0,height:0}} animate={{opacity:1,height:"auto"}}
            exit={{opacity:0,height:0}} transition={{duration:0.2}} className="overflow-hidden mb-5">
            <form onSubmit={save} className="card p-5 space-y-3">
              <h3 style={{color:"var(--text-primary)"}}>New Reminder</h3>
              <input required value={form.title} onChange={e=>setForm({...form,title:e.target.value})}
                placeholder="Reminder title" className="input" />
              <input value={form.message} onChange={e=>setForm({...form,message:e.target.value})}
                placeholder="Note (optional)" className="input" />
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold mb-1.5 block" style={{color:"var(--text-secondary)"}}>DATE</label>
                  <input type="date" required value={form.triggerDate} onChange={e=>setForm({...form,triggerDate:e.target.value})} className="input" />
                </div>
                <div>
                  <label className="text-xs font-semibold mb-1.5 block" style={{color:"var(--text-secondary)"}}>TIME</label>
                  <input type="time" value={form.triggerTime} onChange={e=>setForm({...form,triggerTime:e.target.value})} className="input" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold mb-1.5 block" style={{color:"var(--text-secondary)"}}>PRIORITY</label>
                  <select value={form.priority} onChange={e=>setForm({...form,priority:e.target.value})} className="input">
                    <option value="HIGH">🔴 High</option>
                    <option value="MEDIUM">🟡 Medium</option>
                    <option value="LOW">🟢 Low</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-semibold mb-1.5 block" style={{color:"var(--text-secondary)"}}>CATEGORY</label>
                  <select value={form.category} onChange={e=>setForm({...form,category:e.target.value})} className="input">
                    {Object.keys(CAT_EMOJI).map(c=><option key={c} value={c}>{CAT_EMOJI[c]} {c}</option>)}
                  </select>
                </div>
              </div>
              <div className="flex gap-2 pt-1">
                <button type="submit" disabled={saving} className="btn btn-primary btn-sm">
                  {saving?<Loader2 size={14} className="animate-spin"/>:<AlarmClock size={14}/>} Save
                </button>
                <button type="button" onClick={()=>setShowForm(false)} className="btn btn-ghost btn-sm">Cancel</button>
              </div>
            </form>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Filter tabs */}
      <div className="flex gap-1.5 mb-5">
        {FILTERS.map(f=>(
          <button key={f.id} onClick={()=>setFilter(f.id)}
            className="chip cursor-pointer transition-all"
            style={filter===f.id
              ? {background:"var(--primary)",color:"white"}
              : {background:"var(--surface)",color:"var(--text-secondary)",border:"1px solid var(--divider)"}
            }>
            {f.label}
            {(counts as Record<string,number>)[f.id]>0 && (
              <span className="badge" style={{
                background: filter===f.id ? "rgba(255,255,255,0.25)" : "var(--primary-surface)",
                color: filter===f.id ? "white" : "var(--primary)"
              }}>{(counts as Record<string,number>)[f.id]}</span>
            )}
          </button>
        ))}
      </div>

      {/* List */}
      {loading ? (
        <div className="space-y-2">{[...Array(3)].map((_,i)=><div key={i} className="shimmer h-20"/>)}</div>
      ) : filtered.length===0 ? (
        <div className="empty-state">
          <div className="empty-state-icon"><Bell size={28} style={{color:"var(--primary)"}}/></div>
          <p className="font-semibold" style={{color:"var(--text-primary)"}}>No {filter.toLowerCase()} reminders</p>
          <p className="text-sm" style={{color:"var(--text-secondary)"}}>Reminders you set will appear here.</p>
        </div>
      ) : (
        <div className="space-y-2">
          <AnimatePresence>
            {filtered.map((r,i)=>(
              <motion.div key={r.id} initial={{opacity:0,y:8}} animate={{opacity:1,y:0}}
                exit={{opacity:0}} transition={{delay:i*0.03}}
                className="card p-4 flex items-center gap-3"
                style={{borderColor: r.triggerTime<now&&!r.isCompleted ? "rgba(229,57,53,0.3)" : "var(--divider)"}}>
                <div className="w-11 h-11 rounded-xl flex items-center justify-center text-xl flex-shrink-0"
                  style={{background: CAT_BG[r.category]||"var(--primary-surface)"}}>
                  {CAT_EMOJI[r.category]||"⏰"}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-sm" style={{color:"var(--text-primary)"}}>{r.title}</p>
                  {r.message && <p className="text-xs truncate" style={{color:"var(--text-secondary)"}}>{r.message}</p>}
                  <p className="text-xs mt-1 flex items-center gap-1" style={{
                    color: r.triggerTime<now&&!r.isCompleted ? "var(--negative)" : "var(--text-hint)"
                  }}>
                    <Clock size={11}/>
                    {fmtCountdown(r.triggerTime)} · {new Date(r.triggerTime).toLocaleString("en-IN",{month:"short",day:"numeric",hour:"2-digit",minute:"2-digit"})}
                  </p>
                </div>
                <div className="flex flex-col items-end gap-1.5">
                  <span className={`chip text-xs ${r.priority==="HIGH"?"priority-high":r.priority==="LOW"?"priority-low":"priority-medium"}`}>
                    {r.priority[0]+r.priority.slice(1).toLowerCase()}
                  </span>
                  {!r.isCompleted && (
                    <div className="flex items-center gap-1">
                      {/* Snooze dropdown */}
                      <div className="relative group/snooze">
                        <button className="btn-icon" title="Snooze" style={{ color: "var(--warning)" }}>
                          <AlarmClockOff size={15} />
                        </button>
                        <div className="absolute right-0 top-full mt-1 z-10 rounded-xl shadow-lg border overflow-hidden invisible group-hover/snooze:visible"
                          style={{ background: "var(--surface)", borderColor: "var(--divider)", minWidth: "120px" }}>
                          {[{label:"1 hour",h:1},{label:"3 hours",h:3},{label:"Tomorrow",h:24}].map(opt=>(
                            <button key={opt.h} onClick={()=>snoozeReminder(r,opt.h)}
                              className="block w-full text-left px-3 py-2 text-xs font-medium hover:opacity-80 transition-opacity"
                              style={{ color: "var(--text-primary)" }}>
                              ⏰ {opt.label}
                            </button>
                          ))}
                        </div>
                      </div>
                      <button onClick={()=>markDone(r)} className="btn btn-sm" style={{background:"var(--cash-in-bg)",color:"var(--positive)",border:"none",padding:"4px 10px"}}>
                        <CheckCircle2 size={13}/> Done
                      </button>
                    </div>
                  )}
                  {r.isCompleted && <CheckCircle2 size={18} style={{color:"var(--positive)"}}/>}
                  <button onClick={()=>deleteReminder(r.id)} className="btn-icon" style={{ color: "var(--text-hint)" }} title="Delete">
                    <Trash2 size={13} />
                  </button>
                </div>
              </motion.div>
            ))}
          </AnimatePresence>
        </div>
      )}
    </div>
  );
}
