package com.ruleengine.engine;

import com.ruleengine.index.CandidateResult;
import com.ruleengine.index.RuleIndex;
import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.ParsedUrl;

import java.util.List;
import java.util.Optional;

/**
 * Evaluates a parsed URL against a set of rules and returns the result
 * of the highest-priority matching rule.
 *
 * <p>Matching is accelerated by a {@link RuleIndex} for non-negated conditions.
 * Negated conditions are evaluated directly at match time. Rules are evaluated
 * in descending priority order; ties are broken by definition order.
 */
public final class RuleEngine {

    private final List<Rule> sortedRules;
    private final RuleIndex index;
    private final int[] sortedRuleIds;
    private final boolean[] isAllNegated;

    /**
     * Creates an engine that evaluates the given rules.
     *
     * @param rules the rules to evaluate
     */
    public RuleEngine(List<Rule> rules) {
        this.sortedRules = rules.stream().sorted().toList();
        this.index = new RuleIndex(rules);
        this.sortedRuleIds = new int[sortedRules.size()];
        this.isAllNegated = new boolean[index.ruleCount()];
        for (int i = 0; i < sortedRules.size(); i++) {
            Rule r = sortedRules.get(i);
            sortedRuleIds[i] = index.ruleId(r);
            if (r.conditions().stream().allMatch(Condition::negated)) {
                isAllNegated[sortedRuleIds[i]] = true;
            }
        }
    }

    /**
     * Evaluates a parsed URL against all rules and returns the result of the
     * highest-priority matching rule, or empty if no rule matches.
     *
     * @param url the parsed URL to evaluate
     * @return the result string of the matching rule, or {@link Optional#empty()}
     */
    public Optional<String> evaluate(ParsedUrl url) {
        CandidateResult candidates = index.queryCandidates(url);

        for (int i = 0; i < sortedRules.size(); i++) {
            int ruleId = sortedRuleIds[i];
            if (!candidates.isCandidate(ruleId) && !isAllNegated[ruleId]) {
                continue;
            }
            Rule rule = sortedRules.get(i);
            if (candidates.allSatisfied(ruleId) && noNegatedConditionsMatch(rule, url)) {
                return Optional.of(rule.result());
            }
        }
        return Optional.empty();
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
