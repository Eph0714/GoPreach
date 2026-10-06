import { config } from './config.js';
import { createStore } from './store.js';
import { createApp } from './app.js';

const store = await createStore();
const app = createApp(store, { devAuth: process.env.AUTH_MODE === 'dev' });

// Hostinger's Node.js hosting passes the port in PORT and fronts the app with its own HTTPS proxy.
const server = app.listen(config.port, () => console.log(`GoPreach API listening on ${config.port} (${config.nodeEnv}, store=${config.store})`));

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(async () => { await store.close(); process.exit(0); }));
}
