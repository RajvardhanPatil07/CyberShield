import { appendFile, mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { sha256 } from './support.js';

/**
 * Storage for opt-in, redacted scam-message contributions (the ML training corpus seed).
 *
 * Privacy: records carry only the already-redacted text, the user's label, the detected language and
 * the local finding codes. No install id, IP, phone number or account identifier is stored. The id is
 * a content hash of text+label, which also deduplicates identical submissions downstream.
 */
export function buildContributionRecord(req, nowMs) {
  return {
    id: sha256(`${req.text}\u0000${req.label}`),
    ts: new Date(nowMs).toISOString(),
    label: req.label,
    language: req.language,
    kind: req.kind,
    findings: req.findings,
    text: req.text,
  };
}

/** Appends contributions as daily JSONL files under [dir]. */
export function fileContributionSink(dir, { now = Date.now } = {}) {
  return {
    configured: true,
    async store(record) {
      await mkdir(dir, { recursive: true });
      const day = new Date(now()).toISOString().slice(0, 10);
      await appendFile(join(dir, `contributions-${day}.jsonl`), JSON.stringify(record) + '\n', 'utf8');
    },
  };
}

/** The default when CONTRIBUTIONS_DIR is unset: the endpoint reports "not configured". */
export const nullContributionSink = {
  configured: false,
  async store() {
    throw new Error('contribution sink not configured');
  },
};
