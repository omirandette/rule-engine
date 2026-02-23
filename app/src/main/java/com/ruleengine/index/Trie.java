package com.ruleengine.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A character-based trie that maps string keys to lists of values.
 *
 * <p>Supports prefix queries via {@link #findPrefixesOf(String)}, which returns values for all
 * keys that are prefixes of the input (used for {@code STARTS_WITH} matching). Walks the trie
 * character by character with no substring allocations.
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
     * Invokes the consumer for each value whose key is a prefix of the input.
     *
     * <p>Walks the trie character by character with no allocations.
     *
     * @param input    the string to match prefixes against
     * @param consumer called for each matching value
     */
    public void findPrefixesOf(String input, Consumer<V> consumer) {
        // Empty-key values match every input (a zero-length string is a prefix of all strings)
        for (V v : emptyKeyValues) {
            consumer.accept(v);
        }
        Node current = root;
        for (int i = 0; i < input.length(); i++) {
            current = current.child(input.charAt(i));
            if (current == null) {
                return; // no trie path continues past this character
            }
            if (current.values != null) {
                // This node marks the end of one or more inserted keys,
                // meaning input[0..i] is a stored prefix that matches.
                for (V v : current.values) {
                    consumer.accept(v);
                }
            }
        }
    }

    /**
     * Returns all values whose keys are prefixes of the given input.
     *
     * @param input the string to match prefixes against
     * @return list of values whose keys are prefixes of {@code input}
     */
    public List<V> findPrefixesOf(String input) {
        List<V> result = new ArrayList<>();
        findPrefixesOf(input, result::add);
        return result;
    }

    /**
     * Hybrid trie node using a two-tier child storage strategy:
     * <ul>
     *   <li>ASCII characters (0–127) are stored in a direct-indexed {@code Object[]}
     *       for O(1) lookup with no hashing overhead.</li>
     *   <li>Non-ASCII characters fall back to a {@link HashMap}, which is only
     *       allocated when needed (most URL data is ASCII-only).</li>
     * </ul>
     *
     * <p>The {@code ascii} array stores {@code Node} references as {@code Object}
     * to avoid a self-referential generic array ({@code Node[]} is not allowed in
     * an inner class context without unchecked warnings either way).
     */
    private final class Node {
        private static final int ASCII_SIZE = 128;

        Object[] ascii;
        Map<Character, Node> extended;
        List<V> values;

        @SuppressWarnings("unchecked")
        Node child(char c) {
            if (c < ASCII_SIZE) {
                return ascii == null ? null : (Node) ascii[c];
            }
            return extended == null ? null : extended.get(c);
        }

        @SuppressWarnings("unchecked")
        Node childOrCreate(char c) {
            if (c < ASCII_SIZE) {
                if (ascii == null) {
                    ascii = new Object[ASCII_SIZE];
                }
                Node node = (Node) ascii[c];
                if (node == null) {
                    node = new Node();
                    ascii[c] = node;
                }
                return node;
            }
            if (extended == null) {
                extended = new HashMap<>(4);
            }
            return extended.computeIfAbsent(c, _ -> new Node());
        }
    }
}
