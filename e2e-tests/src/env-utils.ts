export function getCleanEnv(): Record<string, string> {
  const env: Record<string, string> = {};
  for (const [key, value] of Object.entries(process.env)) {
    if (value === undefined) {
      continue;
    }
    if (key === 'NODE_PATH') {
      continue;
    }
    if (key === 'JEST_WORKER_ID') {
      continue;
    }
    env[key] = value;
  }
  env['FORCE_COLOR'] = '0';

  // Without this, oclif's terminalWidth() is Infinity on non-TTY and output
  // never wraps deterministically.
  env['OCLIF_COLUMNS'] = '80';

  // Force production mode so oclif does not remap the published package's
  // `dist/` paths back to nonexistent `src/` sources.
  env['NODE_ENV'] = 'production';

  // 402.4's orchestrator points the spawned CLI at Verdaccio through this.
  // Unset ⇒ the harness adds no registry override; ambient npm config (an
  // inherited npm_config_registry, .npmrc) still applies to the spawned CLI.
  const registry = process.env['THYMIAN_E2E_REGISTRY'];
  if (registry) {
    env['npm_config_registry'] = registry;
  }

  return env;
}
