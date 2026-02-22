package com.ruleengine.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A generic prefix trie that maps string keys to lists of values.
 *
 * <p>Supports three query modes:
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

    private final Node<V> root = new Node<>();

    /**
     * Inserts a value associated with the given key.
     * Multiple values can be stored under the same key.
     *
     * @param key   the string key
     * @param value the value to associate
     */
    public void insert(String key, V value) {
        Node<V> current = root;
        for (int i = 0; i < key.length(); i++) {
            current = current.children.computeIfAbsent(key.charAt(i), _ -> new Node<>());
        }
        current.values.add(value);
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
        List<V> result = new ArrayList<>();
        Node<V> current = root;
        result.addAll(current.values);
        for (int i = 0; i < input.length(); i++) {
            current = current.children.get(input.charAt(i));
            if (current == null) {
                break;
            }
            result.addAll(current.values);
        }
        return result;
    }

    /**
     * Returns all values whose keys appear as substrings of the given input.
     *
     * <p>Iterates every starting position in the input and performs a prefix walk.
     * This reuses the trie structure for substring matching at the cost of
     * O(n * d) where n = input length and d = average trie depth.
     *
     * @param input the string to search for substrings in
     * @return list of values whose keys are substrings of {@code input}
     */
    public List<V> findSubstringsOf(String input) {
        List<V> result = new ArrayList<>();
        for (int start = 0; start < input.length(); start++) {
            Node<V> current = root;
            for (int i = start; i < input.length(); i++) {
                current = current.children.get(input.charAt(i));
                if (current == null) {
                    break;
                }
                result.addAll(current.values);
            }
        }
        result.addAll(root.values);
        return result;
    }

    private static final class Node<V> {
        final Map<Character, Node<V>> children = new HashMap<>();
        final List<V> values = new ArrayList<>();
    }
}
