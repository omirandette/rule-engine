package com.ruleengine.engine;

import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
     * Evaluates a list of URL strings against the engine in parallel.
     *
     * <p>Uses a parallel stream to distribute work across available cores.
     * Encounter order is preserved — results appear in the same order as the
     * input lines (minus blanks).
     *
     * @param lines the URL strings to evaluate
     * @return list of results, one per non-blank line, in input order
     */
    public List<UrlResult> processLines(List<String> lines) {
        return lines.parallelStream()
                .filter(line -> !line.isBlank())
                .map(this::evaluateLine)
                .toList();
    }

    private UrlResult evaluateLine(String line) {
        String stripped = line.strip();
        try {
            ParsedUrl parsed = UrlParser.parse(stripped);
            String result = engine.evaluate(parsed);
            return new UrlResult(stripped, result != null ? result : "NO_MATCH");
        } catch (IllegalArgumentException e) {
            return new UrlResult(stripped, "INVALID_URL");
        }
    }
}
