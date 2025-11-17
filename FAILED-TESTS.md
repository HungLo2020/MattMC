# Failed Tests Analysis

This document provides a comprehensive analysis of the failing tests in the MattMC project. Each test failure is documented with:
- What the test is testing
- Why it's failing
- Whether the failure indicates a real bug or a false positive due to changed behavior

## Summary

**Total Failing Tests: 1**

1. **CrossChunkLightTest**: 1 failure - Deferred updates for unloaded chunks not implemented

**Status Update**: As of the latest test run (383 tests completed, 1 failed), the following tests that were previously failing are now passing:
- ✅ CrossChunkLightRemovalTest.testTorchJustInsideChunkBoundary
- ✅ CrossChunkLightTest.testBlockLightCrossesChunkBoundary
- ✅ CrossChunkLightTest.testLightCrossesMultipleChunkBoundaries
- ✅ CrossChunkLightTest.testNoSeamsAtChunkBoundary
- ✅ CrossChunkVertexLightTest.testBlockLightCrossesChunkBoundaryForVertices
- ✅ CrossChunkVertexLightTest.testVertexLightSamplingConsistency

---

## CrossChunkLightTest (1 failure)

### Test Purpose
Tests that light from blocks (especially torches) properly propagates across chunk boundaries in both directions, with correct attenuation and deferred update handling.

### Failing Test

#### testDeferredUpdateWhenChunkNotLoaded()
**Line 80**: `assertTrue(deferredCount > 0, "Should have deferred updates for unloaded chunk")`

**Failure**: `expected: <true> but was: <false>`

**What it tests**:
- Places torch at chunk edge BEFORE the neighboring chunk is loaded
- Expects the lighting system to queue "deferred updates" for the unloaded chunk
- When the chunk loads, deferred updates should be processed automatically

**Why it's failing**:
The test expects `deferredCount > 0` but gets 0. This means the lighting system is NOT tracking deferred updates for unloaded chunks.

**Root Cause**:
The `CrossChunkLightPropagator.getDeferredUpdateCount()` method is returning 0. Either:
1. The system is not creating deferred updates when propagating to unloaded chunks, OR
2. The deferred update tracking mechanism is not implemented

This is a critical feature for open-world games - when you place a torch near a chunk boundary, the light should automatically propagate when that chunk loads later.

**Is this a real bug or false positive?**: **REAL BUG** - Deferred updates are a required feature for proper lighting in chunk-based worlds. Without this, players would see lighting "pop in" when chunks load.

---

## Recommended Actions

### Critical Fixes (Real Bugs)
1. **Implement deferred updates** - Track and apply light updates for unloaded chunks
   - Ensure `getDeferredUpdateCount()` properly tracks pending updates
   - Process deferred updates when chunks load

---

## Test Status Summary

| Test Class | Test Method | Status | Type |
|------------|------------|--------|------|
| CrossChunkLightTest | testDeferredUpdateWhenChunkNotLoaded | ❌ | Real Bug |

**Real Bugs**: 1 out of 1 failing test
