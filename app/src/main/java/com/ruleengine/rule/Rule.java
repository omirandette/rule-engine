package com.ruleengine.rule;

import java.util.List;
import java.util.Objects;

/**
 * A named rule consisting of one or more conditions and a result string.
 *
 * <p>A rule matches a URL when <em>all</em> of its conditions are satisfied.
 * Rules are compared by {@link #priority} in descending order (highest first);
 * ties are broken by definition order (stable sort).
 *
 * <p>The hash code is cached on construction since rules are immutable
 * and frequently used as {@link java.util.HashMap} keys in the hot path.
 */
public final class Rule implements Comparable<Rule> {

    private final String name;
    private final int priority;
    private final List<Condition> conditions;
    private final String result;
    private final int hash;

    /**
     * Creates a new rule.
     *
     * @param name       human-readable rule name
     * @param priority   numeric priority (higher = evaluated first)
     * @param conditions the list of conditions that must all hold for this rule to match
     * @param result     the result string returned when this rule matches
     */
    public Rule(String name, int priority, List<Condition> conditions, String result) {
        this.name = name;
        this.priority = priority;
        this.conditions = conditions;
        this.result = result;
        this.hash = Objects.hash(name, priority, conditions, result);
    }

    /** Returns the human-readable rule name. */
    public String name() { return name; }

    /** Returns the numeric priority. */
    public int priority() { return priority; }

    /** Returns the list of conditions. */
    public List<Condition> conditions() { return conditions; }

    /** Returns the result string. */
    public String result() { return result; }

    @Override
    public int compareTo(Rule other) {
        return Integer.compare(other.priority, this.priority);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rule r)) return false;
        return priority == r.priority
                && Objects.equals(name, r.name)
                && Objects.equals(conditions, r.conditions)
                && Objects.equals(result, r.result);
    }

    @Override
    public String toString() {
        return "Rule[name=" + name + ", priority=" + priority
                + ", conditions=" + conditions + ", result=" + result + "]";
    }
}
