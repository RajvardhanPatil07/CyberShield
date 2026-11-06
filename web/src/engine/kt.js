// Small helpers that reproduce Kotlin/Java standard-library behaviour the Android engine relies on,
// so the JavaScript port gives the same results. See web/test/parity.test.js.

/** Kotlin Char.isWhitespace(): Java isWhitespace() or isSpaceChar(). */
const WS = /[\t\n\v\f\r\x1C-\x1F\p{Zs}\u2028\u2029]/u;
export const isWhitespace = (c) => WS.test(c);

export const isLetter = (c) => /\p{L}/u.test(c);
export const isLetterOrDigit = (c) => /[\p{L}\p{Nd}]/u.test(c);
export const isDigit = (c) => /\p{Nd}/u.test(c);

/** Kotlin String.trim(): strips Kotlin whitespace (which, unlike JS, excludes U+FEFF). */
export function trimWs(s) {
  let a = 0;
  let b = s.length;
  while (a < b && isWhitespace(s[a])) a++;
  while (b > a && isWhitespace(s[b - 1])) b--;
  return s.slice(a, b);
}

export function trimChars(s, chars) {
  let a = 0;
  let b = s.length;
  while (a < b && chars.includes(s[a])) a++;
  while (b > a && chars.includes(s[b - 1])) b--;
  return s.slice(a, b);
}

export function trimEndChars(s, chars) {
  let b = s.length;
  while (b > 0 && chars.includes(s[b - 1])) b--;
  return s.slice(0, b);
}

/** Kotlin substringBefore / substringAfter (missing delimiter: whole string, or [fallback]). */
export const before = (s, d) => {
  const i = s.indexOf(d);
  return i < 0 ? s : s.slice(0, i);
};
export const after = (s, d, fallback = s) => {
  const i = s.indexOf(d);
  return i < 0 ? fallback : s.slice(i + d.length);
};
export const afterLast = (s, d) => {
  const i = s.lastIndexOf(d);
  return i < 0 ? s : s.slice(i + d.length);
};

/** Char-by-char Character.toLowerCase, which keeps the string length (and highlight indices) unchanged. */
export function lowerCharwise(s) {
  let out = '';
  for (const c of s.split('')) {
    const l = c.toLowerCase();
    out += l.length === 1 ? l : c === '\u0130' ? 'i' : c;
  }
  return out;
}

function digitValue(c) {
  const cp = c.codePointAt(0);
  if (cp >= 48 && cp <= 57) return cp - 48;
  if (!isDigit(c)) return -1;
  let zero = cp;
  while (zero > 0 && cp - zero < 9 && isDigit(String.fromCodePoint(zero - 1))) zero--;
  return (cp - zero) % 10;
}

/** Kotlin String.toIntOrNull(): optional sign, Unicode decimal digits, null on overflow. */
export function toIntOrNull(s) {
  if (s.length === 0) return null;
  let i = 0;
  let negative = false;
  if (s[0] === '-' || s[0] === '+') {
    if (s.length === 1) return null;
    negative = s[0] === '-';
    i = 1;
  }
  let value = 0;
  for (; i < s.length; i++) {
    const d = digitValue(s[i]);
    if (d < 0) return null;
    value = value * 10 + d;
    if (value > 2147483648) return null;
  }
  if (!negative && value > 2147483647) return null;
  return negative ? -value : value;
}

/** java.net.URLDecoder.decode(s, "UTF-8"): '+' is a space; a malformed escape throws. */
export function urlDecode(s) {
  let out = '';
  let i = 0;
  while (i < s.length) {
    const c = s[i];
    if (c === '+') {
      out += ' ';
      i++;
    } else if (c === '%') {
      const bytes = [];
      while (i < s.length && s[i] === '%') {
        const hex = s.slice(i + 1, i + 3);
        if (!/^[0-9a-fA-F]{2}$/.test(hex)) throw new Error('illegal hex');
        bytes.push(parseInt(hex, 16));
        i += 3;
      }
      out += new TextDecoder('utf-8').decode(new Uint8Array(bytes));
    } else {
      out += c;
      i++;
    }
  }
  return out;
}

export function tryUrlDecode(s) {
  try {
    return urlDecode(s);
  } catch {
    return null;
  }
}

/** Escapes a literal for use inside a RegExp (Kotlin Regex.escape). */
export const escapeRegex = (s) => s.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&');

/** All matches of a global regex as {start, end, value}. End is exclusive. */
export function findAll(regex, text) {
  const re = new RegExp(regex.source, regex.flags.includes('g') ? regex.flags : regex.flags + 'g');
  const out = [];
  let m;
  while ((m = re.exec(text)) !== null) {
    out.push({ start: m.index, end: m.index + m[0].length, value: m[0] });
    if (m[0].length === 0) re.lastIndex++;
  }
  return out;
}

export function find(regex, text) {
  const re = new RegExp(regex.source, regex.flags.replace('g', ''));
  const m = re.exec(text);
  return m ? { start: m.index, end: m.index + m[0].length, value: m[0] } : null;
}

export const distinct = (list) => [...new Set(list)];

/** Kotlin integer division for non-negative values. */
export const idiv = (a, b) => Math.trunc(a / b);
