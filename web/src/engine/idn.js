// IDNA conversion close to java.net.IDN (IDNA 2003 with ALLOW_UNASSIGNED), which the Android engine uses.
// Browsers have no toUnicode API, so Punycode (RFC 3492) is implemented here.

const BASE = 36;
const TMIN = 1;
const TMAX = 26;
const SKEW = 38;
const DAMP = 700;
const INITIAL_BIAS = 72;
const INITIAL_N = 128;

function adapt(delta, numPoints, firstTime) {
  let d = firstTime ? Math.floor(delta / DAMP) : delta >> 1;
  d += Math.floor(d / numPoints);
  let k = 0;
  while (d > ((BASE - TMIN) * TMAX) >> 1) {
    d = Math.floor(d / (BASE - TMIN));
    k += BASE;
  }
  return k + Math.floor(((BASE - TMIN + 1) * d) / (d + SKEW));
}

const digitChar = (d) => String.fromCharCode(d + 22 + 75 * (d < 26));

function charDigit(cp) {
  if (cp >= 48 && cp <= 57) return cp - 22;
  if (cp >= 65 && cp <= 90) return cp - 65;
  if (cp >= 97 && cp <= 122) return cp - 97;
  return BASE;
}

export function punycodeEncode(input) {
  const cps = Array.from(input, (c) => c.codePointAt(0));
  let n = INITIAL_N;
  let delta = 0;
  let bias = INITIAL_BIAS;
  let out = cps.filter((c) => c < 0x80).map((c) => String.fromCharCode(c)).join('');
  const basic = out.length;
  let h = basic;
  if (basic > 0) out += '-';
  while (h < cps.length) {
    const m = Math.min(...cps.filter((c) => c >= n));
    delta += (m - n) * (h + 1);
    n = m;
    for (const c of cps) {
      if (c < n) delta++;
      if (c === n) {
        let q = delta;
        for (let k = BASE; ; k += BASE) {
          const t = k <= bias ? TMIN : k >= bias + TMAX ? TMAX : k - bias;
          if (q < t) break;
          out += digitChar(t + ((q - t) % (BASE - t)));
          q = Math.floor((q - t) / (BASE - t));
        }
        out += digitChar(q);
        bias = adapt(delta, h + 1, h === basic);
        delta = 0;
        h++;
      }
    }
    delta++;
    n++;
  }
  return out;
}

export function punycodeDecode(input) {
  const out = [];
  const last = input.lastIndexOf('-');
  const basic = last < 0 ? 0 : last;
  for (let j = 0; j < basic; j++) {
    if (input.charCodeAt(j) >= 0x80) throw new Error('not basic');
    out.push(input.charCodeAt(j));
  }
  let n = INITIAL_N;
  let bias = INITIAL_BIAS;
  let i = 0;
  for (let idx = basic > 0 ? basic + 1 : 0; idx < input.length; ) {
    const oldi = i;
    for (let w = 1, k = BASE; ; k += BASE) {
      if (idx >= input.length) throw new Error('bad input');
      const digit = charDigit(input.charCodeAt(idx++));
      if (digit >= BASE) throw new Error('bad input');
      i += digit * w;
      const t = k <= bias ? TMIN : k >= bias + TMAX ? TMAX : k - bias;
      if (digit < t) break;
      w *= BASE - t;
    }
    bias = adapt(i - oldi, out.length + 1, oldi === 0);
    n += Math.floor(i / (out.length + 1));
    i %= out.length + 1;
    if (n > 0x10ffff) throw new Error('overflow');
    out.splice(i++, 0, n);
  }
  return String.fromCodePoint(...out);
}

// RFC 3491 nameprep, approximated: map-to-nothing characters removed, case-folded, NFKC.
const MAPPED_TO_NOTHING = /[\u00AD\u034F\u1806\u180B-\u180D\u200B-\u200D\u2060\uFE00-\uFE0F\uFEFF]/g;
const nameprep = (label) => label.replace(MAPPED_TO_NOTHING, '').toLowerCase().normalize('NFKC');
const isAscii = (s) => /^[\x00-\x7F]*$/.test(s);
const SEPARATORS = /[.\u3002\uFF0E\uFF61]/;

function labelToAscii(label) {
  let out = label;
  if (!isAscii(out)) {
    out = nameprep(out);
    if (!isAscii(out)) {
      if (out.toLowerCase().startsWith('xn--')) throw new Error('ACE prefix');
      out = 'xn--' + punycodeEncode(out);
    }
  }
  if (out.length > 63) throw new Error('label too long');
  return out;
}

/** java.net.IDN.toASCII: throws on invalid input, like the Java method. */
export function toAscii(host) {
  return host.split(SEPARATORS).map(labelToAscii).join('.');
}

/** java.net.IDN.toUnicode: never throws; labels that do not round-trip are left unchanged. */
export function toUnicode(host) {
  return host
    .split(SEPARATORS)
    .map((label) => {
      if (!label.toLowerCase().startsWith('xn--')) return label;
      try {
        const decoded = punycodeDecode(label.slice(4));
        return labelToAscii(decoded).toLowerCase() === label.toLowerCase() ? decoded : label;
      } catch {
        return label;
      }
    })
    .join('.');
}
