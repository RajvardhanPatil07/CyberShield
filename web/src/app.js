// CyberShield web demo. All checks run in the browser with the same rules as the Android app (src/engine).
// Cloud checks run only when the user taps them and confirms, like the app.
import { checkUrl, cloudConfigured, explain } from './cloud.js';
import { config } from './config.js';
import { analyzeLink, analyzeMessage, analyzeQr, FINDINGS, forReport, MAX_INPUT, parseUrl, redact } from './engine/index.js';
import { examples } from './examples.js';
import { detectedLanguageText, findingText, getLang, levelText, qrTypeText, recText, setLang, t } from './i18n.js';

const $ = (id) => document.getElementById(id);

/** Builds an element. Text children are inserted as text nodes, never as HTML. */
function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) if (c !== null && c !== undefined && c !== false) el.append(c instanceof Node ? c : String(c));
  return el;
}

function svg(tag, attrs = {}, ...children) {
  const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
  el.append(...children);
  return el;
}

// ---------------------------------------------------------------- state

const state = {
  tab: 'message',
  /** Per tab: { kind, input, assessment, qr?, rep?, ai? } */
  results: { message: null, link: null, qr: null },
};

function render() {
  const area = $('result');
  area.replaceChildren();
  const r = state.results[state.tab];
  if (!r) {
    area.append(h('div', { class: 'placeholder card' }, h('img', { src: 'favicon.svg', alt: '', width: 64, height: 64 }), h('p', {}, t('web_privacy'))));
    return;
  }
  area.append(resultCard(r));
  if (r.rep) area.append(reputationCard(r.rep));
  if (r.ai) area.append(aiCard(r.ai));
}

