package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.util.*;

public record ProjectStages(List<String> names) {
    public ProjectStages {
        if (names == null || names.isEmpty() || names.size() > 50) throw new IllegalArgumentException("stages must contain 1 to 50 names");
        var normalized = new ArrayList<String>();
        var unique = new HashSet<String>();
        for (var name : names) {
            if (name == null || name.isBlank() || name.strip().length() > 80) throw new IllegalArgumentException("each stage needs a name of at most 80 characters");
            name = name.strip();
            if (!unique.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("stage names must be unique within the project");
            normalized.add(name);
        }
        names = List.copyOf(normalized);
    }
}
