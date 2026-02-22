package com.ruleengine.index;

/**
 * Selects which data structure backs {@code CONTAINS} matching in the {@link RuleIndex}.
 *
 * <ul>
 *   <li>{@link #TRIE} (default) — reuses the prefix trie with position iteration; good
 *       for short patterns and moderate pattern counts.</li>
 *   <li>{@link #AHO_CORASICK} — Aho-Corasick automaton; better for workloads with
 *       many long patterns or pathological overlap.</li>
 * </ul>
 */
public enum ContainsStrategy {
    /** Use the prefix trie with substring scanning. */
    TRIE,
    /** Use the Aho-Corasick automaton. */
    AHO_CORASICK
}