function show(tab, result) {
  state.results[tab] = result;
  if (state.tab === tab) render();
  if (window.matchMedia('(max-width: 900px)').matches) $('result').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// ---------------------------------------------------------------- result rendering

function ring(score) {
  const r = 46;
  const c = 2 * Math.PI * r;
  const value = svg('circle', { class: 'value', cx: 56, cy: 56, r, 'stroke-dasharray': c, 'stroke-dashoffset': c });
  requestAnimationFrame(() => requestAnimationFrame(() => value.setAttribute('stroke-dashoffset', String(c * (1 - score / 100)))));
  return h('div', { class: 'ring', role: 'img', 'aria-label': t('score_of', score) },
    svg('svg', { viewBox: '0 0 112 112', 'aria-hidden': 'true' }, svg('circle', { class: 'track', cx: 56, cy: 56, r }), value),
    h('span', { class: 'num', 'aria-hidden': 'true' }, String(score)));
}

const weight = (code) => FINDINGS[code][0];
// SUSPICIOUS_LINK has weight 0 because its score comes from the link's own result (extraWeight).
const isStrong = (code) => weight(code) >= 35 || code === 'SUSPICIOUS_LINK';

/** One entry per finding code, with its evidence phrases. */
function reasons(assessment) {
  const byCode = new Map();
  for (const f of assessment.findings) {
    if (!byCode.has(f.code)) byCode.set(f.code, []);
    if (f.evidence && !byCode.get(f.code).includes(f.evidence)) byCode.get(f.code).push(f.evidence);
  }
  if (byCode.size === 0) return null;
  return h('ul', { class: 'reasons' }, [...byCode].map(([code, evidence]) =>
    h('li', {},
      h('span', { class: `bullet ${isStrong(code) ? 'strong' : weight(code) === 0 || code === 'CONTAINS_LINK' ? 'info' : ''}`, 'aria-hidden': 'true' }),
      h('div', {}, h('strong', {}, findingText(code)), evidence.length > 0 && h('span', { class: 'evidence' }, evidence.slice(0, 3).map((e) => `“${e}”`).join(', '))))));
}

/** The analysed message with suspicious phrases marked. */
function highlighted(text, assessment) {
  const ranges = assessment.findings
    .filter((f) => f.start !== null && f.code !== 'CONTAINS_LINK')
    .map((f) => ({ start: f.start, end: f.end, strong: isStrong(f.code) }))
    .sort((a, b) => a.start - b.start);
  const merged = [];
  for (const r of ranges) {
    const last = merged[merged.length - 1];
    if (last && r.start <= last.end) {
      last.end = Math.max(last.end, r.end);
      last.strong ||= r.strong;
    } else merged.push({ ...r });
  }
  if (merged.length === 0) return null;
  const out = h('div', { class: 'highlight' });
  let pos = 0;
  for (const r of merged) {
    out.append(text.slice(pos, r.start), h('mark', { class: r.strong ? 'strong' : null }, text.slice(r.start, r.end)));
    pos = r.end;
  }
  out.append(text.slice(pos));
  return out;
}

function levelPill(a) {
  return h('span', { class: `pill level-${a.level}` }, `${levelText(a.level)} · ${a.score}`);
}

const canCheckReputation = (url) => {
  const p = url && parseUrl(url);
  return Boolean(p && (p.scheme === 'http' || p.scheme === 'https') && p.host);
};

function resultCard(r) {
  const a = r.assessment;
  const card = h('article', { class: `card result ${a.level}` });
  card.append(h('div', { class: 'result-head' },
    h('div', {},
      h('p', { class: 'eyebrow' }, t('result_title')),
      h('p', { class: 'verdict' }, levelText(a.level))),
    ring(a.score)));

  if (r.kind === 'message') {
    card.append(h('p', { class: 'meta' }, t('detected_language', detectedLanguageText(a.language))));
  }
  if (r.kind === 'link') {
    const p = parseUrl(r.input);
    if (p && p.host) card.append(h('p', { class: 'meta' }, t('web_site', p.unicodeHost !== p.host ? `${p.unicodeHost} (${p.host})` : p.host)));
  }
  if (r.kind === 'qr') {
    card.append(h('p', { class: 'meta' }, t('qr_type', qrTypeText(r.qr.type))));
    card.append(h('h3', {}, t('qr_decoded')), h('pre', { class: 'decoded' }, r.input));
    if (r.qr.upi) {
      const u = r.qr.upi;
      const val = (v) => v ?? t('not_provided');
      card.append(h('h3', {}, t('qr_type_upi')), h('dl', { class: 'kv' },
        h('dt', {}, t('upi_payee')), h('dd', {}, val(u.payeeAddress)),
        h('dt', {}, t('upi_name')), h('dd', {}, val(u.payeeName)),
        h('dt', {}, t('upi_amount')), h('dd', {}, u.amount ? `₹${u.amount}${u.currency && u.currency.toUpperCase() !== 'INR' ? ` ${u.currency}` : ''}` : t('not_provided')),
        h('dt', {}, t('upi_note')), h('dd', {}, val(u.note))));
      card.append(h('p', { class: 'warning' }, t('upi_warning')));
    }
  }

  if (r.kind === 'message') {
    const marked = highlighted(r.input.slice(0, MAX_INPUT), a);
    if (marked) card.append(h('h3', {}, t('highlighted_title')), marked);
  }

  card.append(h('h3', {}, t('why_title')));
  card.append(reasons(a) ?? h('p', {}, t('rec_stay_cautious')));

  if (r.kind === 'message' && a.links.length > 0) {
    card.append(h('h3', {}, t('links_found')), h('ul', { class: 'links' }, a.links.map((l) =>
      h('li', {}, h('code', { title: l.url }, l.url), levelPill(l.result),
        h('button', { class: 'btn btn-soft btn-small', type: 'button', onclick: () => inspectLink(l.url) }, t('inspect_link'))))));
  }

  if (a.recommendations.length > 0) {
    card.append(h('h3', {}, t('what_to_do')), h('ul', { class: 'steps' }, a.recommendations.map((rec) => h('li', {}, recText(rec)))));
  }

  const actions = h('div', { class: 'actions' });
  const repUrl = r.kind === 'link' ? r.input : r.kind === 'qr' && r.qr.type === 'WEB_URL' ? r.qr.openTarget : null;
  if (r.kind === 'qr' && r.qr.type === 'WEB_URL' && r.qr.openTarget) {
    actions.append(h('button', { class: 'btn btn-soft', type: 'button', onclick: () => inspectLink(r.qr.openTarget) }, t('analyse_in_linkguard')));
  }
  if (cloudConfigured()) {
    if (canCheckReputation(repUrl)) actions.append(cloudButton(r, 'cloud_check_link', () => askReputation(r, repUrl)));
    if (r.kind === 'message' && r.input.trim()) actions.append(cloudButton(r, 'cloud_explain', () => askAi(r)));
  }
  if (a.level !== 'LOW') {
    actions.append(h('button', { class: 'btn btn-danger', type: 'button', onclick: () => openReport(r) }, t('report_action')));
  }
  if (actions.childElementCount > 0) card.append(actions);
  if (!cloudConfigured() && (repUrl || r.kind === 'message')) card.append(h('p', { class: 'disclaimer' }, t('cloud_not_configured')));
  card.append(h('p', { class: 'disclaimer' }, t('disclaimer')));
  return card;
}

function cloudButton(r, key, onclick) {
  const busy = r.busy === key;
  return h('button', { class: 'btn btn-soft', type: 'button', disabled: busy, onclick }, busy ? t('cloud_loading') : t(key));
}

// ---------------------------------------------------------------- cloud

const providerNames = { google_safe_browsing: 'Google Safe Browsing', virustotal: 'VirusTotal', urlhaus: 'URLhaus (abuse.ch)' };
const cloudError = (res) =>
  res.reason === 'rate_limited' ? t('cloud_rate_limited') : res.reason === 'offline' ? t('cloud_offline')
    : res.reason === 'not_configured' ? t('cloud_not_configured') : t('cloud_failed', res.message ?? '');

function reputationCard(rep) {
  const card = h('section', { class: 'card cloud' }, h('h3', {}, t('rep_title')));
  if (!rep.ok) {
    card.append(h('p', { class: 'headline none' }, cloudError(rep)));
    return card;
  }
  const d = rep.data;
  const names = (status) => (d.providers ?? []).filter((p) => p.status === status).map((p) => providerNames[p.name] ?? p.name).join(', ');
  const [cls, text] =
    d.verdict === 'malicious' ? ['bad', t('rep_malicious', names('malicious'))]
      : d.verdict === 'suspicious' ? ['warn', t('rep_suspicious', names('suspicious'))]
        : d.verdict === 'no_known_threat' ? ['good', t('rep_clean')]
          : ['none', (d.providers ?? []).every((p) => p.status === 'not_configured') ? t('rep_not_configured') : t('rep_unavailable')];
  card.append(h('p', { class: `headline ${cls}` }, text));
  if (d.expandedUrl) card.append(h('p', { class: 'meta' }, t('rep_expanded', d.expandedUrl)));
  card.append(h('ul', { class: 'providers' }, (d.providers ?? []).map((p) =>
    h('li', {}, h('span', {}, providerNames[p.name] ?? p.name), h('strong', {}, t(`provider_status_${p.status}`))))));
  if (d.cached) card.append(h('p', { class: 'meta' }, t('rep_cached')));
  return card;
}

function aiCard(ai) {
  const card = h('section', { class: 'card cloud' }, h('h3', {}, t('ai_title')));
  if (!ai.ok) {
    // The site has a backend (the button is shown only then), so not_configured means the server has no Gemini key.
    card.append(h('p', { class: 'headline none' }, ai.reason === 'not_configured' ? t('web_ai_not_configured') : cloudError(ai)));
    return card;
  }
  const a = ai.data.analysis;
  const level = String(a.riskLevel).toUpperCase();
  const list = (title, items) => items?.length ? [h('strong', {}, title), h('ul', { class: 'ai-list' }, items.map((i) => h('li', {}, i)))] : [];
  card.append(
    h('p', { class: `headline level-${level}` }, levelText(['LOW', 'MEDIUM', 'HIGH'].includes(level) ? level : 'MEDIUM')),
    h('p', {}, a.summary),
    h('p', {}, h('strong', {}, `${t('ai_scam_type')}: `), a.scamType),
    ...list(t('ai_techniques'), a.techniques),
    ...list(t('ai_red_flags'), a.redFlags),
    ...list(t('ai_steps'), a.safeSteps),
    h('p', { class: 'meta' }, t('ai_confidence', a.confidence)),
    h('p', { class: 'disclaimer' }, t('ai_disclaimer') + (ai.data.model ? ` (${ai.data.model})` : '')));
  return card;
}

function consent(bodyKey, preview) {
  return new Promise((resolve) => {
    const dialog = $('dialog');
    $('dialog-body').replaceChildren(
      h('h2', {}, t('cloud_consent_title')),
      h('p', {}, t(bodyKey)),
      h('strong', {}, t('cloud_preview')),
      h('pre', { class: 'decoded' }, preview),
      h('div', { class: 'dialog-actions' },
        h('button', { class: 'btn btn-soft', value: 'cancel' }, t('cancel')),
        h('button', { class: 'btn btn-primary', value: 'send' }, t('cloud_send'))));
    dialog.addEventListener('close', () => resolve(dialog.returnValue === 'send'), { once: true });
    dialog.returnValue = '';
    dialog.showModal();
  });
}

async function askReputation(r, url) {
  if (!(await consent('cloud_consent_link', url))) return;
  r.busy = 'cloud_check_link';
  render();
  r.rep = await checkUrl(url);
  r.busy = null;
  render();
}

async function askAi(r) {
  const text = redact(r.input).slice(0, 4000);
  if (!(await consent('cloud_consent_ai', text))) return;
  r.busy = 'cloud_explain';
  render();
  const codes = [...new Set(r.assessment.findings.map((f) => f.code))];
  // The explanation comes back in the page language, as in the app.
  r.ai = await explain(text, 'message', getLang(), codes);
  r.busy = null;
  render();
}

// ---------------------------------------------------------------- report

const kindKey = { message: 'kind_message', link: 'kind_url', qr: 'kind_qr' };

function buildReport(r) {
  const a = r.assessment;
  const signs = [...new Set(a.findings.map((f) => f.code))].filter((c) => weight(c) > 0).slice(0, 6);
  const lines = [t('report_heading'), t('report_type', t(kindKey[r.kind])), t('report_risk', levelText(a.level), a.score)];
  if (signs.length > 0) lines.push(t('report_signs'), ...signs.map((c) => `• ${findingText(c)}`));
  lines.push('', t('report_content'), forReport(r.input).slice(0, 2000).trim());
  return lines.join('\n');
}

function openReport(r) {
  const dialog = $('dialog');
  const draft = h('textarea', { rows: 9, maxlength: 4000, spellcheck: 'false' });
  draft.value = buildReport(r);
  const note = h('p', { role: 'status' });
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(draft.value);
      note.className = 'copied';
      note.textContent = t('report_copied');
    } catch {
      draft.select();
      note.className = '';
      note.textContent = t('web_copy_failed');
    }
  };
  const share = navigator.share ? h('button', { class: 'btn btn-soft', type: 'button', onclick: () => navigator.share({ text: draft.value }).catch(() => {}) }, t('report_share')) : null;
  const link = (href, key, cls = 'btn btn-soft') => h('a', { class: cls, href, target: '_blank', rel: 'noopener noreferrer' }, t(key));
  $('dialog-body').replaceChildren(
    h('h2', {}, t('report_title')),
    h('p', {}, t('report_explain')),
    draft,
    h('p', { class: 'muted small' }, t('report_redacted')),
    h('div', { class: 'row' }, h('button', { class: 'btn btn-soft grow', type: 'button', onclick: copy }, t('report_copy')), share),
    note,
    link('https://sancharsaathi.gov.in/sfc/', 'report_open_chakshu', 'btn btn-primary'),
    link('https://cybercrime.gov.in/', 'report_open_cybercrime'),
    h('a', { class: 'btn btn-danger', href: 'tel:1930' }, t('report_call_1930')),
    h('div', { class: 'dialog-actions' }, h('button', { class: 'btn btn-soft', value: 'close' }, t('report_close'))));
  dialog.showModal();
}

