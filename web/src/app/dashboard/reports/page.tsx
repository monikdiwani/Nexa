"use client";
import { useAuth } from "@/context/AuthContext";
import { BarChart2 } from "lucide-react";

export default function ReportsPage() {
  return (
    <div className="p-4 md:p-6 max-w-4xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{background:"linear-gradient(135deg,#9C27B0,#6A1B9A)"}}>
          <BarChart2 className="w-5 h-5 text-white" />
        </div>
        <div>
          <h1 className="text-xl font-bold" style={{color:"var(--text-primary)"}}>Reports</h1>
          <p className="text-sm" style={{color:"var(--text-secondary)"}}>Analytics &amp; insights</p>
        </div>
      </div>
      <div className="rounded-2xl p-8 text-center" style={{background:"var(--surface)",boxShadow:"var(--shadow-card)"}}>
        <BarChart2 className="w-16 h-16 mx-auto mb-4" style={{color:"var(--primary)",opacity:0.4}} />
        <h3 className="text-lg font-semibold mb-2" style={{color:"var(--text-primary)"}}>Reports Coming Soon</h3>
        <p style={{color:"var(--text-secondary)"}}>Full charts, category breakdowns, and spending analytics will be available here.</p>
      </div>
    </div>
  );
}
