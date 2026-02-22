package com.ruleengine.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Trie<V> {

    private final Node<V> root = new Node<>();

    public void insert(String key, V value) {
        Node<V> current = root;
        for (int i = 0; i < key.length(); i++) {
            current = current.children.computeIfAbsent(key.charAt(i), _ -> new Node<>());
        }
        current.values.add(value);
    }

    /**
     * Returns all values whose keys are prefixes of the given input.
     * E.g., if "ab" and "abc" are inserted, findPrefixesOf("abcd") returns values for both.
     * Used for STARTS_WITH matching.
     */
    public List<V> findPrefixesOf(String input) {
        List<V> result = new ArrayList<>();
        Node<V> current = root;
        // Empty-string key matches everything
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
     * Iterates every starting position and does a prefix walk.
     * Used for CONTAINS matching via TRIE strategy.
     */
    public List<V> findSubstringsOf(String input) {
        List<V> result = new ArrayList<>();
        for (int start = 0; start < input.length(); start++) {
            Node<V> current = root;
            // Empty-string key at each position — only add once (handled outside loop)
            for (int i = start; i < input.length(); i++) {
                current = current.children.get(input.charAt(i));
                if (current == null) {
                    break;
                }
                result.addAll(current.values);
            }
        }
        // Values stored at root (empty-string keys) match if input is non-empty
        result.addAll(root.values);
        return result;
    }

    private static final class Node<V> {
        final Map<Character, Node<V>> children = new HashMap<>();
        final List<V> values = new ArrayList<>();
    }
}
