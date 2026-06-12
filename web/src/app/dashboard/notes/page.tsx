"use client";
import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, orderBy, onSnapshot, addDoc, deleteDoc, updateDoc, doc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion, AnimatePresence } from "framer-motion";
import { Plus, StickyNote, Trash2, Pin, PinOff, Search, X, Archive } from "lucide-react";

interface Note {
  id: string; title: string; content: string; colorCode: string;
  isPinned: boolean; isArchived: boolean; isDeleted: boolean;
  createdAt: number; updatedAt?: number; folder: string; tags: string[];
}

const COLORS = [
  {hex:"#FFFFFF",label:"White"},
  {hex:"#FFFDE7",label:"Yellow"},
  {hex:"#E8F5E9",label:"Green"},
  {hex:"#E3F2FD",label:"Blue"},
  {hex:"#F3E5F5",label:"Purple"},
  {hex:"#FFF3E0",label:"Orange"},
  {hex:"#E0F7FA",label:"Teal"},
  {hex:"#FCE4EC",label:"Pink"},
];

export default function NotesPage() {
  const { user } = useAuth();
  const [notes, setNotes] = useState<Note[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [filter, setFilter] = useState("ALL");
  const [search, setSearch] = useState("");
  const [form, setForm] = useState({title:"",content:"",colorCode:"#FFFFFF",folder:"Personal"});

  useEffect(() => {
    if (!user) return;
    const q = query(collection(db,"users",user.uid,"notes"), orderBy("createdAt","desc"));
    return onSnapshot(q, snap => {
      setNotes(snap.docs.map(d=>({id:d.id,...d.data()} as Note)));
      setLoading(false);
    });
  }, [user]);

  const addNote = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    const now = Date.now();
    await addDoc(collection(db,"users",user.uid,"notes"), {
      ...form, isPinned:false, isArchived:false, isDeleted:false,
      type:"TEXT", pageStyle:"blank", tags:[], imageUrls:[],
      reminderAt:0, createdAt:now, updatedAt:now
    });
    setShowForm(false);
    setForm({title:"",content:"",colorCode:"#FFFFFF",folder:"Personal"});
  };

  const deleteNote = async (id: string) => {
    if (!user) return;
    await deleteDoc(doc(db,"users",user.uid,"notes",id));
  };

  const togglePin = async (note: Note) => {
    if (!user) return;
    await updateDoc(doc(db,"users",user.uid,"notes",note.id), {isPinned:!note.isPinned});
  };

  const archiveNote = async (note: Note) => {
    if (!user) return;
    await updateDoc(doc(db,"users",user.uid,"notes",note.id), {isArchived:!note.isArchived});
  };

  const FILTERS = [
    {id:"ALL",label:"All"},
    {id:"PINNED",label:"📌 Pinned"},
    {id:"PERSONAL",label:"Personal"},
    {id:"WORK",label:"Work"},
    {id:"ARCHIVED",label:"Archive"},
  ];

  const base = notes.filter(n => {
    if (n.isDeleted) return false;
    if (search) return n.title.toLowerCase().includes(search.toLowerCase()) || n.content.toLowerCase().includes(search.toLowerCase());
    if (filter==="PINNED") return n.isPinned && !n.isArchived;
    if (filter==="ARCHIVED") return n.isArchived;
    if (filter==="PERSONAL") return n.folder==="Personal" && !n.isArchived;
    if (filter==="WORK") return n.folder==="Work" && !n.isArchived;
    return !n.isArchived;
  });

  const pinned = base.filter(n=>n.isPinned);
  const unpinned = base.filter(n=>!n.isPinned);
  const allFiltered = [...pinned, ...unpinned];

  return (
    <div className="p-4 md:p-6 max-w-5xl mx-auto pb-10">
      {/* Header */}
      <div className="flex items-center justify-between mb-5">
        <div>
          <h1 className="text-2xl font-bold" style={{color:"var(--text-primary)"}}>Notes</h1>
          <p className="text-sm mt-0.5" style={{color:"var(--text-secondary)"}}>
            {notes.filter(n=>!n.isDeleted&&!n.isArchived).length} notes
          </p>
        </div>
        <button onClick={()=>setShowForm(!showForm)} className="btn btn-primary btn-sm">
          <Plus size={15}/> New Note
        </button>
      </div>

      {/* Search */}
      <div className="relative mb-4">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2" style={{color:"var(--text-hint)"}}/>
        <input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search notes..."
          className="input pl-9 pr-9" />
        {search && (
          <button onClick={()=>setSearch("")} className="absolute right-3 top-1/2 -translate-y-1/2 btn-icon" style={{padding:"2px"}}>
            <X size={14}/>
          </button>
        )}
      </div>

      {/* Add Form */}
      <AnimatePresence>
        {showForm && (
          <motion.div initial={{opacity:0,height:0}} animate={{opacity:1,height:"auto"}}
            exit={{opacity:0,height:0}} transition={{duration:0.2}} className="overflow-hidden mb-5">
            <form onSubmit={addNote} className="card p-5 space-y-3">
              <h3 style={{color:"var(--text-primary)"}}>New Note</h3>
              <input required value={form.title} onChange={e=>setForm({...form,title:e.target.value})}
                placeholder="Title" className="input font-semibold" />
              <textarea required value={form.content} onChange={e=>setForm({...form,content:e.target.value})}
                placeholder="Write your note..." rows={4}
                className="input resize-none" style={{display:"block"}}/>
              <div className="flex items-center gap-4">
                <div>
                  <label className="text-xs font-semibold mb-2 block" style={{color:"var(--text-secondary)"}}>COLOR</label>
                  <div className="flex gap-1.5 flex-wrap">
                    {COLORS.map(c=>(
                      <button key={c.hex} type="button" onClick={()=>setForm({...form,colorCode:c.hex})}
                        title={c.label}
                        className="w-6 h-6 rounded-full transition-transform hover:scale-110"
                        style={{
                          background:c.hex,
                          border: form.colorCode===c.hex ? "2.5px solid var(--primary)" : "1.5px solid var(--stroke)",
                          transform: form.colorCode===c.hex ? "scale(1.2)" : "scale(1)"
                        }}/>
                    ))}
                  </div>
                </div>
                <div className="ml-auto">
                  <label className="text-xs font-semibold mb-2 block" style={{color:"var(--text-secondary)"}}>FOLDER</label>
                  <select value={form.folder} onChange={e=>setForm({...form,folder:e.target.value})} className="input text-sm" style={{width:"auto"}}>
                    <option>Personal</option>
                    <option>Work</option>
                  </select>
                </div>
              </div>
              <div className="flex gap-2 pt-1">
                <button type="submit" className="btn btn-primary btn-sm"><StickyNote size={14}/> Save Note</button>
                <button type="button" onClick={()=>setShowForm(false)} className="btn btn-ghost btn-sm">Cancel</button>
              </div>
            </form>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Filter Tabs */}
      {!search && (
        <div className="flex gap-1.5 mb-5 flex-wrap">
          {FILTERS.map(f=>(
            <button key={f.id} onClick={()=>setFilter(f.id)}
              className="chip cursor-pointer transition-all"
              style={filter===f.id
                ? {background:"var(--primary)",color:"white"}
                : {background:"var(--surface)",color:"var(--text-secondary)",border:"1px solid var(--divider)"}
              }>{f.label}</button>
          ))}
        </div>
      )}

      {/* Notes Grid */}
      {loading ? (
        <div className="columns-1 sm:columns-2 lg:columns-3 gap-4">
          {[...Array(6)].map((_,i)=><div key={i} className="shimmer mb-4" style={{height:`${100+i*30}px`}}/>)}
        </div>
      ) : allFiltered.length===0 ? (
        <div className="empty-state">
          <div className="empty-state-icon"><StickyNote size={28} style={{color:"var(--primary)"}}/></div>
          <p className="font-semibold" style={{color:"var(--text-primary)"}}>
            {search ? "No notes found" : filter==="PINNED" ? "No pinned notes" : "No notes yet"}
          </p>
          <p className="text-sm" style={{color:"var(--text-secondary)"}}>
            {search ? `No results for "${search}"` : "Create your first note above."}
          </p>
        </div>
      ) : (
        <div className="columns-1 sm:columns-2 lg:columns-3 gap-4">
          {pinned.length>0 && !search && (
            <p className="text-xs font-bold mb-3 flex items-center gap-1.5 break-inside-avoid" style={{color:"var(--text-hint)",letterSpacing:"0.08em"}}>
              <Pin size={11}/> PINNED
            </p>
          )}
          {allFiltered.map((note,i)=>(
            <motion.div key={note.id}
              initial={{opacity:0,scale:0.96}} animate={{opacity:1,scale:1}}
              transition={{delay:i*0.03,duration:0.2}}
              className="break-inside-avoid mb-4 rounded-2xl p-4 group relative border card-hover"
              style={{
                background: note.colorCode||"var(--surface)",
                borderColor: note.colorCode && note.colorCode!=="#FFFFFF" ? "transparent" : "var(--divider)",
                boxShadow: "var(--shadow-card)"
              }}>
              {/* Pin indicator */}
              {note.isPinned && (
                <div className="absolute top-3 right-3">
                  <Pin size={13} style={{color:"var(--primary)"}}/>
                </div>
              )}
              <h3 className="font-semibold text-sm mb-2 pr-6 line-clamp-2" style={{color:"#1A1A2E"}}>{note.title}</h3>
              <p className="text-xs leading-relaxed line-clamp-5" style={{color:"#3D3D60"}}>{note.content}</p>
              {note.folder && note.folder!=="Personal" && (
                <span className="inline-block mt-2 chip text-xs" style={{background:"rgba(92,107,192,0.12)",color:"var(--primary)",padding:"2px 8px"}}>
                  {note.folder}
                </span>
              )}
              <p className="text-xs mt-3" style={{color:"#9090B0"}}>
                {new Date(note.createdAt).toLocaleDateString("en-IN",{month:"short",day:"numeric",year:"2-digit"})}
              </p>

              {/* Hover actions */}
              <div className="absolute bottom-3 right-3 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <button onClick={()=>togglePin(note)} className="w-7 h-7 rounded-lg flex items-center justify-center transition-colors"
                  style={{background:"rgba(92,107,192,0.12)",color:"var(--primary)"}} title={note.isPinned?"Unpin":"Pin"}>
                  {note.isPinned ? <PinOff size={13}/> : <Pin size={13}/>}
                </button>
                <button onClick={()=>archiveNote(note)} className="w-7 h-7 rounded-lg flex items-center justify-center transition-colors"
                  style={{background:"rgba(255,149,0,0.12)",color:"var(--warning)"}} title="Archive">
                  <Archive size={13}/>
                </button>
                <button onClick={()=>deleteNote(note.id)} className="w-7 h-7 rounded-lg flex items-center justify-center transition-colors"
                  style={{background:"rgba(229,57,53,0.12)",color:"var(--negative)"}} title="Delete">
                  <Trash2 size={13}/>
                </button>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}
