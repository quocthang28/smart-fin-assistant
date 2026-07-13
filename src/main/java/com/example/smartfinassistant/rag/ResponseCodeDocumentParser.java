package com.example.smartfinassistant.rag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;

final class ResponseCodeDocumentParser {

    private static final Pattern DATA_ROW = Pattern.compile(
            "^\\|\\s*(\\d{2})\\s*\\|\\s*(.*?)\\s*\\|\\s*(.*?)\\s*\\|$");
    private static final Set<String> EXPECTED_CODES = Set.of(
            "00", "01", "05", "12", "14", "30", "51", "54", "61", "68", "75", "96", "99");

    List<ResponseCodeChunk> parse(Resource resource) {
        List<ResponseCodeChunk> chunks = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = DATA_ROW.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }

                String rc = matcher.group(1);
                String meaning = matcher.group(2).trim();
                String handling = matcher.group(3).trim();
                if (!seenCodes.add(rc)) {
                    throw new IllegalStateException("Duplicate response code in document: " + rc);
                }
                if (meaning.isBlank() || handling.isBlank()) {
                    throw new IllegalStateException("Blank response-code content for RC " + rc);
                }
                chunks.add(new ResponseCodeChunk(rc, meaning, handling));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read response-code document", e);
        }

        if (!seenCodes.equals(EXPECTED_CODES)) {
            Set<String> missing = new HashSet<>(EXPECTED_CODES);
            missing.removeAll(seenCodes);
            Set<String> unexpected = new HashSet<>(seenCodes);
            unexpected.removeAll(EXPECTED_CODES);
            throw new IllegalStateException(
                    "Response-code document must contain exactly the 13 appendix codes; missing="
                            + missing + ", unexpected=" + unexpected);
        }
        return List.copyOf(chunks);
    }
}
