package com.ruleengine.index;

import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlPart;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
     * A reference linking a match back to its parent rule by dense ID.
     *
     * @param ruleId the dense rule ID (0..N-1)
     */
    public record ConditionRef(int ruleId) {}

    private final Map<UrlPart, Map<String, List<ConditionRef>>> equalsIndexes;
    private final Map<UrlPart, Trie<ConditionRef>> startsWithIndexes;
    private final Map<UrlPart, Trie<ConditionRef>> endsWithIndexes;

    private final ContainsStrategy containsStrategy;
    private final Map<UrlPart, Trie<ConditionRef>> containsTrieIndexes;
    private final Map<UrlPart, AhoCorasick<ConditionRef>> containsAcIndexes;

    private final Map<Rule, Integer> ruleIds;
    private final int ruleCount;
    private final int[] nonNegatedCounts;

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

        this.ruleIds = new HashMap<>(rules.size() * 2);
        for (int i = 0; i < rules.size(); i++) {
            ruleIds.put(rules.get(i), i);
        }
        this.ruleCount = rules.size();
        this.nonNegatedCounts = new int[ruleCount];

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
            int id = ruleIds.get(rule);
            for (Condition cond : rule.conditions()) {
                if (cond.negated()) {
                    continue;
                }
                nonNegatedCounts[id]++;
                ConditionRef ref = new ConditionRef(id);
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
     * Returns the dense integer ID assigned to the given rule.
     *
     * @param rule the rule to look up
     * @return the rule's dense ID (0..N-1)
     */
    public int ruleId(Rule rule) {
        return ruleIds.get(rule);
    }

    /**
     * Returns the total number of indexed rules.
     *
     * @return the rule count
     */
    public int ruleCount() {
        return ruleCount;
    }

    /**
     * Returns the expected count of non-negated conditions per rule.
     *
     * @return the non-negated counts array (indexed by rule ID)
     */
    public int[] nonNegatedCounts() {
        return nonNegatedCounts;
    }

    /**
     * Queries the index for all non-negated conditions that match the given URL,
     * grouped by rule in a dense array.
     *
     * @param url the parsed URL to match against
     * @return a {@link CandidateResult} with satisfied conditions per rule ID
     */
    public CandidateResult queryCandidates(ParsedUrl url) {
        CandidateResult candidates = new CandidateResult(ruleCount, nonNegatedCounts);
        Consumer<ConditionRef> consumer = ref -> candidates.increment(ref.ruleId());
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
                for (ConditionRef ref : eqRefs) {
                    consumer.accept(ref);
                }
            }

            startsWithIndexes.get(part).findPrefixesOf(value, consumer);

            if (reversed[part.ordinal()] != null) {
                endsWithIndexes.get(part).findPrefixesOf(reversed[part.ordinal()], consumer);
            }

            if (containsStrategy == ContainsStrategy.AHO_CORASICK) {
                containsAcIndexes.get(part).search(value, consumer);
            } else {
                containsTrieIndexes.get(part).findSubstringsOf(value, consumer);
            }
        }
        return candidates;
    }
}
