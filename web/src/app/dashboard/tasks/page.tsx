"use client";
import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, orderBy, onSnapshot, addDoc, updateDoc, deleteDoc, doc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion, AnimatePresence } from "framer-motion";
import { Plus, CheckSquare, Trash2, Star, Clock, Loader2, Flag } from "lucide-react";

interface Task {
  id: string; title: string; description: string; priority: string;
  isCompleted: boolean; isImportant: boolean; isArchived: boolean;
  dueDate: number | null; createdAt: number;
  subtasks: { title: string; isCompleted: boolean }[];
}

const PRIORITIES = ["HIGH","MEDIUM","LOW"];
const PRIORITY_COLORS: Record<string,string> = { HIGH:"var(--priority-high)", MEDIUM:"var(--priority-medium)", LOW:"var(--priority-low)" };
const PRIORITY_BG: Record<string,string> = { HIGH:"rgba(229,57,53,0.10)", MEDIUM:"rgba(255,149,0,0.10)", LOW:"rgba(40,167,69,0.10)" };

export default function TasksPage() {
  const { user } = useAuth();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [filter, setFilter] = useState("PENDING");
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [title, setTitle] = useState("");
  const [priority, setPriority] = useState("MEDIUM");
  const [dueDate, setDueDate] = useState("");

  useEffect(() => {
    if (!user) return;
    const q = query(collection(db,"users",user.uid,"tasks"), orderBy("createdAt","desc"));
    return onSnapshot(q, snap => {
      setTasks(snap.docs.map(d => ({ id:d.id, ...d.data() } as Task)));
      setLoading(false);
    });
  }, [user]);

  const addTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !title.trim()) return;
    setAdding(true);
    await addDoc(collection(db,"users",user.uid,"tasks"), {
      title: title.trim(), description: "", priority,
      isCompleted: false, isImportant: false, isArchived: false,
      createdAt: Date.now(), dueDate: dueDate ? new Date(dueDate).getTime() : null,
      recurringPattern: "NONE", isRecurring: false, subtasks: []
    });
    setTitle(""); setDueDate(""); setAdding(false); setShowForm(false);
  };

  const toggleComplete = async (task: Task) => {
    if (!user) return;
    await updateDoc(doc(db,"users",user.uid,"tasks",task.id), {
      isCompleted: !task.isCompleted,
      completedAt: !task.isCompleted ? Date.now() : null
    });
  };

  const toggleImportant = async (task: Task) => {
    if (!user) return;
    await updateDoc(doc(db,"users",user.uid,"tasks",task.id), { isImportant: !task.isImportant });
  };

  const deleteTask = async (id: string) => {
    if (!user) return;
    await deleteDoc(doc(db,"users",user.uid,"tasks",id));
  };

  const FILTERS = [
    { id:"PENDING", label:"Pending" },
    { id:"COMPLETED", label:"Done" },
    { id:"IMPORTANT", label:"⭐ Important" },
    { id:"ALL", label:"All" },
  ];

  const filtered = tasks.filter(t => {
    if (t.isArchived) return false;
    if (filter==="PENDING") return !t.isCompleted;
    if (filter==="COMPLETED") return t.isCompleted;
    if (filter==="IMPORTANT") return t.isImportant && !t.isCompleted;
    return true;
  });

  const counts: Record<string,number> = {
    PENDING: tasks.filter(t=>!t.isCompleted&&!t.isArchived).length,
    COMPLETED: tasks.filter(t=>t.isCompleted).length,
    IMPORTANT: tasks.filter(t=>t.isImportant&&!t.isCompleted&&!t.isArchived).length,
    ALL: tasks.filter(t=>!t.isArchived).length,
  };

  const now = Date.now();
  const isOverdue = (t: Task) => !t.isCompleted && t.dueDate && t.dueDate < now;

  return (
    <div className="p-4 md:p-6 max-w-2xl mx-auto pb-10">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold" style={{color:"var(--text-primary)"}}>Tasks</h1>
          <p className="text-sm mt-0.5" style={{color:"var(--text-secondary)"}}>
            {counts.PENDING} pending · {counts.COMPLETED} completed
          </p>
        </div>
        <button onClick={()=>setShowForm(!showForm)} className="btn btn-primary btn-sm">
          <Plus size={15}/> New Task
        </button>
      </div>

      {/* Add Form */}
      <AnimatePresence>
        {showForm && (
          <motion.div initial={{opacity:0,y:-8,height:0}} animate={{opacity:1,y:0,height:"auto"}}
            exit={{opacity:0,y:-8,height:0}} transition={{duration:0.2}}
            className="overflow-hidden mb-5">
            <form onSubmit={addTask} className="card p-5 space-y-3">
              <h3 style={{color:"var(--text-primary)"}}>New Task</h3>
              <input required value={title} onChange={e=>setTitle(e.target.value)}
                placeholder="What needs to be done?" className="input" />
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold mb-1.5 block" style={{color:"var(--text-secondary)"}}>PRIORITY</label>
                  <div className="flex gap-2">
                    {PRIORITIES.map(p=>(
                      <button key={p} type="button" onClick={()=>setPriority(p)}
                        className="flex-1 py-1.5 text-xs font-semibold rounded-lg border transition-all"
                        style={{
                          background: priority===p ? PRIORITY_BG[p] : "var(--surface)",
                          color: priority===p ? PRIORITY_COLORS[p] : "var(--text-hint)",
                          borderColor: priority===p ? PRIORITY_COLORS[p] : "var(--stroke)"
                        }}>{p[0]+p.slice(1).toLowerCase()}</button>
                    ))}
                  </div>
                </div>
                <div>
                  <label className="text-xs font-semibold mb-1.5 block" style={{color:"var(--text-secondary)"}}>DUE DATE</label>
                  <input type="date" value={dueDate} onChange={e=>setDueDate(e.target.value)} className="input text-sm" />
                </div>
              </div>
              <div className="flex gap-2 pt-1">
                <button type="submit" disabled={adding} className="btn btn-primary btn-sm">
                  {adding ? <Loader2 size={14} className="animate-spin"/> : <Plus size={14}/>} Add Task
                </button>
                <button type="button" onClick={()=>setShowForm(false)} className="btn btn-ghost btn-sm">Cancel</button>
              </div>
            </form>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Filter Tabs */}
      <div className="flex gap-1.5 mb-5 flex-wrap">
        {FILTERS.map(f=>(
          <button key={f.id} onClick={()=>setFilter(f.id)}
            className="chip cursor-pointer transition-all"
            style={filter===f.id
              ? {background:"var(--primary)",color:"white"}
              : {background:"var(--surface)",color:"var(--text-secondary)",border:"1px solid var(--divider)"}
            }>
            {f.label}
            {counts[f.id] > 0 && (
              <span className="badge" style={{
                background: filter===f.id ? "rgba(255,255,255,0.25)" : "var(--primary-surface)",
                color: filter===f.id ? "white" : "var(--primary)"
              }}>{counts[f.id]}</span>
            )}
          </button>
        ))}
      </div>

      {/* Task List */}
      {loading ? (
        <div className="space-y-2">
          {[...Array(4)].map((_,i)=><div key={i} className="shimmer h-16"/>)}
        </div>
      ) : filtered.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon"><CheckSquare size={28} style={{color:"var(--primary)"}}/></div>
          <p className="font-semibold" style={{color:"var(--text-primary)"}}>
            {filter==="PENDING" ? "All caught up!" : "Nothing here yet"}
          </p>
          <p className="text-sm" style={{color:"var(--text-secondary)"}}>
            {filter==="PENDING" ? "Add a new task to get started." : "Tasks will appear here."}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          <AnimatePresence>
            {filtered.map((task,i)=>(
              <motion.div key={task.id}
                initial={{opacity:0,y:8}} animate={{opacity:1,y:0}} exit={{opacity:0,x:-20,height:0}}
                transition={{delay:i*0.03,duration:0.2}}
                className="card p-3.5 flex items-center gap-3 group"
                style={{borderColor: isOverdue(task) ? "rgba(229,57,53,0.3)" : "var(--divider)"}}>
                {/* Complete btn */}
                <button onClick={()=>toggleComplete(task)}
                  className="w-5 h-5 rounded-full border-2 flex-shrink-0 flex items-center justify-center transition-all"
                  style={{
                    borderColor: task.isCompleted ? "var(--positive)" : task.priority==="HIGH" ? "var(--priority-high)" : "var(--stroke)",
                    background: task.isCompleted ? "var(--positive)" : "transparent"
                  }}>
                  {task.isCompleted && (
                    <svg width="10" height="8" viewBox="0 0 10 8" fill="none">
                      <path d="M1 4L3.5 6.5L9 1" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  )}
                </button>

                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate transition-all" style={{
                    color: task.isCompleted ? "var(--text-hint)" : "var(--text-primary)",
                    textDecoration: task.isCompleted ? "line-through" : "none"
                  }}>{task.title}</p>
                  <div className="flex items-center gap-2 mt-0.5 flex-wrap">
                    {task.dueDate && (
                      <span className="text-xs flex items-center gap-1" style={{
                        color: isOverdue(task) ? "var(--negative)" : "var(--text-hint)"
                      }}>
                        <Clock size={11}/>
                        {isOverdue(task) ? "Overdue · " : ""}
                        {new Date(task.dueDate).toLocaleDateString("en-IN",{month:"short",day:"numeric"})}
                      </span>
                    )}
                    <span className="chip text-xs" style={{background:PRIORITY_BG[task.priority],color:PRIORITY_COLORS[task.priority],padding:"2px 8px"}}>
                      <Flag size={9}/>{task.priority[0]+task.priority.slice(1).toLowerCase()}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button onClick={()=>toggleImportant(task)} className="btn-icon" style={{color: task.isImportant ? "var(--warning)" : "var(--text-hint)"}}>
                    <Star size={15} fill={task.isImportant ? "currentColor" : "none"}/>
                  </button>
                  <button onClick={()=>deleteTask(task.id)} className="btn-icon" style={{color:"var(--negative)"}}>
                    <Trash2 size={15}/>
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
