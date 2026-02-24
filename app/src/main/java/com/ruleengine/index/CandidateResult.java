package com.ruleengine.index;

import java.util.Arrays;

/**
 * Dense array-based container tracking how many non-negated conditions
 * are satisfied per rule.
 *
 * <p>Each non-negated condition maps to exactly one index entry and produces
 * at most one match per URL, so a simple counter is equivalent to set membership.
 * A rule's non-negated conditions are fully met when
 * {@code satisfiedCounts[ruleId] == nonNegatedCounts[ruleId]}.
 */
public final class CandidateResult {

    private final int[] satisfiedCounts;
    private final int[] nonNegatedCounts;

    /**
     * Creates a result container sized for the given number of rules.
     *
     * @param ruleCount        total number of rules (determines array size)
     * @param nonNegatedCounts shared reference to expected counts per rule
     */
    public CandidateResult(int ruleCount, int[] nonNegatedCounts) {
        this.satisfiedCounts = new int[ruleCount];
        this.nonNegatedCounts = nonNegatedCounts;
    }

    /**
     * Resets all satisfied counts to zero so this instance can be reused.
     */
    public void reset() {
        Arrays.fill(satisfiedCounts, 0);
    }

    /**
     * Increments the satisfied count for the rule at the given ID.
     *
     * @param ruleId the dense rule ID
     */
    public void increment(int ruleId) {
        satisfiedCounts[ruleId]++;
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
