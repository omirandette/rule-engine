package com.ruleengine.engine;

import com.ruleengine.index.ContainsStrategy;
import com.ruleengine.index.RuleIndex;
import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.ParsedUrl;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Creates an engine with the specified contains strategy.
     *
     * @param rules             the rules to evaluate
     * @param containsStrategy  the data structure for CONTAINS matching
     */
    public RuleEngine(List<Rule> rules, ContainsStrategy containsStrategy) {
        this.sortedRules = rules.stream().sorted().toList();
        this.index = new RuleIndex(rules, containsStrategy);
    }

    /**
     * Creates an engine using the default {@link ContainsStrategy#AHO_CORASICK} strategy.
     *
     * @param rules the rules to evaluate
     */
    public RuleEngine(List<Rule> rules) {
        this(rules, ContainsStrategy.AHO_CORASICK);
    }

    /**
     * Evaluates a parsed URL against all rules and returns the result of the
     * highest-priority matching rule, or empty if no rule matches.
     *
     * @param url the parsed URL to evaluate
     * @return the result string of the matching rule, or {@link Optional#empty()}
     */
    public Optional<String> evaluate(ParsedUrl url) {
        Set<RuleIndex.ConditionRef> candidates = index.queryCandidates(url);

        Set<Rule> candidateRules = new HashSet<>();
        Set<ConditionKey> satisfiedConditions = new HashSet<>();

        for (RuleIndex.ConditionRef ref : candidates) {
            candidateRules.add(ref.rule());
            satisfiedConditions.add(new ConditionKey(ref.rule(), ref.condition()));
        }

        // Rules with only negated conditions won't appear in the index
        for (Rule rule : sortedRules) {
            if (rule.conditions().stream().allMatch(Condition::negated)) {
                candidateRules.add(rule);
            }
        }

        for (Rule rule : sortedRules) {
            if (!candidateRules.contains(rule)) {
                continue;
            }
            if (allConditionsMet(rule, url, satisfiedConditions)) {
                return Optional.of(rule.result());
            }
        }
        return Optional.empty();
    }

    private boolean allConditionsMet(Rule rule, ParsedUrl url, Set<ConditionKey> satisfied) {
        for (Condition cond : rule.conditions()) {
            if (cond.negated()) {
                if (matchesDirect(cond, url)) {
                    return false;
                }
            } else {
                if (!satisfied.contains(new ConditionKey(rule, cond))) {
                    return false;
                }
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

    private record ConditionKey(Rule rule, Condition condition) {}
}