// ---------------------------------------------------------------- checks

function runMessage(text) {
  if (!text.trim()) return;
  show('message', { kind: 'message', input: text, assessment: analyzeMessage(text) });
}

function runLink(url) {
  if (!url.trim()) return;
  show('link', { kind: 'link', input: url.trim(), assessment: analyzeLink(url) });
}

function runQr(payload) {
  const q = analyzeQr(payload);
  show('qr', { kind: 'qr', input: payload, assessment: q.assessment, qr: q });
}

function inspectLink(url) {
  $('link-input').value = url;
  selectTab('link');
  runLink(url);
}

// ---------------------------------------------------------------- QR decoding (camera and images)

const canvas = document.createElement('canvas');
const ctx = canvas.getContext('2d', { willReadFrequently: true });
const detector = 'BarcodeDetector' in window ? (() => {
  try {
    return new window.BarcodeDetector({ formats: ['qr_code'] });
  } catch {
    return null;
  }
})() : null;

/** Decodes a QR code from an image, video frame or bitmap. Returns the text or null. */
async function decode(source, width, height) {
  if (detector) {
    try {
      const codes = await detector.detect(source);
      if (codes.length > 0) return codes[0].rawValue;
    } catch { /* fall back to jsQR */ }
  }
  if (!window.jsQR) return null;
  const scale = Math.min(1, 1000 / Math.max(width, height));
  canvas.width = Math.max(1, Math.round(width * scale));
  canvas.height = Math.max(1, Math.round(height * scale));
  ctx.drawImage(source, 0, 0, canvas.width, canvas.height);
  const img = ctx.getImageData(0, 0, canvas.width, canvas.height);
  return window.jsQR(img.data, img.width, img.height, { inversionAttempts: 'attemptBoth' })?.data ?? null;
}

