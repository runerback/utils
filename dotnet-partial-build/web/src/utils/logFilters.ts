import type { BuildLogEvent } from '../types';

export function isErrorLog(log: BuildLogEvent): boolean {
  if (log.stream === 'StandardError') {
    return true;
  }

  return /(^|\s)(error\s+[a-z]+\d+|error\s*:|\[diagnostics\])/i.test(log.message) ||
    /build failed/i.test(log.message);
}
