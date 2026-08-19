/**
 * Vendored protocol snapshot — do not edit by hand; re-vendor on pin bumps.
 *
 * Source: thymianofficial/thymian, tag 0.2.0
 *         (commit a959a7cb25bfcb3f8719677f89ab216f9c2e6e11)
 * Path:   packages/plugin-websocket-proxy/test/reference-client.ts
 *
 * Adaptations (complete list — the upstream file was never typechecked;
 * under this repo's strict tsconfig it needs these mechanical fixes):
 * - `import { RegisterAckMessage, ServerToClientMessage } from '../src/messages'`
 *   → `import type { ... } from './messages.js'` (verbatimModuleSyntax +
 *   nodenext extension + vendored path).
 * - `messageHandlers` values typed `(message: never) => void` so each
 *   registered handler may declare its narrowed message type under
 *   `strictFunctionTypes`; the single dispatch site casts with `as never`.
 * - `pendingActionResults` `resolve` typed `(data: never) => void` for the
 *   same reason (each `emitAction<T>` stores a `(data: T) => void`); the
 *   two settlement sites cast with `as never`.
 * - Behavioral addition: the full `RegisterAckMessage` is captured on the
 *   instance as `registerAck` — upstream `init()` resolves even on
 *   `ok: false`, which the smoke test must be able to detect.
 */
import { WebSocket } from 'ws';

import type { RegisterAckMessage, ServerToClientMessage } from './messages.js';

export class ReferenceClient {
  private socket!: WebSocket;
  private readonly eventHandlers: Map<string, (payload: unknown) => void> =
    new Map();
  private readonly actionHandlers: Map<
    string,
    (payload: unknown) => Promise<unknown> | unknown
  > = new Map();
  private readonly messageHandlers: Map<
    ServerToClientMessage['type'],
    (message: never) => void
  > = new Map();
  options!: { key: string };
  registerAck?: RegisterAckMessage;
  private readonly pendingActionResults: Map<
    string,
    { resolve: (data: never) => void; reject: (err: unknown) => void }
  > = new Map();
  private initResolve!: () => void;

  constructor(
    private readonly port: number,
    private readonly name: string,
    private readonly actions: string[],
    private readonly events: string[],
  ) {
    this.messageHandlers.set('register-ack', (message: RegisterAckMessage) => {
      this.registerAck = message;

      if (message.ok) {
        this.options = message.config as { key: string };

        this.socket.send(
          JSON.stringify({
            type: 'ready',
          }),
        );
      }

      this.initResolve();
    });
    this.messageHandlers.set(
      'event',
      (message: { name: string; payload: unknown }) => {
        const handler = this.eventHandlers.get(message.name);
        if (handler) {
          handler(message.payload);
        }
      },
    );
    this.messageHandlers.set(
      'action',
      (message: { name: string; payload: unknown; id: string }) => {
        const handler = this.actionHandlers.get(message.name);
        if (handler) {
          Promise.resolve(handler(message.payload)).then((result) => {
            this.socket.send(
              JSON.stringify({
                type: 'actionReply',
                correlationId: message.id,
                payload: result,
                name: message.name,
              }),
            );
          });
        }
      },
    );
    this.messageHandlers.set(
      'emitActionResult',
      (message: { name: string; payload: unknown; correlationId: string }) => {
        this.pendingActionResults
          .get(message.correlationId)
          ?.resolve(message.payload as never);
      },
    );

    this.messageHandlers.set(
      'emitActionError',
      (message: {
        name: string;
        error: { name?: string; message?: string };
        correlationId: string;
      }) => {
        this.pendingActionResults
          .get(message.correlationId)
          ?.reject(message.error);
      },
    );
  }

  init(): Promise<void> {
    return new Promise((resolve) => {
      this.socket = new WebSocket(`ws://127.0.0.1:${this.port}`);

      this.socket.on('error', console.error);

      this.socket.on('open', () => {
        this.socket.send(
          JSON.stringify({
            type: 'register',
            name: this.name,
            onActions: this.actions,
            onEvents: this.events,
          }),
        );

        this.initResolve = resolve;
      });

      this.socket.on('message', (data) => {
        const message = JSON.parse(data.toString()) as {
          type: ServerToClientMessage['type'];
        };

        this.messageHandlers.get(message.type)?.(message as never);
      });
    });
  }

  close(): void {
    this.socket.close();
  }

  emitEvent(name: string, payload: unknown): Promise<void> {
    return new Promise((resolve, reject) => {
      this.socket.send(
        JSON.stringify({
          type: 'emit',
          name,
          payload,
        }),
        (err) => {
          if (err) {
            reject(err);
          }
        },
      );

      resolve();
    });
  }

  onEvent(name: string, cb: (payload: unknown) => void): void {
    this.eventHandlers.set(name, cb);
  }

  onAction(
    name: string,
    cb: (payload: unknown) => Promise<unknown> | unknown,
  ): void {
    this.actionHandlers.set(name, cb);
  }

  emitAction<T = unknown>(
    name: string,
    payload: unknown,
    options: {
      strategy?: 'first' | 'collect' | 'deep-merge';
      timeout?: number;
    },
  ): Promise<T> {
    return new Promise((resolve, reject) => {
      const id = crypto.randomUUID();

      this.socket.send(
        JSON.stringify({
          type: 'emitAction',
          name,
          id,
          payload,
          options,
        }),
        (err) => {
          if (err) {
            reject(err);
          }
        },
      );

      this.pendingActionResults.set(id, {
        resolve: resolve as (data: never) => void,
        reject,
      });
    });
  }
}
