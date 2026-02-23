package com.ruleengine.index;

import com.ruleengine.rule.Condition;

import java.util.HashSet;
import java.util.Set;

/**
 * Dense array-based container for candidate rule conditions.
 *
 * <p>Indexed by rule ID (0..N-1), each slot holds the set of non-negated
 * conditions satisfied for that rule. A {@code null} slot means the rule
 * is not a candidate. This replaces {@code Map<Rule, Set<Condition>>}
 * to eliminate HashMap overhead on the hot path.
 */
public final class CandidateResult {

    @SuppressWarnings("unchecked")
    private final Set<Condition>[] satisfied;

    /**
     * Creates a result container sized for the given number of rules.
     *
     * @param ruleCount total number of rules (determines array size)
     */
    @SuppressWarnings("unchecked")
    public CandidateResult(int ruleCount) {
        this.satisfied = new Set[ruleCount];
    }

    /**
     * Records a satisfied condition for the rule at the given ID.
     *
     * @param ruleId    the dense rule ID
     * @param condition the condition that was satisfied
     */
    public void add(int ruleId, Condition condition) {
        Set<Condition> set = satisfied[ruleId];
        if (set == null) {
            set = new HashSet<>();
            satisfied[ruleId] = set;
        }
        set.add(condition);
    }

    /**
     * Returns the set of satisfied conditions for the given rule ID,
     * or {@code null} if the rule is not a candidate.
     *
     * @param ruleId the dense rule ID
     * @return the satisfied conditions, or {@code null}
     */
    public Set<Condition> conditions(int ruleId) {
        return satisfied[ruleId];
    }

    /**
     * Returns {@code true} if the rule at the given ID has at least one
     * satisfied condition.
     *
     * @param ruleId the dense rule ID
     * @return {@code true} if the rule is a candidate
     */
    public boolean isCandidate(int ruleId) {
        return satisfied[ruleId] != null;
    }
}
