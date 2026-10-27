// Automatic tests for firestore.rules (run on the local Firestore emulator, never on real data).
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { test, before, after, beforeEach } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, query, where, setDoc, updateDoc, addDoc, deleteDoc, collection, writeBatch } from 'firebase/firestore';

const mail = (p) => `${p}@mashwarak.app`;
const capMail = (p) => `${p}@captain.mashwarak.app`;
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-mashwarak',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => { await env.cleanup(); });

const user = (role, status, phone, name = 'اسم تجريبي') =>
  ({ role, status, name, phone, carType: 'شانجان', carColor: 'ابيض', carPlate: '123', createdAt: 1 });

const trip = (over = {}) => ({
  captainUid: 'cap', captainName: 'كابتن', captainPhone: '0791000001', carText: 'سيارة',
  from: 'الشجرة', to: 'إربد', road: '', departAt: 2000000000000, seatsTotal: 4, seatsFree: 2,
  price: 1.0, acceptsParcels: false, note: '', status: 'open', cancelReason: '', createdAt: 1, ...over,
});

const booking = (over = {}) => ({
  tripId: 't1', captainUid: 'cap', captainName: 'كابتن', captainPhone: '0791000001',
  passengerUid: 'pax', passengerName: 'راكب', passengerPhone: '0792000001', seats: 1, dropoff: 'x',
  tripSummary: 'a ← b', departAt: 2000000000000, status: 'pending', reason: '', createdAt: 1, updatedAt: 1, ...over,
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/cap'), user('captain', 'approved', '0791000001', 'كابتن'));
    await setDoc(doc(db, 'users/cap2'), user('captain', 'pending', '0791000002'));
    await setDoc(doc(db, 'users/capR'), user('captain', 'rejected', '0791000003'));
    await setDoc(doc(db, 'users/capM'), { ...user('captain', 'approved', '0791000006', 'كابتن كبير'), maxSeats: 6 }); // admin raised his limit to 6
    await setDoc(doc(db, 'users/capRS'), { ...user('captain', 'approved', '0791000005'), riderSuspended: true }); // approved captain, suspended as rider
    await setDoc(doc(db, 'users/paxRS'), { ...user('passenger', 'active', '0792000004'), riderSuspended: true });
    await setDoc(doc(db, 'users/pax'), user('passenger', 'active', '0792000001', 'راكب'));
    await setDoc(doc(db, 'users/paxS'), user('passenger', 'suspended', '0792000002'));
    await setDoc(doc(db, 'trips/t1'), trip());
    await setDoc(doc(db, 'trips/tFull'), trip({ status: 'full', seatsFree: 0 }));
    await setDoc(doc(db, 'trips/tCancelled'), trip({ status: 'cancelled' }));
    await setDoc(doc(db, 'bookings/b1'), booking());
    await setDoc(doc(db, 'bookings/bAcc'), booking({ status: 'accepted' }));
  });
});

const as = (uid, phone) => env.authenticatedContext(uid, { email: mail(phone) }).firestore();
const asCap = () => as('cap', '0791000001');
const asCapR = () => as('capR', '0791000003');
const asPax = () => as('pax', '0792000001');
const asCapM = () => as('capM', '0791000006');
const asCapRS = () => as('capRS', '0791000005');
const asPaxRS = () => as('paxRS', '0792000004');
const asPaxS = () => as('paxS', '0792000002');
// The admin is identified ONLY by the custom claim `admin: true` (set from a trusted environment).
const asAdmin = () => env.authenticatedContext('admin', { admin: true, email: 'admin@example.test' }).firestore();
const asOther = () => as('other', '0793000001');
const anon = () => env.unauthenticatedContext().firestore();

// ===== Not signed in =====
test('not signed in: nothing readable', async () => {
  await assertFails(getDoc(doc(anon(), 'trips/t1')));
  await assertFails(getDoc(doc(anon(), 'users/cap')));
});

// ===== Users: create =====
const newUser = (over = {}) => ({ role: 'passenger', status: 'active', name: 'علي أحمد', phone: '0791234567',
  carType: '', carColor: '', carPlate: '', createdAt: 5, ...over });
const asNew = (phone = '0791234567') => as('new', phone);
const asNewCaptain = (phone = '0791234567', uid = 'new2') => env.authenticatedContext(uid, { email: capMail(phone) }).firestore();

test('create: valid passenger and valid captain(pending)', async () => {
  await assertSucceeds(setDoc(doc(asNew(), 'users/new'), newUser()));
  await assertSucceeds(setDoc(doc(asNewCaptain('0791234568'), 'users/new2'),
    newUser({ phone: '0791234568', role: 'captain', status: 'pending' })));
});
test('create: rider and captain accounts are separate — wrong email domain for the role is rejected', async () => {
  // a captain profile with the rider-app email, and a passenger profile with the captain-app email
  await assertFails(setDoc(doc(as('x1', '0791234569'), 'users/x1'), newUser({ phone: '0791234569', role: 'captain', status: 'pending' })));
  await assertFails(setDoc(doc(asNewCaptain('0791234570'), 'users/x2'), newUser({ phone: '0791234570' })));
  // the same number may hold one account of each kind (different uids)
  await assertSucceeds(setDoc(doc(as('r1', '0791234571'), 'users/r1'), newUser({ phone: '0791234571' })));
  await assertSucceeds(setDoc(doc(asNewCaptain('0791234571', 'c1'), 'users/c1'),
    newUser({ phone: '0791234571', role: 'captain', status: 'pending' })));
});
for (const [label, phone] of [['076 prefix', '0761234567'], ['9 digits', '079123456'], ['11 digits', '07912345678'],
  ['letters', '07912abc67'], ['no leading 0', '791234567'], ['intl format', '+962791234567']]) {
  test(`create: invalid phone rejected (${label})`, async () => {
    await assertFails(setDoc(doc(asNew(phone), 'users/new'), newUser({ phone })));
  });
}
for (const p of ['0771234567', '0781234567', '0791234567']) {
  test(`create: ${p.slice(0, 3)} accepted`, async () => {
    await assertSucceeds(setDoc(doc(asNew(p), 'users/new'), newUser({ phone: p })));
  });
}
test('create: email must equal phone@mashwarak.app exactly', async () => {
  await assertFails(setDoc(doc(env.authenticatedContext('new', { email: '0791234567@evil.com' }).firestore(), 'users/new'), newUser()));
  await assertFails(setDoc(doc(as('new', '0791234568'), 'users/new'), newUser())); // phone field != email
});
test('create: cannot set own role/status to privileged values', async () => {
  await assertFails(setDoc(doc(asNew(), 'users/new'), newUser({ role: 'admin' })));
  await assertFails(setDoc(doc(asNew(), 'users/new'), newUser({ role: 'captain', status: 'approved' })));
  await assertFails(setDoc(doc(asNew(), 'users/new'), newUser({ status: 'approved' })));
  await assertFails(setDoc(doc(asNew(), 'users/new'), newUser({ status: 'suspended' })));
});
test('create: extra field, other uid, bad name rejected', async () => {
  await assertFails(setDoc(doc(asNew(), 'users/new'), newUser({ isAdmin: true })));
  await assertFails(setDoc(doc(asNew(), 'users/someoneElse'), newUser()));
  await assertFails(setDoc(doc(asNew(), 'users/new'), newUser({ name: 'ع' })));
});

// ===== Users: update =====
test('update: user cannot change role/status/phone, can change name', async () => {
  await assertFails(updateDoc(doc(asPax(), 'users/pax'), { role: 'captain' }));
  await assertFails(updateDoc(doc(asPax(), 'users/pax'), { status: 'approved' }));
  await assertFails(updateDoc(doc(asPax(), 'users/pax'), { phone: '0799999999' }));
  await assertFails(updateDoc(doc(asPax(), 'users/pax'), { isAdmin: true }));
  await assertSucceeds(updateDoc(doc(asPax(), 'users/pax'), { name: 'اسم جديد' }));
});
test('update: suspended passenger cannot un-suspend himself; others cannot touch him', async () => {
  await assertFails(updateDoc(doc(asPaxS(), 'users/paxS'), { status: 'active' }));
  await assertFails(updateDoc(doc(asPax(), 'users/paxS'), { name: 'x y' }));
});
test('update: admin changes status (only to known values); phone/role never', async () => {
  await assertSucceeds(updateDoc(doc(asAdmin(), 'users/cap2'), { status: 'approved' }));
  await assertSucceeds(updateDoc(doc(asAdmin(), 'users/pax'), { status: 'suspended' }));
  await assertFails(updateDoc(doc(asAdmin(), 'users/pax'), { status: 'god' }));
  await assertFails(updateDoc(doc(asAdmin(), 'users/pax'), { phone: '0790000000' }));
  await assertFails(updateDoc(doc(asAdmin(), 'users/pax'), { role: 'captain' }));
});
test('update: a fake "admin" with another email cannot approve', async () => {
  await assertFails(updateDoc(doc(asOther(), 'users/cap2'), { status: 'approved' }));
});

