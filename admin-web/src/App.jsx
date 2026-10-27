import { useEffect, useMemo, useState } from 'react';
import {
  onAuthStateChanged, signInWithEmailAndPassword, signOut,
} from 'firebase/auth';
import {
  collection, doc, onSnapshot, setDoc, updateDoc, writeBatch,
} from 'firebase/firestore';
import { auth, db, configured } from './firebase.js';
import {
  CSV_COLUMNS, captainStats, computeOverview, isLiveTrip, matches, toCsv, waLink,
} from './lib.js';

/* ---------- small helpers ---------- */
const fmtTime = (ms) => (ms ? new Intl.DateTimeFormat('ar-JO', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(ms)) : '—');
const num = (n) => new Intl.NumberFormat('ar-JO').format(n ?? 0);
const fmtDay = (ms) => new Intl.DateTimeFormat('ar-JO', { weekday: 'short' }).format(new Date(ms));

/** Saves text as a file in the browser (used for the CSV exports). */
function download(filename, text) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url; a.download = filename;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
const stamp = () => new Date().toISOString().slice(0, 10);

/* Light / dark / follow-the-device. Stored only as a per-browser convenience; the page works if storage is blocked. */
const THEMES = ['auto', 'light', 'dark'];
const THEME_LABEL = { auto: '◐ تلقائي', light: '☀ فاتح', dark: '☾ داكن' };
function useTheme() {
  const [theme, setTheme] = useState(() => {
    try { const t = localStorage.getItem('shajara-admin-theme'); return THEMES.includes(t) ? t : 'auto'; } catch { return 'auto'; }
  });
  useEffect(() => {
    if (theme === 'auto') document.documentElement.removeAttribute('data-theme');
    else document.documentElement.setAttribute('data-theme', theme);
    try { localStorage.setItem('shajara-admin-theme', theme); } catch { /* storage blocked: ignore */ }
  }, [theme]);
  return [theme, () => setTheme(THEMES[(THEMES.indexOf(theme) + 1) % THEMES.length])];
}

/** Shows a "you are offline" strip; Firestore keeps working from cache but writes will wait. */
function useOnline() {
  const [on, setOn] = useState(typeof navigator === 'undefined' ? true : navigator.onLine);
  useEffect(() => {
    const up = () => setOn(true); const down = () => setOn(false);
    window.addEventListener('online', up); window.addEventListener('offline', down);
    return () => { window.removeEventListener('online', up); window.removeEventListener('offline', down); };
  }, []);
  return on;
}

const CAPTAIN_STATUS = { pending: 'بانتظار الموافقة', approved: 'موافَق عليه', rejected: 'مرفوض', suspended: 'موقوف' };
const TRIP_STATUS = { open: 'مفتوحة', full: 'ممتلئة', cancelled: 'ملغاة' };
const BOOKING_STATUS = {
  pending: 'بانتظار الكابتن', accepted: 'مقبول', rejected: 'مرفوض',
  cancelled_by_passenger: 'ألغاه الراكب', cancelled_by_captain: 'ألغاه الكابتن',
};
const TONE = { pending: 'warn', approved: 'ok', active: 'ok', open: 'ok', accepted: 'ok', full: 'info', rejected: 'bad', suspended: 'bad', cancelled: 'muted',
  cancelled_by_passenger: 'muted', cancelled_by_captain: 'muted' };

function useCollection(name, enabled) {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!enabled) return undefined;
    return onSnapshot(collection(db, name),
      (snap) => setRows(snap.docs.map((d) => ({ id: d.id, ...d.data() }))),
      (e) => setError(e.message));
  }, [name, enabled]);
  return { rows, error };
}

