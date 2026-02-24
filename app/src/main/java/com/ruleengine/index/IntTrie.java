package com.ruleengine.index;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * An int-specialized trie that maps string keys to lists of primitive int values.
 *
 * <p>Functionally equivalent to {@link Trie Trie&lt;Integer&gt;} but avoids boxing,
 * {@code List} indirection, and {@code Object[]} casts in the hot path. Values are
 * stored in compact {@code int[]} arrays at each node.
 *
 * <p>Supports prefix queries via {@link #findPrefixesOf(String, IntConsumer)}.
 */
public final class IntTrie {

    private final Node root = new Node();
    private int[] emptyKeyValues = EMPTY;
    private int emptyKeyCount;
    private boolean hasKeys;

    private static final int[] EMPTY = new int[0];

    /**
     * Returns {@code true} if this trie contains no entries.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return !hasKeys && emptyKeyCount == 0;
    }

    /**
     * Inserts an int value associated with the given key.
     *
     * @param key   the string key
     * @param value the int value to associate
     */
    public void insert(String key, int value) {
        hasKeys = true;
        if (key.isEmpty()) {
            if (emptyKeyCount == emptyKeyValues.length) {
                int newLen = emptyKeyValues.length == 0 ? 2 : emptyKeyValues.length * 2;
                int[] grown = new int[newLen];
                System.arraycopy(emptyKeyValues, 0, grown, 0, emptyKeyCount);
                emptyKeyValues = grown;
            }
            emptyKeyValues[emptyKeyCount++] = value;
            return;
        }
        Node current = root;
        for (int i = 0; i < key.length(); i++) {
            current = current.childOrCreate(key.charAt(i));
        }
        current.addValue(value);
    }

    /**
     * Invokes the consumer for each int value whose key is a prefix of the input.
     *
     * @param input    the string to match prefixes against
     * @param consumer called for each matching int value
     */
    public void findPrefixesOf(String input, IntConsumer consumer) {
        for (int i = 0; i < emptyKeyCount; i++) {
            consumer.accept(emptyKeyValues[i]);
        }
        Node current = root;
        for (int i = 0; i < input.length(); i++) {
            current = current.child(input.charAt(i));
            if (current == null) {
                return;
            }
            int[] vals = current.values;
            for (int j = 0; j < current.valueCount; j++) {
                consumer.accept(vals[j]);
            }
        }
    }

    /**
     * Invokes the consumer for each int value whose key is a prefix of the input buffer.
     *
     * @param input    the character buffer to match prefixes against
     * @param length   number of characters to read from the buffer
     * @param consumer called for each matching int value
     */
    public void findPrefixesOf(char[] input, int length, IntConsumer consumer) {
        for (int i = 0; i < emptyKeyCount; i++) {
            consumer.accept(emptyKeyValues[i]);
        }
        Node current = root;
        for (int i = 0; i < length; i++) {
            current = current.child(input[i]);
            if (current == null) {
                return;
            }
            int[] vals = current.values;
            for (int j = 0; j < current.valueCount; j++) {
                consumer.accept(vals[j]);
            }
        }
    }

    /**
     * Hybrid trie node using a two-tier child storage strategy:
     * ASCII characters (0-127) in a direct-indexed array, non-ASCII in a HashMap.
     * Values are stored in a compact {@code int[]} with a separate count.
     */
    private static final class Node {
        private static final int ASCII_SIZE = 128;

        Object[] ascii;
        Map<Character, Node> extended;
        int[] values = EMPTY;
        int valueCount;

        void addValue(int value) {
            if (valueCount == values.length) {
                int newLen = values.length == 0 ? 2 : values.length * 2;
                int[] grown = new int[newLen];
                System.arraycopy(values, 0, grown, 0, valueCount);
                values = grown;
            }
            values[valueCount++] = value;
        }

        Node child(char c) {
            if (c < ASCII_SIZE) {
                return ascii == null ? null : (Node) ascii[c];
            }
            return extended == null ? null : extended.get(c);
        }

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
