import { describe, it } from "node:test";
import assert from "node:assert/strict";

import { applyIfCurrent, createRequestTracker } from "./request-tracking";

describe("request tracking", () => {
  it("marks older requests as stale once a newer request starts", () => {
    const tracker = createRequestTracker();
    const first = tracker.start();
    const second = tracker.start();

    assert.equal(tracker.isCurrent(first), false);
    assert.equal(tracker.isCurrent(second), true);
  });

  it("ignores stale results before updating the UI state", () => {
    const tracker = createRequestTracker();
    const first = tracker.start();
    const second = tracker.start();

    const firstResult = applyIfCurrent(tracker, first, () => "older");
    const secondResult = applyIfCurrent(tracker, second, () => "newer");

    assert.equal(firstResult, undefined);
    assert.equal(secondResult, "newer");
  });
});
