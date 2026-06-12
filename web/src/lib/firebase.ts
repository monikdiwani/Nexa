import { initializeApp, getApps } from "firebase/app";
import { getAuth, GoogleAuthProvider } from "firebase/auth";
import { getFirestore } from "firebase/firestore";
import { getStorage } from "firebase/storage";

const firebaseConfig = {
  apiKey: "AIzaSyAV1sz_aOM1On_WADxnfYMOyDxsvRQpI0E",
  authDomain: "fairshare-d973c.firebaseapp.com",
  projectId: "fairshare-d973c",
  storageBucket: "fairshare-d973c.firebasestorage.app",
  messagingSenderId: "681079299904",
  appId: "1:681079299904:web:8e5cb428e1ac5d72ff24a5",
  measurementId: "G-6RXSFZ3E54",
};

const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];

export const auth = getAuth(app);
export const db = getFirestore(app);
export const storage = getStorage(app);
export const googleProvider = new GoogleAuthProvider();

export default app;
