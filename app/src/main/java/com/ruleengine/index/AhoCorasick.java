package com.ruleengine.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ahocorasick.trie.Emit;

/**
 * Aho-Corasick automaton for efficient multi-pattern substring matching.
 *
 * <p>Backed by the robert-bor Aho-Corasick library. Usage:
 * <ol>
 *   <li>Insert patterns with {@link #insert(String, Object)}</li>
 *   <li>Call {@link #build()} to construct the automaton</li>
 *   <li>Call {@link #search(String)} to find all pattern matches in a text</li>
 * </ol>
 *
 * @param <V> the type of values associated with each pattern
 */
public final class AhoCorasick<V> {

    private final Map<String, List<V>> valueMap = new HashMap<>();
    private final List<V> emptyPatternValues = new ArrayList<>();
    private org.ahocorasick.trie.Trie trie;
    private boolean built = false;

    /**
     * Inserts a pattern with an associated value into the automaton.
     *
     * @param pattern the pattern string
     * @param value   the value to return when this pattern is found
     * @throws IllegalStateException if called after {@link #build()}
     */
    public void insert(String pattern, V value) {
        if (built) {
            throw new IllegalStateException("Cannot insert after build()");
        }
        if (pattern.isEmpty()) {
            emptyPatternValues.add(value);
        } else {
            valueMap.computeIfAbsent(pattern, _ -> new ArrayList<>()).add(value);
        }
    }

    /**
     * Constructs the automaton.
     * Must be called after all patterns are inserted and before any search.
     */
    public void build() {
        org.ahocorasick.trie.Trie.TrieBuilder builder = org.ahocorasick.trie.Trie.builder();
        for (String pattern : valueMap.keySet()) {
            builder.addKeyword(pattern);
        }
        trie = builder.build();
        built = true;
    }

    /**
     * Searches the text for all inserted patterns and returns their associated values.
     *
     * @param text the text to search
     * @return list of values for all patterns found in the text
     * @throws IllegalStateException if {@link #build()} has not been called
     */
    public List<V> search(String text) {
        if (!built) {
            throw new IllegalStateException("Must call build() before search()");
        }
        List<V> result = new ArrayList<>(emptyPatternValues);
        Collection<Emit> emits = trie.parseText(text);
        for (Emit emit : emits) {
            List<V> values = valueMap.get(emit.getKeyword());
            if (values != null) {
                result.addAll(values);
            }
        }
        return result;
    }
}
