package com.ruleengine.index;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.trie.PatriciaTrie;

/**
 * A generic prefix trie that maps string keys to lists of values.
 *
 * <p>Backed by Apache Commons {@link PatriciaTrie}. Supports three query modes:
 * <ul>
 *   <li>{@link #findPrefixesOf(String)} — returns values for all keys that are prefixes of the input
 *       (used for {@code STARTS_WITH} matching)</li>
 *   <li>{@link #findSubstringsOf(String)} — returns values for all keys that appear as substrings
 *       of the input (used for {@code CONTAINS} matching via the TRIE strategy)</li>
 * </ul>
 *
 * <p>Also used for {@code ENDS_WITH} matching by inserting and querying reversed strings.
 *
 * @param <V> the type of values stored at each key
 */
public final class Trie<V> {

    private final PatriciaTrie<List<V>> trie = new PatriciaTrie<>();
    private final List<V> emptyKeyValues = new ArrayList<>();

    /**
     * Inserts a value associated with the given key.
     * Multiple values can be stored under the same key.
     *
     * @param key   the string key
     * @param value the value to associate
     */
    public void insert(String key, V value) {
        if (key.isEmpty()) {
            emptyKeyValues.add(value);
        } else {
            trie.computeIfAbsent(key, _ -> new ArrayList<>()).add(value);
        }
    }

    /**
     * Returns all values whose keys are prefixes of the given input.
     *
     * <p>For example, if {@code "ab"} and {@code "abc"} are inserted,
     * {@code findPrefixesOf("abcd")} returns values for both.
     *
     * @param input the string to match prefixes against
     * @return list of values whose keys are prefixes of {@code input}
     */
    public List<V> findPrefixesOf(String input) {
        List<V> result = new ArrayList<>(emptyKeyValues);
        for (int i = 1; i <= input.length(); i++) {
            List<V> values = trie.get(input.substring(0, i));
            if (values != null) {
                result.addAll(values);
            }
        }
        return result;
    }

    /**
     * Returns all values whose keys appear as substrings of the given input.
     *
     * <p>Iterates every starting position in the input and checks all prefixes
     * from that position against the trie.
     *
     * @param input the string to search for substrings in
     * @return list of values whose keys are substrings of {@code input}
     */
    public List<V> findSubstringsOf(String input) {
        List<V> result = new ArrayList<>(emptyKeyValues);
        for (int start = 0; start < input.length(); start++) {
            for (int end = start + 1; end <= input.length(); end++) {
                List<V> values = trie.get(input.substring(start, end));
                if (values != null) {
                    result.addAll(values);
                }
            }
        }
        return result;
    }
}
