"use client";
import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, onSnapshot, orderBy, addDoc, updateDoc, doc, deleteDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion } from "framer-motion";
import { Plus, CheckSquare, Trash2, Star, Clock, Loader2 } from "lucide-react";

interface Task { id: string; title: string; description: string; priority: string; isCompleted: boolean; isImportant: boolean; dueDate: number | null; createdAt: number; }

export default function TasksPage() {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [filter, setFilter] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState("");
  const [priority, setPriority] = useState("MEDIUM");

  useEffect(() => {
    if (!user) return;
    const q = query(collection(db, "users", user.uid, "tasks"), orderBy("createdAt", "desc"));
    return onSnapshot(q, (snap) => {
      setTasks(snap.docs.map(d => ({ id: d.id, ...d.data() } as Task)));
      setLoading(false);
    });
  }, [user]);

  const addTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !title.trim()) return;
    setAdding(true);
    await addDoc(collection(db, "users", user.uid, "tasks"), {
      title: title.trim(), description: "", priority, isCompleted: false,
      isImportant: false, isArchived: false, createdAt: Date.now(), dueDate: null,
      recurringPattern: "NONE", subtasks: []
    });
    setTitle(""); setAdding(false);
  };

  const toggleComplete = async (task: Task) => {
    if (!user) return;
    await updateDoc(doc(db, "users", user.uid, "tasks", task.id), {
      isCompleted: !task.isCompleted, completedAt: !task.isCompleted ? Date.now() : null
    });
  };

  const deleteTask = async (id: string) => {
    if (!user) return;
    await deleteDoc(doc(db, "users", user.uid, "tasks", id));
  };

  const filters = ["ALL", "PENDING", "COMPLETED", "IMPORTANT"];
  const filtered = tasks.filter(t => {
    if (filter === "PENDING") return !t.isCompleted;
    if (filter === "COMPLETED") return t.isCompleted;
    if (filter === "IMPORTANT") return t.isImportant;
    return true;
  });

  return (
    <div className="p-4 md:p-6 max-w-3xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{background:"linear-gradient(135deg,#43A047,#2E7D32)"}}>
          <CheckSquare className="w-5 h-5 text-white" />
        </div>
        <div>
          <h1 className="text-xl font-bold" style={{color:"var(--text-primary)"}}>Tasks</h1>
          <p className="text-sm" style={{color:"var(--text-secondary)"}}>{tasks.filter(t=>!t.isCompleted).length} pending</p>
        </div>
      </div>

      {/* Add Task */}
      <form onSubmit={addTask} className="flex gap-2 mb-5">
        <select value={priority} onChange={e=>setPriority(e.target.value)}
          className="px-3 py-2.5 rounded-xl text-sm border" style={{background:"var(--surface)",borderColor:"var(--divider)",color:"var(--text-primary)"}}>
          <option value="HIGH">🔴 High</option>
          <option value="MEDIUM">🟡 Medium</option>
          <option value="LOW">🟢 Low</option>
        </select>
        <input value={title} onChange={e=>setTitle(e.target.value)} placeholder="Add a new task..."
          className="flex-1 nexa-input px-4 py-2.5 text-sm" required />
        <button type="submit" disabled={adding} className="btn-primary px-4 py-2.5">
          {adding ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
        </button>
      </form>

      {/* Filters */}
      <div className="flex gap-2 flex-wrap mb-4">
        {filters.map(f => (
          <button key={f} onClick={()=>setFilter(f)}
            className="chip text-xs cursor-pointer transition-all"
            style={filter===f ? {background:"var(--primary)",color:"white"} : {background:"var(--surface)",color:"var(--text-secondary)",border:"1px solid var(--divider)"}}>
            {f}
          </button>
        ))}
      </div>

      {/* Task list */}
      {loading ? (
        <div className="space-y-2">{[...Array(4)].map((_,i)=><div key={i} className="h-14 rounded-xl shimmer" />)}</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-16" style={{color:"var(--text-hint)"}}>
          <CheckSquare className="w-12 h-12 mx-auto mb-3 opacity-30" />
          <p>No tasks here.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {filtered.map((task,i) => (
            <motion.div key={task.id} initial={{opacity:0,y:8}} animate={{opacity:1,y:0}} transition={{delay:i*0.04}}
              className="flex items-center gap-3 p-3.5 rounded-xl group"
              style={{background:"var(--surface)",boxShadow:"var(--shadow-card)"}}>
              <button onClick={()=>toggleComplete(task)}
                className="w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 transition-all"
                style={{borderColor: task.isCompleted ? "var(--positive)" : "var(--stroke)", background: task.isCompleted ? "var(--positive)" : "transparent"}}>
                {task.isCompleted && <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7"/></svg>}
              </button>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate" style={{color:"var(--text-primary)", textDecoration: task.isCompleted ? "line-through" : "none", opacity: task.isCompleted ? 0.5 : 1}}>{task.title}</p>
              </div>
              {task.isImportant && <Star className="w-4 h-4 fill-current" style={{color:"var(--warning)"}} />}
              <span className={`chip text-xs ${task.priority==="HIGH"?"priority-high":task.priority==="LOW"?"priority-low":"priority-medium"}`}>{task.priority}</span>
              <button onClick={()=>deleteTask(task.id)} className="opacity-0 group-hover:opacity-100 transition-opacity p-1 rounded-lg hover:bg-red-50" style={{color:"var(--negative)"}}>
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}
