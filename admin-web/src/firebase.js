import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { initializeFirestore } from 'firebase/firestore';

const env = import.meta.env;
export const configured = Boolean(env.VITE_FIREBASE_API_KEY && env.VITE_FIREBASE_PROJECT_ID && env.VITE_FIREBASE_APP_ID);

const app = initializeApp({
  apiKey: env.VITE_FIREBASE_API_KEY || 'missing',
  authDomain: env.VITE_FIREBASE_AUTH_DOMAIN || `${env.VITE_FIREBASE_PROJECT_ID}.firebaseapp.com`,
  projectId: env.VITE_FIREBASE_PROJECT_ID || 'missing',
  appId: env.VITE_FIREBASE_APP_ID || 'missing',
});

export const auth = getAuth(app);
// Some phone networks block Firestore's default streaming connection; auto-detect falls back to long polling so data still loads.
export const db = initializeFirestore(app, { experimentalAutoDetectLongPolling: true });
