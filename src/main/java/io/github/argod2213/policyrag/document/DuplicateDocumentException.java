package io.github.argod2213.policyrag.document;

import java.util.UUID;

public class DuplicateDocumentException extends RuntimeException {

    private final UUID existingId;

    public DuplicateDocumentException(UUID existingId) {
        super("An identical document has already been ingested: " + existingId);
        this.existingId = existingId;
    }

    public UUID existingId() {
        return existingId;
    }
}
