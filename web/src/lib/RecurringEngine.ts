import { collection, query, where, getDocs, addDoc, updateDoc, doc } from "firebase/firestore";
import { db } from "./firebase";

export async function runRecurringEngine(userId: string) {
  try {
    const now = Date.now();

    // Handle Tasks
    const tasksQuery = query(collection(db, "users", userId, "tasks"), where("isRecurring", "==", true));
    const tasksSnap = await getDocs(tasksQuery);
    for (const docSnap of tasksSnap.docs) {
      const data = docSnap.data();
      if (!data.isDeleted && data.dueDate && data.dueDate < now) {
        // Mark old as non-recurring to stop spawning
        await updateDoc(doc(db, "users", userId, "tasks", docSnap.id), { isRecurring: false });
        
        // Calculate next date
        const nextDate = calculateNextDate(data.dueDate, data.recurringPattern);
        
        // Spawn new
        await addDoc(collection(db, "users", userId, "tasks"), {
          ...data,
          dueDate: nextDate,
          isCompleted: false,
          createdAt: now,
          updatedAt: now
        });
      }
    }

    // Handle Reminders
    const remindersQuery = query(collection(db, "users", userId, "reminders"), where("isRecurring", "==", true));
    const remindersSnap = await getDocs(remindersQuery);
    for (const docSnap of remindersSnap.docs) {
      const data = docSnap.data();
      if (!data.isDeleted && data.triggerAt && data.triggerAt < now) {
        await updateDoc(doc(db, "users", userId, "reminders", docSnap.id), { isRecurring: false });
        
        const nextDate = calculateNextDate(data.triggerAt, data.recurringPattern);
        
        await addDoc(collection(db, "users", userId, "reminders"), {
          ...data,
          triggerAt: nextDate,
          createdAt: now,
          updatedAt: now
        });
      }
    }

    // Handle Cashbook entries
    // Since cashbooks are top-level and user is a member, this is more complex.
    // For simplicity, we just fetch cashbooks where user is admin.
    const booksQuery = query(collection(db, "cashbooks"), where(`members.${userId}`, "in", ["ADMIN", "EDITOR"]));
    const booksSnap = await getDocs(booksQuery);
    for (const bookSnap of booksSnap.docs) {
      const entriesQuery = query(collection(db, "cashbooks", bookSnap.id, "entries"), where("isRecurring", "==", true));
      const entriesSnap = await getDocs(entriesQuery);
      
      for (const entrySnap of entriesSnap.docs) {
        const data = entrySnap.data();
        if (data.date < now) {
          await updateDoc(doc(db, "cashbooks", bookSnap.id, "entries", entrySnap.id), { isRecurring: false });
          const nextDate = calculateNextDate(data.date, data.recurringPattern);
          
          await addDoc(collection(db, "cashbooks", bookSnap.id, "entries"), {
            ...data,
            date: nextDate,
            createdAt: now,
            lastModifiedAt: now
          });
        }
      }
    }
  } catch (err) {
    console.error("Recurring Engine failed:", err);
  }
}

function calculateNextDate(currentMs: number, pattern: string): number {
  const d = new Date(currentMs);
  if (pattern === "DAILY") d.setDate(d.getDate() + 1);
  else if (pattern === "WEEKLY") d.setDate(d.getDate() + 7);
  else if (pattern === "MONTHLY") d.setMonth(d.getMonth() + 1);
  else if (pattern === "YEARLY") d.setFullYear(d.getFullYear() + 1);
  return d.getTime();
}
