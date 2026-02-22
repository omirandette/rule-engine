package com.ruleengine.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Aho-Corasick automaton for efficient multi-pattern substring matching.
 *
 * <p>Usage:
 * <ol>
 *   <li>Insert patterns with {@link #insert(String, Object)}</li>
 *   <li>Call {@link #build()} to construct failure and output links</li>
 *   <li>Call {@link #search(String)} to find all pattern matches in a text</li>
 * </ol>
 *
 * <p>Search runs in O(n + z) where n = text length and z = number of matches.
 *
 * @param <V> the type of values associated with each pattern
 */
public final class AhoCorasick<V> {

    private final Node<V> root = new Node<>();
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
        Node<V> current = root;
        for (int i = 0; i < pattern.length(); i++) {
            current = current.children.computeIfAbsent(pattern.charAt(i), _ -> new Node<>());
        }
        current.values.add(value);
    }

    /**
     * Constructs the failure and output links via BFS.
     * Must be called exactly once after all patterns are inserted and before any search.
     */
    public void build() {
        Queue<Node<V>> queue = new LinkedList<>();
        for (Node<V> child : root.children.values()) {
            child.failure = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node<V> current = queue.poll();
            for (Map.Entry<Character, Node<V>> entry : current.children.entrySet()) {
                char ch = entry.getKey();
                Node<V> child = entry.getValue();
                Node<V> fail = current.failure;
                while (fail != null && !fail.children.containsKey(ch)) {
                    fail = fail.failure;
                }
                child.failure = (fail == null) ? root : fail.children.get(ch);
                child.outputLink = child.failure.values.isEmpty()
                        ? child.failure.outputLink
                        : child.failure;
                queue.add(child);
            }
        }
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
        List<V> result = new ArrayList<>();
        result.addAll(root.values);
        Node<V> current = root;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            while (current != root && !current.children.containsKey(ch)) {
                current = current.failure;
            }
            current = current.children.getOrDefault(ch, root);
            result.addAll(current.values);
            Node<V> out = current.outputLink;
            while (out != null) {
                result.addAll(out.values);
                out = out.outputLink;
            }
        }
        return result;
    }

    private static final class Node<V> {
        final Map<Character, Node<V>> children = new HashMap<>();
        final List<V> values = new ArrayList<>();
        Node<V> failure;
        Node<V> outputLink;
    }
}
