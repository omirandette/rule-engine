package com.ruleengine.engine;

import com.ruleengine.url.ParsedUrl;
import com.ruleengine.url.UrlParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BatchProcessor {

    private final RuleEngine engine;

    public BatchProcessor(RuleEngine engine) {
        this.engine = engine;
    }

    public record UrlResult(String url, String result) {}

    public List<UrlResult> processFile(Path urlFile) throws IOException {
        List<String> lines = Files.readAllLines(urlFile);
        return processLines(lines);
    }

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
