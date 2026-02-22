package com.ruleengine.engine;

import com.ruleengine.index.ContainsStrategy;
import com.ruleengine.rule.Rule;
import com.ruleengine.rule.RuleLoader;
import com.ruleengine.url.UrlParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test exercising every combination of UrlPart x Operator x negation
 * through the full pipeline: JSON loading, RuleEngine, UrlParser, BatchProcessor.
 */
@Tag("integration")
class RuleEngineIntegrationTest {

    private static final String CANONICAL_URL =
            "https://shop.example.ca/api/sport/index.html?lang=en&sort=date";

    private static List<Rule> loadRules() throws IOException {
        try (InputStream is = RuleEngineIntegrationTest.class.getClassLoader()
                .getResourceAsStream("integration-rules.json")) {
            assertNotNull(is, "integration-rules.json not found on classpath");
            return RuleLoader.load(new InputStreamReader(is, StandardCharsets.UTF_8));
        }
    }

    private static List<String> loadUrls() throws IOException {
        try (InputStream is = RuleEngineIntegrationTest.class.getClassLoader()
                .getResourceAsStream("integration-urls.txt")) {
            assertNotNull(is, "integration-urls.txt not found on classpath");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            return reader.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList();
        }
    }

    static Stream<String> allSingleConditionRuleNames() {
        String[] parts = {"host", "path", "file", "query"};
        String[] operators = {"equals", "contains", "starts_with", "ends_with"};
        List<String> names = new ArrayList<>();
        for (String part : parts) {
            for (String op : operators) {
                names.add(part + "-" + op);
                names.add(part + "-" + op + "-neg");
            }
        }
        return names.stream();
    }

    /**
     * Loads all rules from JSON, reads URLs from file, runs BatchProcessor,
     * and asserts expected results including priority ordering.
     */
    @ParameterizedTest
    @EnumSource(ContainsStrategy.class)
    void batchPipelineProducesExpectedResults(ContainsStrategy strategy) throws IOException {
        List<Rule> rules = loadRules();
        RuleEngine engine = new RuleEngine(rules, strategy);
        BatchProcessor processor = new BatchProcessor(engine);
        List<String> urls = loadUrls();

        List<BatchProcessor.UrlResult> results = processor.processLines(urls);

        assertEquals(3, results.size(), "expected one result per URL");
        assertEquals("compound-positive", results.get(0).result(),
                "canonical URL should match compound-positive (priority 10)");
        assertEquals("compound-all-neg", results.get(1).result(),
                "second URL should match compound-all-neg (priority 10)");
        assertEquals("compound-all-neg", results.get(2).result(),
                "third URL should match compound-all-neg (priority 10)");
    }

    /**
     * For each of the 32 single-condition rules, builds a single-rule engine
     * with both contains strategies and verifies the canonical URL matches.
     */
    @ParameterizedTest
    @MethodSource("allSingleConditionRuleNames")
    void eachConditionTypeMatchesCanonicalUrl(String ruleName) throws IOException {
        List<Rule> allRules = loadRules();
        Rule target = allRules.stream()
                .filter(r -> r.name().equals(ruleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Rule not found: " + ruleName));

        var parsed = UrlParser.parse(CANONICAL_URL);

        for (ContainsStrategy strategy : ContainsStrategy.values()) {
            RuleEngine engine = new RuleEngine(List.of(target), strategy);
            var result = engine.evaluate(parsed);
            assertTrue(result.isPresent(),
                    "Rule " + ruleName + " should match canonical URL with " + strategy);
            assertEquals(ruleName, result.get());
        }
    }
}