// ===== Users: read (only owner + admin) =====
test('read users: own profile ok, admin reads all, nobody else', async () => {
  await assertSucceeds(getDoc(doc(asPax(), 'users/pax')));
  await assertSucceeds(getDoc(doc(asCap(), 'users/cap')));
  await assertSucceeds(getDoc(doc(asAdmin(), 'users/paxS')));
  await assertSucceeds(getDoc(doc(asAdmin(), 'users/cap')));
});
test('read users: passenger/captain/other cannot read someone else (incl. captains)', async () => {
  await assertFails(getDoc(doc(asPax(), 'users/cap')));
  await assertFails(getDoc(doc(asPax(), 'users/paxS')));
  await assertFails(getDoc(doc(asOther(), 'users/cap')));
  await assertFails(getDoc(doc(asCap(), 'users/pax')));
});
test('read users: listing all captains is denied for non-admin, allowed for admin', async () => {
  const q = (db) => getDocs(query(collection(db, 'users'), where('role', '==', 'captain')));
  await assertFails(q(asPax()));
  await assertFails(q(asCap()));
  await assertSucceeds(q(asAdmin()));
});
test('read users: a brand-new user can read his own (missing) profile document', async () => {
  await assertSucceeds(getDoc(doc(as('fresh', '0791999999'), 'users/fresh')));
});

// ===== Users: frozen name/car after approval =====
test('freeze: approved captain cannot change name or car details', async () => {
  for (const f of [{ name: 'اسم جديد' }, { carType: 'x' }, { carColor: 'x' }, { carPlate: '999' }]) {
    await assertFails(updateDoc(doc(asCap(), 'users/cap'), f));
  }
});
test('freeze: pending/rejected captain and passenger can still edit before approval', async () => {
  await assertSucceeds(updateDoc(doc(as('cap2', '0791000002'), 'users/cap2'), { carPlate: '777' }));
  await assertSucceeds(updateDoc(doc(asCapR(), 'users/capR'), { carColor: 'احمر' }));
  await assertSucceeds(updateDoc(doc(asPax(), 'users/pax'), { name: 'اسم راكب' }));
});
test('freeze: admin alone can edit an approved captain name/car, but not phone/role', async () => {
  await assertSucceeds(updateDoc(doc(asAdmin(), 'users/cap'), { name: 'اسم معدّل', carPlate: '555' }));
  await assertFails(updateDoc(doc(asAdmin(), 'users/cap'), { phone: '0790000000' }));
  await assertFails(updateDoc(doc(asAdmin(), 'users/cap'), { role: 'passenger' }));
  await assertFails(updateDoc(doc(asAdmin(), 'users/cap'), { name: 'ع' }));
});
test('freeze: approved captain still cannot change his own status', async () => {
  await assertFails(updateDoc(doc(asCap(), 'users/cap'), { status: 'approved', name: 'x y' }));
  await assertFails(updateDoc(doc(asCap(), 'users/cap'), { status: 'pending' }));
});

// ===== Captain phone stays visible in trips (decision) =====
test('captain phone is visible to any signed-in user inside the trip (as decided)', async () => {
  const snap = await assertSucceeds(getDoc(doc(asOther(), 'trips/t1')));
  if (snap.data().captainPhone !== '0791000001') throw new Error('captainPhone not visible');
});

// ===== Trips: create =====
const newTrip = (over = {}) => trip({ seatsFree: 4, ...over });
test('trip create: approved captain ok; others denied', async () => {
  await assertSucceeds(addDoc(collection(asCap(), 'trips'), newTrip()));
  await assertFails(addDoc(collection(as('cap2', '0791000002'), 'trips'), newTrip({ captainUid: 'cap2' })));
  await assertFails(addDoc(collection(asCapR(), 'trips'), newTrip({ captainUid: 'capR' })));
  await assertFails(addDoc(collection(asPax(), 'trips'), newTrip({ captainUid: 'pax' })));
});
test('trip create: spoofed identity, bad seats, extra field denied', async () => {
  await assertFails(addDoc(collection(asCap(), 'trips'), newTrip({ captainUid: 'someone' })));
  await assertFails(addDoc(collection(asCap(), 'trips'), newTrip({ captainName: 'مزوّر' })));
  await assertFails(addDoc(collection(asCap(), 'trips'), newTrip({ captainPhone: '0790000000' })));
  await assertFails(addDoc(collection(asCap(), 'trips'), newTrip({ seatsTotal: 8, seatsFree: 8 })));
  await assertFails(addDoc(collection(asCap(), 'trips'), newTrip({ seatsFree: 3 })));
  await assertFails(addDoc(collection(asCap(), 'trips'), newTrip({ status: 'full' })));
  await assertFails(addDoc(collection(asCap(), 'trips'), newTrip({ hack: 1 })));
});

// ===== Trips: update =====
test('trip update: owner can cancel; suspended owner cannot', async () => {
  await assertSucceeds(updateDoc(doc(asCap(), 'trips/t1'), { status: 'cancelled', cancelReason: 'x' }));
  await env.withSecurityRulesDisabled(async (c) => { await setDoc(doc(c.firestore(), 'trips/tR'), trip({ captainUid: 'capR' })); });
  await assertFails(updateDoc(doc(asCapR(), 'trips/tR'), { status: 'cancelled', cancelReason: 'x' }));
});
test('trip update: owner cannot change price/seatsTotal/captainUid or reopen a cancelled trip', async () => {
  await assertFails(updateDoc(doc(asCap(), 'trips/t1'), { price: 0 }));
  await assertFails(updateDoc(doc(asCap(), 'trips/t1'), { seatsTotal: 7 }));
  await assertFails(updateDoc(doc(asCap(), 'trips/t1'), { captainUid: 'pax' }));
  await assertFails(updateDoc(doc(asCap(), 'trips/tCancelled'), { status: 'open' }));
  await assertFails(updateDoc(doc(asCap(), 'trips/t1'), { seatsFree: 5 }));
});
test('trip update: stranger cannot cancel/close trip or lower seats', async () => {
  await assertFails(updateDoc(doc(asOther(), 'trips/t1'), { status: 'cancelled' }));
  await assertFails(updateDoc(doc(asPax(), 'trips/t1'), { seatsFree: 0, status: 'full' }));
  await assertFails(updateDoc(doc(asPax(), 'trips/t1'), { price: 0 }));
});
test('trip update: seat return needs the booking leaving "accepted" in the same batch, capped by seatsTotal', async () => {
  await assertFails(updateDoc(doc(asPax(), 'trips/t1'), { seatsFree: 5, status: 'open' }));   // no booking named
  await assertFails(leaveWith(asPax(), 'bAcc', 't1', 5));                                      // above seatsTotal and wrong amount
  await assertFails(leaveWith(asPax(), 'bAcc', 'tCancelled', 3));                              // booking is not on that trip
  await assertFails(leaveWith(asPaxS(), 'bAcc', 't1', 3));                                     // not his booking
  await assertSucceeds(leaveWith(asPax(), 'bAcc', 't1', 3));                                   // his own accepted booking: 2 -> 3
});
test('seats: nobody can raise or lower seatsFree alone any more (the old KNOWN LIMIT is closed)', async () => {
  for (const ctxOf of [asPax, asPax2, asOther, asCap, asCapR, asAdmin]) {
    await assertFails(updateDoc(doc(ctxOf(), 'trips/t1'), { seatsFree: 4, status: 'open' }));
    await assertFails(updateDoc(doc(ctxOf(), 'trips/t1'), { seatsFree: 0, status: 'full' }));
  }
});
test('seats: rider books, captain accepts (4 -> 3), rider cancels (3 -> 4) — the exact scenario of the plan', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'trips/t4'), trip({ seatsTotal: 4, seatsFree: 4 }));
    await setDoc(doc(c.firestore(), 'bookings/b4'), booking({ tripId: 't4' }));
  });
  await assertFails(updateDoc(doc(asCap(), 'bookings/b4'), { status: 'accepted', updatedAt: at })); // booking alone: seats not taken
  await assertSucceeds(acceptWith(asCap(), 'b4', 't4', 3));
  await assertFails(leaveWith(asPax(), 'b4', 't4', 5));                                             // cannot return more than the booking had
  await assertSucceeds(leaveWith(asPax(), 'b4', 't4', 4));                                          // back to 4
  await assertFails(leaveWith(asPax(), 'b4', 't4', 5));                                             // and cannot be returned twice
});
test('seats: accepting the last seat closes the trip (full); wrong status or amount is denied', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'trips/t1s'), trip({ seatsTotal: 4, seatsFree: 1 }));
    await setDoc(doc(c.firestore(), 'bookings/b1s'), booking({ tripId: 't1s' }));
  });
  await assertFails(acceptWith(asCap(), 'b1s', 't1s', 0, 'open'));
  await assertFails(acceptWith(asCap(), 'b1s', 't1s', 1));
  await assertFails(acceptWith(asCap(), 'b1s', 't1s', 2));
  await assertSucceeds(acceptWith(asCap(), 'b1s', 't1s', 0, 'full'));
});
test('seats: a captain cancelling an accepted booking returns the seats; passenger/other captain cannot use that path', async () => {
  await assertFails(updateDoc(doc(asCap(), 'bookings/bAcc'), { status: 'cancelled_by_captain', reason: 'x', updatedAt: at })); // alone
  await assertFails(leaveWith(asPax(), 'bAcc', 't1', 3, 'cancelled_by_captain'));            // passenger pretending to be the captain
  await assertFails(leaveWith(asCap3(), 'bAcc', 't1', 3, 'cancelled_by_captain'));           // another captain
  await assertSucceeds(leaveWith(asCap(), 'bAcc', 't1', 3, 'cancelled_by_captain'));
});
test('seats: cancelling an accepted booking WITHOUT giving the seats back is denied', async () => {
  await assertFails(updateDoc(doc(asPax(), 'bookings/bAcc'), { status: 'cancelled_by_passenger', reason: 'x', updatedAt: at }));
  await assertFails(updateDoc(doc(asAdmin(), 'bookings/bAcc'), { status: 'cancelled_by_passenger', reason: 'x', updatedAt: at }));
});
test('trip update: admin may only cancel', async () => {
  await assertSucceeds(updateDoc(doc(asAdmin(), 'trips/t1'), { status: 'cancelled', cancelReason: 'تم إيقاف حساب الكابتن' }));
  await assertFails(updateDoc(doc(asAdmin(), 'trips/tFull'), { seatsFree: 4 }));
  await assertFails(updateDoc(doc(asAdmin(), 'trips/t1'), { status: 'open' }));
});

