package io.github.argod2213.policyrag.access;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Who a policy document is written for. Every chunk stored in the vector store carries
 * exactly one audience in its metadata, and retrieval is filtered on it.
 */
public enum Audience {
    /** Readable by every authenticated user. */
    PUBLIC,
    /** Internal policies for all employees. */
    EMPLOYEE,
    /** Human-resources only (compensation, disciplinary process, ...). */
    HR,
    /** Finance team only (close procedures, approval limits, ...). */
    FINANCE,
    /** Legal team only. */
    LEGAL;

    public static Optional<Audience> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(a -> a.name().equals(normalized)).findFirst();
    }
}
