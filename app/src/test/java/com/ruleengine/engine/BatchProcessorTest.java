package com.ruleengine.engine;

import com.ruleengine.rule.Condition;
import com.ruleengine.rule.Operator;
import com.ruleengine.rule.Rule;
import com.ruleengine.url.UrlPart;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BatchProcessorTest {

    private Rule rule(String name, int priority, String result, Condition... conditions) {
        return new Rule(name, priority, List.of(conditions), result);
    }

    private Condition cond(UrlPart part, Operator op, String value) {
        return new Condition(part, op, value, false);
    }

    @Test
    void processesMultipleUrls() {
        Rule r1 = rule("ca-sport", 10, "Canada Sport",
                cond(UrlPart.HOST, Operator.ENDS_WITH, ".ca"),
                cond(UrlPart.PATH, Operator.CONTAINS, "sport"));
        Rule r2 = rule("example-home", 5, "Example Home",
                cond(UrlPart.HOST, Operator.EQUALS, "example.com"),
                cond(UrlPart.PATH, Operator.EQUALS, "/"));

        RuleEngine engine = new RuleEngine(List.of(r1, r2));
        BatchProcessor processor = new BatchProcessor(engine);

        List<BatchProcessor.UrlResult> results = processor.processLines(List.of(
                "https://shop.example.ca/category/sport/items",
                "https://example.com/",
                "https://other.org/page"
        ));

        assertEquals(3, results.size());
        assertEquals("Canada Sport", results.get(0).result());
        assertEquals("Example Home", results.get(1).result());
        assertEquals("NO_MATCH", results.get(2).result());
    }

    @Test
    void skipsBlankLines() {
        Rule r = rule("r", 1, "ok", cond(UrlPart.HOST, Operator.EQUALS, "x.com"));
        RuleEngine engine = new RuleEngine(List.of(r));
        BatchProcessor processor = new BatchProcessor(engine);

        List<BatchProcessor.UrlResult> results = processor.processLines(List.of(
                "https://x.com/", "", "  ", "https://x.com/page"
        ));
        assertEquals(2, results.size());
    }

    @Test
    void handlesInvalidUrls() {
        Rule r = rule("r", 1, "ok", cond(UrlPart.HOST, Operator.EQUALS, "x.com"));
        RuleEngine engine = new RuleEngine(List.of(r));
        BatchProcessor processor = new BatchProcessor(engine);

        List<BatchProcessor.UrlResult> results = processor.processLines(List.of(
                "://bad-url"
        ));
        assertEquals(1, results.size());
        assertEquals("INVALID_URL", results.getFirst().result());
    }

    @Test
    void emptyInputReturnsEmptyResults() {
        RuleEngine engine = new RuleEngine(List.of());
        BatchProcessor processor = new BatchProcessor(engine);
        List<BatchProcessor.UrlResult> results = processor.processLines(List.of());
        assertTrue(results.isEmpty());
    }

    @Test
    void parallelProcessingPreservesOrder() {
        Rule r = rule("host-match", 1, "matched",
                cond(UrlPart.HOST, Operator.EQUALS, "example.com"));
        RuleEngine engine = new RuleEngine(List.of(r));
        BatchProcessor processor = new BatchProcessor(engine);

        List<String> urls = new ArrayList<>(
                IntStream.range(0, 10_000)
                        .mapToObj(i -> "https://example.com/page/" + i)
                        .toList());

        List<BatchProcessor.UrlResult> results = processor.processLines(urls);

        assertEquals(urls.size(), results.size());
        for (int i = 0; i < urls.size(); i++) {
            assertEquals("https://example.com/page/" + i, results.get(i).url(),
                    "Result at index " + i + " has wrong URL");
            assertEquals("matched", results.get(i).result());
        }
    }
}
