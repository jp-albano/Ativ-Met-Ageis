import { afterEach, describe, it } from "node:test";
import assert from "node:assert/strict";

import { applyIfCurrent, createRequestTracker } from "./request-tracking";

const delays = [0, 5, 10, 15, 20, 30, 40];

function randomDelay() {
  return delays[Math.floor(Math.random() * delays.length)];
}

afterEach(() => {
  // Each stress case is isolated and does not share global state.
});

describe("request tracking stress", () => {
  it("keeps only the newest result when many requests finish out of order", async () => {
    for (let iteration = 0; iteration < 200; iteration += 1) {
      const tracker = createRequestTracker();
      const requestIds = Array.from({ length: 30 }, () => tracker.start());

      const results = await Promise.all(requestIds.map(async (requestId, index) => {
        await new Promise(resolve => setTimeout(resolve, randomDelay()));
        return applyIfCurrent(tracker, requestId, () => ({ requestId, index, accepted: true }));
      }));

      const accepted = results.filter((result): result is { requestId: number; index: number; accepted: true } => Boolean(result));
      const newestAccepted = accepted.at(-1);

      assert.ok(newestAccepted, "esperava que ao menos uma resposta fosse aceita");
      assert.equal(newestAccepted?.requestId, requestIds.at(-1));
    }
  });

  it("does not allow stale writes to overwrite a newer batch", async () => {
    for (let batch = 0; batch < 150; batch += 1) {
      const tracker = createRequestTracker();
      const first = tracker.start();
      const second = tracker.start();
      const third = tracker.start();

      const [older, middle, newer] = await Promise.all([
        new Promise(resolve => setTimeout(() => resolve(applyIfCurrent(tracker, first, () => "old")), 30)),
        new Promise(resolve => setTimeout(() => resolve(applyIfCurrent(tracker, second, () => "mid")), 10)),
        new Promise(resolve => setTimeout(() => resolve(applyIfCurrent(tracker, third, () => "new")), 0)),
      ]);

      const accepted = [older, middle, newer].filter(value => value !== undefined);
      assert.deepEqual(accepted, ["new"]);
      assert.equal(tracker.isCurrent(first), false);
      assert.equal(tracker.isCurrent(second), false);
      assert.equal(tracker.isCurrent(third), true);
    }
  });
});
