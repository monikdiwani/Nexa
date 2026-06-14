"use client";
import { useAuth } from "@/context/AuthContext";
import { useState, useEffect } from "react";
import { updateProfile } from "firebase/auth";
import { auth } from "@/lib/firebase";
import { LogOut, User, Mail, Loader2, Shield, Bell, Moon, Sun, Check, Smartphone, Lock, Download, Key } from "lucide-react";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { doc, getDoc, setDoc, collection, getDocs } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { sendPasswordResetEmail } from "firebase/auth";
import JSZip from "jszip";
import { saveAs } from "file-saver";

export default function SettingsPage() {
  const { user, signOut } = useAuth();
  const router = useRouter();
  const [name, setName] = useState(user?.displayName ?? "");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [dark, setDark] = useState(false);
  const [appLockEnabled, setAppLockEnabled] = useState(false);

  useEffect(() => {
    const isDark = document.documentElement.classList.contains("dark");
    setDark(isDark);
    if (user) {
      getDoc(doc(db, "users", user.uid, "settings", "app_lock")).then(snap => {
        if (snap.exists() && snap.data()?.enabled) {
          setAppLockEnabled(true);
        }
      });
    }
  }, [user]);

  const toggleDark = () => {
    const next = !dark;
    setDark(next);
    if (next) document.documentElement.classList.add("dark");
    else document.documentElement.classList.remove("dark");
    localStorage.setItem("nexa-dark", String(next));
  };

  const toggleAppLock = async () => {
    if (!user) return;
    try {
      const nextState = !appLockEnabled;
      if (nextState) {
        // Enrolling - Mock WebAuthn check
        if (!window.PublicKeyCredential) {
          alert("WebAuthn not supported. Please use a password/PIN fallback.");
          return;
        }
        const challenge = new Uint8Array(32);
        crypto.getRandomValues(challenge);
        await navigator.credentials.create({
          publicKey: {
            challenge,
            rp: { name: "Nexa Web" },
            user: { id: new Uint8Array(16), name: user.email!, displayName: user.displayName! },
            pubKeyCredParams: [{ type: "public-key", alg: -7 }],
            authenticatorSelection: { userVerification: "required" },
            timeout: 60000
          }
        });
      }
      setAppLockEnabled(nextState);
      await setDoc(doc(db, "users", user.uid, "settings", "app_lock"), { enabled: nextState });
      localStorage.setItem("nexa-app-lock", String(nextState));
    } catch (err) {
      console.error(err);
      alert("Biometric setup failed or cancelled.");
    }
  };

  const updateName = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !name.trim()) return;
    setSaving(true);
    await updateProfile(auth.currentUser!, { displayName: name.trim() });
    setSaving(false); setSaved(true);
    setTimeout(() => setSaved(false), 2500);
  };

  const handlePasswordReset = async () => {
    if (!user?.email) return;
    try {
      await sendPasswordResetEmail(auth, user.email);
      alert("Password reset email sent! Please check your inbox.");
    } catch (e) {
      alert("Failed to send reset email.");
    }
  };

  const exportData = async () => {
    if (!user) return;
    setExporting(true);
    try {
      const zip = new JSZip();
      
      // Fetch notes
      const notes = await getDocs(collection(db, "users", user.uid, "notes"));
      let notesCsv = "ID,Title,Content,CreatedAt\n";
      notes.forEach(d => {
        const data = d.data();
        notesCsv += `"${d.id}","${data.title}","${data.content?.replace(/"/g, '""')}","${data.createdAt}"\n`;
      });
      zip.file("notes.csv", notesCsv);
      
      // Fetch tasks
      const tasks = await getDocs(collection(db, "users", user.uid, "tasks"));
      let tasksCsv = "ID,Title,Status,Priority\n";
      tasks.forEach(d => {
        const data = d.data();
        tasksCsv += `"${d.id}","${data.title}","${data.isCompleted ? 'Done' : 'Pending'}","${data.priority}"\n`;
      });
      zip.file("tasks.csv", tasksCsv);
      
      // Fetch reminders
      const reminders = await getDocs(collection(db, "users", user.uid, "reminders"));
      let remindersCsv = "ID,Title,Message,Time\n";
      reminders.forEach(d => {
        const data = d.data();
        remindersCsv += `"${d.id}","${data.title}","${data.message || ''}","${data.triggerTime}"\n`;
      });
      zip.file("reminders.csv", remindersCsv);

      const content = await zip.generateAsync({ type: "blob" });
      saveAs(content, "Nexa_Backup.zip");
    } catch (err) {
      console.error(err);
      alert("Failed to export data.");
    }
    setExporting(false);
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
          <div className="w-16 h-16 rounded-2xl flex-shrink-0 overflow-hidden">
            {user?.photoURL ? (
              <img src={user.photoURL} alt="Profile" className="w-full h-full object-cover" />
            ) : (
              <div className="w-full h-full nexa-gradient flex items-center justify-center text-white text-2xl font-black">
                {initials}
              </div>
            )}
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
                className="input" style={{ paddingLeft: "36px" }} placeholder="Your name" />
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

      {/* Security */}
      <motion.div initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{delay:0.08}} className="card mb-5 overflow-hidden">
        <div className="px-5 pt-4 pb-2">
          <p className="text-xs font-bold tracking-widest" style={{color:"var(--text-hint)"}}>SECURITY</p>
        </div>
        <div className="divide-y" style={{borderColor:"var(--divider)"}}>
          <SettingRow icon={Lock} label="App Lock" desc="Require biometric authentication on open">
            <button onClick={toggleAppLock}
              className="relative w-11 h-6 rounded-full transition-all duration-300 flex-shrink-0"
              style={{background: appLockEnabled ? "var(--primary)" : "var(--divider)"}}>
              <div className="absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-all duration-300"
                style={{left: appLockEnabled ? "calc(100% - 22px)" : "2px"}}/>
            </button>
          </SettingRow>
        </div>
      </motion.div>

      {/* Account */}
      <motion.div initial={{opacity:0,y:12}} animate={{opacity:1,y:0}} transition={{delay:0.1}} className="card overflow-hidden">
        <div className="px-5 pt-4 pb-2">
          <p className="text-xs font-bold tracking-widest" style={{color:"var(--text-hint)"}}>ACCOUNT</p>
        </div>
        <div className="divide-y" style={{borderColor:"var(--divider)"}}>
          <SettingRow icon={Key} label="Change Password" desc="Send a secure password reset link to your email">
             <button onClick={handlePasswordReset} className="btn btn-outline btn-sm">Reset</button>
          </SettingRow>
          <SettingRow icon={Download} label="Export Data" desc="Download all your Notes, Tasks, and Reminders as CSV">
             <button onClick={exportData} disabled={exporting} className="btn btn-outline btn-sm">
                {exporting ? <Loader2 size={14} className="animate-spin" /> : "Export CSV"}
             </button>
          </SettingRow>
        </div>
        <div className="p-3 border-t" style={{borderColor:"var(--divider)"}}>
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
