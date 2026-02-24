package com.ruleengine.engine;

import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Operator;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlPart;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    /** Shorthand to build a ParsedUrl with an auto-derived file component. */
    private ParsedUrl url(String host, String path, String query) {
        String file = "";
        if (!path.isEmpty()) {
            int lastSlash = path.lastIndexOf('/');
            file = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
        }
        return new ParsedUrl(host, path, file, query);
    }

    // --- Isolated operator tests ---

    @Test
    void equalsOperator() {
        Rule r = rule("eq", 1, "matched", cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("matched", engine.evaluate(url("example.com", "/", "")));
        assertNull(engine.evaluate(url("other.com", "/", "")));
    }

    @Test
    void containsOperator() {
        Rule r = rule("ct", 1, "matched", cond(UrlPart.PATH, Operator.CONTAINS, "sport"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("matched", engine.evaluate(url("x.com", "/category/sport/items", "")));
        assertNull(engine.evaluate(url("x.com", "/category/news", "")));
    }

    @Test
    void startsWithOperator() {
        Rule r = rule("sw", 1, "matched", cond(UrlPart.PATH, Operator.STARTS_WITH, "/api"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("matched", engine.evaluate(url("x.com", "/api/users", "")));
        assertNull(engine.evaluate(url("x.com", "/web/api", "")));
    }

    @Test
    void endsWithOperator() {
        Rule r = rule("ew", 1, "matched", cond(UrlPart.HOST, Operator.ENDS_WITH, ".ca"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("matched", engine.evaluate(url("shop.example.ca", "/", "")));
        assertNull(engine.evaluate(url("shop.example.com", "/", "")));
    }

    // --- Negation tests ---

    @Test
    void negatedEquals() {
        Rule r = rule("neq", 1, "not-example",
                negCond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("not-example", engine.evaluate(url("other.com", "/", "")));
        assertNull(engine.evaluate(url("example.com", "/", "")));
    }

    @Test
    void negatedContains() {
        Rule r = rule("nct", 1, "no-sport",
                negCond(UrlPart.PATH, Operator.CONTAINS, "sport"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("no-sport", engine.evaluate(url("x.com", "/news", "")));
        assertNull(engine.evaluate(url("x.com", "/sport/live", "")));
    }

    @Test
    void negatedStartsWith() {
        Rule r = rule("nsw", 1, "not-admin",
                negCond(UrlPart.PATH, Operator.STARTS_WITH, "/admin"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("not-admin", engine.evaluate(url("x.com", "/user", "")));
        assertNull(engine.evaluate(url("x.com", "/admin/panel", "")));
    }

    @Test
    void negatedEndsWith() {
        Rule r = rule("new", 1, "not-ca",
                negCond(UrlPart.HOST, Operator.ENDS_WITH, ".ca"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("not-ca", engine.evaluate(url("example.com", "/", "")));
        assertNull(engine.evaluate(url("example.ca", "/", "")));
    }

    // --- Compound rule tests ---

    @Test
    void canadaSportCompoundRule() {
        Rule r = rule("cs", 1, "Canada Sport",
                cond(UrlPart.HOST, Operator.ENDS_WITH, ".ca"),
                cond(UrlPart.PATH, Operator.CONTAINS, "sport"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("Canada Sport",
                engine.evaluate(url("shop.example.ca", "/category/sport/items", "")));
        assertNull(engine.evaluate(url("shop.example.ca", "/category/news", "")));
        assertNull(engine.evaluate(url("shop.example.com", "/category/sport", "")));
    }

    @Test
    void compoundWithNegation() {
        Rule r = rule("mix", 1, "result",
                cond(UrlPart.HOST, Operator.EQUALS, "example.com"),
                negCond(UrlPart.PATH, Operator.STARTS_WITH, "/admin"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("result",
                engine.evaluate(url("example.com", "/user", "")));
        assertNull(engine.evaluate(url("example.com", "/admin/panel", "")));
        assertNull(engine.evaluate(url("other.com", "/user", "")));
    }

    // --- Priority tests ---

    @Test
    void higherPriorityWins() {
        Rule low = rule("low", 1, "low-result",
                cond(UrlPart.HOST, Operator.ENDS_WITH, ".com"));
        Rule high = rule("high", 10, "high-result",
                cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleEngine engine = new RuleEngine(List.of(low, high));

        assertEquals("high-result",
                engine.evaluate(url("example.com", "/", "")));
    }

    @Test
    void samePriorityUsesDefinitionOrder() {
        Rule first = rule("first", 5, "first-result",
                cond(UrlPart.HOST, Operator.ENDS_WITH, ".com"));
        Rule second = rule("second", 5, "second-result",
                cond(UrlPart.HOST, Operator.ENDS_WITH, ".com"));
        RuleEngine engine = new RuleEngine(List.of(first, second));

        String result = engine.evaluate(url("example.com", "/", ""));
        assertEquals("first-result", result);
    }

    @Test
    void lowerPriorityMatchesWhenHigherDoesNot() {
        Rule high = rule("high", 10, "high-result",
                cond(UrlPart.HOST, Operator.EQUALS, "special.com"));
        Rule low = rule("low", 1, "low-result",
                cond(UrlPart.HOST, Operator.ENDS_WITH, ".com"));
        RuleEngine engine = new RuleEngine(List.of(high, low));

        assertEquals("low-result",
                engine.evaluate(url("example.com", "/", "")));
    }

    // --- Edge cases ---

    @Test
    void noRulesReturnsEmpty() {
        RuleEngine engine = new RuleEngine(List.of());
        assertNull(engine.evaluate(url("x.com", "/", "")));
    }

    @Test
    void noMatchReturnsNull() {
        Rule r = rule("r", 1, "result", cond(UrlPart.HOST, Operator.EQUALS, "specific.com"));
        RuleEngine engine = new RuleEngine(List.of(r));
        assertNull(engine.evaluate(url("other.com", "/", "")));
    }

    @Test
    void queryPartMatching() {
        Rule r = rule("qr", 1, "query-match",
                cond(UrlPart.QUERY, Operator.CONTAINS, "lang=en"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("query-match",
                engine.evaluate(url("x.com", "/", "q=test&lang=en")));
        assertNull(engine.evaluate(url("x.com", "/", "q=test&lang=fr")));
    }

    @Test
    void emptyPathAndQuery() {
        Rule r = rule("empty", 1, "matched",
                cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("matched",
                engine.evaluate(url("example.com", "", "")));
    }

    @Test
    void filePartMatching() {
        Rule r = rule("html", 1, "html-file",
                cond(UrlPart.FILE, Operator.ENDS_WITH, ".html"));
        RuleEngine engine = new RuleEngine(List.of(r));

        assertEquals("html-file",
                engine.evaluate(url("x.com", "/page/index.html", "")));
        assertNull(engine.evaluate(url("x.com", "/page/data.json", "")));
    }
}
