package com.ruleengine.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public final class AhoCorasick<V> {

    private final Node<V> root = new Node<>();
    private boolean built = false;

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

    public void build() {
        Queue<Node<V>> queue = new LinkedList<>();
        // Initialize depth-1 nodes: failure -> root
        for (Node<V> child : root.children.values()) {
            child.failure = root;
            queue.add(child);
        }
        // BFS to build failure and output links
        while (!queue.isEmpty()) {
            Node<V> current = queue.poll();
            for (Map.Entry<Character, Node<V>> entry : current.children.entrySet()) {
                char ch = entry.getKey();
                Node<V> child = entry.getValue();
                // Walk failure chain of parent to find failure for child
                Node<V> fail = current.failure;
                while (fail != null && !fail.children.containsKey(ch)) {
                    fail = fail.failure;
                }
                child.failure = (fail == null) ? root : fail.children.get(ch);
                // Output link: nearest node in failure chain that has values
                child.outputLink = child.failure.values.isEmpty()
                        ? child.failure.outputLink
                        : child.failure;
                queue.add(child);
            }
        }
        built = true;
    }

    public List<V> search(String text) {
        if (!built) {
            throw new IllegalStateException("Must call build() before search()");
        }
        List<V> result = new ArrayList<>();
        // Empty-pattern matches
        result.addAll(root.values);
        Node<V> current = root;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            while (current != root && !current.children.containsKey(ch)) {
                current = current.failure;
            }
            current = current.children.getOrDefault(ch, root);
            // Collect values from this node
            result.addAll(current.values);
            // Walk output links for additional matches
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
