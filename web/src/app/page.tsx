"use client";

import Link from "next/link";
import { useAuth } from "@/context/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { motion } from "framer-motion";
import {
  DollarSign, StickyNote, CheckSquare, Bell, BarChart2,
  Users, Smartphone, Globe, Shield, Zap, RefreshCw,
  ArrowRight, Star, ChevronRight
} from "lucide-react";

const features = [
  {
    icon: DollarSign,
    title: "Smart Cashbook",
    desc: "Track every rupee with dual cash/bank entry. Auto-read SMS transactions, scan bills, and get instant reports.",
    gradient: "linear-gradient(135deg,#7C83F7,#5C6BC0)",
    tag: "FINANCE",
  },
  {
    icon: StickyNote,
    title: "Rich Notes",
    desc: "Samsung Notes-style editor with bold, italic, colors, checklists, images, and multiple page styles.",
    gradient: "linear-gradient(135deg,#FFA726,#E65100)",
    tag: "NOTES",
  },
  {
    icon: CheckSquare,
    title: "Tasks & Subtasks",
    desc: "Manage tasks with priorities, due dates, subtasks, recurring schedules, and importance flags.",
    gradient: "linear-gradient(135deg,#43A047,#2E7D32)",
    tag: "PRODUCTIVITY",
  },
  {
    icon: Bell,
    title: "Smart Reminders",
    desc: "Exact-time alarms with categories (Bills, Meetings, Medicine), snooze, and recurring patterns.",
    gradient: "linear-gradient(135deg,#26C6DA,#00838F)",
    tag: "REMINDERS",
  },
  {
    icon: Users,
    title: "Group Ledgers",
    desc: "Share ledger books with friends and family. Split expenses equally, by percentage, or custom amounts.",
    gradient: "linear-gradient(135deg,#EF5350,#C62828)",
    tag: "COLLABORATION",
  },
  {
    icon: BarChart2,
    title: "Reports & Analytics",
    desc: "Visual spending charts, category breakdowns, task completion gauges, and daily averages.",
    gradient: "linear-gradient(135deg,#9C27B0,#6A1B9A)",
    tag: "ANALYTICS",
  },
];

const whyNexa = [
  { icon: Zap, title: "Lightning Fast", desc: "Offline-first with real-time sync. Works even without internet." },
  { icon: RefreshCw, title: "Cross-Platform Sync", desc: "Change anything on web, see it instantly on your Android app — and vice versa." },
  { icon: Shield, title: "Private & Secure", desc: "Your data is protected by Firebase with end-to-end security rules. Only you can access it." },
  { icon: Globe, title: "Use Anywhere", desc: "Access your data from any browser on any device — laptop, tablet, or phone." },
  { icon: Smartphone, title: "App + Web", desc: "Install the Android app for notifications and offline use. Open the website for a full desktop experience." },
  { icon: Star, title: "All-in-One", desc: "Replace 5 separate apps with one beautifully designed platform that actually talks to itself." },
];

const steps = [
  { step: "01", title: "Create Your Account", desc: "Sign up with Google in one click — the same account works on your Android app and this website." },
  { step: "02", title: "All Your Data, Instantly", desc: "Everything you've saved in the Android app (notes, tasks, money) appears immediately on the website." },
  { step: "03", title: "Make Changes Anywhere", desc: "Edit a note on the website, it syncs to your phone. Add an expense on your phone, see it here instantly." },
];

