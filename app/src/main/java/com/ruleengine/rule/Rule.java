package com.ruleengine.rule;

import java.util.List;

public record Rule(String name, int priority, List<Condition> conditions, String result)
        implements Comparable<Rule> {

    @Override
    public int compareTo(Rule other) {
        return Integer.compare(other.priority, this.priority); // higher priority first
    }
}
