import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  // `npm run e2e` runs from the repo root; anchor test globs to this directory.
  root: fileURLToPath(new URL('.', import.meta.url)),
  test: {
    include: ['src/**/*.test.ts'],
    // No global testTimeout on purpose (mirrors thymian's e2e config):
    // every test must declare its own explicit timeout.
    teardownTimeout: 10000,
    reporters: ['verbose'],
  },
});