// ===== Bookings: create =====
const newBooking = (over = {}) => booking(over);
test('booking create: active passenger ok', async () => {
  await assertSucceeds(addDoc(collection(asPax(), 'bookings'), newBooking()));
});
test('booking create: suspended passenger denied', async () => {
  await assertFails(addDoc(collection(asPaxS(), 'bookings'), newBooking({ passengerUid: 'paxS', passengerName: 'اسم تجريبي', passengerPhone: '0792000002' })));
});
test('booking create: trip must be open, have enough seats, real captain, not own trip', async () => {
  await assertFails(addDoc(collection(asPax(), 'bookings'), newBooking({ tripId: 'tFull' })));
  await assertFails(addDoc(collection(asPax(), 'bookings'), newBooking({ tripId: 'tCancelled' })));
  await assertFails(addDoc(collection(asPax(), 'bookings'), newBooking({ tripId: 'nope' })));
  await assertFails(addDoc(collection(asPax(), 'bookings'), newBooking({ seats: 3 }))); // only 2 free
  await assertFails(addDoc(collection(asPax(), 'bookings'), newBooking({ captainUid: 'other' })));
  await assertFails(addDoc(collection(asCap(), 'bookings'), newBooking({ passengerUid: 'cap', passengerName: 'كابتن', passengerPhone: '0791000001' })));
});
test('booking create: spoofing and bad values denied', async () => {
  await assertFails(addDoc(collection(asPax(), 'bookings'), newBooking({ passengerUid: 'someoneElse' })));
  await assertFails(addDoc(collection(asPax(), 'bookings'), newBooking({ passengerPhone: '0790000000' })));
  await assertFails(addDoc(collection(asPax(), 'bookings'), newBooking({ status: 'accepted' })));
  await assertFails(addDoc(collection(asPax(), 'bookings'), newBooking({ seats: 0 })));
  await assertFails(addDoc(collection(asPax(), 'bookings'), newBooking({ seats: 8 })));
  await assertFails(addDoc(collection(asPax(), 'bookings'), newBooking({ hack: 1 })));
});

// ===== Bookings: read =====
test('booking read: only passenger, captain, admin', async () => {
  await assertSucceeds(getDoc(doc(asPax(), 'bookings/b1')));
  await assertSucceeds(getDoc(doc(asCap(), 'bookings/b1')));
  await assertSucceeds(getDoc(doc(asAdmin(), 'bookings/b1')));
  await assertFails(getDoc(doc(asOther(), 'bookings/b1')));
});

// ===== Bookings: update =====
const at = Date.now();
// Seat accounting is coupled: a booking that enters or leaves 'accepted' must change the trip's seatsFree in the
// SAME batch, and the trip write names that booking in lastBookingId (this is exactly what Repo.kt does).
const acceptWith = (db, bid, tripId, free, st = 'open') => {
  const b = writeBatch(db);
  b.update(doc(db, `bookings/${bid}`), { status: 'accepted', updatedAt: at });
  b.update(doc(db, `trips/${tripId}`), { seatsFree: free, status: st, lastBookingId: bid });
  return b.commit();
};
const leaveWith = (db, bid, tripId, free, status = 'cancelled_by_passenger', reason = 'x') => {
  const b = writeBatch(db);
  b.update(doc(db, `bookings/${bid}`), { status, reason, updatedAt: at });
  b.update(doc(db, `trips/${tripId}`), { seatsFree: free, status: 'open', lastBookingId: bid });
  return b.commit();
};
test('booking update: passenger cancels own; not others; not into accepted', async () => {
  await assertSucceeds(updateDoc(doc(asPax(), 'bookings/b1'), { status: 'cancelled_by_passenger', reason: 'x', updatedAt: at }));
  await assertFails(updateDoc(doc(asPax(), 'bookings/b1'), { status: 'accepted', updatedAt: at }));
  await assertFails(updateDoc(doc(asOther(), 'bookings/b1'), { status: 'cancelled_by_passenger', updatedAt: at }));
  await assertFails(updateDoc(doc(asPax(), 'bookings/b1'), { seats: 7 }));
});
test('booking update: captain accepts pending together with the seat decrease; cannot accept twice; cannot edit seats', async () => {
  await assertFails(updateDoc(doc(asCap(), 'bookings/b1'), { status: 'accepted', updatedAt: at }));  // seats not taken
  await assertFails(acceptWith(asCap(), 'b1', 't1', 0, 'full'));                                       // wrong amount (should be 2 -> 1)
  await assertSucceeds(acceptWith(asCap(), 'b1', 't1', 1));
  await assertFails(acceptWith(asCap(), 'bAcc', 't1', 1));                                             // already accepted
  await assertFails(updateDoc(doc(asCap(), 'bookings/b1'), { seats: 2, status: 'accepted', updatedAt: at }));
});
test('booking update: suspended captain / other captain cannot accept', async () => {
  await env.withSecurityRulesDisabled(async (c) => { await setDoc(doc(c.firestore(), 'bookings/bR'), booking({ captainUid: 'capR' })); });
  await assertFails(updateDoc(doc(asCapR(), 'bookings/bR'), { status: 'accepted', updatedAt: at }));
  await assertFails(updateDoc(doc(as('cap2', '0791000002'), 'bookings/b1'), { status: 'accepted', updatedAt: at }));
});
test('booking update: admin may only cancel as captain (pending/accepted)', async () => {
  await assertSucceeds(updateDoc(doc(asAdmin(), 'bookings/b1'), { status: 'cancelled_by_captain', reason: 'r', updatedAt: at }));
  await assertSucceeds(leaveWith(asAdmin(), 'bAcc', 't1', 3, 'cancelled_by_captain', 'r'));
  await assertFails(updateDoc(doc(asAdmin(), 'bookings/b1'), { status: 'accepted', updatedAt: at }));
});

