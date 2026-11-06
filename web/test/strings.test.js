// Every engine code and every UI key used by the page must have English, Hindi and Marathi text.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { FINDINGS } from '../src/engine/model.js';
import { Rec } from '../src/engine/model.js';
import { appStrings } from '../src/strings.js';
import { LANGS, t } from '../src/i18n.js';

const i18nSource = readFileSync(new URL('../src/i18n.js', import.meta.url), 'utf8');
const webKeys = (lang) => {
  const block = i18nSource.split(new RegExp(`\\n  ${lang}: \\{`))[1].split('\n  },')[0];
  return new Set([...block.matchAll(/^\s+(\w+):/gm)].map((m) => m[1]));
};
const has = (lang, key) => key in appStrings[lang] || webKeys(lang).has(key);

test('finding codes and recommendations are translated', () => {
  for (const lang of LANGS) {
    for (const code of Object.keys(FINDINGS)) assert.ok(has(lang, `f_${code.toLowerCase()}`), `${lang}: f_${code.toLowerCase()}`);
    for (const rec of Object.keys(Rec)) assert.ok(has(lang, `rec_${rec.toLowerCase()}`), `${lang}: rec_${rec.toLowerCase()}`);
  }
});

test('every key used by the page exists in every language', () => {
  const sources = ['../index.html', '../src/app.js'].map((p) => readFileSync(new URL(p, import.meta.url), 'utf8')).join('\n');
  const keys = new Set([
    ...[...sources.matchAll(/data-i18n(?:-placeholder)?="(\w+)"/g)].map((m) => m[1]),
    ...[...sources.matchAll(/\bt\('(\w+)'/g)].map((m) => m[1]),
    ...[...sources.matchAll(/'((?:cloud|report|kind|web|qr|upi|rep|ai)_\w+)'/g)].map((m) => m[1]),
  ]);
  keys.delete('qr_code'); // BarcodeDetector format name, not a UI string
  for (const s of ['malicious', 'suspicious', 'clean', 'unknown', 'error', 'not_configured', 'rate_limited']) keys.add(`provider_status_${s}`);
  for (let i = 1; i <= 6; i++) keys.add(`tip_${i}`);
  assert.ok(keys.size > 50, `only ${keys.size} keys found`);
  const missing = [];
  for (const lang of LANGS) for (const key of keys) if (!has(lang, key)) missing.push(`${lang}:${key}`);
  assert.deepEqual(missing, []);
});

test('placeholders are filled', () => {
  assert.equal(t('score_of', 87), '87/100');
  assert.equal(t('report_risk', 'High', 90), 'Risk: High (90/100)');
});
