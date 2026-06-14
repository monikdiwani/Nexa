"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Calculator, RotateCcw } from "lucide-react";

const DENOMINATIONS = [2000, 500, 200, 100, 50, 20, 10, 5, 2, 1];

export default function CashCounterPage() {
  const router = useRouter();
  const [counts, setCounts] = useState<Record<number, number>>({});

  const handleCountChange = (denom: number, val: string) => {
    const num = parseInt(val) || 0;
    setCounts(prev => ({ ...prev, [denom]: Math.max(0, num) }));
  };

  const clearAll = () => setCounts({});

  const grandTotal = DENOMINATIONS.reduce((sum, denom) => sum + (denom * (counts[denom] || 0)), 0);
  const totalNotes = DENOMINATIONS.reduce((sum, denom) => sum + (counts[denom] || 0), 0);

  const fmt = (n: number) => `₹${n.toLocaleString("en-IN")}`;

  return (
    <div className="p-4 md:p-6 max-w-2xl mx-auto pb-10">
      <div className="flex items-center gap-3 mb-6">
        <button onClick={() => router.back()} className="btn-icon">
          <ArrowLeft size={20} />
        </button>
        <div className="flex-1 min-w-0">
          <h1 className="text-xl font-bold" style={{ color: "var(--text-primary)" }}>Cash Counter</h1>
          <p className="text-xs" style={{ color: "var(--text-hint)" }}>Tally your physical cash denominations</p>
        </div>
        <button onClick={clearAll} className="btn btn-ghost btn-sm text-xs flex items-center gap-1.5" style={{ color: "var(--negative)" }}>
          <RotateCcw size={14} /> Clear
        </button>
      </div>

      <div className="card p-6 mb-6 nexa-gradient text-white shadow-lg text-center rounded-3xl">
        <Calculator size={24} className="mx-auto mb-2 opacity-80" />
        <p className="text-sm font-semibold opacity-90">Grand Total</p>
        <p className="text-4xl font-black mt-1 mb-2 tracking-tight">{fmt(grandTotal)}</p>
        <p className="text-xs opacity-80 bg-black/10 inline-block px-3 py-1 rounded-full">
          Total Notes/Coins: {totalNotes}
        </p>
      </div>

      <div className="card overflow-hidden">
        <div className="grid grid-cols-[1fr,1fr,1.5fr] gap-4 p-4 border-b font-bold text-xs uppercase" style={{ borderColor: "var(--divider)", color: "var(--text-secondary)", background: "var(--surface)" }}>
          <div>Note</div>
          <div>Count</div>
          <div className="text-right">Total</div>
        </div>
        
        {DENOMINATIONS.map((denom) => {
          const count = counts[denom] || 0;
          const total = denom * count;
          return (
            <div key={denom} className="grid grid-cols-[1fr,1fr,1.5fr] gap-4 p-4 border-b items-center transition-colors hover:bg-black/5" style={{ borderColor: "var(--divider)" }}>
              <div className="font-semibold" style={{ color: "var(--text-primary)" }}>₹{denom}</div>
              <div>
                <input
                  type="number"
                  min="0"
                  value={count === 0 ? "" : count}
                  onChange={(e) => handleCountChange(denom, e.target.value)}
                  placeholder="0"
                  className="input text-center py-1.5 px-2 text-sm font-semibold shadow-inner"
                  style={{ width: "100%", maxWidth: "80px", background: "var(--bg)" }}
                />
              </div>
              <div className="text-right font-bold" style={{ color: total > 0 ? "var(--positive)" : "var(--text-hint)" }}>
                {total > 0 ? fmt(total) : "-"}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