// ===== Admin suspending a captain in ONE batch (as AdminScreen does) =====
test('admin batch: cancel trip + bookings + reject captain together', async () => {
  const db = asAdmin();
  const b = writeBatch(db);
  b.update(doc(db, 'trips/t1'), { status: 'cancelled', cancelReason: 'تم إيقاف حساب الكابتن' });
  b.update(doc(db, 'bookings/b1'), { status: 'cancelled_by_captain', reason: 'r', updatedAt: at });
  b.update(doc(db, 'bookings/bAcc'), { status: 'cancelled_by_captain', reason: 'r', updatedAt: at });
  b.update(doc(db, 'users/cap'), { status: 'rejected' });
  await assertSucceeds(b.commit());
});

// ===== Delete =====
test('delete: users/bookings only by admin', async () => {
  await assertFails(deleteDoc(doc(asPax(), 'users/pax')));
  await assertFails(deleteDoc(doc(asPax(), 'bookings/b1')));
  await assertFails(deleteDoc(doc(asOther(), 'trips/t1')));
  await assertSucceeds(deleteDoc(doc(asAdmin(), 'bookings/b1')));
});

// =====================================================================================
// ABUSE SCENARIOS (added): every role against users / trips / bookings.
// Roles: anon, active passenger (pax, pax2), suspended passenger (paxS), pending captain (cap2),
// rejected captain (capR), approved captain (cap, cap3), admin.
// Tests named "KNOWN LIMIT" document gaps that the rules do NOT close today (they assert the
// current, weaker behaviour on purpose, so a future fix will flip them and be noticed).
// =====================================================================================

beforeEach(async () => {
  // Extra seed data for the abuse tests (the main seed above runs first).
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/cap3'), user('captain', 'approved', '0791000004', 'كابتن ثاني'));
    await setDoc(doc(db, 'users/pax2'), user('passenger', 'active', '0792000003', 'راكب٢'));
    await setDoc(doc(db, 'trips/tOther'), trip({ captainUid: 'cap3', captainPhone: '0791000004', captainName: 'كابتن ثاني' }));
    await setDoc(doc(db, 'trips/t7'), trip({ seatsTotal: 7, seatsFree: 7 }));
    await setDoc(doc(db, 'bookings/bRej'), booking({ status: 'rejected' }));
    await setDoc(doc(db, 'bookings/bCan'), booking({ status: 'cancelled_by_passenger' }));
  });
});

const asCap2 = () => as('cap2', '0791000002');
const asCap3 = () => as('cap3', '0791000004');
const asPax2 = () => as('pax2', '0792000003');
const pax2Booking = (over = {}) => booking({ passengerUid: 'pax2', passengerName: 'راكب٢', passengerPhone: '0792000003', ...over });
const mkUser = (uid, phone, data) => setDoc(doc(as(uid, phone), `users/${uid}`), data);
const long = (n) => 'a'.repeat(n); // ASCII on purpose: length limits are exact whatever size() counts

// ---------- Not signed in: no read, no write, no listing ----------
test('abuse/anon: cannot create, update or delete anything', async () => {
  const db = anon();
  await assertFails(setDoc(doc(db, 'users/x'), newUser()));
  await assertFails(addDoc(collection(db, 'trips'), newTrip()));
  await assertFails(addDoc(collection(db, 'bookings'), booking()));
  await assertFails(updateDoc(doc(db, 'trips/t1'), { status: 'cancelled' }));
  await assertFails(updateDoc(doc(db, 'bookings/b1'), { status: 'cancelled_by_passenger', updatedAt: 1 }));
  await assertFails(updateDoc(doc(db, 'users/pax'), { name: 'xx' }));
  await assertFails(deleteDoc(doc(db, 'trips/t1')));
  await assertFails(deleteDoc(doc(db, 'bookings/b1')));
  await assertFails(deleteDoc(doc(db, 'users/pax')));
});
test('abuse/anon: cannot list or read any collection', async () => {
  const db = anon();
  await assertFails(getDocs(collection(db, 'trips')));
  await assertFails(getDocs(collection(db, 'bookings')));
  await assertFails(getDocs(collection(db, 'users')));
  await assertFails(getDoc(doc(db, 'bookings/b1')));
});

// ---------- Users: privilege escalation by every role ----------
const roles = [['pax', asPax], ['paxS', asPaxS], ['cap2', asCap2], ['capR', asCapR], ['cap', asCap]];
// current values of each seeded profile (a write that sets the same value is a no-op, not an escalation)
const current = {
  pax: { role: 'passenger', status: 'active', phone: '0792000001' },
  paxS: { role: 'passenger', status: 'suspended', phone: '0792000002' },
  cap2: { role: 'captain', status: 'pending', phone: '0791000002' },
  capR: { role: 'captain', status: 'rejected', phone: '0791000003' },
  cap: { role: 'captain', status: 'approved', phone: '0791000001' },
};
for (const [uid, ctxOf] of roles) {
  test(`abuse/users: ${uid} cannot change own role, status or phone`, async () => {
    for (const f of [{ role: 'admin' }, { role: 'captain' }, { role: 'passenger' }, { status: 'god' },
      { status: 'approved' }, { status: 'suspended' }, { status: 'pending' }, { status: 'active' }, { phone: '0790000009' }]) {
      const [k, v] = Object.entries(f)[0];
      if (current[uid][k] === v) continue;
      await assertFails(updateDoc(doc(ctxOf(), `users/${uid}`), f));
    }
  });
  test(`abuse/users: ${uid} cannot add new fields or delete own profile`, async () => {
    await assertFails(updateDoc(doc(ctxOf(), `users/${uid}`), { isAdmin: true }));
    await assertFails(updateDoc(doc(ctxOf(), `users/${uid}`), { createdAt: 99 }));
    await assertFails(deleteDoc(doc(ctxOf(), `users/${uid}`)));
  });
  test(`abuse/users: ${uid} cannot edit someone else's profile`, async () => {
    const victim = uid === 'pax2' ? 'pax' : 'pax2';
    await assertFails(updateDoc(doc(ctxOf(), `users/${victim}`), { name: 'اختراق' }));
    await assertFails(updateDoc(doc(ctxOf(), `users/${victim}`), { status: 'suspended' }));
  });
}
test('abuse/users: a suspended passenger can still read his own profile (needed for the suspended screen)', async () => {
  await assertSucceeds(getDoc(doc(asPaxS(), 'users/paxS')));
});
test('abuse/users: nobody reads or lists other profiles (passenger, suspended, pending, rejected, captain)', async () => {
  for (const ctxOf of [asPax, asPaxS, asCap2, asCapR, asCap, asPax2]) {
    await assertFails(getDoc(doc(ctxOf(), 'users/cap3')));
    await assertFails(getDocs(collection(ctxOf(), 'users')));
  }
});
test('abuse/users: only the admin can delete a profile', async () => {
  await assertFails(deleteDoc(doc(asCap(), 'users/pax')));
  await assertSucceeds(deleteDoc(doc(asAdmin(), 'users/pax2')));
});

// ---------- Users: bad values on create ----------
test('abuse/users create: bad phone formats are rejected', async () => {
  const bad = ['0731234567', '0700000000', '0761234567', '0791234567 ', ' 0791234567', '٠٧٩١٢٣٤٥٦٧',
    '079-123-4567', '079123456', '07912345678', '0791234567a', '962791234567', ''];
  let i = 0;
  for (const phone of bad) {
    const uid = `bad${i++}`;
    await assertFails(mkUser(uid, phone || '0791999999', newUser({ phone })));
  }
});
test('abuse/users create: phone as a number, missing name, wrong types, too long values rejected', async () => {
  await assertFails(mkUser('t1x', '0791230001', newUser({ phone: 791230001 })));
  const noName = newUser({ phone: '0791230002' }); delete noName.name;
  await assertFails(mkUser('t2x', '0791230002', noName));
  await assertFails(mkUser('t3x', '0791230003', newUser({ phone: '0791230003', name: 123 })));
  await assertFails(mkUser('t4x', '0791230004', newUser({ phone: '0791230004', createdAt: 'now' })));
  await assertFails(mkUser('t5x', '0791230005', newUser({ phone: '0791230005', name: long(81) })));
  await assertFails(mkUser('t6x', '0791230006', newUser({ phone: '0791230006', carType: long(61) })));
  await assertFails(mkUser('t7x', '0791230007', newUser({ phone: '0791230007', carPlate: long(61) })));
  await assertFails(mkUser('t8x', '0791230008', newUser({ phone: '0791230008', extra: 'x' })));
});
test('abuse/users create: limits are inclusive (2..80 chars name, 60 chars car fields)', async () => {
  await assertSucceeds(mkUser('b1x', '0791230011', newUser({ phone: '0791230011', name: long(80) })));
  await assertSucceeds(mkUser('b2x', '0791230012', newUser({ phone: '0791230012', name: 'عل' })));
  await assertSucceeds(mkUser('b3x', '0791230013', newUser({ phone: '0791230013', carType: long(60), carColor: long(60), carPlate: long(60) })));
});
test('abuse/users create: cannot create a profile with an email/phone of another number', async () => {
  await assertFails(setDoc(doc(as('n1', '0791230021'), 'users/n1'), newUser({ phone: '0791230022' })));
  await assertFails(setDoc(doc(env.authenticatedContext('n2', { email: '0791230023@mashwarak.app.evil.com' }).firestore(), 'users/n2'),
    newUser({ phone: '0791230023' })));
  await assertFails(setDoc(doc(env.authenticatedContext('n3', {}).firestore(), 'users/n3'), newUser({ phone: '0791230024' }))); // no email at all
});