function qrStatus(text, error = false) {
  const el = $('qr-status');
  el.textContent = text;
  el.classList.toggle('error', error);
}

function gotQr(payload) {
  stopCamera();
  $('qr-input').value = payload;
  qrStatus('');
  runQr(payload);
}

let stream = null;
let scanning = false;

async function startCamera() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
  } catch {
    qrStatus(t('qr_camera_error'), true);
    return;
  }
  const video = $('camera-video');
  video.srcObject = stream;
  $('camera').hidden = false;
  await video.play().catch(() => {});
  scanning = true;
  updateCameraButton();
  let last = 0;
  const tick = async (now) => {
    if (!scanning) return;
    if (now - last > 150 && video.readyState >= 2) {
      last = now;
      const text = await decode(video, video.videoWidth, video.videoHeight);
      if (text !== null && scanning) return gotQr(text);
    }
    requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}

function stopCamera() {
  scanning = false;
  stream?.getTracks().forEach((track) => track.stop());
  stream = null;
  $('camera').hidden = true;
  $('camera-video').srcObject = null;
  updateCameraButton();
}

function updateCameraButton() {
  $('camera-toggle').querySelector('span').textContent = t(scanning ? 'web_camera_stop' : 'web_camera_start');
}

async function decodeFile(file) {
  if (!file) return;
  qrStatus(t('cloud_loading'));
  try {
    const bitmap = await createImageBitmap(file);
    const text = await decode(bitmap, bitmap.width, bitmap.height);
    bitmap.close?.();
    if (text === null) qrStatus(t('qr_no_code'), true);
    else gotQr(text);
  } catch {
    qrStatus(t('qr_no_code'), true);
  }
}