/* ---------- UI atoms ---------- */
function Badge({ tone = 'muted', children }) { return <span className={`badge ${tone}`}>{children}</span>; }
function Empty({ text }) { return <div className="empty">{text}</div>; }
function Stat({ label, value, hint, tone }) {
  return (
    <div className={`stat ${tone || ''}`}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  );
}
function Tabs({ items, value, onChange }) {
  return (
    <div className="chips" role="tablist">
      {items.map((t) => (
        <button key={t.key} role="tab" aria-selected={value === t.key} className={`chip ${value === t.key ? 'on' : ''}`} onClick={() => onChange(t.key)}>
          {t.label}{t.count != null && <b>{num(t.count)}</b>}
        </button>
      ))}
    </div>
  );
}
function Search({ value, onChange, placeholder }) {
  return <input className="search" value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} inputMode="search" />;
}
function Confirm({ text, onYes, onNo }) {
  return (
    <div className="modal-back" onClick={onNo}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <p>{text}</p>
        <div className="row end">
          <button className="btn ghost" onClick={onNo}>تراجع</button>
          <button className="btn danger" onClick={onYes}>تأكيد</button>
        </div>
      </div>
    </div>
  );
}

function ExportButton({ name, rows, columns }) {
  return (
    <button className="btn ghost small" disabled={!rows.length}
      onClick={() => download(`${name}-${stamp()}.csv`, toCsv(rows, columns))}>
      ⬇ تصدير CSV ({num(rows.length)})
    </button>
  );
}

/* Reverse verification: the user sends his code from his own number to the support WhatsApp; the admin compares and confirms. */
function VerifyRow({ u, act }) {
  if (!u.verifyCode) return null;
  const confirm = (value) => act(() => updateDoc(doc(db, 'users', u.id), { verified: value }),
    value ? `تم تأكيد رقم ${u.name}` : `تم إلغاء تأكيد رقم ${u.name}`);
  return (
    <>
      <div className="kv">
        <span>رمز التحقق</span>
        <b dir="ltr">{u.verifyCode} {u.verified ? <Badge tone="ok">رقم مؤكَّد</Badge> : <Badge tone="warn">بانتظار رسالة واتساب</Badge>}</b>
      </div>
      <div className="row">
        {u.verified
          ? <button className="btn ghost small" onClick={() => confirm(false)}>إلغاء التأكيد</button>
          : <button className="btn primary small" onClick={() => confirm(true)}>✓ وصلني الرمز من نفس الرقم</button>}
      </div>
    </>
  );
}

/* ---------- login ---------- */
function Login({ error }) {
  const [email, setEmail] = useState('');
  const [pass, setPass] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(error || '');
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setErr('');
    try { await signInWithEmailAndPassword(auth, email.trim(), pass); }
    catch { setErr('الإيميل أو كلمة السر غير صحيحة.'); }
    setBusy(false);
  };
  return (
    <main className="login">
      <form className="card login-card" onSubmit={submit}>
        <div className="logo">🌳</div>
        <h1>لوحة المشرف</h1>
        <p className="muted">شجرة 1</p>
        {!configured && <div className="alert bad">إعدادات Firebase ناقصة (VITE_FIREBASE_*). راجع ADMIN_SETUP.md.</div>}
        <label>الإيميل<input type="email" dir="ltr" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
        <label>كلمة السر<input type="password" dir="ltr" autoComplete="current-password" value={pass} onChange={(e) => setPass(e.target.value)} required /></label>
        {err && <div className="alert bad">{err}</div>}
        <button className="btn primary" disabled={busy || !configured}>{busy ? 'لحظة…' : 'دخول'}</button>
      </form>
    </main>
  );
}

