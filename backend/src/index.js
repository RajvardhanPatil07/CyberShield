import { createAppServer } from './app.js';
import { loadConfig } from './config.js';

const config = loadConfig();
const server = createAppServer(config);

server.listen(config.port, config.host, () => {
  const configured = Object.entries(config.keys).filter(([, v]) => v).map(([k]) => k);
  // Log which providers are configured, never the keys themselves.
  process.stdout.write(`CyberShield backend listening on ${config.host}:${config.port}; configured: ${configured.join(', ') || 'none'}\n`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
