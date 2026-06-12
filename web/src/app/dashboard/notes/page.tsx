"use client";
import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, onSnapshot, orderBy, addDoc, deleteDoc, doc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion } from "framer-motion";
import { Plus, StickyNote, Trash2, Pin } from "lucide-react";

interface Note { id: string; title: string; content: string; colorCode: string; isPinned: boolean; isArchived: boolean; isDeleted: boolean; createdAt: number; folder: string; }

const colors = ["#FFFDE7","#E8F5E9","#E3F2FD","#F3E5F5","#FFF3E0","#E0F7FA","#FFFFFF"];

export default function NotesPage() {
  const { user } = useAuth();
  const [notes, setNotes] = useState<Note[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [filter, setFilter] = useState("ALL");
  const [form, setForm] = useState({ title:"", content:"", colorCode:"#FFFFFF", folder:"Personal" });

  useEffect(() => {
    if (!user) return;
    const q = query(collection(db, "users", user.uid, "notes"), orderBy("updatedAt", "desc"));
    return onSnapshot(q, snap => { setNotes(snap.docs.map(d=>({id:d.id,...d.data()} as Note))); setLoading(false); });
  }, [user]);

  const addNote = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    const now = Date.now();
    await addDoc(collection(db, "users", user.uid, "notes"), {
      ...form, isPinned: false, isArchived: false, isDeleted: false,
      type: "TEXT", pageStyle: "blank", tags: [], imageUrls: [],
      reminderAt: 0, createdAt: now, updatedAt: now
    });
    setShowForm(false);
    setForm({ title:"", content:"", colorCode:"#FFFFFF", folder:"Personal" });
  };

  const deleteNote = async (id: string) => {
    if (!user) return;
    await deleteDoc(doc(db, "users", user.uid, "notes", id));
  };

  const filtered = notes.filter(n => {
    if (n.isDeleted) return false;
    if (filter === "PINNED") return n.isPinned;
    if (filter === "ARCHIVED") return n.isArchived;
    if (filter === "PERSONAL") return n.folder === "Personal" && !n.isArchived;
    if (filter === "WORK") return n.folder === "Work" && !n.isArchived;
    return !n.isArchived;
  });

  const pinned = filtered.filter(n=>n.isPinned);
  const unpinned = filtered.filter(n=>!n.isPinned);

  return (
    <div className="p-4 md:p-6 max-w-5xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{background:"linear-gradient(135deg,#FFA726,#E65100)"}}>
            <StickyNote className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-xl font-bold" style={{color:"var(--text-primary)"}}>Notes</h1>
            <p className="text-sm" style={{color:"var(--text-secondary)"}}>{notes.filter(n=>!n.isDeleted&&!n.isArchived).length} notes</p>
          </div>
        </div>
        <button onClick={()=>setShowForm(!showForm)} className="btn-primary px-4 py-2 text-sm flex items-center gap-2">
          <Plus className="w-4 h-4" /> New Note
        </button>
      </div>

      {showForm && (
        <motion.form initial={{opacity:0,y:-8}} animate={{opacity:1,y:0}} onSubmit={addNote}
          className="rounded-2xl p-5 mb-5 space-y-3" style={{background:"var(--surface)",boxShadow:"var(--shadow-card)"}}>
          <input required value={form.title} onChange={e=>setForm({...form,title:e.target.value})} placeholder="Note title"
            className="w-full nexa-input px-4 py-2.5 text-sm font-semibold" />
          <textarea required value={form.content} onChange={e=>setForm({...form,content:e.target.value})} placeholder="Write your note..."
            rows={4} className="w-full nexa-input px-4 py-2.5 text-sm resize-none" />
          <div className="flex items-center gap-3">
            <span className="text-sm" style={{color:"var(--text-secondary)"}}>Color:</span>
            {colors.map(c=>(
              <button key={c} type="button" onClick={()=>setForm({...form,colorCode:c})}
                className="w-6 h-6 rounded-full border-2 transition-transform hover:scale-110"
                style={{background:c, borderColor: form.colorCode===c ? "var(--primary)" : "var(--stroke)"}} />
            ))}
            <select value={form.folder} onChange={e=>setForm({...form,folder:e.target.value})}
              className="ml-auto nexa-input px-3 py-1.5 text-sm">
              <option>Personal</option>
              <option>Work</option>
            </select>
          </div>
          <div className="flex gap-2">
            <button type="submit" className="btn-primary px-4 py-2 text-sm">Save Note</button>
            <button type="button" onClick={()=>setShowForm(false)} className="btn-secondary px-4 py-2 text-sm">Cancel</button>
          </div>
        </motion.form>
      )}

      <div className="flex gap-2 flex-wrap mb-4">
        {["ALL","PINNED","PERSONAL","WORK","ARCHIVED"].map(f=>(
          <button key={f} onClick={()=>setFilter(f)} className="chip text-xs cursor-pointer"
            style={filter===f?{background:"var(--primary)",color:"white"}:{background:"var(--surface)",color:"var(--text-secondary)",border:"1px solid var(--divider)"}}>           {f}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="columns-2 md:columns-3 gap-4 space-y-4">{[...Array(6)].map((_,i)=><div key={i} className={`h-${24+i*8} rounded-xl shimmer mb-4`} />)}</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16" style={{color:"var(--text-hint)"}}><StickyNote className="w-12 h-12 mx-auto mb-3 opacity-30" /><p>No notes here.</p></div>
      ) : (
        <>
          {pinned.length > 0 && <p className="text-xs font-semibold mb-2" style={{color:"var(--text-hint)"}}>📌 PINNED</p>}
          <div className="columns-1 sm:columns-2 lg:columns-3 gap-4">
            {[...pinned,...unpinned].map((note,i)=>(
              <motion.div key={note.id} initial={{opacity:0,scale:0.95}} animate={{opacity:1,scale:1}} transition={{delay:i*0.04}}
                className="break-inside-avoid mb-4 rounded-2xl p-4 group relative card-hover cursor-pointer border"
                style={{background:note.colorCode||"var(--surface)",borderColor:"var(--divider)"}}>
                {note.isPinned && <Pin className="absolute top-3 right-3 w-3.5 h-3.5" style={{color:"var(--primary)"}} />}
                <h3 className="font-semibold text-sm mb-2 pr-6" style={{color:"var(--text-primary)"}}>{note.title}</h3>
                <p className="text-xs leading-relaxed line-clamp-6" style={{color:"var(--text-secondary)"}}>{note.content}</p>
                <p className="text-xs mt-3" style={{color:"var(--text-hint)"}}>{new Date(note.createdAt).toLocaleDateString("en-IN",{month:"short",day:"numeric"})}</p>
                <button onClick={()=>deleteNote(note.id)}
                  className="absolute bottom-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity p-1 rounded-lg"
                  style={{color:"var(--negative)",background:"rgba(220,53,69,0.1)"}}>
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </motion.div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
