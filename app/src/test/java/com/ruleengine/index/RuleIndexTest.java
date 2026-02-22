package com.ruleengine.index;

import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Operator;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuleIndexTest {

    private Rule rule(String name, Condition... conditions) {
        return new Rule(name, 1, List.of(conditions), name);
    }

    private Condition cond(UrlPart part, Operator op, String value) {
        return new Condition(part, op, value, false);
    }

    private Condition negCond(UrlPart part, Operator op, String value) {
        return new Condition(part, op, value, true);
    }

    @ParameterizedTest
    @EnumSource(ContainsStrategy.class)
    void equalsMatch(ContainsStrategy strategy) {
        Rule r = rule("eq", cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleIndex index = new RuleIndex(List.of(r), strategy);

        Set<RuleIndex.ConditionRef> refs = index.queryCandidates(new ParsedUrl("example.com", "/", "", ""));
        assertTrue(refs.stream().anyMatch(cr -> cr.rule().name().equals("eq")));
    }

    @ParameterizedTest
    @EnumSource(ContainsStrategy.class)
    void equalsNoMatch(ContainsStrategy strategy) {
        Rule r = rule("eq", cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleIndex index = new RuleIndex(List.of(r), strategy);

        Set<RuleIndex.ConditionRef> refs = index.queryCandidates(new ParsedUrl("other.com", "/", "", ""));
        assertTrue(refs.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ContainsStrategy.class)
    void startsWithMatch(ContainsStrategy strategy) {
        Rule r = rule("sw", cond(UrlPart.PATH, Operator.STARTS_WITH, "/api"));
        RuleIndex index = new RuleIndex(List.of(r), strategy);

        Set<RuleIndex.ConditionRef> refs = index.queryCandidates(new ParsedUrl("x.com", "/api/users", "users", ""));
        assertTrue(refs.stream().anyMatch(cr -> cr.rule().name().equals("sw")));
    }

    @ParameterizedTest
    @EnumSource(ContainsStrategy.class)
    void endsWithMatch(ContainsStrategy strategy) {
        Rule r = rule("ew", cond(UrlPart.HOST, Operator.ENDS_WITH, ".ca"));
        RuleIndex index = new RuleIndex(List.of(r), strategy);

        Set<RuleIndex.ConditionRef> refs = index.queryCandidates(new ParsedUrl("shop.example.ca", "/", "", ""));
        assertTrue(refs.stream().anyMatch(cr -> cr.rule().name().equals("ew")));
    }

    @ParameterizedTest
    @EnumSource(ContainsStrategy.class)
    void containsMatch(ContainsStrategy strategy) {
        Rule r = rule("ct", cond(UrlPart.PATH, Operator.CONTAINS, "sport"));
        RuleIndex index = new RuleIndex(List.of(r), strategy);

        Set<RuleIndex.ConditionRef> refs = index.queryCandidates(new ParsedUrl("x.com", "/category/sport/items", "items", ""));
        assertTrue(refs.stream().anyMatch(cr -> cr.rule().name().equals("ct")));
    }

    @ParameterizedTest
    @EnumSource(ContainsStrategy.class)
    void negatedConditionsNotIndexed(ContainsStrategy strategy) {
        Rule r = rule("neg", negCond(UrlPart.PATH, Operator.STARTS_WITH, "/admin"));
        RuleIndex index = new RuleIndex(List.of(r), strategy);

        Set<RuleIndex.ConditionRef> refs = index.queryCandidates(new ParsedUrl("x.com", "/admin/panel", "panel", ""));
        assertTrue(refs.isEmpty());
    }

    @Test
    void multipleRulesMultipleOperators() {
        Rule r1 = rule("r1", cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        Rule r2 = rule("r2", cond(UrlPart.PATH, Operator.CONTAINS, "sport"));
        Rule r3 = rule("r3", cond(UrlPart.HOST, Operator.ENDS_WITH, ".com"));
        RuleIndex index = new RuleIndex(List.of(r1, r2, r3));

        Set<RuleIndex.ConditionRef> refs = index.queryCandidates(
                new ParsedUrl("example.com", "/sport", "sport", ""));

        Set<String> ruleNames = new java.util.HashSet<>();
        for (RuleIndex.ConditionRef cr : refs) {
            ruleNames.add(cr.rule().name());
        }
        assertTrue(ruleNames.containsAll(Set.of("r1", "r2", "r3")));
    }

    @Test
    void queryOnQueryParam() {
        Rule r = rule("qp", cond(UrlPart.QUERY, Operator.CONTAINS, "lang=en"));
        RuleIndex index = new RuleIndex(List.of(r));

        Set<RuleIndex.ConditionRef> refs = index.queryCandidates(
                new ParsedUrl("x.com", "/", "", "q=hello&lang=en"));
        assertTrue(refs.stream().anyMatch(cr -> cr.rule().name().equals("qp")));
    }
}
