package com.ruleengine;

import com.ruleengine.engine.BatchProcessor;
import com.ruleengine.engine.BatchProcessor.UrlResult;
import com.ruleengine.engine.RuleEngine;
import com.ruleengine.index.ContainsStrategy;
import com.ruleengine.rule.Rule;
import com.ruleengine.rule.RuleLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI entry point for the rule engine.
 *
 * <p>Usage: {@code rule-engine <rules.json> <urls.txt> [--aho-corasick]}
 */
public class App {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: rule-engine <rules.json> <urls.txt> [--aho-corasick]");
            System.exit(1);
        }

        Path rulesPath = Path.of(args[0]);
        Path urlsPath = Path.of(args[1]);
        ContainsStrategy strategy = ContainsStrategy.TRIE;
        if (args.length >= 3 && "--aho-corasick".equals(args[2])) {
            strategy = ContainsStrategy.AHO_CORASICK;
        }

        try {
            List<Rule> rules = RuleLoader.loadFromFile(rulesPath);
            RuleEngine engine = new RuleEngine(rules, strategy);
            BatchProcessor processor = new BatchProcessor(engine);
            List<UrlResult> results = processor.processFile(urlsPath);

            for (UrlResult result : results) {
                System.out.println(result.url() + " -> " + result.result());
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