// ---------- Admin impersonation ----------
test('abuse/admin: no valid admin claim means no admin (emails, wrong claim values, other claim names)', async () => {
  const fakes = [
    { email: 'admin@example.test' },
    { email: mail('0790000001') },
    { admin: false }, { admin: 'true' }, { admin: 1 }, { admin: null },
    { Admin: true }, { isAdmin: true }, { role: 'admin' },
  ];
  for (const claims of fakes) {
    const db = env.authenticatedContext('fake', claims).firestore();
    await assertFails(updateDoc(doc(db, 'users/cap2'), { status: 'approved' }));
    await assertFails(updateDoc(doc(db, 'trips/t1'), { status: 'cancelled', cancelReason: 'x' }));
    await assertFails(getDoc(doc(db, 'users/pax')));
    await assertFails(getDocs(collection(db, 'bookings')));
    await assertFails(deleteDoc(doc(db, 'users/pax')));
  }
});
test('abuse/admin: an account that has the admin claim but no profile can administer, but cannot book as a passenger', async () => {
  await assertSucceeds(updateDoc(doc(asAdmin(), 'users/cap2'), { status: 'approved' }));
  await assertFails(addDoc(collection(asAdmin(), 'bookings'), booking({ passengerUid: 'admin' })));
});

// ---------- Trips: create ----------
test('abuse/trips create: anon, passengers, pending/rejected captains cannot publish', async () => {
  await assertFails(addDoc(collection(anon(), 'trips'), newTrip()));
  await assertFails(addDoc(collection(asPax(), 'trips'), newTrip({ captainUid: 'pax' })));
  await assertFails(addDoc(collection(asPaxS(), 'trips'), newTrip({ captainUid: 'paxS' })));
  await assertFails(addDoc(collection(asCap2(), 'trips'), newTrip({ captainUid: 'cap2' })));
  await assertFails(addDoc(collection(asCapR(), 'trips'), newTrip({ captainUid: 'capR' })));
});
test('abuse/trips create: an approved captain cannot publish as another captain', async () => {
  await assertFails(addDoc(collection(asCap(), 'trips'), newTrip({ captainUid: 'cap3', captainName: 'كابتن ثاني', captainPhone: '0791000004' })));
  await assertFails(addDoc(collection(asCap3(), 'trips'), newTrip({ captainUid: 'cap' })));
});
test('abuse/trips create: wrong types and out-of-range values are rejected', async () => {
  const c = () => collection(asCap(), 'trips');
  await assertFails(addDoc(c(), newTrip({ departAt: 'tomorrow' })));
  await assertFails(addDoc(c(), newTrip({ price: 'free' })));
  await assertFails(addDoc(c(), newTrip({ note: long(301) })));
  await assertFails(addDoc(c(), newTrip({ seatsTotal: 0, seatsFree: 0 })));
  await assertFails(addDoc(c(), newTrip({ seatsTotal: -1, seatsFree: -1 })));
  await assertFails(addDoc(c(), newTrip({ seatsTotal: 2.5, seatsFree: 2.5 })));
  await assertFails(addDoc(c(), newTrip({ seatsTotal: '3', seatsFree: '3' })));
  await assertFails(addDoc(c(), newTrip({ seatsTotal: 3, seatsFree: 2 })));
  await assertFails(addDoc(c(), newTrip({ status: 'cancelled' })));
});
test('abuse/trips create: limits are inclusive (1..4 seats by default, 300 chars note, null price)', async () => {
  const c = () => collection(asCap(), 'trips');
  await assertSucceeds(addDoc(c(), newTrip({ seatsTotal: 1, seatsFree: 1 })));
  await assertSucceeds(addDoc(c(), newTrip({ seatsTotal: 4, seatsFree: 4 })));
  await assertFails(addDoc(c(), newTrip({ seatsTotal: 5, seatsFree: 5 })));
  await assertSucceeds(addDoc(c(), newTrip({ note: long(300) })));
  await assertSucceeds(addDoc(c(), newTrip({ price: null })));
});
test('KNOWN LIMIT: trip text fields (from/to/road/carText), negative price and past date are not validated', async () => {
  await assertSucceeds(addDoc(collection(asCap(), 'trips'),
    newTrip({ carText: 'سيارة غير سيارتي', from: 'مكان وهمي', to: 'مكان وهمي', road: 'x'.repeat(2000), price: -5, departAt: 1 })));
});

// ---------- Trips: update / delete ----------
test('abuse/trips update: other captains, pending/rejected captains and passengers cannot cancel or edit a trip', async () => {
  for (const ctxOf of [asCap3, asCap2, asCapR, asPax, asPaxS, asPax2, asOther]) {
    await assertFails(updateDoc(doc(ctxOf(), 'trips/t1'), { status: 'cancelled', cancelReason: 'x' }));
    await assertFails(updateDoc(doc(ctxOf(), 'trips/t1'), { price: 0 }));
    await assertFails(updateDoc(doc(ctxOf(), 'trips/t1'), { captainPhone: '0790000000' }));
    await assertFails(updateDoc(doc(ctxOf(), 'trips/t1'), { seatsFree: 0, status: 'full' }));
  }
});
test('abuse/trips update: owner cannot use invalid status or seat values', async () => {
  const d = () => doc(asCap(), 'trips/t1');
  await assertFails(updateDoc(d(), { status: 'weird' }));
  await assertFails(updateDoc(d(), { seatsFree: -1 }));
  await assertFails(updateDoc(d(), { seatsFree: '2' }));
  await assertFails(updateDoc(d(), { seatsFree: 1.5 }));
  await assertFails(updateDoc(d(), { seatsTotal: 1 }));
  await assertFails(updateDoc(d(), { captainName: 'مزوّر' }));
  await assertFails(updateDoc(d(), { carText: 'مزوّر' }));
});
test('abuse/trips update: nobody can reopen or change a cancelled trip', async () => {
  await assertFails(updateDoc(doc(asCap(), 'trips/tCancelled'), { status: 'open' }));
  await assertFails(updateDoc(doc(asCap(), 'trips/tCancelled'), { seatsFree: 0 }));
  await assertFails(updateDoc(doc(asPax(), 'trips/tCancelled'), { seatsFree: 4, status: 'open' }));
  await assertFails(updateDoc(doc(asAdmin(), 'trips/tCancelled'), { status: 'open' }));
});
test('abuse/trips update: admin can only cancel open/full trips, and nothing else', async () => {
  await assertSucceeds(updateDoc(doc(asAdmin(), 'trips/tFull'), { status: 'cancelled', cancelReason: 'x' }));
  await assertFails(updateDoc(doc(asAdmin(), 'trips/tCancelled'), { status: 'cancelled', cancelReason: 'x' }));
  await assertFails(updateDoc(doc(asAdmin(), 'trips/t1'), { price: 0 }));
  await assertFails(updateDoc(doc(asAdmin(), 'trips/t1'), { seatsTotal: 7 }));
});
test('abuse/trips delete: only the owner or the admin', async () => {
  await assertFails(deleteDoc(doc(anon(), 'trips/t1')));
  for (const ctxOf of [asCap3, asCap2, asPax, asPaxS]) await assertFails(deleteDoc(doc(ctxOf(), 'trips/t1')));
  await assertSucceeds(deleteDoc(doc(asAdmin(), 'trips/t1')));
  await assertSucceeds(deleteDoc(doc(asCap3(), 'trips/tOther')));
});
test('abuse/trips read: signed-in users read trips; anonymous cannot', async () => {
  await assertFails(getDoc(doc(anon(), 'trips/t1')));
  await assertSucceeds(getDoc(doc(asPax(), 'trips/t1')));
});
test('KNOWN LIMIT: a suspended passenger can still read trips (and the captain phone inside them)', async () => {
  await assertSucceeds(getDoc(doc(asPaxS(), 'trips/t1')));
});

