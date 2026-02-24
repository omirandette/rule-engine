package com.ruleengine.index;

/**
 * Dense array-based container tracking how many non-negated conditions
 * are satisfied per rule.
 *
 * <p>Each non-negated condition maps to exactly one index entry and produces
 * at most one match per URL, so a simple counter is equivalent to set membership.
 * A rule's non-negated conditions are fully met when
 * {@code satisfiedCounts[ruleId] == nonNegatedCounts[ruleId]}.
 *
 * <p>Uses sparse reset: only rule IDs touched during {@link #increment(int)} are
 * zeroed in {@link #reset()}, turning O(ruleCount) into O(touched).
 */
public final class CandidateResult {

    private final int[] satisfiedCounts;
    private final int[] nonNegatedCounts;
    private final int[] dirty;
    private int dirtyCount;

    /**
     * Creates a result container sized for the given number of rules.
     *
     * @param ruleCount        total number of rules (determines array size)
     * @param nonNegatedCounts shared reference to expected counts per rule
     */
    public CandidateResult(int ruleCount, int[] nonNegatedCounts) {
        this.satisfiedCounts = new int[ruleCount];
        this.nonNegatedCounts = nonNegatedCounts;
        this.dirty = new int[ruleCount];
    }

    /**
     * Resets only the touched entries so this instance can be reused.
     */
    public void reset() {
        for (int i = 0; i < dirtyCount; i++) {
            satisfiedCounts[dirty[i]] = 0;
        }
        dirtyCount = 0;
    }

    /**
     * Increments the satisfied count for the rule at the given ID.
     * Tracks the rule ID for sparse reset on first touch.
     *
     * @param ruleId the dense rule ID
     */
    public void increment(int ruleId) {
        if (satisfiedCounts[ruleId]++ == 0) {
            dirty[dirtyCount++] = ruleId;
        }
    }

    /**
     * Returns {@code true} if all non-negated conditions for the given rule
     * have been satisfied.
     *
     * @param ruleId the dense rule ID
     * @return {@code true} if the satisfied count equals the expected count
     */
    public boolean allSatisfied(int ruleId) {
        return satisfiedCounts[ruleId] == nonNegatedCounts[ruleId];
    }

    /**
     * Returns {@code true} if the rule at the given ID has at least one
     * satisfied condition.
     *
     * @param ruleId the dense rule ID
     * @return {@code true} if the rule is a candidate
     */
    public boolean isCandidate(int ruleId) {
        return satisfiedCounts[ruleId] > 0;
    }
}
