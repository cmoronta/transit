/**
 * One-time helper script to grant the { admin: true } Firebase Auth custom claim
 * to your personal Google account UID so only your account can open the hidden
 * Admin Diagnostics Sheet on the phone or call GET /v1/admin/quota.
 *
 * Usage:
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json npx tsx scripts/set-admin-claim.ts <YOUR_FIREBASE_UID>
 */

const uid = process.argv[2];
if (!uid) {
  console.error("Usage: npx tsx scripts/set-admin-claim.ts <FIREBASE_UID>");
  process.exit(1);
}

console.log(`To grant { admin: true } to UID=${uid} using Firebase CLI or firebase-admin:`);
console.log(`  1. In Firebase Console -> Authentication -> Users, copy your UID (${uid}).`);
console.log(`  2. Run with firebase-admin: admin.auth().setCustomUserClaims("${uid}", { admin: true })`);
