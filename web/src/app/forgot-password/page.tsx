"use client";

import Link from "next/link";
import { useState } from "react";
import { motion } from "framer-motion";
import { sendPasswordResetEmail } from "firebase/auth";
import { auth } from "@/lib/firebase";
import { Loader2, Mail, ArrowLeft, CheckCircle } from "lucide-react";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await sendPasswordResetEmail(auth, email);
      setSent(true);
    } catch {
      setError("Could not send reset email. Check the address and try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen auth-gradient flex items-center justify-center p-4">
      <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }} className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-white/15 backdrop-blur-sm mb-4 border border-white/20">
            <span className="text-3xl font-black text-white">N</span>
          </div>
          <h1 className="text-3xl font-bold text-white">Reset password</h1>
          <p className="text-white/70 mt-1 text-sm">We'll send you a link to reset it</p>
        </div>

        <div className="bg-white/10 backdrop-blur-xl border border-white/20 rounded-3xl p-8 shadow-2xl">
          {sent ? (
            <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="text-center space-y-4">
              <div className="flex justify-center">
                <CheckCircle className="w-16 h-16 text-green-400" />
              </div>
              <h3 className="text-xl font-bold text-white">Email sent!</h3>
              <p className="text-white/70 text-sm">Check your inbox at <strong className="text-white">{email}</strong> for the reset link.</p>
              <button onClick={() => { setSent(false); setEmail(""); }} className="text-white/60 hover:text-white text-sm underline">Resend email</button>
              <div className="pt-2">
                <Link href="/login" className="btn-primary inline-block px-6 py-2 text-sm">Back to Login</Link>
              </div>
            </motion.div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              {error && (
                <div className="bg-red-500/20 border border-red-400/30 text-red-200 text-sm px-4 py-3 rounded-xl">{error}</div>
              )}
              <div>
                <label className="text-white/80 text-sm font-medium mb-1.5 block">Email address</label>
                <div className="relative">
                  <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/40" />
                  <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@email.com"
                    className="w-full bg-white/10 border border-white/20 text-white placeholder-white/30 rounded-xl pl-10 pr-4 py-3 text-sm focus:outline-none focus:border-white/50 transition-all" />
                </div>
              </div>
              <button type="submit" disabled={loading} className="w-full btn-primary py-3 text-sm font-semibold">
                {loading ? <span className="flex items-center justify-center gap-2"><Loader2 className="w-4 h-4 animate-spin" /> Sending...</span> : "Send Reset Link"}
              </button>
              <Link href="/login" className="flex items-center justify-center gap-1 text-white/60 hover:text-white text-sm transition-colors mt-2">
                <ArrowLeft className="w-4 h-4" /> Back to Login
              </Link>
            </form>
          )}
        </div>
      </motion.div>
    </div>
  );
}