export default function LandingPage() {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && user) router.push("/dashboard");
  }, [user, loading, router]);

  return (
    <div className="min-h-screen" style={{ background: "var(--bg)" }}>
      {/* Navbar */}
      <nav className="fixed top-0 left-0 right-0 z-50 border-b"
        style={{ background: "rgba(255,255,255,0.85)", backdropFilter: "blur(20px)", borderColor: "var(--divider)" }}>
        <div className="max-w-6xl mx-auto px-4 sm:px-6 flex items-center justify-between h-14">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl nexa-gradient flex items-center justify-center shadow-md">
              <span className="text-sm font-black text-white">N</span>
            </div>
            <span className="font-bold text-base" style={{ color: "var(--text-primary)" }}>Nexa</span>
          </div>
          <div className="hidden sm:flex items-center gap-6 text-sm font-medium" style={{ color: "var(--text-secondary)" }}>
            <a href="#features" className="hover:text-indigo-600 transition-colors">Features</a>
            <a href="#why" className="hover:text-indigo-600 transition-colors">Why Nexa</a>
            <a href="#how" className="hover:text-indigo-600 transition-colors">How it Works</a>
          </div>
          <div className="flex items-center gap-2">
            <Link href="/login" className="btn-secondary text-sm px-4 py-2">Sign In</Link>
            <Link href="/signup" className="btn-primary text-sm px-4 py-2">Get Started</Link>
          </div>
        </div>
      </nav>

      {/* Hero */}
      <section className="pt-28 pb-20 px-4 relative overflow-hidden">
        {/* Background decoration */}
        <div className="absolute inset-0 pointer-events-none overflow-hidden">
          <div className="absolute top-20 left-1/2 -translate-x-1/2 w-[600px] h-[600px] rounded-full opacity-10"
            style={{ background: "radial-gradient(circle, #5C6BC0, transparent)" }} />
          <div className="absolute -top-10 -right-20 w-80 h-80 rounded-full opacity-5" style={{ background: "#00ACC1" }} />
          <div className="absolute -bottom-10 -left-20 w-80 h-80 rounded-full opacity-5" style={{ background: "#5C6BC0" }} />
        </div>

        <div className="max-w-4xl mx-auto text-center relative z-10">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full border text-sm font-medium mb-6"
              style={{ background: "var(--primary-surface)", color: "var(--primary)", borderColor: "var(--primary-light)" }}>
              <span className="w-2 h-2 rounded-full pulse-dot" style={{ background: "var(--primary)" }} />
              Available on Android + Web · Real-time sync
            </div>

            <h1 className="text-4xl sm:text-5xl md:text-6xl font-black mb-6 leading-tight" style={{ color: "var(--text-primary)" }}>
              Your entire life,{" "}
              <span className="gradient-text">organized</span>{" "}
              in one place.
            </h1>

            <p className="text-lg sm:text-xl max-w-2xl mx-auto mb-8 leading-relaxed" style={{ color: "var(--text-secondary)" }}>
              Nexa combines a smart cashbook, rich notes, task manager, and reminder system into one
              beautifully designed app — synced across your phone and browser in real-time.
            </p>

            <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
              <Link href="/signup" className="btn-primary px-7 py-3.5 text-base font-semibold flex items-center gap-2 w-full sm:w-auto justify-center">
                Start for free <ArrowRight className="w-4 h-4" />
              </Link>
              <Link href="/login" className="btn-secondary px-7 py-3.5 text-base font-semibold flex items-center gap-2 w-full sm:w-auto justify-center">
                Sign in to your account
              </Link>
            </div>

            <p className="mt-4 text-sm" style={{ color: "var(--text-hint)" }}>
              Free to use · No credit card required · Same account as your Android app
            </p>
          </motion.div>

          {/* Hero visual */}
          <motion.div initial={{ opacity: 0, y: 40 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.2 }}
            className="mt-14 relative">
            <div className="rounded-2xl border overflow-hidden shadow-2xl mx-auto max-w-3xl"
              style={{ background: "var(--surface)", borderColor: "var(--divider)" }}>
              {/* Fake browser bar */}
              <div className="flex items-center gap-2 px-4 py-3 border-b" style={{ background: "var(--bg)", borderColor: "var(--divider)" }}>
                <div className="flex gap-1.5">
                  <div className="w-3 h-3 rounded-full bg-red-400" />
                  <div className="w-3 h-3 rounded-full bg-yellow-400" />
                  <div className="w-3 h-3 rounded-full bg-green-400" />
                </div>
                <div className="flex-1 mx-4 h-6 rounded-md flex items-center px-3 text-xs" style={{ background: "var(--surface)", color: "var(--text-hint)" }}>
                  nexa.web.app/dashboard
                </div>
              </div>
              {/* Mock dashboard */}
              <div className="p-5" style={{ background: "var(--bg)" }}>
                <div className="h-8 rounded-xl mb-4 nexa-gradient opacity-80" />
                <div className="grid grid-cols-3 gap-3 mb-4">
                  {[1,2,3].map(i => <div key={i} className="h-16 rounded-xl" style={{ background: "var(--surface)" }} />)}
                </div>
                <div className="grid grid-cols-2 gap-3">
                  {[1,2,3,4].map(i => <div key={i} className="h-12 rounded-xl" style={{ background: "var(--surface)" }} />)}
                </div>
              </div>
            </div>
          </motion.div>
        </div>
      </section>

      {/* Features */}
      <section id="features" className="py-20 px-4">
        <div className="max-w-6xl mx-auto">
          <div className="text-center mb-12">
            <div className="chip mb-3" style={{ background: "var(--primary-surface)", color: "var(--primary)" }}>FEATURES</div>
            <h2 className="text-3xl sm:text-4xl font-bold mb-4" style={{ color: "var(--text-primary)" }}>
              Everything you need, nothing you don&apos;t
            </h2>
            <p className="text-lg max-w-xl mx-auto" style={{ color: "var(--text-secondary)" }}>
              Six powerful modules that work together and sync across your devices.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {features.map(({ icon: Icon, title, desc, gradient, tag }, i) => (
              <motion.div key={title} initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, delay: i * 0.07 }} viewport={{ once: true }}
                className="card-hover rounded-2xl p-6 relative overflow-hidden group cursor-pointer"
                style={{ background: "var(--surface)", boxShadow: "var(--shadow-card)" }}>
                {/* Gradient accent */}
                <div className="absolute top-0 right-0 w-32 h-32 rounded-full opacity-5 group-hover:opacity-10 transition-opacity"
                  style={{ background: gradient, transform: "translate(40%, -40%)" }} />
                <div className="w-12 h-12 rounded-2xl flex items-center justify-center mb-4 relative"
                  style={{ background: gradient }}>
                  <Icon className="w-6 h-6 text-white" />
                </div>
                <div className="chip mb-2" style={{ background: "var(--bg)", color: "var(--text-hint)", fontSize: 10 }}>{tag}</div>
                <h3 className="font-bold text-base mb-2" style={{ color: "var(--text-primary)" }}>{title}</h3>
                <p className="text-sm leading-relaxed" style={{ color: "var(--text-secondary)" }}>{desc}</p>
                <div className="mt-4 flex items-center gap-1 text-sm font-semibold" style={{ color: "var(--primary)" }}>
                  Learn more <ChevronRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* Why Nexa */}
      <section id="why" className="py-20 px-4" style={{ background: "var(--surface)" }}>
        <div className="max-w-6xl mx-auto">
          <div className="text-center mb-12">
            <div className="chip mb-3" style={{ background: "var(--primary-surface)", color: "var(--primary)" }}>WHY NEXA</div>
            <h2 className="text-3xl sm:text-4xl font-bold mb-4" style={{ color: "var(--text-primary)" }}>
              Why should you use Nexa?
            </h2>
            <p className="text-lg max-w-xl mx-auto" style={{ color: "var(--text-secondary)" }}>
              Not just another productivity app. Nexa is built different.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {whyNexa.map(({ icon: Icon, title, desc }, i) => (
              <motion.div key={title} initial={{ opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.35, delay: i * 0.06 }} viewport={{ once: true }}
                className="flex gap-4 p-5 rounded-2xl" style={{ background: "var(--bg)" }}>
                <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: "var(--primary-surface)" }}>
                  <Icon className="w-5 h-5" style={{ color: "var(--primary)" }} />
                </div>
                <div>
                  <h3 className="font-semibold mb-1" style={{ color: "var(--text-primary)" }}>{title}</h3>
                  <p className="text-sm leading-relaxed" style={{ color: "var(--text-secondary)" }}>{desc}</p>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* How it Works */}
      <section id="how" className="py-20 px-4">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-12">
            <div className="chip mb-3" style={{ background: "var(--primary-surface)", color: "var(--primary)" }}>HOW IT WORKS</div>
            <h2 className="text-3xl sm:text-4xl font-bold mb-4" style={{ color: "var(--text-primary)" }}>
              Set up in 60 seconds
            </h2>
          </div>
          <div className="space-y-6">
            {steps.map(({ step, title, desc }, i) => (
              <motion.div key={step} initial={{ opacity: 0, x: -20 }} whileInView={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.4, delay: i * 0.1 }} viewport={{ once: true }}
                className="flex gap-5 p-6 rounded-2xl card-hover" style={{ background: "var(--surface)", boxShadow: "var(--shadow-card)" }}>
                <div className="w-12 h-12 rounded-2xl flex items-center justify-center text-white font-black text-lg flex-shrink-0 nexa-gradient">
                  {step}
                </div>
                <div>
                  <h3 className="font-bold text-lg mb-1" style={{ color: "var(--text-primary)" }}>{title}</h3>
                  <p style={{ color: "var(--text-secondary)" }}>{desc}</p>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-20 px-4">
        <motion.div initial={{ opacity: 0, scale: 0.97 }} whileInView={{ opacity: 1, scale: 1 }}
          viewport={{ once: true }} transition={{ duration: 0.4 }}
          className="max-w-3xl mx-auto text-center rounded-3xl p-10 relative overflow-hidden"
          style={{ background: "linear-gradient(135deg, #3949AB 0%, #1A237E 100%)" }}>
          <div className="absolute inset-0 overflow-hidden">
            <div className="absolute top-0 right-0 w-48 h-48 rounded-full opacity-10" style={{ background: "white", transform: "translate(30%,-30%)" }} />
            <div className="absolute bottom-0 left-0 w-36 h-36 rounded-full opacity-10" style={{ background: "white", transform: "translate(-30%,30%)" }} />
          </div>
          <div className="relative z-10">
            <h2 className="text-3xl sm:text-4xl font-bold text-white mb-4">Ready to get organized?</h2>
            <p className="text-blue-200 text-lg mb-8 max-w-xl mx-auto">
              Join Nexa for free. Sign up with Google, and your Android app data appears instantly.
            </p>
            <div className="flex flex-col sm:flex-row gap-3 justify-center">
              <Link href="/signup"
                className="flex items-center justify-center gap-2 bg-white text-indigo-700 font-semibold px-8 py-3.5 rounded-xl hover:bg-gray-50 transition-all hover:shadow-lg">
                Create free account <ArrowRight className="w-4 h-4" />
              </Link>
              <Link href="/login"
                className="flex items-center justify-center gap-2 bg-white/15 text-white font-semibold px-8 py-3.5 rounded-xl border border-white/30 hover:bg-white/25 transition-all">
                Already have an account
              </Link>
            </div>
          </div>
        </motion.div>
      </section>

      {/* Footer */}
      <footer className="py-8 px-4 border-t" style={{ borderColor: "var(--divider)" }}>
        <div className="max-w-6xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg nexa-gradient flex items-center justify-center">
              <span className="text-xs font-black text-white">N</span>
            </div>
            <span className="font-semibold text-sm" style={{ color: "var(--text-secondary)" }}>Nexa</span>
          </div>
          <p className="text-xs" style={{ color: "var(--text-hint)" }}>
            © {new Date().getFullYear()} Nexa. Built with ❤️ · Powered by Firebase
          </p>
          <div className="flex gap-4 text-xs" style={{ color: "var(--text-hint)" }}>
            <a href="#" className="hover:underline">Privacy</a>
            <a href="#" className="hover:underline">Terms</a>
          </div>
        </div>
      </footer>
    </div>
  );
}
