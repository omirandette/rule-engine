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

public final class RuleEngine {

    private final List<Rule> sortedRules;
    private final RuleIndex index;

    public RuleEngine(List<Rule> rules, ContainsStrategy containsStrategy) {
        this.sortedRules = rules.stream().sorted().toList();
        this.index = new RuleIndex(rules, containsStrategy);
    }

    public RuleEngine(List<Rule> rules) {
        this(rules, ContainsStrategy.TRIE);
    }

    public record UrlResult(String url, String result) {}

    public Optional<String> evaluate(ParsedUrl url) {
        // Query the index to get all non-negated conditions that matched
        Set<RuleIndex.ConditionRef> candidates = index.queryCandidates(url);

        // Collect rules that have at least one indexed condition match
        Set<Rule> candidateRules = new HashSet<>();
        // Track which (rule, condition) pairs were satisfied by the index
        Set<ConditionKey> satisfiedConditions = new HashSet<>();

        for (RuleIndex.ConditionRef ref : candidates) {
            candidateRules.add(ref.rule());
            satisfiedConditions.add(new ConditionKey(ref.rule(), ref.condition()));
        }

        // Also add rules that have ONLY negated conditions (they won't appear in index)
        for (Rule rule : sortedRules) {
            if (rule.conditions().stream().allMatch(Condition::negated)) {
                candidateRules.add(rule);
            }
        }

        // Evaluate in priority order
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
                // Negated conditions are checked directly
                if (matchesDirect(cond, url)) {
                    return false; // The condition matched, but it's negated → rule fails
                }
            } else {
                // Non-negated: must be in the satisfied set from the index
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