/* ---------- pages ---------- */
function Overview({ users, trips, bookings, go }) {
  const o = useMemo(() => computeOverview({ users, trips, bookings, now: Date.now() }), [users, trips, bookings]);
  const maxRoute = o.routes[0]?.[1] || 1;
  const maxDay = Math.max(1, ...o.daily.map((d) => d.count));
  const weekTotal = o.daily.reduce((a, d) => a + d.count, 0);

  return (
    <>
      {o.attention.length > 0 && (
        <section className="card attention" aria-label="بحاجة انتباه">
          <h2>بحاجة انتباهك</h2>
          {o.attention.map((a) => (
            <button key={a.text} className={`attn ${a.tone}`} onClick={() => go(a.page)}>
              <span>{a.text}</span><i aria-hidden>←</i>
            </button>
          ))}
        </section>
      )}
      <div className="grid stats">
        <Stat label="الكباتن" value={num(o.captains)} hint={`${num(o.approved)} موافَق عليهم`} />
        <Stat label="الركاب" value={num(o.riders)} />
        <Stat label="رحلات قادمة" value={num(o.liveTrips)} hint={`${num(o.seatsFree)} مقعد فاضي`} />
        <Stat label="حجوزات فعّالة" value={num(o.activeBookings)} hint={`${num(o.acceptedBookings)} مقبولة`} />
        <Stat label="نسبة الإلغاء" value={`${num(o.cancelRate)}٪`} tone={o.cancelRate > 30 ? 'bad' : ''} hint={`${num(o.cancelled)} من ${num(o.totalBookings)} حجز`} />
      </div>
      <section className="card">
        <h2>الحجوزات آخر ٧ أيام <small className="muted">({num(weekTotal)})</small></h2>
        <div className="chart" role="img" aria-label={`حجوزات آخر سبعة أيام: ${o.daily.map((d) => num(d.count)).join('، ')}`}>
          {o.daily.map((d) => (
            <div className="col" key={d.start}>
              <b>{d.count ? num(d.count) : ''}</b>
              <i style={{ height: `${Math.max(d.count ? 8 : 2, (d.count / maxDay) * 100)}%` }} />
              <span>{fmtDay(d.start)}</span>
            </div>
          ))}
        </div>
      </section>
      <section className="card">
        <h2>أكثر الخطوط نشاطاً</h2>
        {o.routes.length === 0 ? <Empty text="ما في رحلات لسا." /> : o.routes.map(([name, n]) => (
          <div className="bar-row" key={name}>
            <span>{name}</span>
            <div className="bar"><i style={{ width: `${(n / maxRoute) * 100}%` }} /></div>
            <b>{num(n)}</b>
          </div>
        ))}
      </section>
    </>
  );
}

function Captains({ users, trips, bookings, act }) {
  const [tab, setTab] = useState('pending');
  const [q, setQ] = useState('');
  const [ask, setAsk] = useState(null);
  const captains = users.filter((u) => u.role === 'captain');
  const count = (s) => captains.filter((c) => c.status === s).length;
  const list = captains
    .filter((c) => c.status === tab)
    .filter((c) => matches(q, c.name, c.phone, c.carPlate, c.carType))
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

  const setStatus = (c, status) => act(() => updateDoc(doc(db, 'users', c.id), { status }), `تم تحديث ${c.name}`);
  const suspend = (c) => act(async () => {
    const batch = writeBatch(db);
    batch.update(doc(db, 'users', c.id), { status: 'suspended' });
    trips.filter((t) => t.captainUid === c.id && (t.status === 'open' || t.status === 'full'))
      .forEach((t) => batch.update(doc(db, 'trips', t.id), { status: 'cancelled', cancelReason: 'تم إيقاف حساب الكابتن' }));
    await batch.commit();
  }, `تم إيقاف ${c.name} وإلغاء رحلاته المفتوحة`);

  return (
    <>
      <Tabs value={tab} onChange={setTab} items={Object.keys(CAPTAIN_STATUS).map((k) => ({ key: k, label: CAPTAIN_STATUS[k], count: count(k) }))} />
      <div className="toolbar">
        <Search value={q} onChange={setQ} placeholder="بحث بالاسم أو الرقم أو اللوحة" />
        <ExportButton name="captains" rows={captains} columns={CSV_COLUMNS.users} />
      </div>
      {list.length === 0 ? <Empty text="ما في كباتن بهالحالة." /> : (
        <div className="list">
          {list.map((c) => {
            const s = captainStats(c.id, trips, bookings);
            const wa = waLink(c.phone);
            return (
            <article className="card item" key={c.id}>
              <div className="item-head">
                <div>
                  <h3>{c.name}</h3>
                  {wa ? <a className="phone" dir="ltr" href={wa} target="_blank" rel="noreferrer">{c.phone} ↗ واتساب</a>
                    : <span className="phone" dir="ltr">{c.phone}</span>}
                </div>
                <Badge tone={TONE[c.status]}>{CAPTAIN_STATUS[c.status]}</Badge>
              </div>
              <div className="kv"><span>السيارة</span><b>{c.carType || '—'} • {c.carColor || '—'}</b></div>
              <div className="kv"><span>اللوحة</span><b dir="ltr">{c.carPlate || '—'}</b></div>
              <div className="kv"><span>التسجيل</span><b>{fmtTime(c.createdAt)}</b></div>
              {c.status !== 'pending' && c.status !== 'rejected' && (
                <div className="kv"><span>النشاط</span>
                  <b>{num(s.trips)} رحلة • {num(s.bookings)} حجز{s.acceptRate != null && ` • قبول ${num(s.acceptRate)}٪`}{s.cancelledByCaptain > 0 && ` • ألغى ${num(s.cancelledByCaptain)}`}</b>
                </div>
              )}
              <VerifyRow u={c} act={act} />
              <div className="row">
                {(c.status === 'pending' || c.status === 'rejected') && <button className="btn primary" onClick={() => setStatus(c, 'approved')}>✓ موافقة</button>}
                {c.status === 'pending' && <button className="btn ghost danger-text" onClick={() => setAsk({ text: `رفض طلب ${c.name}؟`, run: () => setStatus(c, 'rejected') })}>رفض</button>}
                {c.status === 'approved' && <button className="btn ghost danger-text" onClick={() => setAsk({ text: `إيقاف ${c.name}؟ رح تنلغي رحلاته المفتوحة.`, run: () => suspend(c) })}>إيقاف</button>}
                {c.status === 'suspended' && <button className="btn primary" onClick={() => setStatus(c, 'approved')}>إعادة تفعيل</button>}
              </div>
            </article>
            );
          })}
        </div>
      )}
      {ask && <Confirm text={ask.text} onNo={() => setAsk(null)} onYes={() => { ask.run(); setAsk(null); }} />}
    </>
  );
}

