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

    // Build-phase structures (nulled after build)
    private List<int[]> gotoRows = new ArrayList<>(INITIAL_CAPACITY);
    @SuppressWarnings("unchecked")
    private List<Map<Character, Integer>> extendedRows = new ArrayList<>(INITIAL_CAPACITY);
    private List<List<V>> buildOutput = new ArrayList<>(INITIAL_CAPACITY);
    private int stateCount = 0;

    // Search-phase structures (populated by build)
    private int[][] gotoTable;
    @SuppressWarnings("unchecked")
    private Map<Character, Integer>[] extendedGoto;
    @SuppressWarnings("unchecked")
    private List<V>[] output;
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
     * is ready for searching.
     */
    @SuppressWarnings("unchecked")
    public void build() {
        int[] failure = new int[stateCount];
        Queue<Integer> queue = new ArrayDeque<>();

        // Initialize depth-1 states: failure → root, complete missing root transitions
        int[] rootRow = gotoRows.get(0);
        for (int c = 0; c < ASCII_SIZE; c++) {
            int s = rootRow[c];
            if (s == NO_STATE) {
                rootRow[c] = 0; // missing root transitions loop to root
            } else {
                failure[s] = 0;
                queue.add(s);
            }
        }
        Map<Character, Integer> rootExt = extendedRows.get(0);
        if (rootExt != null) {
            for (Map.Entry<Character, Integer> e : rootExt.entrySet()) {
                failure[e.getValue()] = 0;
                queue.add(e.getValue());
            }
        }

        // BFS to compute failure links and merge outputs
        while (!queue.isEmpty()) {
            int r = queue.poll();
            int[] row = gotoRows.get(r);

            for (int c = 0; c < ASCII_SIZE; c++) {
                int s = row[c];
                if (s != NO_STATE) {
                    failure[s] = followFailure(failure, r, (char) c);
                    mergeOutput(s, failure[s]);
                    queue.add(s);
                }
            }

            Map<Character, Integer> ext = extendedRows.get(r);
            if (ext != null) {
                for (Map.Entry<Character, Integer> e : ext.entrySet()) {
                    int s = e.getValue();
                    failure[s] = followFailure(failure, r, e.getKey());
                    mergeOutput(s, failure[s]);
                    queue.add(s);
                }
            }
        }

        // Complete the DFA: fill missing transitions via failure links
        // (BFS order ensures failure[s] is already completed)
        // Re-run BFS to fill non-root states
        queue.clear();
        for (int c = 0; c < ASCII_SIZE; c++) {
            int s = rootRow[c];
            if (s != 0) {
                queue.add(s);
            }
        }
        if (rootExt != null) {
            queue.addAll(rootExt.values());
        }

        while (!queue.isEmpty()) {
            int r = queue.poll();
            int[] row = gotoRows.get(r);
            int[] failRow = gotoRows.get(failure[r]);

            for (int c = 0; c < ASCII_SIZE; c++) {
                if (row[c] == NO_STATE) {
                    row[c] = failRow[c]; // failRow is already complete (BFS order)
                } else {
                    queue.add(row[c]);
                }
            }

            // Non-ASCII: inherit from failure state for missing chars
            Map<Character, Integer> ext = extendedRows.get(r);
            Map<Character, Integer> failExt = extendedRows.get(failure[r]);
            if (failExt != null) {
                for (Map.Entry<Character, Integer> e : failExt.entrySet()) {
                    if (ext == null) {
                        ext = new HashMap<>(4);
                        extendedRows.set(r, ext);
                    }
                    ext.putIfAbsent(e.getKey(), e.getValue());
                }
            }
            if (ext != null) {
                for (int s : ext.values()) {
                    if (s != 0) {
                        queue.add(s);
                    }
                }
            }
        }

        // Flatten to arrays for search
        gotoTable = gotoRows.toArray(new int[0][]);
        extendedGoto = extendedRows.toArray(new Map[0]);
        output = buildOutput.toArray(new List[0]);

        // Release build-phase structures
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
    public void search(String text, Consumer<V> consumer) {
        if (!built) {
            throw new IllegalStateException("Must call build() before search()");
        }
        for (V v : emptyPatternValues) {
            consumer.accept(v);
        }
        int state = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            state = nextState(state, c);
            List<V> out = output[state];
            if (out != null && !out.isEmpty()) {
                for (V v : out) {
                    consumer.accept(v);
                }
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
    public List<V> search(String text) {
        List<V> result = new ArrayList<>();
        search(text, result::add);
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

    private int followFailure(int[] failure, int parent, char c) {
        int f = failure[parent];
        while (f != 0 && getGoto(f, c) == NO_STATE) {
            f = failure[f];
        }
        int g = getGoto(f, c);
        return (g != NO_STATE && g != 0) ? g : (getGoto(0, c) != NO_STATE ? getGoto(0, c) : 0);
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
