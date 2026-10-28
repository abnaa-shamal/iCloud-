// Sample data in the same shape as the real Firestore collections (names and numbers are made up).
const H = 3600e3, D = 24 * H, now = Date.now();
const users = [
  { id: 'c1', role: 'captain', name: 'أحمد المحمد', phone: '0791234567', status: 'approved', carType: 'كيا سيراتو', carColor: 'أبيض', carPlate: '12-34567', createdAt: now - 9 * D, verifyCode: '482913', verified: true },
  { id: 'c2', role: 'captain', name: 'خالد العلي', phone: '0777654321', status: 'pending', carType: 'هيونداي إلنترا', carColor: 'رمادي', carPlate: '23-45678', createdAt: now - 2 * H, verifyCode: '730155', verified: false },
  { id: 'c3', role: 'captain', name: 'محمود الصالح', phone: '0788112233', status: 'pending', carType: 'تويوتا كامري', carColor: 'أسود', carPlate: '34-56789', createdAt: now - 5 * H, verifyCode: '115820', verified: false },
  { id: 'c4', role: 'captain', name: 'يوسف الخطيب', phone: '0795556677', status: 'suspended', carType: 'نيسان صني', carColor: 'فضي', carPlate: '45-67890', createdAt: now - 20 * D },
  { id: 'p1', role: 'passenger', name: 'سارة أحمد', phone: '0796001122', createdAt: now - 3 * D, verifyCode: '221904', verified: true },
  { id: 'p2', role: 'passenger', name: 'محمد خالد', phone: '0786003344', createdAt: now - 1 * D, verifyCode: '908711', verified: false },
  { id: 'p3', role: 'passenger', name: 'ليلى حسن', phone: '0776005566', createdAt: now - 6 * D, riderSuspended: true },
  { id: 'p4', role: 'passenger', name: 'عمر نادر', phone: '0796007788', createdAt: now - 12 * D },
];
const trips = [
  { id: 't1', captainUid: 'c1', captainName: 'أحمد المحمد', captainPhone: '0791234567', from: 'الشجرة', to: 'عمّان', road: 'الطريق الصحراوي', departAt: now + 3 * H, seatsTotal: 4, seatsFree: 2, price: 3, status: 'open' },
  { id: 't2', captainUid: 'c1', captainName: 'أحمد المحمد', captainPhone: '0791234567', from: 'عمّان', to: 'الشجرة', road: 'طريق الزرقاء', departAt: now + 27 * H, seatsTotal: 4, seatsFree: 4, price: 3, status: 'open' },
  { id: 't3', captainUid: 'c1', captainName: 'أحمد المحمد', captainPhone: '0791234567', from: 'الشجرة', to: 'إربد', road: '', departAt: now + 5 * H, seatsTotal: 4, seatsFree: 0, price: 1, status: 'full' },
  { id: 't4', captainUid: 'c4', captainName: 'يوسف الخطيب', captainPhone: '0795556677', from: 'الذنيبة', to: 'الزرقاء', road: '', departAt: now - 2 * D, seatsTotal: 4, seatsFree: 1, price: null, status: 'cancelled' },
];
const mk = (i, d, status, seats, trip, pass) => ({
  id: `b${i}`, status, seats, tripId: trip.id, tripSummary: `${trip.from} ← ${trip.to}`, departAt: trip.departAt,
  passengerName: pass.name, passengerPhone: pass.phone, captainUid: trip.captainUid, captainName: trip.captainName, captainPhone: trip.captainPhone,
  createdAt: now - d * D - 2 * H, dropoff: 'وسط البلد',
});
const bookings = [
  mk(1, 0, 'pending', 1, trips[0], users[5]), mk(2, 0, 'accepted', 2, trips[0], users[4]), mk(3, 1, 'accepted', 1, trips[2], users[7]),
  mk(4, 1, 'accepted', 1, trips[2], users[4]), mk(5, 2, 'rejected', 1, trips[0], users[5]), mk(6, 3, 'cancelled_by_passenger', 1, trips[1], users[7]),
  mk(7, 4, 'accepted', 2, trips[0], users[4]), mk(8, 5, 'accepted', 1, trips[2], users[5]), mk(9, 5, 'pending', 1, trips[1], users[7]), mk(10, 6, 'accepted', 1, trips[0], users[4]),
];
const data = { users, trips, bookings };
export const collection = (_db, name) => ({ __name: name });
export const doc = (_db, ...parts) => ({ __path: parts.join('/') });
export const onSnapshot = (ref, cb) => {
  if (ref.__name) setTimeout(() => cb({ docs: (data[ref.__name] || []).map((r) => ({ id: r.id, data: () => { const { id, ...rest } = r; return rest; } })) }), 0);
  else setTimeout(() => cb({ exists: () => false, data: () => ({}) }), 0);
  return () => {};
};
export const setDoc = async () => {};
export const updateDoc = async () => {};
export const writeBatch = () => ({ update() {}, set() {}, commit: async () => {} });
export const getFirestore = () => ({});
