"use client";
import { useState, useEffect } from "react";
import { WifiOff } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";

export default function NetworkBanner() {
  const [isOnline, setIsOnline] = useState(true);

  useEffect(() => {
    // Only run on client
    if (typeof window !== "undefined") {
      setIsOnline(navigator.onLine);

      const handleOnline = () => setIsOnline(true);
      const handleOffline = () => setIsOnline(false);

      window.addEventListener("online", handleOnline);
      window.addEventListener("offline", handleOffline);

      return () => {
        window.removeEventListener("online", handleOnline);
        window.removeEventListener("offline", handleOffline);
      };
    }
  }, []);

  return (
    <AnimatePresence>
      {!isOnline && (
        <motion.div
          initial={{ opacity: 0, y: -50 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -50 }}
          transition={{ type: "spring", stiffness: 300, damping: 25 }}
          className="fixed top-0 left-0 right-0 z-[100] flex items-center justify-center gap-2 py-2.5 px-4 shadow-md"
          style={{ background: "var(--negative)", color: "white" }}
        >
          <WifiOff size={16} />
          <p className="text-sm font-semibold tracking-wide">
            You are offline. Changes will sync when connection is restored.
          </p>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
