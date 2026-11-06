// Local preview server for the static web demo: node scripts/serve.mjs [port]. No dependencies.
// Camera scanning needs a secure context; browsers treat http://localhost as one.
import { createReadStream, readFileSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(fileURLToPath(new URL('..', import.meta.url)));
const port = Number(process.argv[2] ?? process.env.PORT ?? 5173);
const types = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.json': 'application/json', '.apk': 'application/vnd.android.package-archive',
};

// Same security headers as production (vercel.json). connect-src also allows a local backend.
const headers = Object.fromEntries(
  JSON.parse(readFileSync(join(root, 'vercel.json'), 'utf8')).headers[0].headers.map(({ key, value }) => [
    key,
    key === 'Content-Security-Policy' ? value.replace("connect-src 'self' https:", "connect-src 'self' https: http://localhost:* http://127.0.0.1:*") : value,
  ]),
);

createServer((req, res) => {
  const path = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const file = normalize(join(root, path.endsWith('/') ? `${path}index.html` : path));
  if (!file.startsWith(root.endsWith(sep) ? root : root + sep) || /[\\/](test|scripts|node_modules)[\\/]/.test(file.slice(root.length - 1))) {
    res.writeHead(404).end('Not found');
    return;
  }
  try {
    if (!statSync(file).isFile()) throw new Error('not a file');
  } catch {
    res.writeHead(404).end('Not found');
    return;
  }
  res.writeHead(200, { ...headers, 'Content-Type': types[extname(file)] ?? 'application/octet-stream', 'Cache-Control': 'no-store' });
  createReadStream(file).pipe(res);
}).listen(port, '127.0.0.1', () => console.log(`CyberShield web demo: http://localhost:${port}`));