// ---------------------------------------------------------------- tabs, language, wiring

const tabs = ['message', 'link', 'qr'];

function selectTab(tab) {
  state.tab = tab;
  for (const name of tabs) {
    const button = $(`tab-${name}`);
    button.setAttribute('aria-selected', String(name === tab));
    button.tabIndex = name === tab ? 0 : -1;
    $(`panel-${name}`).hidden = name !== tab;
  }
  if (tab !== 'qr') stopCamera();
  render();
}

function applyLanguage(lang) {
  setLang(lang);
  try {
    localStorage.setItem('cybershield.lang', lang);
  } catch { /* private mode */ }
  for (const el of document.querySelectorAll('[data-i18n]')) el.textContent = t(el.dataset.i18n);
  for (const el of document.querySelectorAll('[data-i18n-placeholder]')) el.placeholder = t(el.dataset.i18nPlaceholder);
  $('tips').replaceChildren(...[1, 2, 3, 4, 5, 6].map((i) => h('li', {}, t(`tip_${i}`))));
  updateCameraButton();
  render();
}

function wireExamples(tab, onPick) {
  const box = $(`${tab}-examples`);
  for (const ex of examples[tab]) box.append(h('button', { class: 'chip', type: 'button', onclick: () => onPick(ex.text) }, ex.label));
}

