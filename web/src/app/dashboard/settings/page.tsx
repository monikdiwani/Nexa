"use client";
import { useAuth } from "@/context/AuthContext";
import { useState } from "react";
import { updateProfile } from "firebase/auth";
import { auth } from "@/lib/firebase";
import { Settings, LogOut, User, Mail, Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";

export default function SettingsPage() {
  const { user, signOut } = useAuth();
  const router = useRouter();
  const [name, setName] = useState(user?.displayName ?? "");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const updateName = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !name.trim()) return;
    setSaving(true);
    await updateProfile(auth.currentUser!, { displayName: name.trim() });
    setSaving(false); setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const handleSignOut = async () => { await signOut(); router.push("/"); };

  const initials = user?.displayName?.split(" ").map(n=>n[0]).join("").toUpperCase().slice(0,2) ?? "N";

  return (
    <div className="p-4 md:p-6 max-w-2xl mx-auto space-y-5">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{background:"linear-gradient(135deg,#546E7A,#263238)"}}>
          <Settings className="w-5 h-5 text-white" />
        </div>
        <h1 className="text-xl font-bold" style={{color:"var(--text-primary)"}}>Settings</h1>
      </div>

      {/* Profile Card */}
      <div className="rounded-2xl p-6" style={{background:"var(--surface)",boxShadow:"var(--shadow-card)"}}>
        <div className="flex items-center gap-4 mb-5">
          <div className="w-16 h-16 rounded-2xl nexa-gradient flex items-center justify-center text-white text-2xl font-black">{initials}</div>
          <div>
            <p className="font-bold text-lg" style={{color:"var(--text-primary)"}}>{user?.displayName ?? "Nexa User"}</p>
            <p className="text-sm flex items-center gap-1" style={{color:"var(--text-secondary)"}}><Mail className="w-3.5 h-3.5" />{user?.email}</p>
          </div>
        </div>
        <form onSubmit={updateName} className="space-y-3">
          <div>
            <label className="text-sm font-medium block mb-1.5" style={{color:"var(--text-secondary)"}}>Display Name</label>
            <div className="relative">
              <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4" style={{color:"var(--text-hint)"}} />
              <input value={name} onChange={e=>setName(e.target.value)} className="w-full nexa-input pl-10 pr-4 py-2.5 text-sm" />
            </div>
          </div>
          <button type="submit" disabled={saving} className="btn-primary px-5 py-2 text-sm">
            {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : saved ? "✓ Saved!" : "Update Name"}
          </button>
        </form>
      </div>

      {/* Account actions */}
      <div className="rounded-2xl p-5" style={{background:"var(--surface)",boxShadow:"var(--shadow-card)"}}>
        <h3 className="font-semibold mb-4" style={{color:"var(--text-primary)"}}>Account</h3>
        <button onClick={handleSignOut}
          className="w-full flex items-center gap-3 p-3 rounded-xl text-left transition-colors hover:bg-red-50"
          style={{color:"var(--negative)"}}>
          <LogOut className="w-4 h-4" />
          <span className="font-medium text-sm">Sign Out</span>
        </button>
      </div>
    </div>
  );
}
