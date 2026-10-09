package com.novacorp.inmonode.inmonodebackend.shared.application.documents;

import java.util.List;

/** Infrastructure port. Contents always come from server-side domain snapshots. */
public interface PdfGenerator {
    byte[] generateReadOnly(String title, List<String> lines);
}
