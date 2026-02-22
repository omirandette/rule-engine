package com.ruleengine.rule;

/**
 * String-matching operators supported by rule conditions.
 *
 * <p>Each operator defines how a condition's value is compared against
 * a URL part. Every operator can be negated at the {@link Condition} level.
 */
public enum Operator {
    /** Exact string equality. */
    EQUALS,
    /** Substring existence anywhere in the target. */
    CONTAINS,
    /** The target starts with the condition value. */
    STARTS_WITH,
    /** The target ends with the condition value. */
    ENDS_WITH
}
