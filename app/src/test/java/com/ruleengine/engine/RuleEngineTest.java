package com.ruleengine.engine;

import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Operator;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlPart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    private Rule rule(String name, int priority, String result, Condition... conditions) {
        return new Rule(name, priority, List.of(conditions), result);
    }

    private Condition cond(UrlPart part, Operator op, String value) {
        return new Condition(part, op, value, false);
    }

    private Condition negCond(UrlPart part, Operator op, String value) {
        return new Condition(part, op, value, true);
    }

    // --- Isolated operator tests ---

    @Test
    void equalsOperator() {
        Rule r = rule("eq", 1, "matched", cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("matched"), engine.evaluate(new ParsedUrl("example.com", "/", "")));
        assertEquals(Optional.empty(), engine.evaluate(new ParsedUrl("other.com", "/", "")));
    }

    @Test
    void containsOperator() {
        Rule r = rule("ct", 1, "matched", cond(UrlPart.PATH, Operator.CONTAINS, "sport"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("matched"), engine.evaluate(new ParsedUrl("x.com", "/category/sport/items", "")));
        assertEquals(Optional.empty(), engine.evaluate(new ParsedUrl("x.com", "/category/news", "")));
    }

    @Test
    void startsWithOperator() {
        Rule r = rule("sw", 1, "matched", cond(UrlPart.PATH, Operator.STARTS_WITH, "/api"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("matched"), engine.evaluate(new ParsedUrl("x.com", "/api/users", "")));
        assertEquals(Optional.empty(), engine.evaluate(new ParsedUrl("x.com", "/web/api", "")));
    }

    @Test
    void endsWithOperator() {
        Rule r = rule("ew", 1, "matched", cond(UrlPart.HOST, Operator.ENDS_WITH, ".ca"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("matched"), engine.evaluate(new ParsedUrl("shop.example.ca", "/", "")));
        assertEquals(Optional.empty(), engine.evaluate(new ParsedUrl("shop.example.com", "/", "")));
    }

    // --- Negation tests ---

    @Test
    void negatedEquals() {
        Rule r = rule("neq", 1, "not-example",
                negCond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("not-example"), engine.evaluate(new ParsedUrl("other.com", "/", "")));
        assertEquals(Optional.empty(), engine.evaluate(new ParsedUrl("example.com", "/", "")));
    }

    @Test
    void negatedContains() {
        Rule r = rule("nct", 1, "no-sport",
                negCond(UrlPart.PATH, Operator.CONTAINS, "sport"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("no-sport"), engine.evaluate(new ParsedUrl("x.com", "/news", "")));
        assertEquals(Optional.empty(), engine.evaluate(new ParsedUrl("x.com", "/sport/live", "")));
    }

    @Test
    void negatedStartsWith() {
        Rule r = rule("nsw", 1, "not-admin",
                negCond(UrlPart.PATH, Operator.STARTS_WITH, "/admin"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("not-admin"), engine.evaluate(new ParsedUrl("x.com", "/user", "")));
        assertEquals(Optional.empty(), engine.evaluate(new ParsedUrl("x.com", "/admin/panel", "")));
    }

    @Test
    void negatedEndsWith() {
        Rule r = rule("new", 1, "not-ca",
                negCond(UrlPart.HOST, Operator.ENDS_WITH, ".ca"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("not-ca"), engine.evaluate(new ParsedUrl("example.com", "/", "")));
        assertEquals(Optional.empty(), engine.evaluate(new ParsedUrl("example.ca", "/", "")));
    }

    // --- Compound rule tests ---

    @Test
    void canadaSportCompoundRule() {
        Rule r = rule("cs", 1, "Canada Sport",
                cond(UrlPart.HOST, Operator.ENDS_WITH, ".ca"),
                cond(UrlPart.PATH, Operator.CONTAINS, "sport"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("Canada Sport"),
                engine.evaluate(new ParsedUrl("shop.example.ca", "/category/sport/items", "")));
        // Host matches but path doesn't
        assertEquals(Optional.empty(),
                engine.evaluate(new ParsedUrl("shop.example.ca", "/category/news", "")));
        // Path matches but host doesn't
        assertEquals(Optional.empty(),
                engine.evaluate(new ParsedUrl("shop.example.com", "/category/sport", "")));
    }

    @Test
    void compoundWithNegation() {
        Rule r = rule("mix", 1, "result",
                cond(UrlPart.HOST, Operator.EQUALS, "example.com"),
                negCond(UrlPart.PATH, Operator.STARTS_WITH, "/admin"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("result"),
                engine.evaluate(new ParsedUrl("example.com", "/user", "")));
        assertEquals(Optional.empty(),
                engine.evaluate(new ParsedUrl("example.com", "/admin/panel", "")));
        assertEquals(Optional.empty(),
                engine.evaluate(new ParsedUrl("other.com", "/user", "")));
    }

    // --- Priority tests ---

    @Test
    void higherPriorityWins() {
        Rule low = rule("low", 1, "low-result",
                cond(UrlPart.HOST, Operator.ENDS_WITH, ".com"));
        Rule high = rule("high", 10, "high-result",
                cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleEngine engine = new RuleEngine(List.of(low, high));

        assertEquals(Optional.of("high-result"),
                engine.evaluate(new ParsedUrl("example.com", "/", "")));
    }

    @Test
    void samePriorityUsesDefinitionOrder() {
        Rule first = rule("first", 5, "first-result",
                cond(UrlPart.HOST, Operator.ENDS_WITH, ".com"));
        Rule second = rule("second", 5, "second-result",
                cond(UrlPart.HOST, Operator.ENDS_WITH, ".com"));
        RuleEngine engine = new RuleEngine(List.of(first, second));

        // Both match, same priority — first defined wins (stable sort)
        Optional<String> result = engine.evaluate(new ParsedUrl("example.com", "/", ""));
        assertEquals(Optional.of("first-result"), result);
    }

    @Test
    void lowerPriorityMatchesWhenHigherDoesNot() {
        Rule high = rule("high", 10, "high-result",
                cond(UrlPart.HOST, Operator.EQUALS, "special.com"));
        Rule low = rule("low", 1, "low-result",
                cond(UrlPart.HOST, Operator.ENDS_WITH, ".com"));
        RuleEngine engine = new RuleEngine(List.of(high, low));

        assertEquals(Optional.of("low-result"),
                engine.evaluate(new ParsedUrl("example.com", "/", "")));
    }

    // --- Edge cases ---

    @Test
    void noRulesReturnsEmpty() {
        RuleEngine engine = new RuleEngine(List.of());
        assertEquals(Optional.empty(), engine.evaluate(new ParsedUrl("x.com", "/", "")));
    }

    @Test
    void noMatchReturnsEmpty() {
        Rule r = rule("r", 1, "result", cond(UrlPart.HOST, Operator.EQUALS, "specific.com"));
        RuleEngine engine = new RuleEngine(List.of(r));
        assertEquals(Optional.empty(), engine.evaluate(new ParsedUrl("other.com", "/", "")));
    }

    @Test
    void queryPartMatching() {
        Rule r = rule("qr", 1, "query-match",
                cond(UrlPart.QUERY, Operator.CONTAINS, "lang=en"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("query-match"),
                engine.evaluate(new ParsedUrl("x.com", "/", "q=test&lang=en")));
        assertEquals(Optional.empty(),
                engine.evaluate(new ParsedUrl("x.com", "/", "q=test&lang=fr")));
    }

    @Test
    void emptyPathAndQuery() {
        Rule r = rule("empty", 1, "matched",
                cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals(Optional.of("matched"),
                engine.evaluate(new ParsedUrl("example.com", "", "")));
    }
}
