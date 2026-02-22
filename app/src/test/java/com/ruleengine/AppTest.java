package com.ruleengine;

import com.ruleengine.engine.BatchProcessor;
import com.ruleengine.engine.BatchProcessor.UrlResult;
import com.ruleengine.engine.RuleEngine;
import com.ruleengine.rule.Rule;
import com.ruleengine.rule.RuleLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @Test
    void integrationTestWithResourceFiles() {
        InputStream rulesStream = getClass().getClassLoader().getResourceAsStream("test-rules.json");
        assertNotNull(rulesStream, "test-rules.json must be on classpath");

        List<Rule> rules = RuleLoader.load(new InputStreamReader(rulesStream));
        RuleEngine engine = new RuleEngine(rules);
        BatchProcessor processor = new BatchProcessor(engine);

        List<UrlResult> results = processor.processLines(List.of(
                "https://shop.example.ca/category/sport/items",  // matches Canada Sport (priority 10)
                "https://example.com/",                           // matches Example Home (priority 5)
                "https://example.com/admin/panel",                // matches Example Home (5) but NOT Not Admin (3)
                "https://example.com/user/profile",               // matches Not Admin (3) and could match others
                "https://news.example.ca/sport/hockey"            // matches Canada Sport (priority 10)
        ));

        assertEquals(5, results.size());
        assertEquals("Canada Sport", results.get(0).result());
        assertEquals("Example Home", results.get(1).result());
        // /admin/panel: Example Home requires path=/, so doesn't match. Not Admin is negated starts_with /admin → fails.
        assertEquals("NO_MATCH", results.get(2).result());
        assertEquals("Not Admin", results.get(3).result());
        assertEquals("Canada Sport", results.get(4).result());
    }
}
