package com.ruleengine.index;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.IntConsumer;

/**
 * An int-specialized Aho-Corasick automaton for multi-pattern substring matching.
 *
 * <p>Functionally equivalent to {@link AhoCorasick AhoCorasick&lt;Integer&gt;} but
 * stores output as primitive {@code int[][]} instead of {@code Object[][]}, eliminating
 * boxing, unchecked casts, and pointer chasing in the search hot path.
 *
 * <p>Uses a DFA with array-indexed transitions for ASCII characters and a
 * {@link HashMap} fallback for non-ASCII. After {@link #build()}, the goto function
 * is fully completed so search requires no failure-link chasing.
 */
public final class IntAhoCorasick {

    private static final int ASCII_SIZE = 128;
    private static final int NO_STATE = -1;
    private static final int INITIAL_CAPACITY = 64;
    private static final int[] EMPTY_OUTPUT = new int[0];

    private int[] emptyPatternValues = EMPTY_OUTPUT;
    private int emptyPatternCount;

    // Build-phase structures (nulled after build)
    private List<int[]> gotoRows = new ArrayList<>(INITIAL_CAPACITY);
    private List<Map<Character, Integer>> extendedRows = new ArrayList<>(INITIAL_CAPACITY);
    private List<int[]> buildOutput = new ArrayList<>(INITIAL_CAPACITY);
    private List<Integer> buildOutputCounts = new ArrayList<>(INITIAL_CAPACITY);
    private int stateCount = 0;

    // Search-phase arrays (populated by build)
    private int[][] gotoTable;
    @SuppressWarnings("unchecked")
    private Map<Character, Integer>[] extendedGoto;
    private int[][] output;
    private boolean built = false;

    /** Creates a new empty automaton with the root state allocated. */
    public IntAhoCorasick() {
        allocateState();
    }

    /**
     * Returns {@code true} if no patterns have been inserted.
     *
     * @return {@code true} if the automaton has no patterns
     */
    public boolean isEmpty() {
        return emptyPatternCount == 0 && stateCount <= 1;
    }

    /**
     * Inserts a pattern with an associated int value into the automaton.
     *
     * @param pattern the pattern string
     * @param value   the int value to return when this pattern is found
     * @throws IllegalStateException if called after {@link #build()}
     */
    public void insert(String pattern, int value) {
        if (built) {
            throw new IllegalStateException("Cannot insert after build()");
        }
        if (pattern.isEmpty()) {
            if (emptyPatternCount == emptyPatternValues.length) {
                int newLen = emptyPatternValues.length == 0 ? 2 : emptyPatternValues.length * 2;
                int[] grown = new int[newLen];
                System.arraycopy(emptyPatternValues, 0, grown, 0, emptyPatternCount);
                emptyPatternValues = grown;
            }
            emptyPatternValues[emptyPatternCount++] = value;
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
        addOutput(state, value);
    }

    /**
     * Constructs the automaton by computing failure links and completing the DFA.
     */
    @SuppressWarnings("unchecked")
    public void build() {
        int[] failure = new int[stateCount];
        Queue<Integer> queue = new ArrayDeque<>();

        initDepthOneStates(failure, queue);
        computeFailureLinks(failure, queue);
        completeDfa(failure, queue);
        flattenToArrays();
    }

    /**
     * Searches the text and invokes the consumer for each matching int value.
     *
     * @param text     the text to search
     * @param consumer called for each matching int value
     * @throws IllegalStateException if {@link #build()} has not been called
     */
    public void search(String text, IntConsumer consumer) {
        if (!built) {
            throw new IllegalStateException("Must call build() before search()");
        }
        for (int i = 0; i < emptyPatternCount; i++) {
            consumer.accept(emptyPatternValues[i]);
        }
        int state = 0;
        for (int i = 0; i < text.length(); i++) {
            state = nextState(state, text.charAt(i));
            int[] matches = output[state];
            for (int j = 0; j < matches.length; j++) {
                consumer.accept(matches[j]);
            }
        }
    }

    private int allocateState() {
        int id = stateCount++;
        int[] row = new int[ASCII_SIZE];
        Arrays.fill(row, NO_STATE);
        gotoRows.add(row);
        extendedRows.add(null);
        buildOutput.add(EMPTY_OUTPUT);
        buildOutputCounts.add(0);
        return id;
    }

    private void addOutput(int state, int value) {
        int[] arr = buildOutput.get(state);
        int count = buildOutputCounts.get(state);
        if (count == arr.length) {
            int newLen = arr.length == 0 ? 2 : arr.length * 2;
            int[] grown = new int[newLen];
            System.arraycopy(arr, 0, grown, 0, count);
            arr = grown;
            buildOutput.set(state, arr);
        }
        arr[count] = value;
        buildOutputCounts.set(state, count + 1);
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

    private void initDepthOneStates(int[] failure, Queue<Integer> queue) {
        int[] rootRow = gotoRows.get(0);
        for (int c = 0; c < ASCII_SIZE; c++) {
            int child = rootRow[c];
            if (child == NO_STATE) {
                rootRow[c] = 0;
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

    private void completeDfa(int[] failure, Queue<Integer> queue) {
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
                    row[c] = failRow[c];
                } else {
                    queue.add(row[c]);
                }
            }

            // Enqueue original children BEFORE inheriting from failure state,
            // so inherited transitions are not re-enqueued (which causes infinite loops).
            enqueueExtendedChildren(current, queue);
            inheritExtendedTransitions(current, failure[current]);
        }
    }

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

    @SuppressWarnings("unchecked")
    private void flattenToArrays() {
        gotoTable = gotoRows.toArray(new int[0][]);
        extendedGoto = extendedRows.toArray(new Map[0]);

        output = new int[stateCount][];
        for (int i = 0; i < stateCount; i++) {
            int count = buildOutputCounts.get(i);
            if (count == 0) {
                output[i] = EMPTY_OUTPUT;
            } else {
                int[] src = buildOutput.get(i);
                if (src.length == count) {
                    output[i] = src;
                } else {
                    output[i] = Arrays.copyOf(src, count);
                }
            }
        }

        gotoRows = null;
        extendedRows = null;
        buildOutput = null;
        buildOutputCounts = null;
        built = true;
    }

    private int followFailure(int[] failure, int parent, char c) {
        int state = failure[parent];
        while (state != 0 && getGoto(state, c) == NO_STATE) {
            state = failure[state];
        }
        int target = getGoto(state, c);
        return target != NO_STATE ? target : 0;
    }

    private void mergeOutput(int state, int failState) {
        int failCount = buildOutputCounts.get(failState);
        if (failCount == 0) {
            return;
        }
        int[] failArr = buildOutput.get(failState);
        int[] stateArr = buildOutput.get(state);
        int stateCount = buildOutputCounts.get(state);

        int newCount = stateCount + failCount;
        int[] merged;
        if (stateArr.length >= newCount) {
            merged = stateArr;
        } else {
            merged = new int[newCount];
            System.arraycopy(stateArr, 0, merged, 0, stateCount);
            buildOutput.set(state, merged);
        }
        System.arraycopy(failArr, 0, merged, stateCount, failCount);
        buildOutputCounts.set(state, newCount);
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
        return 0;
    }
}