// ---------- Bookings: create ----------
test('abuse/bookings create: anon and suspended passenger cannot book', async () => {
  await assertFails(addDoc(collection(anon(), 'bookings'), booking()));
  await assertFails(addDoc(collection(asPaxS(), 'bookings'), booking({ passengerUid: 'paxS', passengerName: 'اسم تجريبي', passengerPhone: '0792000002' })));
});
test('abuse/bookings create: cannot book as someone else or with a forged passenger name/phone', async () => {
  await assertFails(addDoc(collection(asPax(), 'bookings'), pax2Booking()));
  await assertFails(addDoc(collection(asPax2(), 'bookings'), booking())); // passengerUid = pax
  await assertFails(addDoc(collection(asPax(), 'bookings'), booking({ passengerName: 'مزوّر' })));
  await assertFails(addDoc(collection(asPax(), 'bookings'), booking({ passengerPhone: '0790000009' })));
});
test('abuse/bookings create: trip must be open, exist, have the seats, and belong to the stated captain', async () => {
  const c = () => collection(asPax(), 'bookings');
  await assertFails(addDoc(c(), booking({ tripId: 'tFull' })));
  await assertFails(addDoc(c(), booking({ tripId: 'tCancelled' })));
  await assertFails(addDoc(c(), booking({ tripId: 'ghost' })));
  await assertFails(addDoc(c(), booking({ seats: 3 })));            // t1 has only 2 free
  await assertFails(addDoc(c(), booking({ tripId: 'tOther' })));    // captainUid says 'cap', trip belongs to cap3
  await assertFails(addDoc(c(), booking({ tripId: 'tOther', captainUid: 'pax' })));
  await assertFails(addDoc(collection(asCap(), 'bookings'),
    booking({ passengerUid: 'cap', passengerName: 'كابتن', passengerPhone: '0791000001' }))); // own trip
});
test('abuse/bookings create: wrong types and out-of-range values are rejected', async () => {
  const c = () => collection(asPax(), 'bookings');
  await assertFails(addDoc(c(), booking({ seats: 0 })));
  await assertFails(addDoc(c(), booking({ seats: -1 })));
  await assertFails(addDoc(c(), booking({ seats: 8 })));
  await assertFails(addDoc(c(), booking({ seats: 1.5 })));
  await assertFails(addDoc(c(), booking({ seats: '1' })));
  await assertFails(addDoc(c(), booking({ tripId: 5 })));
  await assertFails(addDoc(c(), booking({ dropoff: long(121) })));
  await assertFails(addDoc(c(), booking({ status: 'accepted' })));
  await assertFails(addDoc(c(), booking({ status: 'cancelled_by_captain' })));
  await assertFails(addDoc(c(), booking({ extra: 1 })));
});
test('abuse/bookings create: limits are inclusive (7 seats on a 7-seat trip, 120 chars drop-off)', async () => {
  await assertSucceeds(addDoc(collection(asPax(), 'bookings'), booking({ tripId: 't7', seats: 7 })));
  await assertSucceeds(addDoc(collection(asPax2(), 'bookings'), pax2Booking({ dropoff: long(120) })));
});
test('KNOWN LIMIT: booking copies of captainName/captainPhone/tripSummary/departAt are not checked against the trip', async () => {
  await assertSucceeds(addDoc(collection(asPax(), 'bookings'),
    booking({ captainName: 'اسم مزوّر', captainPhone: '0790000000', tripSummary: 'x ← y', departAt: 1 })));
});

// ---------- Bookings: update / delete / read ----------
test('abuse/bookings update: another passenger or another captain cannot touch a booking', async () => {
  for (const ctxOf of [asPax2, asCap3, asCap2, asCapR, asPaxS, asOther]) {
    for (const status of ['cancelled_by_passenger', 'cancelled_by_captain', 'accepted', 'rejected']) {
      await assertFails(updateDoc(doc(ctxOf(), 'bookings/b1'), { status, updatedAt: at }));
    }
  }
  await assertFails(updateDoc(doc(anon(), 'bookings/b1'), { status: 'cancelled_by_passenger', updatedAt: at }));
});
test('abuse/bookings update: the passenger can only cancel (never accept/reject/cancel-as-captain) and only active bookings', async () => {
  const d = (id) => doc(asPax(), `bookings/${id}`);
  await assertFails(updateDoc(d('b1'), { status: 'accepted', updatedAt: at }));
  await assertFails(updateDoc(d('b1'), { status: 'rejected', updatedAt: at }));
  await assertFails(updateDoc(d('b1'), { status: 'cancelled_by_captain', updatedAt: at }));
  await assertFails(updateDoc(d('bRej'), { status: 'cancelled_by_passenger', updatedAt: at }));
  await assertFails(updateDoc(d('bCan'), { status: 'cancelled_by_passenger', updatedAt: at }));
  await assertFails(updateDoc(d('bAcc'), { status: 'cancelled_by_passenger', reason: 'x', updatedAt: at })); // seats not returned
  await assertSucceeds(leaveWith(asPax(), 'bAcc', 't1', 3));
});
test('abuse/bookings update: nobody can change ownership, trip, seats or contact data of a booking', async () => {
  for (const f of [{ passengerUid: 'pax2' }, { captainUid: 'cap3' }, { tripId: 'tOther' }, { seats: 7 },
    { dropoff: 'x' }, { passengerPhone: '0790000000' }, { captainPhone: '0790000000' }, { departAt: 1 }]) {
    await assertFails(updateDoc(doc(asPax(), 'bookings/b1'), f));
    await assertFails(updateDoc(doc(asCap(), 'bookings/b1'), f));
    await assertFails(updateDoc(doc(asAdmin(), 'bookings/b1'), f));
  }
});
test('abuse/bookings update: the captain cannot revive rejected/cancelled bookings or cancel them again', async () => {
  for (const id of ['bRej', 'bCan']) {
    await assertFails(updateDoc(doc(asCap(), `bookings/${id}`), { status: 'accepted', updatedAt: at }));
    await assertFails(updateDoc(doc(asCap(), `bookings/${id}`), { status: 'rejected', updatedAt: at }));
    await assertFails(updateDoc(doc(asCap(), `bookings/${id}`), { status: 'cancelled_by_captain', updatedAt: at }));
    await assertFails(updateDoc(doc(asAdmin(), `bookings/${id}`), { status: 'cancelled_by_captain', updatedAt: at }));
  }
  await assertFails(updateDoc(doc(asCap(), 'bookings/bAcc'), { status: 'accepted', updatedAt: at })); // already accepted
});
test('abuse/bookings update: over-long reason is rejected', async () => {
  await assertFails(updateDoc(doc(asPax(), 'bookings/b1'), { status: 'cancelled_by_passenger', reason: long(301), updatedAt: at }));
  await assertFails(updateDoc(doc(asCap(), 'bookings/b1'), { status: 'cancelled_by_captain', reason: long(301), updatedAt: at }));
  await assertSucceeds(updateDoc(doc(asCap(), 'bookings/b1'), { status: 'cancelled_by_captain', reason: long(300), updatedAt: at }));
});
test('abuse/bookings update: a captain who is no longer approved cannot act on bookings', async () => {
  await env.withSecurityRulesDisabled(async (c) => { await updateDoc(doc(c.firestore(), 'users/cap'), { status: 'rejected' }); });
  await assertFails(updateDoc(doc(asCap(), 'bookings/b1'), { status: 'accepted', updatedAt: at }));
  await assertFails(updateDoc(doc(asCap(), 'bookings/bAcc'), { status: 'cancelled_by_captain', updatedAt: at }));
});
test('abuse/bookings delete: only the admin', async () => {
  for (const ctxOf of [asPax, asCap, asPax2, asCap3, asPaxS]) await assertFails(deleteDoc(doc(ctxOf(), 'bookings/b1')));
  await assertFails(deleteDoc(doc(anon(), 'bookings/b1')));
  await assertSucceeds(deleteDoc(doc(asAdmin(), 'bookings/b1')));
});
test('abuse/bookings read: only the passenger, the captain of the trip and the admin', async () => {
  for (const ctxOf of [asPax2, asCap3, asCap2, asCapR, asPaxS, asOther]) {
    await assertFails(getDoc(doc(ctxOf(), 'bookings/b1')));
  }
  await assertSucceeds(getDoc(doc(asPax(), 'bookings/b1')));
  await assertSucceeds(getDoc(doc(asCap(), 'bookings/b1')));
  await assertSucceeds(getDoc(doc(asAdmin(), 'bookings/b1')));
});
test('abuse/bookings list: only own bookings (as the app queries them); whole collection only for the admin', async () => {
  const byField = (db, f, v) => getDocs(query(collection(db, 'bookings'), where(f, '==', v)));
  await assertFails(getDocs(collection(asPax(), 'bookings')));
  await assertFails(getDocs(collection(asCap(), 'bookings')));
  await assertSucceeds(byField(asPax(), 'passengerUid', 'pax'));
  await assertSucceeds(byField(asCap(), 'captainUid', 'cap'));
  await assertFails(byField(asPax(), 'passengerUid', 'pax2'));
  await assertFails(byField(asCap3(), 'captainUid', 'cap'));
  await assertSucceeds(getDocs(collection(asAdmin(), 'bookings')));
});

