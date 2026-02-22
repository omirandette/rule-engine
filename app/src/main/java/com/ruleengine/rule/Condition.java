package com.ruleengine.rule;

import com.ruleengine.url.UrlPart;

import java.util.Objects;

/**
 * A single condition within a rule, targeting one URL part with one operator.
 *
 * <p>When {@code negated} is {@code true}, the condition matches when the
 * operator comparison <em>does not</em> hold.
 *
 * <p>The hash code is cached on construction since conditions are immutable
 * and frequently used as {@link java.util.HashSet} elements in the hot path.
 */
public final class Condition {

    private final UrlPart part;
    private final Operator operator;
    private final String value;
    private final boolean negated;
    private final int hash;

    /**
     * Creates a new condition.
     *
     * @param part     the URL part to test (e.g. HOST, PATH, FILE, QUERY)
     * @param operator the comparison operator
     * @param value    the value to compare against
     * @param negated  if {@code true}, the match result is inverted
     */
    public Condition(UrlPart part, Operator operator, String value, boolean negated) {
        this.part = part;
        this.operator = operator;
        this.value = value;
        this.negated = negated;
        this.hash = Objects.hash(part, operator, value, negated);
    }

    /** Returns the URL part to test. */
    public UrlPart part() { return part; }

    /** Returns the comparison operator. */
    public Operator operator() { return operator; }

    /** Returns the value to compare against. */
    public String value() { return value; }

    /** Returns {@code true} if the match result is inverted. */
    public boolean negated() { return negated; }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Condition c)) return false;
        return negated == c.negated
                && part == c.part
                && operator == c.operator
                && Objects.equals(value, c.value);
    }

    @Override
    public String toString() {
        return "Condition[part=" + part + ", operator=" + operator
                + ", value=" + value + ", negated=" + negated + "]";
    }
}
