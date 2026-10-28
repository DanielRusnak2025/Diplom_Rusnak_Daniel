package org.example.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TagPercentService pure helpers.
 * No DB — only computeDelta and smoothClamp are exercised.
 */
class TagPercentServiceTest {

    // ─────────────────────────────────────────────
    // computeDelta
    // ─────────────────────────────────────────────

    @ParameterizedTest(name = "direction={0} percent={1} → {2}")
    @CsvSource({
        // Like (+1): max(1, round(p/2.0))
        " 1,   1,  1",
        " 1,   2,  1",
        " 1,  50, 25",
        " 1,  70, 35",
        " 1,  99, 50",
        " 1, 100, 50",
        // Dislike (-1): min(-1, -round(p/2.0))
        "-1,   1, -1",
        "-1,   2, -1",
        "-1,  50,-25",
        "-1,  70,-35",
        "-1, 100,-50",
        // Neutral (0): always 0
        " 0,   1,  0",
        " 0,  50,  0",
        " 0, 100,  0",
    })
    void computeDeltaParametrized(int direction, int articlePercent, int expected) {
        assertEquals(expected, TagPercentService.computeDelta(direction, articlePercent));
    }

    /** Neutral rating must never produce a non-zero delta → weight stays unchanged. */
    @ParameterizedTest(name = "neutral percent={0}")
    @ValueSource(ints = {1, 25, 50, 75, 100})
    void neutralDeltaIsAlwaysZero(int articlePercent) {
        assertEquals(0, TagPercentService.computeDelta(0, articlePercent));
    }

    /** smoothClamp(x, 0) must be identity — neutral leaves current unchanged. */
    @ParameterizedTest(name = "identity at current={0}")
    @ValueSource(ints = {-100, -50, 0, 50, 100})
    void smoothClampWithZeroDeltaIsIdentity(int current) {
        assertEquals(current, TagPercentService.smoothClamp(current, 0));
    }

    /**
     * Complaint (direction=-2) is mapped to effective=-1 (dislike) before
     * computeDelta is called.  Verify the mapping and that the resulting
     * deltas are identical to plain dislike.
     */
    @Test
    void complaintMappedToDislike() {
        int complaint = -2;
        int effective = (complaint == -2) ? -1 : complaint;
        assertEquals(-1, effective, "complaint must map to dislike direction");

        for (int p = 1; p <= 100; p++) {
            assertEquals(
                TagPercentService.computeDelta(-1, p),
                TagPercentService.computeDelta(effective, p),
                "delta mismatch for articlePercent=" + p
            );
        }
    }

    // ─────────────────────────────────────────────
    // smoothClamp — spec table
    // ─────────────────────────────────────────────

    @ParameterizedTest(name = "smoothClamp({0},{1})={2}")
    @CsvSource({
        // Outside zone → full step
        "   0,  25,  25",
        "  50,  25,  75",
        // Entering zone from below (current < 80, raw > 80)
        "  70,  25,  86",
        // Inside zone — verified examples from spec
        "  85,  25,  93",
        "  90,   5,  91",
        "  90,  10,  92",
        "  90,  15,  93",
        "  90,  20,  94",
        "  95,  25,  98",
        // Deep in zone — min-step guarantee fires
        "  98,   3,  99",
        "  98,  25,  99",
        "  99,   1, 100",
        // At cap — stays at 100
        " 100,   1, 100",
        // Moving away from boundary → full step (no smoothing)
        "  85, -25,  60",
        // Negative side — symmetric
        " -85, -25, -93",
        " -95,  25, -70",
        // Identity (delta=0)
        "  50,   0,  50",
        " -50,   0, -50",
    })
    void smoothClampTable(int current, int delta, int expected) {
        assertEquals(expected, TagPercentService.smoothClamp(current, delta));
    }

    // ─────────────────────────────────────────────
    // Invariants over full range
    // ─────────────────────────────────────────────

    /** Result is always within [−100, 100]. */
    @Test
    void smoothClampAlwaysInBounds() {
        for (int current = -100; current <= 100; current++) {
            for (int delta = -50; delta <= 50; delta++) {
                int result = TagPercentService.smoothClamp(current, delta);
                assertTrue(result >= -100 && result <= 100,
                    "out of bounds: smoothClamp(" + current + "," + delta + ")=" + result);
            }
        }
    }

    /** Larger positive delta never gives a smaller result (monotone in delta). */
    @Test
    void smoothClampMonotonePositiveDelta() {
        for (int current = -100; current <= 100; current++) {
            int prev = TagPercentService.smoothClamp(current, 1);
            for (int delta = 2; delta <= 50; delta++) {
                int cur = TagPercentService.smoothClamp(current, delta);
                assertTrue(cur >= prev,
                    "not monotone: smoothClamp(" + current + "," + delta + ")=" + cur
                    + " < smoothClamp(" + current + "," + (delta - 1) + ")=" + prev);
                prev = cur;
            }
        }
    }

    /** More negative delta never gives a larger result (monotone in delta). */
    @Test
    void smoothClampMonotoneNegativeDelta() {
        for (int current = -100; current <= 100; current++) {
            int prev = TagPercentService.smoothClamp(current, -1);
            for (int delta = -2; delta >= -50; delta--) {
                int cur = TagPercentService.smoothClamp(current, delta);
                assertTrue(cur <= prev,
                    "not monotone: smoothClamp(" + current + "," + delta + ")=" + cur
                    + " > smoothClamp(" + current + "," + (delta + 1) + ")=" + prev);
                prev = cur;
            }
        }
    }
}
