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
  // `||`, not `??`: an empty-string override would produce `thymian@`, which
  // npm resolves to `latest` — silently defeating the exact-pin policy.
  return process.env['THYMIAN_E2E_VERSION'] || DEFAULT_THYMIAN_VERSION;
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
    case 'local': {
      // Runs a locally built thymian checkout (402.4's cross-repo drift
      // loop: `nx build` the sibling checkout, point this at its bin).
      // Spawned via the current node binary — portable, no npx/shell.
      const cli = process.env['THYMIAN_E2E_CLI'];
      if (!cli) {
        throw new Error(
          'THYMIAN_E2E_MODE=local requires THYMIAN_E2E_CLI (path to a built ' +
            'thymian entry, e.g. <thymian>/packages/thymian/bin/run.js)',
        );
      }
      return { cmd: process.execPath, argv: [cli, ...args] };
    }
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
  // Windows: spawning a .cmd without a shell throws EINVAL since the
  // CVE-2024-27980 hardening; args carry no spaces, so no quoting hazard.
  // detached (POSIX): the child leads its own process group, so kill
  // escalation can signal the whole tree — in npx mode the CLI is a
  // grandchild the wrapper does not forward signals to.
  const child = spawn(cmd, argv, {
    env: getCleanEnv(),
    stdio: ['pipe', 'pipe', 'pipe'],
    shell: isWindows,
    detached: !isWindows,
  });

  // Windows has no process groups: best-effort direct kill.
  const killTree = (signal: NodeJS.Signals) => {
    if (!isWindows && child.pid !== undefined) {
      try {
        process.kill(-child.pid, signal);
        return;
      } catch {
        // group already gone — fall through to the direct kill
      }
    }
    child.kill(signal);
  };

  // The 'q' write in shutdown() can race the child's death; an EPIPE on
  // stdin must not crash the test worker.
  child.stdin.on('error', () => {});

  const stdoutChunks: string[] = [];
  const stderrChunks: string[] = [];
  child.stdout.setEncoding('utf-8');
  child.stdout.on('data', (chunk: string) => stdoutChunks.push(chunk));
  child.stderr.setEncoding('utf-8');
  child.stderr.on('data', (chunk: string) => stderrChunks.push(chunk));

  // Spawn failures (e.g. ENOENT) emit 'error' and may never emit 'close';
  // settle `exited` for both so nothing awaits forever.
  let spawnError: Error | undefined;
  const exited = new Promise<number | null>((resolve) => {
    child.once('close', (code) => resolve(code));
    child.once('error', (error) => {
      spawnError = error;
      resolve(null);
    });
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
      const onError = (error: Error) => {
        cleanup();
        reject(new Error(`Failed to spawn '${cmd}': ${error.message}`));
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
        child.removeListener('error', onError);
      };
      child.stdout.on('data', check);
      child.once('close', onClose);
      child.once('error', onError);
      check();
      if (spawnError) {
        onError(spawnError);
      }
    });
  };

  const shutdown = async (): Promise<number | null> => {
    // `exitCode` stays null for signal-killed children — check both.
    if (child.exitCode !== null || child.signalCode !== null) {
      return child.exitCode;
    }
    // 'q' is the CLI's documented quit key; escalate only if it is ignored.
    // The write can throw synchronously on an already-destroyed stream.
    try {
      child.stdin.write('q');
    } catch {
      // fall through to the signal escalation
    }
    // { ref: false } on every delay: a losing race timer must not hold the
    // event loop open into vitest's teardown.
    const afterQuit = await Promise.race([
      exited,
      delay(10_000, 'timeout' as const, { ref: false }),
    ]);
    if (afterQuit !== 'timeout') {
      return afterQuit;
    }
    killTree('SIGTERM');
    const afterTerm = await Promise.race([
      exited,
      delay(5_000, 'timeout' as const, { ref: false }),
    ]);
    if (afterTerm !== 'timeout') {
      return afterTerm;
    }
    killTree('SIGKILL');
    // Even after SIGKILL, 'close' can stay pending while the npx grandchild
    // holds the stdio pipes — bound the wait and report "unknown" (null).
    const afterKill = await Promise.race([
      exited,
      delay(5_000, 'timeout' as const, { ref: false }),
    ]);
    return afterKill === 'timeout' ? null : afterKill;
  };

  return { child, output, waitForBanner, shutdown };
}
