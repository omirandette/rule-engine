package com.ruleengine.rule;

import com.ruleengine.url.UrlPart;

/**
 * A single condition within a rule, targeting one URL part with one operator.
 *
 * <p>When {@code negated} is {@code true}, the condition matches when the
 * operator comparison <em>does not</em> hold.
 *
 * @param part     the URL part to test (e.g. HOST, PATH, FILE, QUERY)
 * @param operator the comparison operator
 * @param value    the value to compare against
 * @param negated  if {@code true}, the match result is inverted
 */
public record Condition(UrlPart part, Operator operator, String value, boolean negated) {
}
