/**
 * Vendored protocol snapshot — do not edit by hand; re-vendor on pin bumps.
 *
 * Source: thymianofficial/thymian, tag 0.2.0
 *         (commit a959a7cb25bfcb3f8719677f89ab216f9c2e6e11)
 * Path:   packages/plugin-websocket-proxy/src/messages.ts
 *
 * Adaptations (complete list):
 * - `import type { ThymianActionName, ThymianEventName } from '@thymian/core'`
 *   replaced with local `string` aliases — pulling `@thymian/core` in
 *   transitively is the only alternative and defeats the frozen-snapshot
 *   purpose.
 *
 * Why vendored: no client SDK is published (`files: ["dist"]` excludes
 * `test/`; the package entry re-exports no message types) and the handshake
 * carries no protocol version field (the register/register-ack schemas are
 * `additionalProperties: false` — never add one; the server would close the
 * socket with 1008). This frozen snapshot IS the protocol versioning
 * mechanism.
 */
type ThymianActionName = string;
type ThymianEventName = string;

export type RegisterMessage = {
  type: 'register';
  name: string;
  token?: string;
  onActions?: ThymianActionName[];
  onEvents?: ThymianEventName[];
};

export type ReadyMessage = { type: 'ready' };

export type EmitMessage = {
  type: 'emit';
  name: ThymianEventName;
  payload: unknown;
};

export type EmitActionMessage = {
  type: 'emitAction';
  id: string; // client-generated id for correlation
  name: ThymianActionName;
  payload: unknown;
  options?: { strategy?: 'first' | 'collect' | 'deep-merge'; timeout?: number };
};

export type ActionReplyMessage = {
  type: 'actionReply';
  correlationId: string;
  name: ThymianActionName;
  payload: unknown;
};

export type ActionErrorMessage = {
  type: 'actionError';
  correlationId: string;
  name: ThymianActionName;
  error: { name?: string; message: string; options?: Record<string, unknown> };
};

export type ClientToServerMessage =
  | RegisterMessage
  | ReadyMessage
  | EmitMessage
  | EmitActionMessage
  | ActionReplyMessage
  | ActionErrorMessage;

export type RegisterAckMessage = {
  type: 'register-ack';
  ok: boolean;
  config?: Record<PropertyKey, unknown>;
  reason?: string;
};

export type ServerActionMessage = {
  type: 'action';
  id: string;
  name: ThymianActionName;
  payload: unknown;
};

export type ServerEventMessage = {
  type: 'event';
  name: ThymianEventName;
  payload: unknown;
};

export type EmitActionResultMessage = {
  type: 'emitActionResult';
  correlationId: string;
  name: ThymianActionName;
  payload?: unknown;
};

export type EmitActionErrorMessage = {
  type: 'emitActionError';
  correlationId: string;
  name: ThymianActionName;
  error: { name?: string; message?: string };
};

export type ServerToClientMessage =
  | RegisterAckMessage
  | ServerActionMessage
  | ServerEventMessage
  | EmitActionResultMessage
  | EmitActionErrorMessage;
