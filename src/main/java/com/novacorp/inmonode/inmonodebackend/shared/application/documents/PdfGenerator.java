package com.novacorp.inmonode.inmonodebackend.shared.application.documents;

import java.util.List;

/** Infrastructure port. Contents always come from server-side domain snapshots. */
public interface PdfGenerator {

    /**
     * A read-only PDF of {@code lines}, wrapped to the page width. Consecutive lines with tabs ({@code \t}) form a
     * table: each tab separates two cells and the first of them is the header, repeated on every page the table
     * spans. Numbers line up to the right.
     */
    byte[] generateReadOnly(String title, List<String> lines);
}
