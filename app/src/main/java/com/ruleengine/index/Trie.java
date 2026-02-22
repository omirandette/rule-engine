package com.ruleengine.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A character-based trie that maps string keys to lists of values.
 *
 * <p>Supports three query modes:
 * <ul>
 *   <li>{@link #findPrefixesOf(String)} — returns values for all keys that are prefixes of the input
 *       (used for {@code STARTS_WITH} matching). Walks the trie character by character with no
 *       substring allocations.</li>
 *   <li>{@link #findSubstringsOf(String)} — returns values for all keys that appear as substrings
 *       of the input (used for {@code CONTAINS} matching via the TRIE strategy)</li>
 * </ul>
 *
 * <p>Also used for {@code ENDS_WITH} matching by inserting and querying reversed strings.
 *
 * @param <V> the type of values stored at each key
 */
public final class Trie<V> {

    private final Node root = new Node();
    private final List<V> emptyKeyValues = new ArrayList<>();
    private boolean hasKeys;

    /**
     * Returns {@code true} if this trie contains no entries.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return !hasKeys && emptyKeyValues.isEmpty();
    }

    /**
     * Inserts a value associated with the given key.
     * Multiple values can be stored under the same key.
     *
     * @param key   the string key
     * @param value the value to associate
     */
    public void insert(String key, V value) {
        hasKeys = true;
        if (key.isEmpty()) {
            emptyKeyValues.add(value);
            return;
        }
        Node current = root;
        for (int i = 0; i < key.length(); i++) {
            current = current.childOrCreate(key.charAt(i));
        }
        if (current.values == null) {
            current.values = new ArrayList<>(2);
        }
        current.values.add(value);
    }

    /**
     * Returns all values whose keys are prefixes of the given input.
     *
     * <p>Walks the trie character by character with no substring allocations.
     * For example, if {@code "ab"} and {@code "abc"} are inserted,
     * {@code findPrefixesOf("abcd")} returns values for both.
     *
     * @param input the string to match prefixes against
     * @return list of values whose keys are prefixes of {@code input}
     */
    public List<V> findPrefixesOf(String input) {
        List<V> result = new ArrayList<>(emptyKeyValues);
        Node current = root;
        for (int i = 0; i < input.length(); i++) {
            current = current.child(input.charAt(i));
            if (current == null) {
                break;
            }
            if (current.values != null) {
                result.addAll(current.values);
            }
        }
        return result;
    }

    /**
     * Returns all values whose keys appear as substrings of the given input.
     *
     * <p>For each starting position, walks the trie character by character
     * collecting matches. No substring allocations are performed.
     *
     * @param input the string to search for substrings in
     * @return list of values whose keys are substrings of {@code input}
     */
    public List<V> findSubstringsOf(String input) {
        List<V> result = new ArrayList<>(emptyKeyValues);
        for (int start = 0; start < input.length(); start++) {
            Node current = root;
            for (int i = start; i < input.length(); i++) {
                current = current.child(input.charAt(i));
                if (current == null) {
                    break;
                }
                if (current.values != null) {
                    result.addAll(current.values);
                }
            }
        }
        return result;
    }

    private final class Node {
        Map<Character, Node> children;
        List<V> values;

        Node child(char c) {
            return children == null ? null : children.get(c);
        }

        Node childOrCreate(char c) {
            if (children == null) {
                children = new HashMap<>(4);
            }
            return children.computeIfAbsent(c, _ -> new Node());
        }
    }
}
