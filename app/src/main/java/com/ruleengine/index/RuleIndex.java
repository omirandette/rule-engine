package com.ruleengine.index;

import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Operator;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlPart;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indexes non-negated rule conditions by (UrlPart, Operator) for fast lookup.
 *
 * <p>Each operator type is backed by an appropriate data structure:
 * <ul>
 *   <li>{@code EQUALS} — {@link HashMap} for O(1) lookup</li>
 *   <li>{@code STARTS_WITH} — {@link Trie} for prefix matching</li>
 *   <li>{@code ENDS_WITH} — {@link Trie} on reversed strings</li>
 *   <li>{@code CONTAINS} — {@link Trie} or {@link AhoCorasick} (selected via {@link ContainsStrategy})</li>
 * </ul>
 *
 * <p>Negated conditions are excluded from the index and must be evaluated directly at match time.
 */
public final class RuleIndex {

    /**
     * A reference linking a condition back to its parent rule.
     *
     * @param rule      the rule containing this condition
     * @param condition the specific condition that matched
     */
    public record ConditionRef(Rule rule, Condition condition) {}

    private final Map<UrlPart, Map<String, List<ConditionRef>>> equalsIndexes;
    private final Map<UrlPart, Trie<ConditionRef>> startsWithIndexes;
    private final Map<UrlPart, Trie<ConditionRef>> endsWithIndexes;

    private final ContainsStrategy containsStrategy;
    private final Map<UrlPart, Trie<ConditionRef>> containsTrieIndexes;
    private final Map<UrlPart, AhoCorasick<ConditionRef>> containsAcIndexes;

    /**
     * Builds the index from a list of rules using the specified contains strategy.
     *
     * @param rules             the rules to index
     * @param containsStrategy  the data structure to use for CONTAINS matching
     */
    public RuleIndex(List<Rule> rules, ContainsStrategy containsStrategy) {
        this.containsStrategy = containsStrategy;
        this.equalsIndexes = new EnumMap<>(UrlPart.class);
        this.startsWithIndexes = new EnumMap<>(UrlPart.class);
        this.endsWithIndexes = new EnumMap<>(UrlPart.class);
        this.containsTrieIndexes = new EnumMap<>(UrlPart.class);
        this.containsAcIndexes = new EnumMap<>(UrlPart.class);

        for (UrlPart part : UrlPart.values()) {
            equalsIndexes.put(part, new HashMap<>());
            startsWithIndexes.put(part, new Trie<>());
            endsWithIndexes.put(part, new Trie<>());
            if (containsStrategy == ContainsStrategy.AHO_CORASICK) {
                containsAcIndexes.put(part, new AhoCorasick<>());
            } else {
                containsTrieIndexes.put(part, new Trie<>());
            }
        }

        for (Rule rule : rules) {
            for (Condition cond : rule.conditions()) {
                if (cond.negated()) {
                    continue;
                }
                ConditionRef ref = new ConditionRef(rule, cond);
                switch (cond.operator()) {
                    case EQUALS -> equalsIndexes.get(cond.part())
                            .computeIfAbsent(cond.value(), _ -> new ArrayList<>())
                            .add(ref);
                    case STARTS_WITH -> startsWithIndexes.get(cond.part())
                            .insert(cond.value(), ref);
                    case ENDS_WITH -> endsWithIndexes.get(cond.part())
                            .insert(new StringBuilder(cond.value()).reverse().toString(), ref);
                    case CONTAINS -> {
                        if (containsStrategy == ContainsStrategy.AHO_CORASICK) {
                            containsAcIndexes.get(cond.part()).insert(cond.value(), ref);
                        } else {
                            containsTrieIndexes.get(cond.part()).insert(cond.value(), ref);
                        }
                    }
                }
            }
        }

        if (containsStrategy == ContainsStrategy.AHO_CORASICK) {
            for (AhoCorasick<ConditionRef> ac : containsAcIndexes.values()) {
                ac.build();
            }
        }
    }

    /**
     * Builds the index using the default {@link ContainsStrategy#TRIE} strategy.
     *
     * @param rules the rules to index
     */
    public RuleIndex(List<Rule> rules) {
        this(rules, ContainsStrategy.TRIE);
    }

    /**
     * Queries the index for all non-negated conditions that match the given URL,
     * grouped by rule.
     *
     * @param url the parsed URL to match against
     * @return map from each candidate rule to its set of satisfied conditions
     */
    public Map<Rule, Set<Condition>> queryCandidates(ParsedUrl url) {
        Map<Rule, Set<Condition>> candidates = new HashMap<>();
        UrlPart[] parts = UrlPart.values();

        // Pre-compute reversed values only for parts that have ENDS_WITH rules
        String[] reversed = new String[parts.length];
        for (UrlPart part : parts) {
            if (!endsWithIndexes.get(part).isEmpty()) {
                reversed[part.ordinal()] = new StringBuilder(url.part(part)).reverse().toString();
            }
        }

        for (UrlPart part : parts) {
            String value = url.part(part);

            List<ConditionRef> eqRefs = equalsIndexes.get(part).get(value);
            if (eqRefs != null) {
                addAll(candidates, eqRefs);
            }

            addAll(candidates, startsWithIndexes.get(part).findPrefixesOf(value));

            if (reversed[part.ordinal()] != null) {
                addAll(candidates, endsWithIndexes.get(part).findPrefixesOf(reversed[part.ordinal()]));
            }

            if (containsStrategy == ContainsStrategy.AHO_CORASICK) {
                addAll(candidates, containsAcIndexes.get(part).search(value));
            } else {
                addAll(candidates, containsTrieIndexes.get(part).findSubstringsOf(value));
            }
        }
        return candidates;
    }

    private void addAll(Map<Rule, Set<Condition>> candidates, List<ConditionRef> refs) {
        for (ConditionRef ref : refs) {
            candidates.computeIfAbsent(ref.rule(), _ -> new HashSet<>()).add(ref.condition());
        }
    }
}
