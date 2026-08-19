import {
  type ChildProcessWithoutNullStreams,
  spawn,
} from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';

import { getCleanEnv } from './env-utils.js';

// The exact-pin policy: one exact version string covers all @thymian/*
// (lockstep versioning). A pin bump means editing this constant in a
// deliberate PR whose CI runs the e2e suite against the new version.
export const DEFAULT_THYMIAN_VERSION = '0.2.0';

export type InstallationMode = 'npx' | 'global' | 'local';
const validModes: InstallationMode[] = ['npx', 'global', 'local'];
const rawMode = process.env['THYMIAN_E2E_MODE'] || 'npx';
if (!validModes.includes(rawMode as InstallationMode)) {
  throw new Error(
    `Invalid THYMIAN_E2E_MODE: '${rawMode}'. Must be one of: ${validModes.join(', ')}`,
  );
}
export const installationMode: InstallationMode = rawMode as InstallationMode;

const isWindows = process.platform === 'win32';
const npxCmd = isWindows ? 'npx.cmd' : 'npx';

export function resolveThymianVersion(): string {
  return process.env['THYMIAN_E2E_VERSION'] ?? DEFAULT_THYMIAN_VERSION;
}

export function resolveThymianCommand(args: string[]): {
  cmd: string;
  argv: string[];
} {
  switch (installationMode) {
    case 'npx':
      return {
        cmd: npxCmd,
        argv: ['--yes', `thymian@${resolveThymianVersion()}`, ...args],
      };
    case 'global':
      return { cmd: 'thymian', argv: args };
    case 'local':
      throw new Error('Local installation mode not yet implemented');
  }
}

// Full banner: `Thymian is now in "serve" mode. Press "q" to exit.` — printed
// only after the WS server listens. Prefix match because wrap() may
// line-break the rest on narrow widths.
const SERVE_BANNER = /Thymian is now in "serve" mode/;

// A cold npx fetch of the pinned version must fit inside this; CI is colder
// than dev machines.
const BANNER_TIMEOUT_MS = 120_000;

export interface ServeHandle {
  child: ChildProcessWithoutNullStreams;
  output: () => string;
  waitForBanner: (timeoutMs?: number) => Promise<void>;
  shutdown: () => Promise<number | null>;
}

export function spawnThymianServe(args: string[]): ServeHandle {
  const { cmd, argv } = resolveThymianCommand(args);
  // stdin must stay writable ('pipe') — shutdown is the CLI's 'q' quit key.
  const child = spawn(cmd, argv, {
    env: getCleanEnv(),
    stdio: ['pipe', 'pipe', 'pipe'],
  });

  const stdoutChunks: string[] = [];
  const stderrChunks: string[] = [];
  child.stdout.setEncoding('utf-8');
  child.stdout.on('data', (chunk: string) => stdoutChunks.push(chunk));
  child.stderr.setEncoding('utf-8');
  child.stderr.on('data', (chunk: string) => stderrChunks.push(chunk));

  const exited = new Promise<number | null>((resolve) => {
    child.once('close', (code) => resolve(code));
  });

  const output = () => stdoutChunks.join('') + stderrChunks.join('');

  const waitForBanner = (timeoutMs = BANNER_TIMEOUT_MS): Promise<void> => {
    return new Promise((resolve, reject) => {
      const check = () => {
        if (SERVE_BANNER.test(stdoutChunks.join(''))) {
          cleanup();
          resolve();
        }
      };
      const onClose = (code: number | null) => {
        cleanup();
        reject(
          new Error(
            `thymian serve exited (code ${code ?? 'unknown'}) before the banner.\n\nOutput:\n${output()}`,
          ),
        );
      };
      const timer = setTimeout(() => {
        cleanup();
        reject(
          new Error(
            `Banner not seen within ${timeoutMs}ms.\n\nOutput so far:\n${output()}`,
          ),
        );
      }, timeoutMs);
      const cleanup = () => {
        clearTimeout(timer);
        child.stdout.removeListener('data', check);
        child.removeListener('close', onClose);
      };
      child.stdout.on('data', check);
      child.once('close', onClose);
      check();
    });
  };

  const shutdown = async (): Promise<number | null> => {
    if (child.exitCode !== null) {
      return child.exitCode;
    }
    // 'q' is the CLI's documented quit key; escalate only if it is ignored.
    child.stdin.write('q');
    const afterQuit = await Promise.race([
      exited,
      delay(10_000, 'timeout' as const),
    ]);
    if (afterQuit !== 'timeout') {
      return afterQuit;
    }
    child.kill('SIGTERM');
    const afterTerm = await Promise.race([
      exited,
      delay(5_000, 'timeout' as const),
    ]);
    if (afterTerm !== 'timeout') {
      return afterTerm;
    }
    child.kill('SIGKILL');
    return exited;
  };

  return { child, output, waitForBanner, shutdown };
}
