import { request } from './http';
import type { CallOptions } from './activities';

/** Jeton de notification de cet appareil (JWT requis). */
const BASE = '/api/v1/users/me/push-tokens';

export function registerPushToken(
  token: string,
  platform: 'ios' | 'android',
  options: CallOptions = {},
): Promise<void> {
  return request<void>(BASE, { method: 'PUT', body: { token, platform }, authenticated: true, signal: options.signal });
}

export function unregisterPushToken(token: string, options: CallOptions = {}): Promise<void> {
  return request<void>(BASE, { method: 'DELETE', body: { token }, authenticated: true, signal: options.signal });
}
