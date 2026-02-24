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
 *   <li>{@code CONTAINS} — {@link AhoCorasick} automaton for multi-pattern substring matching</li>
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
    private final Map<UrlPart, AhoCorasick<ConditionRef>> containsAcIndexes;

    private final Map<Rule, Integer> ruleIds;
    private final int ruleCount;
    private final int[] nonNegatedCounts;
    private final CandidateResult reusableCandidates;
    private final boolean[] hasEndsWith;
    private final Consumer<ConditionRef> incrementConsumer;
    private char[] reverseBuf = new char[256];

    /**
     * Builds the index from a list of rules.
     *
     * @param rules the rules to index
     */
    public RuleIndex(List<Rule> rules) {
        this.equalsIndexes = new EnumMap<>(UrlPart.class);
        this.startsWithIndexes = new EnumMap<>(UrlPart.class);
        this.endsWithIndexes = new EnumMap<>(UrlPart.class);
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
            containsAcIndexes.put(part, new AhoCorasick<>());
        }

        for (Rule rule : rules) {
            int id = ruleIds.get(rule);
            for (Condition cond : rule.conditions()) {
                if (!cond.negated()) {
                    indexCondition(cond, id);
                }
            }
        }

        for (AhoCorasick<ConditionRef> ac : containsAcIndexes.values()) {
            ac.build();
        }

        this.reusableCandidates = new CandidateResult(ruleCount, nonNegatedCounts);
        this.incrementConsumer = ref -> reusableCandidates.increment(ref.ruleId());

        UrlPart[] parts = UrlPart.values();
        this.hasEndsWith = new boolean[parts.length];
        for (UrlPart part : parts) {
            hasEndsWith[part.ordinal()] = !endsWithIndexes.get(part).isEmpty();
        }
    }

    /** Adds a non-negated condition to the appropriate operator-specific index. */
    private void indexCondition(Condition cond, int ruleId) {
        nonNegatedCounts[ruleId]++;
        ConditionRef ref = new ConditionRef(ruleId);
        switch (cond.operator()) {
            case EQUALS -> equalsIndexes.get(cond.part())
                    .computeIfAbsent(cond.value(), _ -> new ArrayList<>())
                    .add(ref);
            case STARTS_WITH -> startsWithIndexes.get(cond.part())
                    .insert(cond.value(), ref);
            case ENDS_WITH -> endsWithIndexes.get(cond.part())
                    .insert(new StringBuilder(cond.value()).reverse().toString(), ref);
            case CONTAINS -> containsAcIndexes.get(cond.part())
                    .insert(cond.value(), ref);
        }
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
        reusableCandidates.reset();

        for (UrlPart part : UrlPart.values()) {
            String value = url.part(part);

            List<ConditionRef> eqRefs = equalsIndexes.get(part).get(value);
            if (eqRefs != null) {
                for (ConditionRef ref : eqRefs) {
                    incrementConsumer.accept(ref);
                }
            }

            startsWithIndexes.get(part).findPrefixesOf(value, incrementConsumer);

            if (hasEndsWith[part.ordinal()]) {
                int len = value.length();
                if (len > reverseBuf.length) {
                    reverseBuf = new char[len];
                }
                for (int i = 0; i < len; i++) {
                    reverseBuf[i] = value.charAt(len - 1 - i);
                }
                endsWithIndexes.get(part).findPrefixesOf(reverseBuf, len, incrementConsumer);
            }

            containsAcIndexes.get(part).search(value, incrementConsumer);
        }
        return reusableCandidates;
    }
}
