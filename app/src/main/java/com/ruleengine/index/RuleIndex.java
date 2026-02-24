package com.ruleengine.index;

import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlPart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Indexes non-negated rule conditions by (UrlPart, Operator) for fast lookup.
 *
 * <p>Each operator type is backed by an appropriate data structure:
 * <ul>
 *   <li>{@code EQUALS} — {@link HashMap} for O(1) lookup</li>
 *   <li>{@code STARTS_WITH} — {@link IntTrie} for prefix matching</li>
 *   <li>{@code ENDS_WITH} — {@link IntTrie} on reversed strings</li>
 *   <li>{@code CONTAINS} — {@link IntAhoCorasick} automaton for multi-pattern substring matching</li>
 * </ul>
 *
 * <p>Per-part indexes are stored in flat arrays indexed by {@link UrlPart#ordinal()}
 * to avoid {@code EnumMap} lookup overhead in the hot path. Rule IDs are stored as
 * primitive {@code int} values throughout to eliminate wrapper object allocations.
 *
 * <p>Negated conditions are excluded from the index and must be evaluated directly at match time.
 */
public final class RuleIndex {

    private static final UrlPart[] PARTS = UrlPart.values();
    private static final int PART_COUNT = PARTS.length;

    private final Map<String, int[]>[] equalsIndexes;
    private final IntTrie[] startsWithIndexes;
    private final IntTrie[] endsWithIndexes;
    private final IntAhoCorasick[] containsAcIndexes;

    private final Map<Rule, Integer> ruleIds;
    private final int ruleCount;
    private final int[] nonNegatedCounts;
    private final boolean[] hasEquals;
    private final boolean[] hasStartsWith;
    private final boolean[] hasEndsWith;
    private final boolean[] hasContains;
    private final ThreadLocal<QueryContext> threadContext;

    /** Per-thread mutable state used during {@link #queryCandidates(ParsedUrl)}. */
    private static final class QueryContext {
        final CandidateResult candidates;
        final IntConsumer incrementConsumer;
        char[] reverseBuf;

        QueryContext(int ruleCount, int[] nonNegatedCounts) {
            this.candidates = new CandidateResult(ruleCount, nonNegatedCounts);
            this.incrementConsumer = ruleId -> candidates.increment(ruleId);
            this.reverseBuf = new char[256];
        }
    }

    /**
     * Builds the index from a list of rules.
     *
     * @param rules the rules to index
     */
    @SuppressWarnings("unchecked")
    public RuleIndex(List<Rule> rules) {
        this.ruleIds = new HashMap<>(rules.size() * 2);
        for (int i = 0; i < rules.size(); i++) {
            ruleIds.put(rules.get(i), i);
        }
        this.ruleCount = rules.size();
        this.nonNegatedCounts = new int[ruleCount];

        // Build-phase: accumulate equals values in Lists, then freeze to int[]
        Map<String, List<Integer>>[] equalsBuild = new HashMap[PART_COUNT];
        IntTrie[] swIndexes = new IntTrie[PART_COUNT];
        IntTrie[] ewIndexes = new IntTrie[PART_COUNT];
        IntAhoCorasick[] ctIndexes = new IntAhoCorasick[PART_COUNT];

        for (int p = 0; p < PART_COUNT; p++) {
            equalsBuild[p] = new HashMap<>();
            swIndexes[p] = new IntTrie();
            ewIndexes[p] = new IntTrie();
            ctIndexes[p] = new IntAhoCorasick();
        }

        for (Rule rule : rules) {
            int id = ruleIds.get(rule);
            for (Condition cond : rule.conditions()) {
                if (!cond.negated()) {
                    indexCondition(cond, id, equalsBuild, swIndexes, ewIndexes, ctIndexes);
                }
            }
        }

        for (int p = 0; p < PART_COUNT; p++) {
            ctIndexes[p].build();
        }

        // Freeze equals indexes: List<Integer> -> int[]
        this.equalsIndexes = new HashMap[PART_COUNT];
        for (int p = 0; p < PART_COUNT; p++) {
            Map<String, List<Integer>> buildMap = equalsBuild[p];
            Map<String, int[]> frozen = new HashMap<>(buildMap.size() * 2);
            for (Map.Entry<String, List<Integer>> entry : buildMap.entrySet()) {
                List<Integer> list = entry.getValue();
                int[] ids = new int[list.size()];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = list.get(i);
                }
                frozen.put(entry.getKey(), ids);
            }
            this.equalsIndexes[p] = frozen;
        }

        this.startsWithIndexes = swIndexes;
        this.endsWithIndexes = ewIndexes;
        this.containsAcIndexes = ctIndexes;

        this.hasEquals = new boolean[PART_COUNT];
        this.hasStartsWith = new boolean[PART_COUNT];
        this.hasEndsWith = new boolean[PART_COUNT];
        this.hasContains = new boolean[PART_COUNT];
        for (int p = 0; p < PART_COUNT; p++) {
            hasEquals[p] = !equalsIndexes[p].isEmpty();
            hasStartsWith[p] = !startsWithIndexes[p].isEmpty();
            hasEndsWith[p] = !endsWithIndexes[p].isEmpty();
            hasContains[p] = !containsAcIndexes[p].isEmpty();
        }

        this.threadContext = ThreadLocal.withInitial(
                () -> new QueryContext(ruleCount, nonNegatedCounts));
    }

    /** Adds a non-negated condition to the appropriate operator-specific index. */
    private void indexCondition(Condition cond, int ruleId,
            Map<String, List<Integer>>[] equalsBuild,
            IntTrie[] swIndexes, IntTrie[] ewIndexes, IntAhoCorasick[] ctIndexes) {
        nonNegatedCounts[ruleId]++;
        int p = cond.part().ordinal();
        switch (cond.operator()) {
            case EQUALS -> equalsBuild[p]
                    .computeIfAbsent(cond.value(), _ -> new ArrayList<>())
                    .add(ruleId);
            case STARTS_WITH -> swIndexes[p]
                    .insert(cond.value(), ruleId);
            case ENDS_WITH -> ewIndexes[p]
                    .insert(new StringBuilder(cond.value()).reverse().toString(), ruleId);
            case CONTAINS -> ctIndexes[p]
                    .insert(cond.value(), ruleId);
        }
    }

    /**
     * Returns the dense integer ID assigned to the given rule.
     *
     * @param rule the rule to look up
     * @return the rule's dense ID (0..N-1)
     */
    public int ruleId(Rule rule) {
        return ruleIds.get(rule);
    }

    /**
     * Returns the total number of indexed rules.
     *
     * @return the rule count
     */
    public int ruleCount() {
        return ruleCount;
    }

    /**
     * Returns the expected count of non-negated conditions per rule.
     *
     * @return the non-negated counts array (indexed by rule ID)
     */
    public int[] nonNegatedCounts() {
        return nonNegatedCounts;
    }

    /**
     * Queries the index for all non-negated conditions that match the given URL,
     * grouped by rule in a dense array.
     *
     * @param url the parsed URL to match against
     * @return a {@link CandidateResult} with satisfied conditions per rule ID
     */
    public CandidateResult queryCandidates(ParsedUrl url) {
        QueryContext ctx = threadContext.get();
        ctx.candidates.reset();

        for (UrlPart part : PARTS) {
            int p = part.ordinal();
            String value = url.part(part);

            if (hasEquals[p]) {
                int[] eqIds = equalsIndexes[p].get(value);
                if (eqIds != null) {
                    for (int i = 0; i < eqIds.length; i++) {
                        ctx.candidates.increment(eqIds[i]);
                    }
                }
            }

            if (hasStartsWith[p]) {
                startsWithIndexes[p].findPrefixesOf(value, ctx.incrementConsumer);
            }

            if (hasEndsWith[p]) {
                int len = value.length();
                if (len > ctx.reverseBuf.length) {
                    ctx.reverseBuf = new char[len];
                }
                for (int i = 0; i < len; i++) {
                    ctx.reverseBuf[i] = value.charAt(len - 1 - i);
                }
                endsWithIndexes[p].findPrefixesOf(ctx.reverseBuf, len, ctx.incrementConsumer);
            }

            if (hasContains[p]) {
                containsAcIndexes[p].search(value, ctx.incrementConsumer);
            }
        }
        return ctx.candidates;
    }
}