// ---------- Suspension takes effect immediately ----------
test('abuse/suspension: a passenger who gets suspended can no longer book', async () => {
  await assertSucceeds(addDoc(collection(asPax(), 'bookings'), booking()));
  await env.withSecurityRulesDisabled(async (c) => { await updateDoc(doc(c.firestore(), 'users/pax'), { status: 'suspended' }); });
  await assertFails(addDoc(collection(asPax(), 'bookings'), booking()));
});
test('abuse/suspension: a captain who gets suspended can no longer publish or edit trips', async () => {
  await assertSucceeds(addDoc(collection(asCap(), 'trips'), newTrip()));
  await env.withSecurityRulesDisabled(async (c) => { await updateDoc(doc(c.firestore(), 'users/cap'), { status: 'rejected' }); });
  await assertFails(addDoc(collection(asCap(), 'trips'), newTrip()));
  await assertFails(updateDoc(doc(asCap(), 'trips/t1'), { status: 'cancelled', cancelReason: 'x' }));
});

// ===== Two independent states: captain status vs riderSuspended (new) =====
const profileName = { pax: 'راكب', cap: 'كابتن', cap3: 'كابتن ثاني', capM: 'كابتن كبير' }; // seeded names; the rest use the default
const bookAs = (ctx, uid, phone, over = {}) =>
  addDoc(collection(ctx, 'bookings'), booking({ passengerUid: uid, passengerName: profileName[uid] ?? 'اسم تجريبي', passengerPhone: phone, ...over }));

test('rider-state: captain suspended/rejected AS CAPTAIN still books as a rider', async () => {
  await assertSucceeds(bookAs(asCapR(), 'capR', '0791000003'));   // rejected captain
});
test('rider-state: pending captain books as a rider too (assumption to confirm)', async () => {
  await assertSucceeds(bookAs(asCap2(), 'cap2', '0791000002'));
});
test('rider-state: approved captain books as a rider; never on his own trip', async () => {
  await assertSucceeds(bookAs(asCap3(), 'cap3', '0791000004')); // cap3 is another approved captain (trip t1 belongs to cap)
  await assertFails(bookAs(asCap(), 'cap', '0791000001'));       // own trip
});
test('rider-state: suspended AS RIDER cannot book (captain and passenger), seat return also denied', async () => {
  await assertFails(bookAs(asCapRS(), 'capRS', '0791000005'));
  await assertFails(bookAs(asPaxRS(), 'paxRS', '0792000004'));
  await assertFails(updateDoc(doc(asCapRS(), 'trips/t1'), { seatsFree: 3, status: 'open' }));
});
test('rider-state: suspension as rider does NOT touch the captain side', async () => {
  await assertSucceeds(addDoc(collection(asCapRS(), 'trips'), newTrip({ captainUid: 'capRS', captainName: 'اسم تجريبي', captainPhone: '0791000005' })));
  await env.withSecurityRulesDisabled(async (c) => { await setDoc(doc(c.firestore(), 'trips/tRS'), trip({ captainUid: 'capRS' })); await setDoc(doc(c.firestore(), 'bookings/bRS'), booking({ captainUid: 'capRS', tripId: 'tRS' })); });
  await assertSucceeds(acceptWith(asCapRS(), 'bRS', 'tRS', 1));
  await assertSucceeds(updateDoc(doc(asCapRS(), 'trips/tRS'), { status: 'cancelled', cancelReason: 'x' }));
});
test('rider-state: captain suspended as captain does not get a rider suspension, and rider-suspend keeps captain status', async () => {
  const other = { tripId: 'tOther', captainUid: 'cap3', captainName: 'كابتن ثاني', captainPhone: '0791000004' };
  await assertSucceeds(bookAs(asCap(), 'cap', '0791000001', { ...other, passengerName: 'كابتن' })); // control: books before the suspension
  await assertSucceeds(updateDoc(doc(asAdmin(), 'users/cap'), { riderSuspended: true }));
  await assertSucceeds(addDoc(collection(asCap(), 'trips'), newTrip()));                            // still publishes trips
  await assertFails(bookAs(asCap(), 'cap', '0791000001', { ...other, passengerName: 'كابتن' }));    // cannot book as a rider now
});
test('rider-state: old accounts without the field are not suspended', async () => {
  const snap = await assertSucceeds(getDoc(doc(asPax(), 'users/pax')));
  assert.equal(snap.data().riderSuspended, undefined);
  await assertSucceeds(bookAs(asPax(), 'pax', '0792000001'));
  await assertSucceeds(bookAs(asCap3(), 'cap3', '0791000004'));
});
test('rider-state: admin re-activates (false) and removes the suspension', async () => {
  await assertSucceeds(updateDoc(doc(asAdmin(), 'users/capRS'), { riderSuspended: false }));
  await assertSucceeds(bookAs(asCapRS(), 'capRS', '0791000005'));
});
test('rider-state: only the admin writes riderSuspended; users cannot set, clear or create it', async () => {
  // (a write that sets the value a profile already has is a no-op, so each profile gets only the value that CHANGES it)
  for (const [uid, ctxOf] of [['pax', asPax], ['cap', asCap], ['capR', asCapR]]) {
    await assertFails(updateDoc(doc(ctxOf(), `users/${uid}`), { riderSuspended: true }));
    await assertFails(updateDoc(doc(ctxOf(), `users/${uid}`), { riderSuspended: false }));
  }
  for (const [uid, ctxOf] of [['capRS', asCapRS], ['paxRS', asPaxRS]]) {
    await assertFails(updateDoc(doc(ctxOf(), `users/${uid}`), { riderSuspended: false })); // cannot lift his own suspension
  }
  await assertFails(updateDoc(doc(asOther(), 'users/capRS'), { riderSuspended: false }));
  await assertFails(setDoc(doc(asNew(), 'users/new'), { ...newUser(), riderSuspended: false })); // not in the create whitelist
});
test('rider-state: admin value must be a boolean; admin cannot change role/phone with it', async () => {
  await assertFails(updateDoc(doc(asAdmin(), 'users/cap'), { riderSuspended: 'yes' }));
  await assertFails(updateDoc(doc(asAdmin(), 'users/cap'), { riderSuspended: 1 }));
  await assertFails(updateDoc(doc(asAdmin(), 'users/cap'), { riderSuspended: true, role: 'passenger' }));
});
test('rider-state: the admin account is not a rider and cannot book (it has no passenger profile)', async () => {
  await assertFails(bookAs(asAdmin(), 'admin', '0790000001'));
});

