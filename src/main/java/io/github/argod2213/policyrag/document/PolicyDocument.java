package io.github.argod2213.policyrag.document;

import io.github.argod2213.policyrag.access.Audience;

import java.time.Instant;
import java.util.UUID;

public record PolicyDocument(UUID id,
                             String title,
                             Audience audience,
                             String sourceName,
                             String contentType,
                             String contentSha256,
                             int chunkCount,
                             String createdBy,
                             Instant createdAt) {
}