function init() {
  const saved = (() => {
    try {
      return localStorage.getItem('cybershield.lang');
    } catch {
      return null;
    }
  })();
  const browser = (navigator.language || 'en').slice(0, 2);
  const lang = ['en', 'hi', 'mr'].includes(saved) ? saved : ['hi', 'mr'].includes(browser) ? browser : 'en';
  $('lang').value = lang;
  $('lang').addEventListener('change', (e) => applyLanguage(e.target.value));

  if (config.apkUrl) {
    const a = $('get-app');
    a.href = config.apkUrl;
    a.hidden = false;
  }

  for (const name of tabs) $(`tab-${name}`).addEventListener('click', () => selectTab(name));
  document.querySelector('.tabs').addEventListener('keydown', (e) => {
    const i = tabs.indexOf(state.tab);
    const next = e.key === 'ArrowRight' ? tabs[(i + 1) % 3] : e.key === 'ArrowLeft' ? tabs[(i + 2) % 3] : null;
    if (next) {
      selectTab(next);
      $(`tab-${next}`).focus();
    }
  });

  $('message-go').addEventListener('click', () => runMessage($('message-input').value));
  $('message-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) runMessage($('message-input').value);
  });
  $('message-clear').addEventListener('click', () => {
    $('message-input').value = '';
    show('message', null);
  });
  wireExamples('message', (text) => {
    $('message-input').value = text;
    runMessage(text);
  });

  $('link-go').addEventListener('click', () => runLink($('link-input').value));
  $('link-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') runLink($('link-input').value);
  });
  $('link-clear').addEventListener('click', () => {
    $('link-input').value = '';
    show('link', null);
  });
  wireExamples('link', (text) => {
    $('link-input').value = text;
    runLink(text);
  });

  $('camera-toggle').addEventListener('click', () => (scanning ? stopCamera() : startCamera()));
  if (!navigator.mediaDevices?.getUserMedia) $('camera-toggle').hidden = true;
  $('qr-file').addEventListener('change', (e) => {
    decodeFile(e.target.files[0]);
    e.target.value = '';
  });
  $('qr-go').addEventListener('click', () => runQr($('qr-input').value));
  wireExamples('qr', (text) => {
    $('qr-input').value = text;
    runQr(text);
  });
  // Paste a QR screenshot anywhere on the QR tab.
  document.addEventListener('paste', (e) => {
    if (state.tab !== 'qr') return;
    const file = [...(e.clipboardData?.files ?? [])].find((f) => f.type.startsWith('image/'));
    if (file) {
      e.preventDefault();
      decodeFile(file);
    }
  });

  applyLanguage(lang);
}

init();
