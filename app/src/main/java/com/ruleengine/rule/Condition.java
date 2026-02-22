package com.ruleengine.rule;

import com.ruleengine.url.UrlPart;

public record Condition(UrlPart part, Operator operator, String value, boolean negated) {
}
