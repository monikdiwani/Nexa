"use client";
import { useAuth } from "@/context/AuthContext";
import { useEffect, useState } from "react";
import { collection, query, onSnapshot, orderBy } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { motion } from "framer-motion";
import { TrendingUp, TrendingDown, DollarSign, Calendar } from "lucide-react";

interface Entry { id: string; type: string; amount: number; date: number; category: string; particulars: string; }
interface Task { id: string; isCompleted: boolean; }

const CATEGORIES = ["Sales","Rent","Salary","Office","Personal","Food","Transport","Shopping","Bills","Other"];
const CAT_COLORS = ["#7C83F7","#EF5350","#66BB6A","#FFA726","#26C6DA","#FF8A65","#AB47BC","#42A5F5","#EC407A","#8D6E63"];

export default function ReportsPage() {
  const { user } = useAuth();
  const [entries, setEntries] = useState<Entry[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [range, setRange] = useState<"7"|"30"|"90">("30");

  useEffect(() => {
    if (!user) return;
    let done1 = false, done2 = false;
    const check = () => { if (done1 && done2) setLoading(false); };

    const tUnsub = onSnapshot(
      query(collection(db,"users",user.uid,"tasks"), orderBy("createdAt","desc")),
      snap => { setTasks(snap.docs.map(d=>({id:d.id,...d.data()} as Task))); done1=true; check(); }
    );
    done2 = true; check();
    return () => tUnsub();
  }, [user]);

  const now = Date.now();
  const days = parseInt(range);
  const from = now - days * 86400000;

  const rangeEntries = entries.filter(e => e.date >= from);
  const totalIn = rangeEntries.filter(e=>e.type==="CASH_IN").reduce((s,e)=>s+e.amount,0);
  const totalOut = rangeEntries.filter(e=>e.type==="CASH_OUT").reduce((s,e)=>s+e.amount,0);
  const net = totalIn - totalOut;
  const avgPerDay = totalOut / days;

  // Daily spend for bar chart (last 7 days)
  const last7 = Array.from({length:7},(_,i)=>{
    const d = new Date(now - (6-i)*86400000);
    const dayStart = new Date(d.getFullYear(),d.getMonth(),d.getDate()).getTime();
    const dayEnd = dayStart + 86400000;
    const spent = entries.filter(e=>e.type==="CASH_OUT"&&e.date>=dayStart&&e.date<dayEnd).reduce((s,e)=>s+e.amount,0);
    return { label: d.toLocaleDateString("en-IN",{weekday:"short"}), amount: spent };
  });
  const maxDay = Math.max(...last7.map(d=>d.amount), 1);

  // Category breakdown
  const catData = CATEGORIES.map((cat,i)=>{
    const amt = rangeEntries.filter(e=>e.type==="CASH_OUT"&&e.category===cat).reduce((s,e)=>s+e.amount,0);
    return { cat, amt, color: CAT_COLORS[i] };
  }).filter(c=>c.amt>0).sort((a,b)=>b.amt-a.amt);
  const maxCat = catData[0]?.amt || 1;

  // Task stats
  const totalTasks = tasks.length;
  const completedTasks = tasks.filter(t=>t.isCompleted).length;
  const completionPct = totalTasks > 0 ? Math.round((completedTasks/totalTasks)*100) : 0;
  const circumference = 2 * Math.PI * 36;
  const dashOffset = circumference * (1 - completionPct/100);

  const fmt = (n: number) => n >= 1000 ? `₹${(n/1000).toFixed(1)}k` : `₹${Math.round(n)}`;

  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto pb-10">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold" style={{color:"var(--text-primary)"}}>Reports</h1>
          <p className="text-sm mt-0.5" style={{color:"var(--text-secondary)"}}>Analytics &amp; spending insights</p>
        </div>
        <div className="flex gap-1 p-1 rounded-xl" style={{background:"var(--surface)",border:"1px solid var(--divider)"}}>
          {(["7","30","90"] as const).map(r=>(
            <button key={r} onClick={()=>setRange(r)}
              className="px-3 py-1.5 rounded-lg text-sm font-semibold transition-all"
              style={range===r
                ? {background:"var(--primary)",color:"white"}
                : {background:"transparent",color:"var(--text-secondary)"}
              }>{r}d</button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          {[...Array(4)].map((_,i)=><div key={i} className="shimmer h-24"/>)}
        </div>
      ) : (
        <>
          {/* Stats cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
            {[
              {label:"Cash In",val:totalIn,icon:TrendingUp,color:"var(--positive)",bg:"var(--cash-in-bg)"},
              {label:"Cash Out",val:totalOut,icon:TrendingDown,color:"var(--negative)",bg:"var(--cash-out-bg)"},
              {label:"Net Balance",val:net,icon:DollarSign,color:net>=0?"var(--positive)":"var(--negative)",bg:net>=0?"var(--cash-in-bg)":"var(--cash-out-bg)"},
              {label:"Avg/Day",val:avgPerDay,icon:Calendar,color:"var(--info)",bg:"rgba(0,122,255,0.08)"},
            ].map(({label,val,icon:Icon,color,bg})=>(
              <motion.div key={label} initial={{opacity:0,y:12}} animate={{opacity:1,y:0}}
                className="card p-4 flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0" style={{background:bg}}>
                  <Icon size={18} style={{color}}/>
                </div>
                <div>
                  <p className="text-xs font-medium" style={{color:"var(--text-secondary)"}}>{label}</p>
                  <p className="font-bold text-base" style={{color}}>{fmt(Math.abs(val))}</p>
                </div>
              </motion.div>
            ))}
          </div>

          {/* Bar chart — last 7 days spending */}
          <motion.div initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{delay:0.1}}
            className="card p-5 mb-5">
            <h2 className="text-base font-semibold mb-1" style={{color:"var(--text-primary)"}}>Daily Spending</h2>
            <p className="text-sm mb-5" style={{color:"var(--text-secondary)"}}>Last 7 days</p>
            <div className="flex items-end gap-2 h-32">
              {last7.map((d,i)=>(
                <div key={i} className="flex-1 flex flex-col items-center gap-1">
                  <span className="text-xs font-semibold" style={{color:d.amount>0?"var(--primary)":"var(--text-hint)"}}>
                    {d.amount>0?fmt(d.amount):""}
                  </span>
                  <div className="w-full rounded-t-lg transition-all" style={{
                    height: `${d.amount>0 ? Math.max((d.amount/maxDay)*100, 8) : 4}%`,
                    background: d.amount>0 ? "linear-gradient(180deg,#7C83F7,#5C6BC0)" : "var(--divider)",
                    minHeight: "4px"
                  }}/>
                  <span className="text-xs" style={{color:"var(--text-hint)"}}>{d.label}</span>
                </div>
              ))}
            </div>
          </motion.div>

          {/* Two columns */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            {/* Category breakdown */}
            <motion.div initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{delay:0.15}} className="card p-5">
              <h2 className="text-base font-semibold mb-4" style={{color:"var(--text-primary)"}}>Spending by Category</h2>
              {catData.length===0 ? (
                <p className="text-sm text-center py-8" style={{color:"var(--text-hint)"}}>No spending data yet</p>
              ) : (
                <div className="space-y-3">
                  {catData.slice(0,6).map(({cat,amt,color})=>(
                    <div key={cat}>
                      <div className="flex justify-between mb-1">
                        <span className="text-sm font-medium" style={{color:"var(--text-primary)"}}>{cat}</span>
                        <span className="text-sm font-bold" style={{color:"var(--negative)"}}>{fmt(amt)}</span>
                      </div>
                      <div className="h-2 rounded-full overflow-hidden" style={{background:"var(--divider)"}}>
                        <div className="h-full rounded-full transition-all duration-700" style={{
                          width:`${(amt/maxCat)*100}%`,
                          background:color
                        }}/>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </motion.div>

            {/* Task completion */}
            <motion.div initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{delay:0.2}} className="card p-5">
              <h2 className="text-base font-semibold mb-4" style={{color:"var(--text-primary)"}}>Task Completion</h2>
              <div className="flex items-center gap-6">
                {/* Circular progress */}
                <div className="relative flex-shrink-0">
                  <svg width="88" height="88" viewBox="0 0 88 88">
                    <circle cx="44" cy="44" r="36" fill="none" stroke="var(--divider)" strokeWidth="8"/>
                    <circle cx="44" cy="44" r="36" fill="none"
                      stroke="var(--primary)" strokeWidth="8"
                      strokeLinecap="round"
                      strokeDasharray={circumference}
                      strokeDashoffset={dashOffset}
                      transform="rotate(-90 44 44)"
                      style={{transition:"stroke-dashoffset 0.8s ease"}}
                    />
                  </svg>
                  <div className="absolute inset-0 flex items-center justify-center">
                    <span className="text-xl font-black" style={{color:"var(--primary)"}}>{completionPct}%</span>
                  </div>
                </div>
                <div className="space-y-3">
                  <div>
                    <p className="text-xs" style={{color:"var(--text-secondary)"}}>Completed</p>
                    <p className="text-2xl font-bold" style={{color:"var(--positive)"}}>{completedTasks}</p>
                  </div>
                  <div>
                    <p className="text-xs" style={{color:"var(--text-secondary)"}}>Total Tasks</p>
                    <p className="text-2xl font-bold" style={{color:"var(--text-primary)"}}>{totalTasks}</p>
                  </div>
                </div>
              </div>
              {totalTasks===0 && (
                <p className="text-sm text-center mt-4" style={{color:"var(--text-hint)"}}>Add tasks to see stats</p>
              )}
            </motion.div>
          </div>
        </>
      )}
    </div>
  );
}
