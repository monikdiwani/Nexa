"use client";
import { useAuth } from "@/context/AuthContext";
import { useState, useEffect } from "react";
import { updateProfile } from "firebase/auth";
import { auth } from "@/lib/firebase";
import { LogOut, User, Mail, Loader2, Shield, Bell, Moon, Sun, Check, Smartphone } from "lucide-react";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";

export default function SettingsPage() {
  const { user, signOut } = useAuth();
  const router = useRouter();
  const [name, setName] = useState(user?.displayName ?? "");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [dark, setDark] = useState(false);

  useEffect(() => {
    const isDark = document.documentElement.classList.contains("dark");
    setDark(isDark);
  }, []);

  const toggleDark = () => {
    const next = !dark;
    setDark(next);
    if (next) document.documentElement.classList.add("dark");
    else document.documentElement.classList.remove("dark");
    localStorage.setItem("nexa-dark", String(next));
  };

  const updateName = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !name.trim()) return;
    setSaving(true);
    await updateProfile(auth.currentUser!, { displayName: name.trim() });
    setSaving(false); setSaved(true);
    setTimeout(() => setSaved(false), 2500);
  };

  const handleSignOut = async () => { await signOut(); router.push("/"); };

  const initials = user?.displayName?.split(" ").map(n=>n[0]).join("").toUpperCase().slice(0,2) ?? "N";

  const SettingRow = ({ icon: Icon, label, desc, children }: {
    icon: React.ElementType; label: string; desc?: string; children?: React.ReactNode;
  }) => (
    <div className="flex items-center gap-3 py-3.5 px-5">
      <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0" style={{background:"var(--primary-surface)"}}>
        <Icon size={16} style={{color:"var(--primary)"}}/>
      </div>
      <div className="flex-1">
        <p className="text-sm font-semibold" style={{color:"var(--text-primary)"}}>{label}</p>
        {desc && <p className="text-xs" style={{color:"var(--text-secondary)"}}>{desc}</p>}
      </div>
      {children}
    </div>
  );

  return (
    <div className="p-4 md:p-6 max-w-xl mx-auto pb-10">
      <h1 className="text-2xl font-bold mb-6" style={{color:"var(--text-primary)"}}>Settings</h1>

      {/* Profile card */}
      <motion.div initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} className="card mb-5">
        {/* Avatar + info */}
        <div className="p-5 flex items-center gap-4 border-b" style={{borderColor:"var(--divider)"}}>
          <div className="w-16 h-16 rounded-2xl nexa-gradient flex items-center justify-center text-white text-2xl font-black flex-shrink-0">
            {initials}
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-bold text-lg truncate" style={{color:"var(--text-primary)"}}>{user?.displayName || "Nexa User"}</p>
            <p className="text-sm flex items-center gap-1.5 truncate" style={{color:"var(--text-secondary)"}}>
              <Mail size={13}/>{user?.email}
            </p>
            <p className="text-xs mt-1 flex items-center gap-1" style={{color:"var(--positive)"}}>
              <Shield size={11}/> Account verified
            </p>
          </div>
        </div>

        {/* Edit name */}
        <div className="p-5">
          <label className="text-xs font-bold tracking-wide block mb-2" style={{color:"var(--text-secondary)"}}>DISPLAY NAME</label>
          <form onSubmit={updateName} className="flex gap-2">
            <div className="relative flex-1">
              <User size={15} className="absolute left-3 top-1/2 -translate-y-1/2" style={{color:"var(--text-hint)"}}/>
              <input value={name} onChange={e=>setName(e.target.value)}
                className="input pl-9" placeholder="Your name" />
            </div>
            <button type="submit" disabled={saving} className="btn btn-primary btn-sm flex-shrink-0">
              {saving ? <Loader2 size={14} className="animate-spin"/> : saved ? <Check size={14}/> : "Save"}
            </button>
          </form>
          {saved && <p className="text-xs mt-2" style={{color:"var(--positive)"}}>✓ Name updated successfully</p>}
        </div>
      </motion.div>

      {/* Preferences */}
      <motion.div initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{delay:0.05}} className="card mb-5 overflow-hidden">
        <div className="px-5 pt-4 pb-2">
          <p className="text-xs font-bold tracking-widest" style={{color:"var(--text-hint)"}}>PREFERENCES</p>
        </div>
        <div className="divide-y" style={{borderColor:"var(--divider)"}}>
          <SettingRow icon={dark ? Moon : Sun} label="Dark Mode" desc={dark ? "Dark theme active" : "Light theme active"}>
            <button onClick={toggleDark}
              className="relative w-11 h-6 rounded-full transition-all duration-300 flex-shrink-0"
              style={{background: dark ? "var(--primary)" : "var(--divider)"}}>
              <div className="absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-all duration-300"
                style={{left: dark ? "calc(100% - 22px)" : "2px"}}/>
            </button>
          </SettingRow>
          <SettingRow icon={Bell} label="Notifications" desc="Reminder alerts"/>
          <SettingRow icon={Smartphone} label="Sync with Android" desc="Real-time data sync enabled">
            <span className="text-xs font-semibold px-2 py-0.5 rounded-full" style={{background:"var(--cash-in-bg)",color:"var(--positive)"}}>Active</span>
          </SettingRow>
        </div>
      </motion.div>

      {/* Account */}
      <motion.div initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{delay:0.1}} className="card overflow-hidden">
        <div className="px-5 pt-4 pb-2">
          <p className="text-xs font-bold tracking-widest" style={{color:"var(--text-hint)"}}>ACCOUNT</p>
        </div>
        <div className="p-3">
          <button onClick={handleSignOut}
            className="w-full flex items-center gap-3 px-4 py-3 rounded-xl transition-all text-left"
            style={{color:"var(--negative)"}}
            onMouseEnter={e=>(e.currentTarget.style.background="rgba(229,57,53,0.08)")}
            onMouseLeave={e=>(e.currentTarget.style.background="transparent")}>
            <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0" style={{background:"rgba(229,57,53,0.10)"}}>
              <LogOut size={16} style={{color:"var(--negative)"}}/>
            </div>
            <div>
              <p className="text-sm font-semibold" style={{color:"var(--negative)"}}>Sign Out</p>
              <p className="text-xs" style={{color:"var(--text-hint)"}}>Sign out of your Nexa account</p>
            </div>
          </button>
        </div>
      </motion.div>

      {/* Version */}
      <p className="text-center text-xs mt-6" style={{color:"var(--text-hint)"}}>
        Nexa v1.0 · Made with ❤️
      </p>
    </div>
  );
}
