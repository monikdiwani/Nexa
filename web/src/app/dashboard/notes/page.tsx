"use client";
import { useAuth } from "@/context/AuthContext";
import { useEffect, useState, useCallback } from "react";
import { collection, query, orderBy, onSnapshot, addDoc, deleteDoc, updateDoc, doc, setDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion, AnimatePresence } from "framer-motion";
import { Plus, StickyNote, Trash2, Pin, PinOff, Search, X, Archive, Pencil, Save, FolderPlus, CheckSquare, Lock, LayoutGrid, Calendar, Wallet, Loader2 } from "lucide-react";
import Masonry from 'react-masonry-css';
import RichTextEditor from '@/components/RichTextEditor';
import { useRouter } from "next/navigation";

interface Note {
  id: string; title: string; content: string; colorCode: string;
  isPinned: boolean; isArchived: boolean; isDeleted: boolean;
  createdAt: number; updatedAt?: number; folder: string; tags: string[];
  isLocked?: boolean; pageStyle?: string;
}

const COLORS = [
  { hex: "#FFFFFF", label: "White" }, { hex: "#FFFDE7", label: "Yellow" },
  { hex: "#E8F5E9", label: "Green" }, { hex: "#E3F2FD", label: "Blue" },
  { hex: "#F3E5F5", label: "Purple" }, { hex: "#FFF3E0", label: "Orange" },
  { hex: "#E0F7FA", label: "Teal" }, { hex: "#FCE4EC", label: "Pink" },
];

function getNoteTextColor(hex: string): string {
  if (!hex || hex === "#FFFFFF") return "var(--text-primary)";
  return "#1A1A2E"; 
}
function getNoteSubColor(hex: string): string {
  if (!hex || hex === "#FFFFFF") return "var(--text-secondary)";
  return "#4A4A6A";
}

const PAGE_STYLES = [
  { id: "blank", label: "Blank" },
  { id: "lined", label: "Lined" },
  { id: "dotted", label: "Dotted" },
  { id: "grid", label: "Grid" }
];

