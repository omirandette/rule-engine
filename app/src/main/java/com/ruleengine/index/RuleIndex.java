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

public final class RuleIndex {

    public record ConditionRef(Rule rule, Condition condition) {}

    // Per (UrlPart, Operator) indexes
    private final Map<UrlPart, Map<String, List<ConditionRef>>> equalsIndexes;
    private final Map<UrlPart, Trie<ConditionRef>> startsWithIndexes;
    private final Map<UrlPart, Trie<ConditionRef>> endsWithIndexes;

    // CONTAINS: backed by either Trie or AhoCorasick
    private final ContainsStrategy containsStrategy;
    private final Map<UrlPart, Trie<ConditionRef>> containsTrieIndexes;
    private final Map<UrlPart, AhoCorasick<ConditionRef>> containsAcIndexes;

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
                    continue; // negated conditions are not indexed
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

        // Build Aho-Corasick automata if that strategy was chosen
        if (containsStrategy == ContainsStrategy.AHO_CORASICK) {
            for (AhoCorasick<ConditionRef> ac : containsAcIndexes.values()) {
                ac.build();
            }
        }
    }

    public RuleIndex(List<Rule> rules) {
        this(rules, ContainsStrategy.TRIE);
    }

    /**
     * Query the index for a parsed URL and return all matching ConditionRefs
     * (only non-negated conditions that matched).
     */
    public Set<ConditionRef> queryCandidates(ParsedUrl url) {
        Set<ConditionRef> candidates = new HashSet<>();
        for (UrlPart part : UrlPart.values()) {
            String value = url.part(part);

            // EQUALS
            List<ConditionRef> eqRefs = equalsIndexes.get(part).get(value);
            if (eqRefs != null) {
                candidates.addAll(eqRefs);
            }

            // STARTS_WITH
            candidates.addAll(startsWithIndexes.get(part).findPrefixesOf(value));

            // ENDS_WITH (reverse the input)
            String reversed = new StringBuilder(value).reverse().toString();
            candidates.addAll(endsWithIndexes.get(part).findPrefixesOf(reversed));

            // CONTAINS
            if (containsStrategy == ContainsStrategy.AHO_CORASICK) {
                candidates.addAll(containsAcIndexes.get(part).search(value));
            } else {
                candidates.addAll(containsTrieIndexes.get(part).findSubstringsOf(value));
            }
        }
        return candidates;
    }
}