function Riders({ users, act }) {
  const [q, setQ] = useState('');
  const [tab, setTab] = useState('all');
  const [ask, setAsk] = useState(null);
  const filters = {
    all: () => true,
    riders: (u) => u.role !== 'captain',
    captains: (u) => u.role === 'captain',
    unverified: (u) => !!u.verifyCode && !u.verified,
    blocked: (u) => !!u.riderSuspended,
  };
  const list = users
    .filter(filters[tab])
    .filter((u) => matches(q, u.name, u.phone))
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
  const toggle = (u, value) => act(() => updateDoc(doc(db, 'users', u.id), { riderSuspended: value }),
    value ? `تم منع ${u.name} من الحجز` : `تم السماح لـ ${u.name} بالحجز`);
  return (
    <>
      <Tabs value={tab} onChange={setTab} items={[
        { key: 'all', label: 'الكل', count: users.length },
        { key: 'riders', label: 'ركاب', count: users.filter(filters.riders).length },
        { key: 'captains', label: 'كباتن', count: users.filter(filters.captains).length },
        { key: 'unverified', label: 'غير موثّق', count: users.filter(filters.unverified).length },
        { key: 'blocked', label: 'ممنوع من الحجز', count: users.filter(filters.blocked).length },
      ]} />
      <div className="toolbar">
        <Search value={q} onChange={setQ} placeholder="بحث بالاسم أو الرقم" />
        <ExportButton name="users" rows={list} columns={CSV_COLUMNS.users} />
      </div>
      {list.length === 0 ? <Empty text="ما في مستخدمين." /> : (
        <div className="list">
          {list.map((u) => (
            <article className="card item" key={u.id}>
              <div className="item-head">
                <div>
                  <h3>{u.name}</h3>
                  {waLink(u.phone)
                    ? <a className="phone" dir="ltr" href={waLink(u.phone)} target="_blank" rel="noreferrer">{u.phone} ↗ واتساب</a>
                    : <span className="phone" dir="ltr">{u.phone}</span>}
                </div>
                <div className="stack">
                  <Badge tone="info">{u.role === 'captain' ? 'كابتن' : 'راكب'}</Badge>
                  {u.riderSuspended && <Badge tone="bad">ممنوع من الحجز</Badge>}
                </div>
              </div>
              <VerifyRow u={u} act={act} />
              <div className="row">
                {u.riderSuspended
                  ? <button className="btn primary" onClick={() => toggle(u, false)}>السماح بالحجز</button>
                  : <button className="btn ghost danger-text" onClick={() => setAsk({ text: `منع ${u.name} من الحجز؟`, run: () => toggle(u, true) })}>منع من الحجز</button>}
              </div>
            </article>
          ))}
        </div>
      )}
      {ask && <Confirm text={ask.text} onNo={() => setAsk(null)} onYes={() => { ask.run(); setAsk(null); }} />}
    </>
  );
}

