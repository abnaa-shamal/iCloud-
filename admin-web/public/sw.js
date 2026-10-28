// Minimal service worker: lets Chrome offer "Install app". It does not cache anything, so the dashboard always shows live data.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));
self.addEventListener('fetch', () => {});
