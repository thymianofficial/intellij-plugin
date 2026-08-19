import { describe, expect, it } from 'vitest';

import { spawnThymianServe } from './helpers.js';
import { getAvailablePort } from './port-utils.js';
import { ReferenceClient } from './vendor/reference-client.js';

describe('thymian serve websocket handshake (smoke)', () => {
  it(
    'completes register → register-ack(ok) → ready against the pinned CLI',
    async () => {
      const port = await getAvailablePort();
      const serve = spawnThymianServe([
        'serve',
        '-o',
        `@thymian/plugin-websocket-proxy.port=${port}`,
        '-p',
        '@thymian/plugin-websocket-proxy',
      ]);

      let exitCode: number | null;
      try {
        await serve.waitForBanner();

        // Registered under a name distinct from the production plugin's
        // (`intellij-plugin`) to keep the smoke identity separate; unknown
        // names get `ok: true, config: {}` from the server.
        const client = new ReferenceClient(port, 'e2e-smoke', [], []);
        await client.init();

        // The client auto-sends `ready` inside its register-ack handler, so
        // an ok ack means the full register → register-ack(ok) → ready
        // handshake completed.
        expect(client.registerAck).toBeDefined();
        expect(client.registerAck?.ok).toBe(true);

        client.close();
      } finally {
        exitCode = await serve.shutdown();
      }

      expect(
        exitCode,
        `expected a clean exit after 'q'.\n\nCLI output:\n${serve.output()}`,
      ).toBe(0);
    },
    180_000,
  );
});
