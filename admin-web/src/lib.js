/* Pure helpers for the admin dashboard: no React, no Firebase, so they can be unit-tested with `node --test`. */

export const DAY_MS = 86_400_000;

export const isLiveTrip = (t, now) => (t.status === 'open' || t.status === 'full') && (t.departAt || 0) > now;
export const isActiveBooking = (b) => b.status === 'pending' || b.status === 'accepted';
export const isCancelledBooking = (b) => String(b.status || '').startsWith('cancelled');

/** Local midnight of the day containing `ms`. */
export function startOfDay(ms) {
  const d = new Date(ms);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

/**
 * Bookings created per day for the last `days` days (oldest first, today last).
 * Uses calendar days (not 24h blocks) so it stays correct across a time-zone change.
 */
export function dailyCounts(items, now, days = 7, field = 'createdAt') {
  const d0 = new Date(now);
  const buckets = [];
  for (let k = days - 1; k >= 0; k -= 1) {
    const start = new Date(d0.getFullYear(), d0.getMonth(), d0.getDate() - k).getTime();
    const end = new Date(d0.getFullYear(), d0.getMonth(), d0.getDate() - k + 1).getTime();
    buckets.push({ start, end, count: 0 });
  }
  items.forEach((it) => {
    const ts = it[field];
    if (typeof ts !== 'number') return;
    const b = buckets.find((x) => ts >= x.start && ts < x.end);
    if (b) b.count += 1;
  });
  return buckets;
}

/** Top routes by number of trips, newest data first when counts tie. */
export function topRoutes(trips, limit = 6) {
  const m = new Map();
  trips.forEach((t) => {
    if (!t.from || !t.to) return;
    const k = `${t.from} ← ${t.to}`;
    m.set(k, (m.get(k) || 0) + 1);
  });
  return [...m.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'ar')).slice(0, limit);
}

/**
 * Everything the overview page shows, computed once.
 * `attention` lists things the admin should look at, most urgent first.
 */
export function computeOverview({ users, trips, bookings, now }) {
  const captains = users.filter((u) => u.role === 'captain');
  const riders = users.filter((u) => u.role === 'passenger');
  const pending = captains.filter((u) => u.status === 'pending');
  const approved = captains.filter((u) => u.status === 'approved');
  const unverified = users.filter((u) => u.verifyCode && !u.verified);
  const liveTrips = trips.filter((t) => isLiveTrip(t, now));
  const seatsFree = liveTrips.reduce((a, t) => a + (t.seatsFree || 0), 0);
  const active = bookings.filter(isActiveBooking);
  const accepted = bookings.filter((b) => b.status === 'accepted');
  const cancelled = bookings.filter(isCancelledBooking);
  const cancelRate = bookings.length ? Math.round((cancelled.length / bookings.length) * 100) : 0;

  // A pending booking whose trip leaves within 3 hours still has no answer from the captain.
  const stalePending = bookings.filter((b) => b.status === 'pending' && b.departAt > now && b.departAt - now <= 3 * 3_600_000);
  // Trips leaving within 24h with nobody booked at all.
  const emptySoon = liveTrips.filter((t) => t.departAt - now <= DAY_MS && t.seatsFree === t.seatsTotal);

  const attention = [];
  if (pending.length) attention.push({ tone: 'warn', page: 'captains', text: `${pending.length} كابتن بانتظار الموافقة` });
  if (stalePending.length) attention.push({ tone: 'bad', page: 'bookings', text: `${stalePending.length} حجز بدون رد والرحلة قريبة (أقل من ٣ ساعات)` });
  if (unverified.length) attention.push({ tone: 'info', page: 'riders', text: `${unverified.length} حساب لسا ما تأكد رقمه` });
  if (emptySoon.length) attention.push({ tone: 'muted', page: 'trips', text: `${emptySoon.length} رحلة خلال ٢٤ ساعة بدون أي حجز` });
  if (bookings.length >= 10 && cancelRate > 30) attention.push({ tone: 'bad', page: 'bookings', text: `نسبة الإلغاء مرتفعة (${cancelRate}٪)` });

  return {
    captains: captains.length,
    approved: approved.length,
    pending: pending.length,
    riders: riders.length,
    liveTrips: liveTrips.length,
    seatsFree,
    activeBookings: active.length,
    acceptedBookings: accepted.length,
    cancelled: cancelled.length,
    totalBookings: bookings.length,
    cancelRate,
    unverified: unverified.length,
    routes: topRoutes(trips),
    daily: dailyCounts(bookings, now, 7),
    attention,
  };
}

