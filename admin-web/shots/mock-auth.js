// Pretends a signed-in admin so the real dashboard code renders. `#login` in the URL shows the login screen instead.
export function onAuthStateChanged(_auth, cb) {
  const loggedOut = typeof location !== 'undefined' && location.hash === '#login';
  setTimeout(() => cb(loggedOut ? null : { email: 'admin@abnaa-alshamal.test', getIdTokenResult: async () => ({ claims: { admin: true } }) }), 0);
  return () => {};
}
export const signInWithEmailAndPassword = async () => {};
export const signOut = async () => {};
export const getAuth = () => ({});
