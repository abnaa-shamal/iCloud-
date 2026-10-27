// Gives (or removes) the `admin` claim of ONE Firebase Auth account.
// Run it ONLY from a trusted environment (for example Google Cloud Shell, which is free),
// never from the app and never inside GitHub Actions. No password or key is stored in this file.
//
//   node set-admin-claim.mjs --project <FIREBASE_PROJECT_ID> --email <admin email>
//   node set-admin-claim.mjs --project <FIREBASE_PROJECT_ID> --email <admin email> --remove
//
// Do it once per Firebase project (the real one and the test one are separate projects).
import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';

const args = process.argv.slice(2);
const get = (name) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : undefined;
};
const projectId = get('project');
const email = get('email');
const remove = args.includes('--remove');

if (!projectId || !email || args.some((a) => a.startsWith('--') && !['--project', '--email', '--remove'].includes(a))) {
  console.error('Usage: node set-admin-claim.mjs --project <FIREBASE_PROJECT_ID> --email <admin email> [--remove]');
  process.exit(2);
}

const app = initializeApp({ credential: applicationDefault(), projectId });
const auth = getAuth(app);
const user = await auth.getUserByEmail(email);
const claims = { ...(user.customClaims ?? {}) };
if (remove) delete claims.admin;
else claims.admin = true;
await auth.setCustomUserClaims(user.uid, claims);
console.log(`${remove ? 'Removed' : 'Granted'} the admin claim for ${email} (uid ${user.uid}) in project ${projectId}.`);
console.log('The account must sign out and sign in again (or the app must refresh its token) to see the change.');
