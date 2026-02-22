package com.ruleengine.rule;

import java.util.List;

/**
 * A named rule consisting of one or more conditions and a result string.
 *
 * <p>A rule matches a URL when <em>all</em> of its conditions are satisfied.
 * Rules are compared by {@link #priority} in descending order (highest first);
 * ties are broken by definition order (stable sort).
 *
 * @param name       human-readable rule name
 * @param priority   numeric priority (higher = evaluated first)
 * @param conditions the list of conditions that must all hold for this rule to match
 * @param result     the result string returned when this rule matches
 */
public record Rule(String name, int priority, List<Condition> conditions, String result)
        implements Comparable<Rule> {

    @Override
    public int compareTo(Rule other) {
        return Integer.compare(other.priority, this.priority);
    }
}
