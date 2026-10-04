package io.github.argod2213.policyrag.document;

import io.github.argod2213.policyrag.access.Audience;
import io.github.argod2213.policyrag.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestion;
    private final PolicyDocumentRepository repository;
    private final CurrentUser currentUser;

    public DocumentController(DocumentIngestionService ingestion, PolicyDocumentRepository repository, CurrentUser currentUser) {
        this.ingestion = ingestion;
        this.repository = repository;
        this.currentUser = currentUser;
    }

    /** Lists only the documents the caller is allowed to see. */
    @GetMapping
    public List<PolicyDocument> list(Authentication auth) {
        return repository.findByAudienceIn(currentUser.visibleAudiences(auth));
    }

    /** Upload a file (PDF, DOCX, HTML, Markdown, plain text). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyDocument upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam("title") @NotBlank String title,
                                 @RequestParam("audience") Audience audience,
                                 Authentication auth) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String type = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        return ingestion.ingest(new DocumentIngestionService.IngestCommand(
                title, audience, name, type, file.getBytes(), auth.getName()));
    }

    /** Convenience endpoint for ingesting raw text / Markdown as JSON. */
    @PostMapping(path = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyDocument ingestText(@Valid @RequestBody TextDocumentRequest request, Authentication auth) {
        return ingestion.ingest(new DocumentIngestionService.IngestCommand(
                request.title(), request.audience(), request.title() + ".md", "text/markdown",
                request.content().getBytes(StandardCharsets.UTF_8), auth.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        ingestion.delete(id);
    }

    public record TextDocumentRequest(@NotBlank @Size(max = 255) String title,
                                      @NotNull Audience audience,
                                      @NotBlank @Size(max = 500_000) String content) {
    }
}