function Trips({ trips, act }) {
  const [tab, setTab] = useState('live');
  const [q, setQ] = useState('');
  const [ask, setAsk] = useState(null);
  const now = Date.now();
  const isLive = (t) => isLiveTrip(t, now);
  const filters = {
    live: (t) => isLive(t),
    past: (t) => (t.status === 'open' || t.status === 'full') && t.departAt <= now,
    cancelled: (t) => t.status === 'cancelled',
  };
  const list = trips.filter(filters[tab])
    .filter((t) => matches(q, t.from, t.to, t.road, t.captainName, t.captainPhone))
    .sort((a, b) => (tab === 'live' ? a.departAt - b.departAt : b.departAt - a.departAt));
  const cancel = (t) => act(() => updateDoc(doc(db, 'trips', t.id), { status: 'cancelled', cancelReason: 'ألغاها المشرف' }), 'تم إلغاء الرحلة');
  return (
    <>
      <Tabs value={tab} onChange={setTab} items={[
        { key: 'live', label: 'قادمة', count: trips.filter(filters.live).length },
        { key: 'past', label: 'منتهية', count: trips.filter(filters.past).length },
        { key: 'cancelled', label: 'ملغاة', count: trips.filter(filters.cancelled).length },
      ]} />
      <div className="toolbar">
        <Search value={q} onChange={setQ} placeholder="بحث بالمكان أو الكابتن" />
        <ExportButton name="trips" rows={list} columns={CSV_COLUMNS.trips} />
      </div>
      {list.length === 0 ? <Empty text="ما في رحلات هون." /> : (
        <div className="list">
          {list.map((t) => (
            <article className="card item" key={t.id}>
              <div className="item-head">
                <h3>{t.from} ← {t.to}</h3>
                <Badge tone={TONE[t.status]}>{TRIP_STATUS[t.status]}</Badge>
              </div>
              <div className="kv"><span>الموعد</span><b>{fmtTime(t.departAt)}</b></div>
              <div className="kv"><span>الكابتن</span><b>{t.captainName} • <span dir="ltr">{t.captainPhone}</span></b></div>
              <div className="kv"><span>المقاعد</span><b>{num(t.seatsFree)} / {num(t.seatsTotal)} فاضي</b></div>
              {t.road && <div className="kv"><span>الطريق</span><b>{t.road}</b></div>}
              <div className="kv"><span>السعر</span><b>{t.price == null ? 'حسب الاتفاق' : `${num(t.price)} د.أ`}</b></div>
              {isLive(t) && <div className="row"><button className="btn ghost danger-text" onClick={() => setAsk({ text: 'إلغاء هالرحلة؟', run: () => cancel(t) })}>إلغاء الرحلة</button></div>}
            </article>
          ))}
        </div>
      )}
      {ask && <Confirm text={ask.text} onNo={() => setAsk(null)} onYes={() => { ask.run(); setAsk(null); }} />}
    </>
  );
}

