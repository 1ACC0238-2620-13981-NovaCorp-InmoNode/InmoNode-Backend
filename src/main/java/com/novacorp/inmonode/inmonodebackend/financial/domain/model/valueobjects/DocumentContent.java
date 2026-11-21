package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.util.List;

/**
 * What a background PDF says: its title and its lines, taken from the server's own data when it was requested. It
 * travels in the message to the document consumer, so rendering never reads the database again.
 *
 * @param title shown at the top of every page
 * @param lines the body, one paragraph per line; long lines wrap
 */
public record DocumentContent(String title, List<String> lines) {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_LINES = 5_000;

    public DocumentContent {
        if (title == null || title.isBlank() || title.strip().length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("a document needs a title of at most %d characters"
                    .formatted(MAX_TITLE_LENGTH));
        }
        if (lines == null || lines.size() > MAX_LINES || lines.stream().anyMatch(line -> line == null)) {
            throw new IllegalArgumentException("a document has at most %d lines, none of them null"
                    .formatted(MAX_LINES));
        }
        title = title.strip();
        lines = List.copyOf(lines);
    }
}
