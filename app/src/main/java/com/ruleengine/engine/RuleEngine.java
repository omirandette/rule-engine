package com.ruleengine.engine;

import com.ruleengine.index.CandidateResult;
import com.ruleengine.index.RuleIndex;
import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.ParsedUrl;

import java.util.List;

/**
 * Evaluates a parsed URL against a set of rules and returns the result
 * of the highest-priority matching rule.
 *
 * <p>Matching is accelerated by a {@link RuleIndex} for non-negated conditions.
 * Negated conditions are evaluated directly at match time. Rules are evaluated
 * in descending priority order; ties are broken by definition order.
 */
public final class RuleEngine {

    /** Bundles a rule with its precomputed index ID and negation flag. */
    private record SortedEntry(Rule rule, int ruleId, boolean allNegated) {}

    private final SortedEntry[] entries;
    private final RuleIndex index;

    /**
     * Creates an engine that evaluates the given rules.
     *
     * @param rules the rules to evaluate
     */
    public RuleEngine(List<Rule> rules) {
        this.index = new RuleIndex(rules);
        List<Rule> sorted = rules.stream().sorted().toList();
        this.entries = new SortedEntry[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            Rule rule = sorted.get(i);
            int ruleId = index.ruleId(rule);
            boolean allNegated = rule.conditions().stream().allMatch(Condition::negated);
            entries[i] = new SortedEntry(rule, ruleId, allNegated);
        }
    }

    /**
     * Evaluates a parsed URL against all rules and returns the result of the
     * highest-priority matching rule, or {@code null} if no rule matches.
     *
     * @param url the parsed URL to evaluate
     * @return the result string of the matching rule, or {@code null}
     */
    public String evaluate(ParsedUrl url) {
        CandidateResult candidates = index.queryCandidates(url);

        for (SortedEntry entry : entries) {
            if (!candidates.isCandidate(entry.ruleId) && !entry.allNegated) {
                continue;
            }
            if (candidates.allSatisfied(entry.ruleId)
                    && noNegatedConditionsMatch(entry.rule, url)) {
                return entry.rule.result();
            }
        }
        return null;
    }

    /** Returns {@code true} if none of the rule's negated conditions match the URL. */
    private boolean noNegatedConditionsMatch(Rule rule, ParsedUrl url) {
        for (Condition cond : rule.conditions()) {
            if (cond.negated() && matchesDirect(cond, url)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesDirect(Condition cond, ParsedUrl url) {
        String value = url.part(cond.part());
        return switch (cond.operator()) {
            case EQUALS -> value.equals(cond.value());
            case CONTAINS -> value.contains(cond.value());
            case STARTS_WITH -> value.startsWith(cond.value());
            case ENDS_WITH -> value.endsWith(cond.value());
        };
    }

}
