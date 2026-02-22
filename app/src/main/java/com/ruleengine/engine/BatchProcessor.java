package com.ruleengine.engine;

import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Processes batches of URLs against a {@link RuleEngine}.
 *
 * <p>Reads URLs from a file (one per line) or from a list of strings,
 * evaluates each against the engine, and returns results. Blank lines
 * are skipped; malformed URLs are reported as {@code INVALID_URL}.
 */
public final class BatchProcessor {

    private final RuleEngine engine;

    /**
     * Creates a batch processor backed by the given engine.
     *
     * @param engine the rule engine to evaluate URLs against
     */
    public BatchProcessor(RuleEngine engine) {
        this.engine = engine;
    }

    /**
     * The result of evaluating a single URL.
     *
     * @param url    the original URL string
     * @param result the matched rule result, {@code "NO_MATCH"}, or {@code "INVALID_URL"}
     */
    public record UrlResult(String url, String result) {}

    /**
     * Reads URLs from a file and evaluates each against the engine.
     *
     * @param urlFile path to a plain text file with one URL per line
     * @return list of results, one per non-blank line
     * @throws IOException if the file cannot be read
     */
    public List<UrlResult> processFile(Path urlFile) throws IOException {
        List<String> lines = Files.readAllLines(urlFile);
        return processLines(lines);
    }

    /**
     * Evaluates a list of URL strings against the engine.
     *
     * @param lines the URL strings to evaluate
     * @return list of results, one per non-blank line
     */
    public List<UrlResult> processLines(List<String> lines) {
        List<UrlResult> results = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                ParsedUrl parsed = UrlParser.parse(line.strip());
                Optional<String> result = engine.evaluate(parsed);
                results.add(new UrlResult(line.strip(), result.orElse("NO_MATCH")));
            } catch (IllegalArgumentException e) {
                results.add(new UrlResult(line.strip(), "INVALID_URL"));
            }
        }
        return results;
    }
}
