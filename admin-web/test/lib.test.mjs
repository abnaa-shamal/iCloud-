import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  DAY_MS, dailyCounts, topRoutes, computeOverview, captainStats, matches, waLink, csvCell, toCsv, isoDate, CSV_COLUMNS,
} from '../src/lib.js';

const NOW = new Date(2026, 9, 27, 15, 0, 0).getTime(); // 27 Oct 2026, 15:00 local

test('dailyCounts: 7 buckets, today last, counts by calendar day', () => {
  const items = [
    { createdAt: NOW },                       // today
    { createdAt: NOW - 1 * DAY_MS },          // yesterday
    { createdAt: NOW - 1 * DAY_MS - 1000 },   // yesterday
    { createdAt: NOW - 6 * DAY_MS },          // oldest bucket
    { createdAt: NOW - 7 * DAY_MS },          // outside the window
    { createdAt: undefined },                 // ignored
  ];
  const d = dailyCounts(items, NOW, 7);
  assert.equal(d.length, 7);
  assert.equal(d[6].count, 1);
  assert.equal(d[5].count, 2);
  assert.equal(d[0].count, 1);
  assert.equal(d.reduce((a, b) => a + b.count, 0), 4);
});

test('topRoutes: sorted, limited, skips incomplete trips', () => {
  const trips = [
    { from: 'عمراوة', to: 'عمّان' }, { from: 'عمراوة', to: 'عمّان' }, { from: 'عمراوة', to: 'إربد' },
    { from: '', to: 'عمّان' }, { to: 'x' },
  ];
  const r = topRoutes(trips, 2);
  assert.equal(r.length, 2);
  assert.deepEqual(r[0], ['عمراوة ← عمّان', 2]);
});

test('computeOverview: counts, cancel rate and attention list', () => {
  const users = [
    { id: 'c1', role: 'captain', status: 'pending', verifyCode: '123456', verified: false },
    { id: 'c2', role: 'captain', status: 'approved' },
    { id: 'p1', role: 'passenger', status: 'active' },
  ];
  const trips = [
    { id: 't1', status: 'open', departAt: NOW + 2 * 3_600_000, seatsFree: 4, seatsTotal: 4, from: 'a', to: 'b' },
    { id: 't2', status: 'full', departAt: NOW + 5 * DAY_MS, seatsFree: 0, seatsTotal: 4, from: 'a', to: 'b' },
    { id: 't3', status: 'cancelled', departAt: NOW + DAY_MS, seatsFree: 0, seatsTotal: 4, from: 'a', to: 'b' },
    { id: 't4', status: 'open', departAt: NOW - DAY_MS, seatsFree: 1, seatsTotal: 4, from: 'a', to: 'b' },
  ];
  const bookings = [
    { id: 'b1', status: 'pending', departAt: NOW + 2 * 3_600_000, createdAt: NOW },
    { id: 'b2', status: 'accepted', departAt: NOW + 5 * DAY_MS, createdAt: NOW },
    ...Array.from({ length: 8 }, (_, i) => ({ id: `x${i}`, status: i < 4 ? 'cancelled_by_passenger' : 'rejected', createdAt: NOW })),
  ];
  const o = computeOverview({ users, trips, bookings, now: NOW });
  assert.equal(o.captains, 2);
  assert.equal(o.pending, 1);
  assert.equal(o.approved, 1);
  assert.equal(o.riders, 1);
  assert.equal(o.liveTrips, 2);
  assert.equal(o.seatsFree, 4);
  assert.equal(o.activeBookings, 2);
  assert.equal(o.cancelled, 4);
  assert.equal(o.cancelRate, 40);
  assert.equal(o.unverified, 1);
  const pages = o.attention.map((a) => a.page);
  assert.ok(pages.includes('captains'));   // pending captain
  assert.ok(pages.includes('bookings'));   // stale pending booking + high cancel rate
  assert.ok(pages.includes('riders'));     // unverified
  assert.ok(pages.includes('trips'));      // t1 leaves in 2h with nobody booked
  assert.equal(o.daily.at(-1).count, 10);
});