export default function NotesPage() {
  const { user } = useAuth();
  const router = useRouter();
  const [notes, setNotes] = useState<Note[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [filter, setFilter] = useState("ALL");
  const [search, setSearch] = useState("");
  const [customFolders, setCustomFolders] = useState<string[]>(["Personal", "Work"]);
  const [form, setForm] = useState({ title: "", content: "", colorCode: "#FFFFFF", folder: "Personal", pageStyle: "blank", isLocked: false });

  // Multi-select
  const [selectedNotes, setSelectedNotes] = useState<string[]>([]);

  // Note detail/edit modal
  const [editNote, setEditNote] = useState<Note | null>(null);
  const [editForm, setEditForm] = useState({ title: "", content: "", colorCode: "#FFFFFF", folder: "Personal", pageStyle: "blank", isLocked: false });
  const [savingEdit, setSavingEdit] = useState(false);

  useEffect(() => {
    if (!user) return;
    const unsubFolders = onSnapshot(doc(db, "users", user.uid, "settings", "custom_folders"), (snap) => {
      if (snap.exists() && snap.data()?.folders) setCustomFolders(snap.data().folders);
      else setCustomFolders(["Personal", "Work"]);
    });

    const q = query(collection(db, "users", user.uid, "notes"), orderBy("createdAt", "desc"));
    const unsubNotes = onSnapshot(q, snap => {
      setNotes(snap.docs.map(d => ({ id: d.id, ...d.data() } as Note)));
      setLoading(false);
    });
    return () => { unsubFolders(); unsubNotes(); };
  }, [user]);

  const authenticateBiometric = async (): Promise<boolean> => {
    try {
      if (!window.PublicKeyCredential) {
        const pin = prompt("WebAuthn not supported. Enter PIN (default: 0000):");
        return pin === "0000";
      }
      const challenge = new Uint8Array(32);
      crypto.getRandomValues(challenge);
      await navigator.credentials.get({
        publicKey: { challenge, rpId: window.location.hostname, userVerification: "preferred" }
      });
      return true;
    } catch (err) {
      console.error(err);
      return false;
    }
  };

  const openNote = async (note: Note) => {
    if (note.isLocked) {
      const auth = await authenticateBiometric();
      if (!auth) return;
    }
    setEditNote(note);
    setEditForm({ 
      title: note.title, content: note.content, 
      colorCode: note.colorCode || "#FFFFFF", folder: note.folder || "Personal",
      pageStyle: note.pageStyle || "blank", isLocked: note.isLocked || false
    });
  };

  const closeEdit = () => setEditNote(null);

  const saveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !editNote) return;
    setSavingEdit(true);
    await updateDoc(doc(db, "users", user.uid, "notes", editNote.id), {
      ...editForm, updatedAt: Date.now(),
    });
    setSavingEdit(false);
    closeEdit();
  };

  const addNote = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    const now = Date.now();
    await addDoc(collection(db, "users", user.uid, "notes"), {
      ...form, isPinned: false, isArchived: false, isDeleted: false,
      type: "RICH_TEXT", tags: [], imageUrls: [],
      reminderAt: 0, createdAt: now, updatedAt: now
    });
    setShowForm(false);
    setForm({ title: "", content: "", colorCode: "#FFFFFF", folder: "Personal", pageStyle: "blank", isLocked: false });
  };

  const toggleSelect = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedNotes(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);
  };

  const batchAction = async (action: 'ARCHIVE' | 'DELETE' | 'PIN') => {
    if (!user || selectedNotes.length === 0) return;
    if (action === 'DELETE' && !confirm(`Delete ${selectedNotes.length} notes?`)) return;
    
    selectedNotes.forEach(async id => {
      const ref = doc(db, "users", user.uid, "notes", id);
      if (action === 'DELETE') await updateDoc(ref, { isDeleted: true });
      if (action === 'ARCHIVE') await updateDoc(ref, { isArchived: true, isPinned: false });
      if (action === 'PIN') await updateDoc(ref, { isPinned: true, isArchived: false });
    });
    setSelectedNotes([]);
  };

  const convertTo = (type: 'TASK' | 'REMINDER' | 'CASHBOOK') => {
    if (!editForm.title && !editForm.content) return;
    const text = encodeURIComponent(editForm.title + " " + editForm.content.replace(/<[^>]*>?/gm, ''));
    if (type === 'TASK') router.push(`/dashboard/tasks?new=${text}`);
    if (type === 'REMINDER') router.push(`/dashboard/reminders?new=${text}`);
    if (type === 'CASHBOOK') router.push(`/dashboard/money?new=${text}`);
  };

  const FILTERS = [
    { id: "ALL", label: "All Notes" },
    { id: "PINNED", label: "📌 Pinned" },
    ...customFolders.map(f => ({ id: f, label: f })),
    { id: "ARCHIVED", label: "Archive" },
  ];

  const base = notes.filter(n => {
    if (n.isDeleted) return false;
    if (search) return n.title.toLowerCase().includes(search.toLowerCase()) || n.content.toLowerCase().includes(search.toLowerCase());
    if (filter === "PINNED") return n.isPinned && !n.isArchived;
    if (filter === "ARCHIVED") return n.isArchived;
    if (filter !== "ALL") return n.folder === filter && !n.isArchived;
    return !n.isArchived;
  });

  const pinned = base.filter(n => n.isPinned);
  const unpinned = base.filter(n => !n.isPinned);
  const allFiltered = [...pinned, ...unpinned];

  const getPageStyleCss = (style: string) => {
    if (style === "lined") return { backgroundImage: "repeating-linear-gradient(transparent, transparent 27px, rgba(0,0,0,0.1) 28px)", backgroundPositionY: "36px" };
    if (style === "dotted") return { backgroundImage: "radial-gradient(rgba(0,0,0,0.15) 1.5px, transparent 1.5px)", backgroundSize: "20px 20px" };
    if (style === "grid") return { backgroundImage: "linear-gradient(rgba(0,0,0,0.1) 1px, transparent 1px), linear-gradient(90deg, rgba(0,0,0,0.1) 1px, transparent 1px)", backgroundSize: "20px 20px" };
    return {};
  };

  return (
    <div className="flex h-[calc(100vh-60px)] md:h-[calc(100vh-80px)] overflow-hidden">
      {/* Left Drawer UI (Desktop) */}
      <div className="hidden md:block w-64 border-r overflow-y-auto p-4" style={{ borderColor: "var(--divider)", background: "var(--bg)" }}>
        <h2 className="text-xl font-bold mb-6" style={{ color: "var(--text-primary)" }}>Folders</h2>
        <div className="space-y-1">
          {FILTERS.map(f => (
            <button key={f.id} onClick={() => setFilter(f.id)}
              className={`w-full text-left px-3 py-2 rounded-xl text-sm font-medium transition-colors flex items-center gap-2
                ${filter === f.id ? 'bg-blue-500 text-white' : 'hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-700 dark:text-gray-300'}`}>
               {f.label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-4 md:p-6 relative">
        <div className="flex items-center justify-between mb-5">
          <div className="md:hidden">
             <select value={filter} onChange={e => setFilter(e.target.value)} className="input text-sm font-bold bg-transparent border-0 px-0 shadow-none">
                {FILTERS.map(f => <option key={f.id} value={f.id}>{f.label}</option>)}
             </select>
          </div>
          <div className="hidden md:block">
            <h1 className="text-2xl font-bold" style={{ color: "var(--text-primary)" }}>
              {FILTERS.find(f => f.id === filter)?.label}
            </h1>
          </div>
          <button onClick={() => setShowForm(!showForm)} className="btn btn-primary btn-sm">
            <Plus size={15} /> New Note
          </button>
        </div>

        <div className="relative mb-6">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2" style={{ color: "var(--text-hint)" }} />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search notes..."
            className="input pr-9" style={{ paddingLeft: "36px" }} />
          {search && (
            <button onClick={() => setSearch("")} className="absolute right-3 top-1/2 -translate-y-1/2 btn-icon" style={{ padding: "2px" }}>
              <X size={14} />
            </button>
          )}
        </div>

        <AnimatePresence>
          {showForm && (
            <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }}
              exit={{ opacity: 0, height: 0 }} transition={{ duration: 0.2 }} className="overflow-hidden mb-6">
              <form onSubmit={addNote} className="card rounded-2xl overflow-hidden border" style={{ borderColor: "var(--divider)", background: form.colorCode || "var(--surface)" }}>
                <div style={getPageStyleCss(form.pageStyle)} className="p-4">
                  <div className="flex items-center justify-between mb-2">
                     <input required value={form.title} onChange={e => setForm({ ...form, title: e.target.value })}
                        placeholder="Title" className="input font-bold text-lg border-0 bg-transparent px-0" style={{ color: getNoteTextColor(form.colorCode), boxShadow: 'none' }} />
                     <button type="button" onClick={() => setForm({...form, isLocked: !form.isLocked})} className="btn-icon" style={{ color: getNoteTextColor(form.colorCode) }}>
                       {form.isLocked ? <Lock size={16} /> : <CheckSquare size={16} className="opacity-30"/>}
                     </button>
                  </div>
                  <div className="bg-white/50 dark:bg-black/20 rounded-xl overflow-hidden">
                    <RichTextEditor content={form.content} onChange={html => setForm({ ...form, content: html })} textColor={getNoteTextColor(form.colorCode)} />
                  </div>
                  
                  <div className="flex items-center gap-4 flex-wrap mt-4">
                    <div className="flex gap-1.5 flex-wrap">
                      {COLORS.map(c => (
                        <button key={c.hex} type="button" onClick={() => setForm({ ...form, colorCode: c.hex })} title={c.label}
                          className={`w-5 h-5 rounded-full transition-all border-2 ${form.colorCode === c.hex ? 'scale-125 border-blue-500' : 'border-gray-300'}`}
                          style={{ background: c.hex }} />
                      ))}
                    </div>
                    <select value={form.pageStyle} onChange={e => setForm({...form, pageStyle: e.target.value})} className="input text-xs w-auto py-1 h-auto">
                      {PAGE_STYLES.map(s => <option key={s.id} value={s.id}>{s.label}</option>)}
                    </select>
                    <select value={form.folder} onChange={e => setForm({ ...form, folder: e.target.value })} className="input text-xs w-auto py-1 h-auto">
                      {customFolders.map(f => <option key={f} value={f}>{f}</option>)}
                    </select>
                    <div className="ml-auto flex gap-2">
                      <button type="submit" className="btn btn-primary btn-sm"><Save size={14} /> Save</button>
                      <button type="button" onClick={() => setShowForm(false)} className="btn btn-ghost btn-sm">Cancel</button>
                    </div>
                  </div>
                </div>
              </form>
            </motion.div>
          )}
        </AnimatePresence>

        {loading ? (
          <div className="flex justify-center py-10"><Loader2 className="animate-spin text-blue-500" size={30} /></div>
        ) : allFiltered.length === 0 ? (
          <div className="text-center py-16">
            <StickyNote size={40} className="mx-auto text-gray-300 mb-3" />
            <p className="text-gray-500 font-medium">No notes here.</p>
          </div>
        ) : (
          <Masonry breakpointCols={{ default: 3, 1100: 3, 700: 2, 500: 1 }} className="flex w-auto -ml-4" columnClassName="pl-4 bg-clip-padding">
            {allFiltered.map((note) => (
              <motion.div key={note.id} layoutId={note.id}
                onClick={() => openNote(note)}
                className="mb-4 rounded-2xl relative border cursor-pointer overflow-hidden group transition-all hover:shadow-lg hover:-translate-y-1"
                style={{
                  background: note.colorCode || "var(--surface)",
                  borderColor: selectedNotes.includes(note.id) ? "var(--primary)" : (note.colorCode && note.colorCode !== "#FFFFFF" ? "transparent" : "var(--divider)"),
                  borderWidth: selectedNotes.includes(note.id) ? "2px" : "1px",
                }}>
                
                {/* Multi-select checkbox */}
                <div className={`absolute top-3 left-3 z-10 ${selectedNotes.includes(note.id) ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'} transition-opacity`}>
                   <input type="checkbox" checked={selectedNotes.includes(note.id)} onChange={(e) => { e.stopPropagation(); toggleSelect(note.id, e as any); }}
                     onClick={e => e.stopPropagation()} className="w-4 h-4 cursor-pointer accent-blue-500" />
                </div>

                <div className="p-4 pt-5" style={getPageStyleCss(note.pageStyle || 'blank')}>
                  {note.isPinned && <Pin size={13} className="absolute top-3 right-3" style={{ color: "var(--primary)" }} />}
                  {note.isLocked ? (
                    <div className="flex flex-col items-center justify-center py-6 text-center">
                       <Lock size={24} style={{ color: getNoteSubColor(note.colorCode) }} className="mb-2" />
                       <p className="text-sm font-semibold" style={{ color: getNoteTextColor(note.colorCode) }}>Locked Note</p>
                    </div>
                  ) : (
                    <>
                      <h3 className="font-semibold text-sm mb-1.5 pr-6 line-clamp-2" style={{ color: getNoteTextColor(note.colorCode) }}>{note.title}</h3>
                      <div className="text-xs leading-relaxed line-clamp-6 prose prose-sm max-w-none" 
                        style={{ color: getNoteSubColor(note.colorCode) }}
                        dangerouslySetInnerHTML={{ __html: note.content }} />
                    </>
                  )}
                </div>
              </motion.div>
            ))}
          </Masonry>
        )}

        {/* Floating Multi-select Action Bar */}
        <AnimatePresence>
          {selectedNotes.length > 0 && (
            <motion.div initial={{ y: 50, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: 50, opacity: 0 }}
              className="fixed bottom-6 left-1/2 -translate-x-1/2 bg-gray-900 text-white px-4 py-3 rounded-2xl shadow-xl flex items-center gap-4 z-40">
               <span className="text-sm font-medium">{selectedNotes.length} selected</span>
               <div className="w-px h-4 bg-gray-700" />
               <button onClick={() => batchAction('PIN')} className="p-1 hover:text-blue-400"><Pin size={16} /></button>
               <button onClick={() => batchAction('ARCHIVE')} className="p-1 hover:text-yellow-400"><Archive size={16} /></button>
               <button onClick={() => batchAction('DELETE')} className="p-1 hover:text-red-400"><Trash2 size={16} /></button>
               <button onClick={() => setSelectedNotes([])} className="p-1 ml-2"><X size={16} /></button>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Note Edit Modal */}
        <AnimatePresence>
          {editNote && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm"
              onClick={closeEdit}>
              <motion.div layoutId={editNote.id} onClick={e => e.stopPropagation()}
                className="w-full max-w-2xl rounded-3xl shadow-2xl overflow-hidden flex flex-col"
                style={{ background: editForm.colorCode || "var(--surface)", maxHeight: "90vh" }}>
                
                <div className="flex items-center justify-between p-4 border-b" style={{ borderColor: "rgba(0,0,0,0.08)" }}>
                  <div className="flex items-center gap-2">
                     <button type="button" onClick={() => setEditForm({...editForm, isLocked: !editForm.isLocked})} className="btn-icon" style={{ color: getNoteTextColor(editForm.colorCode) }}>
                       {editForm.isLocked ? <Lock size={16} /> : <CheckSquare size={16} className="opacity-50"/>}
                     </button>
                  </div>
                  <div className="flex items-center gap-2">
                    <select className="input text-xs h-7 py-0 bg-white/50 dark:bg-black/50 border-0" 
                      onChange={(e) => convertTo(e.target.value as any)} value="">
                      <option value="" disabled>Convert to...</option>
                      <option value="TASK">Task</option>
                      <option value="REMINDER">Reminder</option>
                      <option value="CASHBOOK">Cashbook</option>
                    </select>
                    <button onClick={closeEdit} className="btn-icon"><X size={18} /></button>
                  </div>
                </div>

                <form onSubmit={saveEdit} className="flex-1 overflow-y-auto" style={getPageStyleCss(editForm.pageStyle)}>
                  <div className="p-5">
                    <input required value={editForm.title} onChange={e => setEditForm({ ...editForm, title: e.target.value })}
                      placeholder="Title" className="input font-bold text-xl border-0 bg-transparent px-0 mb-2"
                      style={{ color: getNoteTextColor(editForm.colorCode), boxShadow: "none" }} />
                    <div className="bg-white/50 dark:bg-black/20 rounded-xl overflow-hidden min-h-[300px]">
                      <RichTextEditor content={editForm.content} onChange={html => setEditForm({ ...editForm, content: html })} textColor={getNoteTextColor(editForm.colorCode)} />
                    </div>
                  </div>
                </form>

                <div className="p-4 flex items-center gap-4 flex-wrap border-t" style={{ borderColor: "rgba(0,0,0,0.08)" }}>
                  <div className="flex gap-1.5 flex-wrap">
                    {COLORS.map(c => (
                      <button key={c.hex} type="button" onClick={() => setEditForm({ ...editForm, colorCode: c.hex })} title={c.label}
                        className={`w-5 h-5 rounded-full transition-all border-2 ${editForm.colorCode === c.hex ? 'scale-125 border-blue-500' : 'border-gray-300'}`}
                        style={{ background: c.hex }} />
                    ))}
                  </div>
                  <select value={editForm.pageStyle} onChange={e => setEditForm({...editForm, pageStyle: e.target.value})} className="input text-xs w-auto py-1 h-auto">
                    {PAGE_STYLES.map(s => <option key={s.id} value={s.id}>{s.label}</option>)}
                  </select>
                  <select value={editForm.folder} onChange={e => setEditForm({ ...editForm, folder: e.target.value })} className="input text-xs w-auto py-1 h-auto">
                    {customFolders.map(f => <option key={f} value={f}>{f}</option>)}
                  </select>
                  <div className="ml-auto flex gap-2">
                    <button onClick={saveEdit} disabled={savingEdit} className="btn btn-primary btn-sm px-6">
                      <Save size={14} /> {savingEdit ? "Saving..." : "Save Changes"}
                    </button>
                  </div>
                </div>

              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>

      </div>
    </div>
  );
}
