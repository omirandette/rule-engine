package com.ruleengine.index;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Aho-Corasick automaton for efficient multi-pattern substring matching.
 *
 * <p>Uses a DFA with array-indexed transitions for ASCII characters
 * (direct {@code int[128]} lookup per state) and a {@link HashMap} fallback
 * for non-ASCII. After {@link #build()}, the goto function is fully completed
 * so search requires no failure-link chasing — just a single array lookup
 * per character.
 *
 * <p>Usage:
 * <ol>
 *   <li>Insert patterns with {@link #insert(String, Object)}</li>
 *   <li>Call {@link #build()} to construct the automaton</li>
 *   <li>Call {@link #search(String, Consumer)} or {@link #search(String)}</li>
 * </ol>
 *
 * @param <V> the type of values associated with each pattern
 */
public final class AhoCorasick<V> {

    private static final int ASCII_SIZE = 128;
    private static final int NO_STATE = -1;
    private static final int INITIAL_CAPACITY = 64;

    private final List<V> emptyPatternValues = new ArrayList<>();

    // --- Two-phase data structures ---
    // During insertion, Lists allow dynamic growth as states are allocated.
    // After build(), these are flattened into fixed-size arrays for cache-friendly
    // search-phase access (contiguous memory, no List indirection). The build-phase
    // Lists are then nulled to free memory.
    //
    // Each state has two tiers of transitions (same strategy as Trie.Node):
    //   - ASCII (0–127): direct int[] array per state for O(1) lookup
    //   - Non-ASCII:      HashMap<Character, Integer> fallback (often null)

    // Build-phase structures (nulled after build)
    private List<int[]> gotoRows = new ArrayList<>(INITIAL_CAPACITY);
    @SuppressWarnings("unchecked")
    private List<Map<Character, Integer>> extendedRows = new ArrayList<>(INITIAL_CAPACITY);
    private List<List<V>> buildOutput = new ArrayList<>(INITIAL_CAPACITY);
    private int stateCount = 0;

    private static final Object[] EMPTY_OUTPUT = new Object[0];

    // Search-phase arrays (populated by build, one entry per state)
    private int[][] gotoTable;
    @SuppressWarnings("unchecked")
    private Map<Character, Integer>[] extendedGoto;
    private Object[][] output;
    private boolean built = false;

    /** Creates a new empty automaton with the root state allocated. */
    public AhoCorasick() {
        allocateState(); // state 0 = root
    }

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
            return;
        }
        int state = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            int next = getGoto(state, c);
            if (next == NO_STATE) {
                next = allocateState();
                setGoto(state, c, next);
            }
            state = next;
        }
        buildOutput.get(state).add(value);
    }

    /**
     * Constructs the automaton by computing failure links and completing
     * the DFA transition table.
     *
     * <p>After this call, no further inserts are allowed and the automaton
     * is ready for searching. The build proceeds in four phases:
     * <ol>
     *   <li>Initialize depth-1 states (direct children of root)</li>
     *   <li>Compute failure links for all deeper states via BFS</li>
     *   <li>Complete the DFA so every state has a transition for every character</li>
     *   <li>Flatten build-phase Lists into fixed-size arrays for search</li>
     * </ol>
     */
    public void build() {
        int[] failure = new int[stateCount];
        Queue<Integer> queue = new ArrayDeque<>();

        initDepthOneStates(failure, queue);
        computeFailureLinks(failure, queue);
        completeDfa(failure, queue);
        flattenToArrays();
    }

    /**
     * Sets failure links for root's direct children to 0 (root) and fills
     * missing root transitions with self-loops back to root.
     */
    private void initDepthOneStates(int[] failure, Queue<Integer> queue) {
        int[] rootRow = gotoRows.get(0);
        for (int c = 0; c < ASCII_SIZE; c++) {
            int child = rootRow[c];
            if (child == NO_STATE) {
                rootRow[c] = 0; // missing root transitions loop to root
            } else {
                failure[child] = 0;
                queue.add(child);
            }
        }
        Map<Character, Integer> rootExt = extendedRows.get(0);
        if (rootExt != null) {
            for (Map.Entry<Character, Integer> entry : rootExt.entrySet()) {
                failure[entry.getValue()] = 0;
                queue.add(entry.getValue());
            }
        }
    }

    /**
     * BFS from depth-1 states to compute failure links and merge output lists.
     * Each state's failure link points to the longest proper suffix of the
     * path leading to that state which is also a prefix of some pattern.
     */
    private void computeFailureLinks(int[] failure, Queue<Integer> queue) {
        while (!queue.isEmpty()) {
            int current = queue.poll();
            int[] row = gotoRows.get(current);

            for (int c = 0; c < ASCII_SIZE; c++) {
                int child = row[c];
                if (child != NO_STATE) {
                    failure[child] = followFailure(failure, current, (char) c);
                    mergeOutput(child, failure[child]);
                    queue.add(child);
                }
            }

            Map<Character, Integer> ext = extendedRows.get(current);
            if (ext != null) {
                for (Map.Entry<Character, Integer> entry : ext.entrySet()) {
                    int child = entry.getValue();
                    failure[child] = followFailure(failure, current, entry.getKey());
                    mergeOutput(child, failure[child]);
                    queue.add(child);
                }
            }
        }
    }

    /**
     * Completes the DFA so every state has an explicit transition for every
     * character. Missing transitions are filled from the failure state's
     * (already-completed) row. BFS order guarantees that failure states are
     * processed before their dependents.
     */
    private void completeDfa(int[] failure, Queue<Integer> queue) {
        // Seed BFS with root's direct children (skip root itself)
        int[] rootRow = gotoRows.get(0);
        for (int c = 0; c < ASCII_SIZE; c++) {
            int child = rootRow[c];
            if (child != 0) {
                queue.add(child);
            }
        }
        Map<Character, Integer> rootExt = extendedRows.get(0);
        if (rootExt != null) {
            queue.addAll(rootExt.values());
        }

        while (!queue.isEmpty()) {
            int current = queue.poll();
            int[] row = gotoRows.get(current);
            int[] failRow = gotoRows.get(failure[current]);

            for (int c = 0; c < ASCII_SIZE; c++) {
                if (row[c] == NO_STATE) {
                    row[c] = failRow[c]; // inherit from failure state
                } else {
                    queue.add(row[c]);
                }
            }

            inheritExtendedTransitions(current, failure[current]);
            enqueueExtendedChildren(current, queue);
        }
    }

    /** Inherits non-ASCII transitions from the failure state for any chars not already present. */
    private void inheritExtendedTransitions(int state, int failState) {
        Map<Character, Integer> failExt = extendedRows.get(failState);
        if (failExt == null) {
            return;
        }
        Map<Character, Integer> ext = extendedRows.get(state);
        if (ext == null) {
            ext = new HashMap<>(4);
            extendedRows.set(state, ext);
        }
        for (Map.Entry<Character, Integer> entry : failExt.entrySet()) {
            ext.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    /** Adds non-root non-ASCII child states to the BFS queue. */
    private void enqueueExtendedChildren(int state, Queue<Integer> queue) {
        Map<Character, Integer> ext = extendedRows.get(state);
        if (ext != null) {
            for (int child : ext.values()) {
                if (child != 0) {
                    queue.add(child);
                }
            }
        }
    }

    /** Flattens build-phase Lists into arrays and releases build-phase memory. */
    @SuppressWarnings("unchecked")
    private void flattenToArrays() {
        gotoTable = gotoRows.toArray(new int[0][]);
        extendedGoto = extendedRows.toArray(new Map[0]);

        output = new Object[stateCount][];
        for (int i = 0; i < stateCount; i++) {
            List<V> list = buildOutput.get(i);
            output[i] = list.isEmpty() ? EMPTY_OUTPUT : list.toArray();
        }

        gotoRows = null;
        extendedRows = null;
        buildOutput = null;
        built = true;
    }

    /**
     * Searches the text and invokes the consumer for each matching value.
     *
     * @param text     the text to search
     * @param consumer called for each matching value
     * @throws IllegalStateException if {@link #build()} has not been called
     */
    @SuppressWarnings("unchecked")
    public void search(String text, Consumer<V> consumer) {
        if (!built) {
            throw new IllegalStateException("Must call build() before search()");
        }
        for (V v : emptyPatternValues) {
            consumer.accept(v);
        }
        int state = 0;
        for (int i = 0; i < text.length(); i++) {
            state = nextState(state, text.charAt(i));
            Object[] matches = output[state];
            for (int j = 0; j < matches.length; j++) {
                consumer.accept((V) matches[j]);
            }
        }
    }

    /**
     * Searches the text for all inserted patterns and returns their associated values.
     *
     * @param text the text to search
     * @return list of values for all patterns found in the text
     * @throws IllegalStateException if {@link #build()} has not been called
     */
    @SuppressWarnings("unchecked")
    public List<V> search(String text) {
        if (!built) {
            throw new IllegalStateException("Must call build() before search()");
        }
        List<V> result = new ArrayList<>();
        for (V v : emptyPatternValues) {
            result.add(v);
        }
        int state = 0;
        for (int i = 0; i < text.length(); i++) {
            state = nextState(state, text.charAt(i));
            Object[] matches = output[state];
            for (int j = 0; j < matches.length; j++) {
                result.add((V) matches[j]);
            }
        }
        return result;
    }

    private int allocateState() {
        int id = stateCount++;
        int[] row = new int[ASCII_SIZE];
        Arrays.fill(row, NO_STATE);
        gotoRows.add(row);
        extendedRows.add(null);
        buildOutput.add(new ArrayList<>());
        return id;
    }

    private int getGoto(int state, char c) {
        if (c < ASCII_SIZE) {
            return gotoRows.get(state)[c];
        }
        Map<Character, Integer> ext = extendedRows.get(state);
        if (ext == null) {
            return NO_STATE;
        }
        return ext.getOrDefault(c, NO_STATE);
    }

    private void setGoto(int state, char c, int target) {
        if (c < ASCII_SIZE) {
            gotoRows.get(state)[c] = target;
        } else {
            Map<Character, Integer> ext = extendedRows.get(state);
            if (ext == null) {
                ext = new HashMap<>(4);
                extendedRows.set(state, ext);
            }
            ext.put(c, target);
        }
    }

    /**
     * Walks the failure chain from {@code parent} to find the deepest state
     * that has a goto transition for {@code c}, then returns that target.
     */
    private int followFailure(int[] failure, int parent, char c) {
        int state = failure[parent];
        while (state != 0 && getGoto(state, c) == NO_STATE) {
            state = failure[state];
        }
        int target = getGoto(state, c);
        return target != NO_STATE ? target : 0;
    }

    private void mergeOutput(int state, int failState) {
        List<V> failOut = buildOutput.get(failState);
        if (failOut != null && !failOut.isEmpty()) {
            buildOutput.get(state).addAll(failOut);
        }
    }

    private int nextState(int state, char c) {
        if (c < ASCII_SIZE) {
            return gotoTable[state][c];
        }
        Map<Character, Integer> ext = extendedGoto[state];
        if (ext != null) {
            Integer next = ext.get(c);
            if (next != null) {
                return next;
            }
        }
        return 0; // unknown non-ASCII char → root
    }
}