test('computeOverview: empty data does not divide by zero', () => {
  const o = computeOverview({ users: [], trips: [], bookings: [], now: NOW });
  assert.equal(o.cancelRate, 0);
  assert.equal(o.attention.length, 0);
  assert.deepEqual(o.routes, []);
});

test('captainStats: accept rate ignores unanswered requests', () => {
  const bookings = [
    { captainUid: 'c', status: 'accepted' }, { captainUid: 'c', status: 'accepted' },
    { captainUid: 'c', status: 'rejected' }, { captainUid: 'c', status: 'pending' },
    { captainUid: 'c', status: 'cancelled_by_captain' }, { captainUid: 'other', status: 'accepted' },
  ];
  const s = captainStats('c', [{ captainUid: 'c' }, { captainUid: 'z' }], bookings);
  assert.equal(s.trips, 1);
  assert.equal(s.bookings, 5);
  assert.equal(s.accepted, 2);
  assert.equal(s.cancelledByCaptain, 1);
  assert.equal(s.acceptRate, 50); // 2 of 4 answered
  assert.equal(captainStats('none', [], []).acceptRate, null);
});

test('matches: every word must appear, case-insensitive', () => {
  assert.ok(matches('', 'x'));
  assert.ok(matches('  ', 'x'));
  assert.ok(matches('ali 079', 'Ali Ahmad', '0791234567'));
  assert.ok(!matches('ali 078', 'Ali Ahmad', '0791234567'));
  assert.ok(matches('عمّان', undefined, 'عمراوة ← عمّان'));
});

test('waLink: only valid Jordanian mobiles', () => {
  assert.equal(waLink('0791234567'), 'https://wa.me/962791234567');
  assert.equal(waLink('0781234567', 'مرحبا'), `https://wa.me/962781234567?text=${encodeURIComponent('مرحبا')}`);
  assert.equal(waLink('0761234567'), null);
  assert.equal(waLink('079123'), null);
  assert.equal(waLink(undefined), null);
  assert.equal(waLink('0791234567/../x'), null);
});

test('csvCell: quoting and formula neutralisation', () => {
  assert.equal(csvCell('abc'), 'abc');
  assert.equal(csvCell('a,b'), '"a,b"');
  assert.equal(csvCell('say "hi"'), '"say ""hi"""');
  assert.equal(csvCell('line\nbreak'), '"line\nbreak"');
  assert.equal(csvCell('=HYPERLINK("http://x")'), '"\'=HYPERLINK(""http://x"")"');
  assert.equal(csvCell('+1'), "'+1");
  assert.equal(csvCell('-5'), "'-5");
  assert.equal(csvCell('@cmd'), "'@cmd");
  assert.equal(csvCell(null), '');
  assert.equal(csvCell(0), '0');
});

test('toCsv: BOM, header, rows, getters', () => {
  const csv = toCsv([{ name: 'سامي', n: 2 }, { name: '=1+1', n: 3 }], [
    { key: 'name', label: 'الاسم' }, { get: (r) => r.n * 2, label: 'ضعف' },
  ]);
  assert.ok(csv.startsWith('﻿'));
  const lines = csv.slice(1).trimEnd().split('\r\n');
  assert.deepEqual(lines, ['الاسم,ضعف', 'سامي,4', "'=1+1,6"]);
});

test('CSV_COLUMNS: bookings/trips/users export without throwing on sparse rows', () => {
  for (const k of ['users', 'trips', 'bookings']) {
    const csv = toCsv([{}, { price: null, createdAt: NOW, departAt: NOW, role: 'captain', verified: true }], CSV_COLUMNS[k]);
    assert.ok(csv.length > 10);
  }
});

test('isoDate', () => {
  assert.equal(isoDate(0), '');
  assert.match(isoDate(NOW), /^2026-10-27 15:00$/);
});