function Bookings({ bookings, trips, act }) {
  const [tab, setTab] = useState('active');
  const [q, setQ] = useState('');
  const [ask, setAsk] = useState(null);
  // Cancelling an accepted booking must give its seats back to the trip in the SAME batch (the rules demand it).
  const cancel = (b) => act(async () => {
    const batch = writeBatch(db);
    batch.update(doc(db, 'bookings', b.id), { status: 'cancelled_by_passenger', reason: 'ألغاه المشرف', updatedAt: Date.now() });
    const t = trips.find((x) => x.id === b.tripId);
    if (b.status === 'accepted' && t && (t.status === 'open' || t.status === 'full')) {
      batch.update(doc(db, 'trips', t.id), {
        seatsFree: Math.min((t.seatsFree || 0) + (b.seats || 0), t.seatsTotal || 0), status: 'open', lastBookingId: b.id,
      });
    }
    await batch.commit();
  }, 'تم إلغاء الحجز وإرجاع المقاعد');
  const groups = {
    active: (b) => b.status === 'pending' || b.status === 'accepted',
    done: (b) => b.status === 'rejected',
    cancelled: (b) => b.status.startsWith('cancelled'),
  };
  const list = bookings.filter(groups[tab])
    .filter((b) => matches(q, b.passengerName, b.passengerPhone, b.captainName, b.captainPhone, b.tripSummary))
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
  return (
    <>
      <Tabs value={tab} onChange={setTab} items={[
        { key: 'active', label: 'فعّالة', count: bookings.filter(groups.active).length },
        { key: 'done', label: 'مرفوضة', count: bookings.filter(groups.done).length },
        { key: 'cancelled', label: 'ملغاة', count: bookings.filter(groups.cancelled).length },
      ]} />
      <div className="toolbar">
        <Search value={q} onChange={setQ} placeholder="بحث بالراكب أو الكابتن أو الرحلة" />
        <ExportButton name="bookings" rows={list} columns={CSV_COLUMNS.bookings} />
      </div>
      {list.length === 0 ? <Empty text="ما في حجوزات هون." /> : (
        <div className="list">
          {list.map((b) => (
            <article className="card item" key={b.id}>
              <div className="item-head">
                <h3>{b.tripSummary || 'رحلة'}</h3>
                <Badge tone={TONE[b.status]}>{BOOKING_STATUS[b.status] || b.status}</Badge>
              </div>
              <div className="kv"><span>الراكب</span><b>{b.passengerName} • <span dir="ltr">{b.passengerPhone}</span></b></div>
              <div className="kv"><span>الكابتن</span><b>{b.captainName} • <span dir="ltr">{b.captainPhone}</span></b></div>
              <div className="kv"><span>المقاعد</span><b>{num(b.seats)}</b></div>
              {b.dropoff && <div className="kv"><span>النزول</span><b>{b.dropoff}</b></div>}
              {b.reason && <div className="kv"><span>السبب</span><b>{b.reason}</b></div>}
              <div className="kv"><span>الطلب</span><b>{fmtTime(b.createdAt)}</b></div>
              {(b.status === 'pending' || b.status === 'accepted') && (
                <div className="row"><button className="btn ghost danger-text" onClick={() => setAsk({ text: 'إلغاء هالحجز؟ المقاعد بترجع للرحلة.', run: () => cancel(b) })}>إلغاء الحجز</button></div>
              )}
            </article>
          ))}
        </div>
      )}
      {ask && <Confirm text={ask.text} onNo={() => setAsk(null)} onYes={() => { ask.run(); setAsk(null); }} />}
    </>
  );
}

/* ---------- editable place lists (config/places) ---------- */
const MAIN_PLACES = ['عمّان', 'إربد', 'الزرقاء', 'الرمثا', 'الشجرة', 'الطرة', 'عمراوة', 'الذنيبة'];
const ROAD_PLACES = ['عمّان', 'الزرقاء'];                       // these two can have a list per road
const ROADS = ['الطريق الصحراوي', 'طريق الزرقاء'];
// Same starter lists as the apps; used until the admin saves his own.
const DEFAULT_AREAS = {
  'عمّان': ['الدوار الأول', 'الدوار الثاني', 'الدوار الثالث', 'الدوار الرابع', 'الدوار الخامس', 'الدوار السادس',
    'الدوار السابع', 'الدوار الثامن', 'صويلح', 'الجبيهة', 'طبربور', 'العبدلي', 'دوار الداخلية'],
  'إربد': ['مجمع عمّان الجديد', 'جامعة اليرموك', 'بوابة الاقتصاد الجديد'],
  'الزرقاء': ['وسط البلد', 'الجامعة الهاشمية', 'مجمع الزرقاء الجديد'],
  'الرمثا': ['جامعة العلوم والتكنولوجيا'],
};