/** Numbers for one captain card. */
export function captainStats(captainId, trips, bookings) {
  const myTrips = trips.filter((t) => t.captainUid === captainId);
  const myBookings = bookings.filter((b) => b.captainUid === captainId);
  const decided = myBookings.filter((b) => b.status !== 'pending');
  const accepted = myBookings.filter((b) => b.status === 'accepted').length;
  const byCaptain = myBookings.filter((b) => b.status === 'cancelled_by_captain').length;
  return {
    trips: myTrips.length,
    bookings: myBookings.length,
    accepted,
    // share of answered requests that ended accepted (cancellations by the passenger don't count against the captain)
    acceptRate: decided.length ? Math.round((accepted / decided.length) * 100) : null,
    cancelledByCaptain: byCaptain,
  };
}

/** True when every word the admin typed appears somewhere in the haystack (case/space-insensitive). */
export function matches(query, ...fields) {
  const words = String(query ?? '').toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return true;
  const hay = fields.map((f) => String(f ?? '').toLowerCase()).join(' ');
  return words.every((w) => hay.includes(w));
}

/** Jordanian mobile 07XXXXXXXX -> https://wa.me/9627XXXXXXXX (optional prefilled text). null when not a valid number. */
export function waLink(phone, text) {
  const p = String(phone ?? '').trim();
  if (!/^07[789][0-9]{7}$/.test(p)) return null;
  const base = `https://wa.me/962${p.slice(1)}`;
  return text ? `${base}?text=${encodeURIComponent(text)}` : base;
}

/** A CSV cell. Quotes are doubled; a leading = + - @ (spreadsheet formula) is neutralised so user-typed names can't run formulas. */
export function csvCell(v) {
  let s = v == null ? '' : String(v);
  if (/^[=+\-@\t\r]/.test(s)) s = `'${s}`;
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/** columns: [{ key | get(row), label }]. Returns text starting with a BOM so Excel reads Arabic correctly. */
export function toCsv(rows, columns) {
  const head = columns.map((c) => csvCell(c.label)).join(',');
  const body = rows.map((r) => columns.map((c) => csvCell(c.get ? c.get(r) : r[c.key])).join(','));
  return `﻿${[head, ...body].join('\r\n')}\r\n`;
}

export function isoDate(ms) {
  if (!ms) return '';
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export const CSV_COLUMNS = {
  users: [
    { key: 'name', label: 'الاسم' }, { key: 'phone', label: 'الهاتف' },
    { get: (u) => (u.role === 'captain' ? 'كابتن' : 'راكب'), label: 'النوع' }, { key: 'status', label: 'الحالة' },
    { key: 'carType', label: 'نوع السيارة' }, { key: 'carColor', label: 'اللون' }, { key: 'carPlate', label: 'اللوحة' },
    { get: (u) => (u.verified ? 'نعم' : 'لا'), label: 'رقم مؤكد' }, { get: (u) => isoDate(u.createdAt), label: 'تاريخ التسجيل' },
  ],
  trips: [
    { key: 'from', label: 'من' }, { key: 'to', label: 'إلى' }, { key: 'road', label: 'الطريق' },
    { get: (t) => isoDate(t.departAt), label: 'الموعد' }, { key: 'captainName', label: 'الكابتن' }, { key: 'captainPhone', label: 'هاتف الكابتن' },
    { key: 'seatsTotal', label: 'المقاعد' }, { key: 'seatsFree', label: 'الفاضي' },
    { get: (t) => (t.price == null ? 'حسب الاتفاق' : t.price), label: 'السعر' }, { key: 'status', label: 'الحالة' },
  ],
  bookings: [
    { key: 'tripSummary', label: 'الرحلة' }, { key: 'passengerName', label: 'الراكب' }, { key: 'passengerPhone', label: 'هاتف الراكب' },
    { key: 'captainName', label: 'الكابتن' }, { key: 'captainPhone', label: 'هاتف الكابتن' }, { key: 'seats', label: 'المقاعد' },
    { key: 'dropoff', label: 'النزول' }, { key: 'status', label: 'الحالة' }, { key: 'reason', label: 'السبب' },
    { get: (b) => isoDate(b.createdAt), label: 'وقت الطلب' },
  ],
};
