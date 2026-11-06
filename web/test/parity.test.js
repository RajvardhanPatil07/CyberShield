// Checks that the JavaScript engine gives exactly the same results as the Android (Kotlin) engine.
// The reference file is produced by android/app/src/test/.../WebReferenceTest.kt; see web/README.md.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { analyzeLink, analyzeMessage, analyzeQr, forReport, redact } from '../src/engine/index.js';

const reference = JSON.parse(readFileSync(new URL('./fixtures/kotlin-reference.json', import.meta.url), 'utf8'));

const shape = (r) => ({
  score: r.score,
  level: r.level,
  language: r.language,
  findings: r.findings.map((f) => ({ code: f.code, evidence: f.evidence, start: f.start, end: f.end, extraWeight: f.extraWeight })),
  recommendations: r.recommendations,
  links: r.links.map((l) => ({ url: l.url, score: l.result.score })),
});

function actual(item) {
  if (item.kind === 'message') {
    return { result: shape(analyzeMessage(item.input)), redacted: redact(item.input), report: forReport(item.input) };
  }
  if (item.kind === 'url') return { result: shape(analyzeLink(item.input)) };
  const q = analyzeQr(item.input);
  return { result: shape(q.assessment), qrType: q.type, openTarget: q.openTarget, summary: q.summary };
}

function expected(item) {
  const { id, kind, input, ...rest } = item;
  return rest;
}

test('reference file covers every engine', () => {
  const kinds = new Set(reference.map((r) => r.kind));
  assert.deepEqual([...kinds].sort(), ['message', 'qr', 'url']);
  assert.ok(reference.length > 300, `only ${reference.length} reference cases`);
});

for (const kind of ['message', 'url', 'qr']) {
  test(`${kind} results match the Kotlin engine`, () => {
    const mismatches = [];
    for (const item of reference.filter((r) => r.kind === kind)) {
      try {
        assert.deepEqual(actual(item), expected(item));
      } catch (err) {
        mismatches.push(`${item.id} ${JSON.stringify(item.input).slice(0, 80)}\n${err.message.split('\n').slice(0, 30).join('\n')}`);
      }
    }
    assert.equal(mismatches.length, 0, `${mismatches.length} mismatch(es):\n\n${mismatches.slice(0, 8).join('\n\n')}`);
  });
}