// ===== maxSeats: 4 seats for everyone, more only when the admin raises it =====
const mTrip = (n, over = {}) => newTrip({ captainUid: 'capM', captainName: 'كابتن كبير', captainPhone: '0791000006', seatsTotal: n, seatsFree: n, ...over });
test('maxSeats: default captain (no field) publishes 1..4 seats, never 5', async () => {
  for (const n of [1, 2, 3, 4]) await assertSucceeds(addDoc(collection(asCap(), 'trips'), newTrip({ seatsTotal: n, seatsFree: n })));
  for (const n of [5, 6, 7]) await assertFails(addDoc(collection(asCap(), 'trips'), newTrip({ seatsTotal: n, seatsFree: n })));
});
test('maxSeats: a captain raised to 6 publishes up to 6, not 7', async () => {
  await assertSucceeds(addDoc(collection(asCapM(), 'trips'), mTrip(6)));
  await assertSucceeds(addDoc(collection(asCapM(), 'trips'), mTrip(5)));
  await assertFails(addDoc(collection(asCapM(), 'trips'), mTrip(7)));
});
test('maxSeats: only the admin sets it, only an integer 4..7, never together with role/phone', async () => {
  for (const [uid, ctxOf] of [['cap', asCap], ['capM', asCapM], ['pax', asPax], ['capR', asCapR]]) {
    await assertFails(updateDoc(doc(ctxOf(), `users/${uid}`), { maxSeats: 7 }));
  }
  await assertFails(updateDoc(doc(asOther(), 'users/cap'), { maxSeats: 6 }));
  await assertFails(setDoc(doc(asNew(), 'users/new'), { ...newUser(), maxSeats: 6 })); // not in the create whitelist
  for (const bad of [3, 8, 0, -1, 5.5, '6', true, null]) await assertFails(updateDoc(doc(asAdmin(), 'users/cap'), { maxSeats: bad }));
  await assertFails(updateDoc(doc(asAdmin(), 'users/cap'), { maxSeats: 6, role: 'passenger' }));
  await assertSucceeds(updateDoc(doc(asAdmin(), 'users/cap'), { maxSeats: 6 }));
  await assertSucceeds(addDoc(collection(asCap(), 'trips'), newTrip({ seatsTotal: 6, seatsFree: 6 }))); // takes effect at once
  await assertSucceeds(updateDoc(doc(asAdmin(), 'users/cap'), { maxSeats: 4 }));                          // and can be lowered again
  await assertFails(addDoc(collection(asCap(), 'trips'), newTrip({ seatsTotal: 5, seatsFree: 5 })));
});
test('maxSeats: lowering it only affects new trips', async () => {
  await assertSucceeds(addDoc(collection(asCapM(), 'trips'), mTrip(6)));
  await assertSucceeds(updateDoc(doc(asAdmin(), 'users/capM'), { maxSeats: 4 }));
  await assertFails(addDoc(collection(asCapM(), 'trips'), mTrip(6)));
});

// ===== Rider suspension cleanup: admin cancels the rider's bookings and returns the seats =====
test('auto-cancel: admin cancels a rider booking as cancelled_by_passenger with a reason (pending and accepted)', async () => {
  const reason = 'يعطيك العافية كابتن، نعتذر منك. تم إلغاء الحجز من قبل الراكب بدون ذكر سبب.';
  await assertSucceeds(updateDoc(doc(asAdmin(), 'bookings/b1'), { status: 'cancelled_by_passenger', reason, updatedAt: at }));
  await assertSucceeds(leaveWith(asAdmin(), 'bAcc', 't1', 3, 'cancelled_by_passenger', reason));
});
test('auto-cancel: admin cannot cancel an already finished booking, nor accept/reject, nor edit other fields', async () => {
  await assertFails(updateDoc(doc(asAdmin(), 'bookings/bCan'), { status: 'cancelled_by_passenger', updatedAt: at }));
  await assertFails(updateDoc(doc(asAdmin(), 'bookings/bRej'), { status: 'cancelled_by_passenger', updatedAt: at }));
  await assertFails(updateDoc(doc(asAdmin(), 'bookings/b1'), { status: 'accepted', updatedAt: at }));
  await assertFails(updateDoc(doc(asAdmin(), 'bookings/b1'), { status: 'cancelled_by_passenger', seats: 2, updatedAt: at }));
  await assertFails(updateDoc(doc(asAdmin(), 'bookings/b1'), { status: 'cancelled_by_passenger', reason: long(301), updatedAt: at }));
  await assertFails(updateDoc(doc(asAdmin(), 'bookings/b1'), { status: 'cancelled_by_passenger', passengerUid: 'admin', updatedAt: at }));
});
test('auto-cancel: nobody else gets the new power (captain of another trip, other passenger, suspended user)', async () => {
  for (const ctxOf of [asCap3, asCap2, asCapR, asPax2, asPaxS, asPaxRS, asCapRS, asOther]) {
    await assertFails(updateDoc(doc(ctxOf(), 'bookings/b1'), { status: 'cancelled_by_passenger', reason: 'x', updatedAt: at }));
  }
});
test('auto-cancel: admin returns the seats (+ reopens a full trip) in ONE batch with the cancel, capped by seatsTotal', async () => {
  await env.withSecurityRulesDisabled(async (c) => { await setDoc(doc(c.firestore(), 'bookings/bF'), booking({ tripId: 'tFull', status: 'accepted', seats: 2 })); });
  const db = asAdmin();
  const b = writeBatch(db);
  b.update(doc(db, 'bookings/bF'), { status: 'cancelled_by_passenger', reason: 'r', updatedAt: at });
  b.update(doc(db, 'trips/tFull'), { seatsFree: 2, status: 'open', lastBookingId: 'bF' });
  await assertSucceeds(b.commit());
  const over = writeBatch(db);
  over.update(doc(db, 'trips/t1'), { seatsFree: 5, status: 'open' }); // above seatsTotal (4)
  await assertFails(over.commit());
});
test('auto-cancel: the admin batch also works when the rider is a captain (cap3) being suspended as rider', async () => {
  await env.withSecurityRulesDisabled(async (c) => { await setDoc(doc(c.firestore(), 'bookings/bC'), booking({ passengerUid: 'cap3', passengerName: 'كابتن ثاني', passengerPhone: '0791000004', status: 'accepted' })); });
  const db = asAdmin();
  const b = writeBatch(db);
  b.update(doc(db, 'bookings/bC'), { status: 'cancelled_by_passenger', reason: 'r', updatedAt: at });
  b.update(doc(db, 'trips/t1'), { seatsFree: 3, status: 'open', lastBookingId: 'bC' });
  b.update(doc(db, 'users/cap3'), { riderSuspended: true });
  await assertSucceeds(b.commit());
});

// ===== Places config (editable lists) =====
test('config/places: signed-in users read, nobody but the admin writes', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'config/places'), { subAreas: { 'عمّان': ['الدوار الخامس'] }, updatedAt: 1 });
  });
  await assertSucceeds(getDoc(doc(asPax(), 'config/places')));
  await assertFails(getDoc(doc(anon(), 'config/places')));
  const body = { subAreas: { 'عمّان': ['الدوار الخامس', 'صويلح'], 'عمّان|الطريق الصحراوي': ['العبدلي'] }, updatedAt: 2 };
  await assertSucceeds(setDoc(doc(asAdmin(), 'config/places'), body));
  for (const ctx of [asPax, asCap, asCapR, asOther]) await assertFails(setDoc(doc(ctx(), 'config/places'), body));
  await assertFails(setDoc(doc(anon(), 'config/places'), body));
});
test('config/places: admin write must keep the shape; other config docs are closed', async () => {
  await assertFails(setDoc(doc(asAdmin(), 'config/places'), { subAreas: ['x'], updatedAt: 2 }));              // not a map
  await assertFails(setDoc(doc(asAdmin(), 'config/places'), { subAreas: {}, updatedAt: 2, extra: 1 }));       // extra field
  await assertFails(setDoc(doc(asAdmin(), 'config/places'), { subAreas: {}, updatedAt: 'now' }));             // bad time
  await assertFails(setDoc(doc(asAdmin(), 'config/other'), { subAreas: {}, updatedAt: 2 }));                   // other doc
  await assertFails(deleteDoc(doc(asAdmin(), 'config/places')));
});

// ===== Reverse number verification =====
test('verify code: 6 digits accepted at sign-up, anything else rejected; `verified` cannot be set by the user', async () => {
  await assertSucceeds(setDoc(doc(asNew('0791234600'), 'users/new'), newUser({ phone: '0791234600', verifyCode: '482917' })));
  for (const [i, bad] of ['12345', '1234567', 'abcdef', 482917, ''].entries()) {
    const p = `07912347${i}0`;
    await assertFails(setDoc(doc(as(`v${i}`, p), `users/v${i}`), newUser({ phone: p, verifyCode: bad })));
  }
  await assertFails(setDoc(doc(asNew('0791234601'), 'users/new'), newUser({ phone: '0791234601', verifyCode: '482917', verified: true })));
});
test('verify code: only the admin marks a number verified; the owner cannot change the code or verify himself', async () => {
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'users/vp'), { ...user('passenger', 'active', '0792111111'), verifyCode: '111222' });
  });
  await assertFails(updateDoc(doc(as('vp', '0792111111'), 'users/vp'), { verified: true }));
  await assertFails(updateDoc(doc(as('vp', '0792111111'), 'users/vp'), { verifyCode: '999999' }));
  await assertFails(updateDoc(doc(asOther(), 'users/vp'), { verified: true }));
  await assertSucceeds(updateDoc(doc(asAdmin(), 'users/vp'), { verified: true }));
  await assertFails(updateDoc(doc(asAdmin(), 'users/vp'), { verified: 'yes' }));
  await assertFails(updateDoc(doc(asAdmin(), 'users/vp'), { verifyCode: '999999' }));
});

