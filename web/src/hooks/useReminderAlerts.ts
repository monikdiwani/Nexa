"use client";
import { useEffect, useRef } from "react";
import { collection, query, orderBy, onSnapshot } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { useAuth } from "@/context/AuthContext";
import { playAlarm } from "@/lib/reminderAlarm";

interface Reminder {
  id: string;
  title: string;
  message: string;
  triggerTime: number;
  priority: string;
  isCompleted: boolean;
  isSnoozed: boolean;
}

/**
 * Monitors reminders in real-time and fires browser notification + alarm sound
 * when a reminder becomes due. Runs across all dashboard pages.
 */
export function useReminderAlerts() {
  const { user } = useAuth();
  const firedRef = useRef<Set<string>>(new Set());
  const permissionRef = useRef<boolean>(false);

  // Request notification permission once
  useEffect(() => {
    if (typeof Notification !== "undefined" && Notification.permission === "default") {
      Notification.requestPermission().then((p) => {
        permissionRef.current = p === "granted";
      });
    } else {
      permissionRef.current = Notification.permission === "granted";
    }
  }, []);

  useEffect(() => {
    if (!user) return;

    const q = query(
      collection(db, "users", user.uid, "reminders"),
      orderBy("triggerTime", "asc")
    );

    const unsub = onSnapshot(q, (snap) => {
      const now = Date.now();
      snap.docs.forEach((d) => {
        const r = { id: d.id, ...d.data() } as Reminder;

        // Only fire if: not completed, not snoozed, within last 2 min window, not already fired
        const overdue = r.triggerTime <= now && r.triggerTime >= now - 2 * 60 * 1000;
        if (r.isCompleted || r.isSnoozed || !overdue || firedRef.current.has(r.id)) return;

        // Mark as fired so we don't repeat
        firedRef.current.add(r.id);

        // Play alarm sound
        playAlarm(r.priority);

        // Browser notification
        if (permissionRef.current) {
          const emoji =
            r.priority === "HIGH" ? "🚨" : r.priority === "MEDIUM" ? "🔔" : "💬";
          new Notification(`${emoji} ${r.title}`, {
            body: r.message || "Reminder from Nexa",
            icon: "/nexa-logo.svg",
            badge: "/nexa-logo.svg",
            tag: r.id,
            requireInteraction: r.priority === "HIGH",
          });
        }
      });
    });

    // Check every 30 seconds for newly due reminders
    const interval = setInterval(() => {
      // Clear stale fired IDs older than 5 min
      firedRef.current = new Set();
    }, 5 * 60 * 1000);

    return () => {
      unsub();
      clearInterval(interval);
    };
  }, [user]);
}
