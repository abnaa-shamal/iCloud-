// Photographs the real dashboard (built with `vite build --mode shots`) into PNG files: node shots/shoot.mjs <outDir>
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const out = process.argv[2] || 'shots-out';
mkdirSync(out, { recursive: true });
const base = process.env.SHOT_URL || 'http://localhost:4173';

const browser = await chromium.launch();
async function run(name, viewport, steps) {
  const page = await browser.newPage({ viewport, deviceScaleFactor: 2 });
  await page.goto(base + (name.includes('login') ? '/#login' : '/'));
  await page.waitForTimeout(700);
  for (const s of steps) await s(page);
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${out}/${name}.png` });
  await page.close();
}
const nav = (label) => async (p) => { await p.getByRole('button', { name: label }).first().click(); await p.waitForTimeout(300); };
const D = { width: 1280, height: 860 }, M = { width: 390, height: 844 };
await run('1-desktop-overview', D, []);
await run('2-desktop-captains', D, [nav('الكباتن')]);
await run('3-desktop-bookings', D, [nav('الحجوزات')]);
await run('4-desktop-trips', D, [nav('الرحلات')]);
await run('5-desktop-login', D, []);
await run('6-mobile-overview', M, []);
await run('7-mobile-captains', M, [nav('الكباتن')]);
await run('8-desktop-places', D, [nav('الأماكن')]);
await browser.close();
