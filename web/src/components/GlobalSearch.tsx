"use client";
import { useState, useEffect } from "react";
import { useAuth } from "@/context/AuthContext";
import { collection, query, getDocs } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion, AnimatePresence } from "framer-motion";
import { Search, X, StickyNote, CheckSquare, Bell, DollarSign, Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";

export default function GlobalSearch({ onClose }: { onClose: () => void }) {
  const { user } = useAuth();
  const router = useRouter();
  const [q, setQ] = useState("");
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<any[]>([]);

  useEffect(() => {
    if (!user || q.trim().length < 2) {
      setResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      const searchTxt = q.toLowerCase();
      const newResults: any[] = [];

      try {
        // Search Notes
        const notesSnap = await getDocs(query(collection(db, "users", user.uid, "notes")));
        notesSnap.forEach(d => {
          const data = d.data();
          if ((data.title && data.title.toLowerCase().includes(searchTxt)) || 
              (data.content && data.content.toLowerCase().includes(searchTxt))) {
            newResults.push({ id: d.id, type: "NOTE", title: data.title, desc: data.content, route: "/dashboard/notes" });
          }
        });

        // Search Tasks
        const tasksSnap = await getDocs(query(collection(db, "users", user.uid, "tasks")));
        tasksSnap.forEach(d => {
          const data = d.data();
          if (data.title && data.title.toLowerCase().includes(searchTxt)) {
            newResults.push({ id: d.id, type: "TASK", title: data.title, desc: data.description || "Task", route: "/dashboard/tasks" });
          }
        });

        // Search Reminders
        const remindersSnap = await getDocs(query(collection(db, "users", user.uid, "reminders")));
        remindersSnap.forEach(d => {
          const data = d.data();
          if (data.title && data.title.toLowerCase().includes(searchTxt)) {
            newResults.push({ id: d.id, type: "REMINDER", title: data.title, desc: "Reminder", route: "/dashboard/reminders" });
          }
        });

        // Search Cashbooks
        const booksSnap = await getDocs(query(collection(db, "cashbooks")));
        for (const book of booksSnap.docs) {
          const bData = book.data();
          if (bData.ownerId === user.uid || (bData.members && bData.members[user.uid])) {
            const entriesSnap = await getDocs(query(collection(db, "cashbooks", book.id, "entries")));
            entriesSnap.forEach(eDoc => {
              const eData = eDoc.data();
              if ((eData.particulars && eData.particulars.toLowerCase().includes(searchTxt)) ||
                  (eData.note && eData.note.toLowerCase().includes(searchTxt))) {
                newResults.push({ id: eDoc.id, type: "MONEY", title: eData.particulars, desc: `₹${eData.amount} in ${bData.name}`, route: `/dashboard/money/${book.id}` });
              }
            });
          }
        }
      } catch (err) {
        console.error("Search error:", err);
      }

      setResults(newResults);
      setLoading(false);
    }, 500); // debounce

    return () => clearTimeout(timer);
  }, [q, user]);

  const handleGo = (route: string) => {
    onClose();
    router.push(route);
  };

  const getIcon = (type: string) => {
    if (type === "NOTE") return <StickyNote size={16} />;
    if (type === "TASK") return <CheckSquare size={16} />;
    if (type === "REMINDER") return <Bell size={16} />;
    if (type === "MONEY") return <DollarSign size={16} />;
    return <Search size={16} />;
  };

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      className="fixed inset-0 z-[200] flex justify-center p-4 pt-20"
      style={{ background: "rgba(0,0,0,0.6)", backdropFilter: "blur(4px)" }}
      onClick={onClose}>
      
      <motion.div initial={{ opacity: 0, scale: 0.95, y: -20 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: -20 }}
        onClick={e => e.stopPropagation()}
        className="w-full max-w-2xl rounded-2xl shadow-2xl flex flex-col overflow-hidden"
        style={{ background: "var(--surface)", maxHeight: "70vh" }}>
        
        <div className="flex items-center gap-3 p-4 border-b" style={{ borderColor: "var(--divider)" }}>
          <Search size={20} style={{ color: "var(--text-hint)" }} />
          <input autoFocus value={q} onChange={e => setQ(e.target.value)}
            placeholder="Search notes, tasks, money..." 
            className="flex-1 bg-transparent border-0 outline-none text-lg"
            style={{ color: "var(--text-primary)" }} />
          {loading && <Loader2 size={18} className="animate-spin" style={{ color: "var(--primary)" }} />}
          <button onClick={onClose} className="btn-icon"><X size={20} /></button>
        </div>

        <div className="flex-1 overflow-y-auto p-2">
          {q.trim().length < 2 ? (
            <p className="text-center p-6 text-sm" style={{ color: "var(--text-hint)" }}>
              Type at least 2 characters to search across Nexa.
            </p>
          ) : results.length === 0 && !loading ? (
            <p className="text-center p-6 text-sm" style={{ color: "var(--text-hint)" }}>
              No results found for "{q}".
            </p>
          ) : (
            <div className="space-y-1">
              {results.map((r, i) => (
                <div key={`${r.id}-${i}`} onClick={() => handleGo(r.route)}
                  className="flex items-center gap-4 p-3 rounded-xl cursor-pointer transition-colors hover:bg-black/5">
                  <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
                    style={{ background: "var(--primary-surface)", color: "var(--primary)" }}>
                    {getIcon(r.type)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold text-sm truncate" style={{ color: "var(--text-primary)" }}>{r.title}</p>
                    <p className="text-xs truncate" style={{ color: "var(--text-secondary)" }}>{r.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </motion.div>
    </motion.div>
  );
}