function Places({ act }) {
  const [saved, setSaved] = useState(null);     // what Firestore holds (null = not loaded)
  const [draft, setDraft] = useState(null);     // what is being edited
  const [place, setPlace] = useState(MAIN_PLACES[0]);
  const [road, setRoad] = useState('');         // '' = all roads
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => onSnapshot(doc(db, 'config', 'places'), (snap) => {
    const remote = snap.exists() ? (snap.data().subAreas || {}) : {};
    const merged = { ...DEFAULT_AREAS, ...remote };
    setSaved(JSON.stringify(merged));
    setDraft((cur) => cur ?? merged);
  }), []);

  if (!draft) return <div className="skeleton"><i /><i /></div>;
  const key = road && ROAD_PLACES.includes(place) ? `${place}|${road}` : place;
  const list = draft[key] ?? (road ? draft[place] ?? [] : []);
  const dirty = JSON.stringify(draft) !== saved;
  const setList = (next) => setDraft({ ...draft, [key]: next });
  const add = () => {
    const v = text.trim();
    if (!v || v.length > 60 || list.includes(v)) return;
    setList([...list, v]); setText('');
  };
  const move = (i, d) => {
    const j = i + d; if (j < 0 || j >= list.length) return;
    const next = [...list]; [next[i], next[j]] = [next[j], next[i]]; setList(next);
  };
  const save = async () => {
    setBusy(true);
    const clean = Object.fromEntries(Object.entries(draft).map(([k, v]) => [k, v.map((x) => x.trim()).filter(Boolean)]));
    await act(() => setDoc(doc(db, 'config', 'places'), { subAreas: clean, updatedAt: Date.now() }), 'تم حفظ الأماكن، بتظهر بالتطبيقات مباشرة');
    setBusy(false);
  };

  return (
    <>
      <p className="muted">المناطق اللي بتظهر للراكب تحت كل مكان رئيسي. خيار «حسب اتفاق الكابتن» و«مكان آخر» بينضافوا تلقائياً.</p>
      <Tabs value={place} onChange={(p) => { setPlace(p); setRoad(''); }} items={MAIN_PLACES.map((m) => ({ key: m, label: m, count: (draft[m] || []).length }))} />
      {ROAD_PLACES.includes(place) && (
        <Tabs value={road} onChange={setRoad} items={[{ key: '', label: 'كل الطرق' }, ...ROADS.map((r) => ({ key: r, label: r }))]} />
      )}
      <section className="card">
        <h2>{place}{road && ` — ${road}`}</h2>
        {road && draft[key] === undefined && <div className="alert warn">هالطريق ما إله قائمة خاصة، بتستعمل قائمة «كل الطرق». أي مكان بتضيفه هون بينشئ قائمة خاصة فيه.</div>}
        {list.length === 0 ? <Empty text="ما في مناطق. الراكب رح يكتب المكان بإيده." /> : list.map((item, i) => (
          <div className="place-row" key={`${item}-${i}`}>
            <input value={item} maxLength={60} onChange={(e) => setList(list.map((x, k) => (k === i ? e.target.value : x)))} />
            <button className="btn ghost small" onClick={() => move(i, -1)} aria-label="فوق" disabled={i === 0}>↑</button>
            <button className="btn ghost small" onClick={() => move(i, 1)} aria-label="تحت" disabled={i === list.length - 1}>↓</button>
            <button className="btn ghost small danger-text" onClick={() => setList(list.filter((_, k) => k !== i))} aria-label="حذف">✕</button>
          </div>
        ))}
        <div className="place-row">
          <input value={text} placeholder="مكان جديد" maxLength={60} onChange={(e) => setText(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && add()} />
          <button className="btn primary small" onClick={add}>إضافة</button>
        </div>
      </section>
      <div className="row">
        <button className="btn primary" disabled={!dirty || busy} onClick={save}>{busy ? 'لحظة…' : 'حفظ التغييرات'}</button>
        {dirty && <button className="btn ghost" onClick={() => setDraft(JSON.parse(saved))}>تراجع عن التعديل</button>}
      </div>
    </>
  );
}

/* ---------- shell ---------- */
const NAV = [
  { key: 'overview', label: 'نظرة عامة', icon: '◧' },
  { key: 'captains', label: 'الكباتن', icon: '🚗' },
  { key: 'riders', label: 'الركاب', icon: '👤' },
  { key: 'trips', label: 'الرحلات', icon: '🛣️' },
  { key: 'bookings', label: 'الحجوزات', icon: '🎫' },
  { key: 'places', label: 'الأماكن', icon: '📍' },
];

