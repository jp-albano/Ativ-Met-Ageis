export type RequestTracker = {
  start: () => number;
  isCurrent: (requestId: number) => boolean;
};

export function createRequestTracker(): RequestTracker {
  let currentRequestId = 0;

  return {
    start() {
      currentRequestId += 1;
      return currentRequestId;
    },
    isCurrent(requestId) {
      return requestId === currentRequestId;
    },
  };
}

export function applyIfCurrent<T>(tracker: RequestTracker, requestId: number, update: () => T): T | undefined {
  return tracker.isCurrent(requestId) ? update() : undefined;
}