function Dashboard({ user }) {
  const [page, setPage] = useState('overview');
  const [toast, setToast] = useState(null);
  const [theme, cycleTheme] = useTheme();
  const online = useOnline();
  const users = useCollection('users', true);
  const trips = useCollection('trips', true);
  const bookings = useCollection('bookings', true);
  const loadError = users.error || trips.error || bookings.error;
  const ready = users.rows && trips.rows && bookings.rows;
  const pendingCount = (users.rows || []).filter((u) => u.role === 'captain' && u.status === 'pending').length;

  const act = async (fn, okText) => {
    try { await fn(); setToast({ ok: true, text: okText }); }
    catch (e) { setToast({ ok: false, text: `ما نفّذت العملية: ${e.code || e.message}` }); }
    setTimeout(() => setToast(null), 4000);
  };

  return (
    <div className="shell">
      <header className="top">
        <div className="brand"><span>🌳</span> شجرة 1 <small>مشرف</small></div>
        <div className="who">
          <span dir="ltr">{user.email}</span>
          <button className="btn ghost small" onClick={cycleTheme} aria-label="تغيير المظهر">{THEME_LABEL[theme]}</button>
          <button className="btn ghost small" onClick={() => signOut(auth)}>خروج</button>
        </div>
      </header>
      {!online && <div className="alert warn offline" role="status">ما في اتصال بالإنترنت. العمليات رح تنتظر لين يرجع الاتصال.</div>}
      <nav className="nav" aria-label="التنقل">
        {NAV.map((n) => (
          <button key={n.key} className={`nav-item ${page === n.key ? 'on' : ''}`} onClick={() => setPage(n.key)}>
            <span aria-hidden>{n.icon}</span>{n.label}
            {n.key === 'captains' && pendingCount > 0 && <i className="dot">{num(pendingCount)}</i>}
          </button>
        ))}
      </nav>
      <main className="content">
        <h1 className="page-title">{NAV.find((n) => n.key === page).label}</h1>
        {loadError && <div className="alert bad">تعذّر تحميل البيانات: {loadError}</div>}
        {!ready && !loadError && <div className="skeleton"><i /><i /><i /></div>}
        {ready && page === 'overview' && <Overview users={users.rows} trips={trips.rows} bookings={bookings.rows} go={setPage} />}
        {ready && page === 'captains' && <Captains users={users.rows} trips={trips.rows} bookings={bookings.rows} act={act} />}
        {ready && page === 'riders' && <Riders users={users.rows} act={act} />}
        {ready && page === 'trips' && <Trips trips={trips.rows} act={act} />}
        {ready && page === 'bookings' && <Bookings bookings={bookings.rows} trips={trips.rows} act={act} />}
        {ready && page === 'places' && <Places act={act} />}
      </main>
      {toast && <div className={`toast ${toast.ok ? 'ok' : 'bad'}`} role="status">{toast.text}</div>}
    </div>
  );
}

/* ---------- root: only an account carrying the admin claim gets in ---------- */
export default function App() {
  const [user, setUser] = useState(undefined);
  const [isAdmin, setIsAdmin] = useState(null);

  useEffect(() => onAuthStateChanged(auth, async (u) => {
    setUser(u);
    if (!u) { setIsAdmin(null); return; }
    try {
      const r = await u.getIdTokenResult(true);
      setIsAdmin(r.claims.admin === true);
    } catch { setIsAdmin(false); }
  }), []);

  if (user === undefined || (user && isAdmin === null)) return <div className="splash">🌳</div>;
  if (!user) return <Login />;
  if (!isAdmin) {
    return (
      <main className="login">
        <div className="card login-card">
          <div className="logo">⛔</div>
          <h1>ما عندك صلاحية</h1>
          <p className="muted">هذا الحساب مش مشرف. الصلاحية بتنعطى من خارج التطبيق (راجع ADMIN_SETUP.md).</p>
          <button className="btn primary" onClick={() => signOut(auth)}>خروج</button>
        </div>
      </main>
    );
  }
  return <Dashboard user={user} />;
}
