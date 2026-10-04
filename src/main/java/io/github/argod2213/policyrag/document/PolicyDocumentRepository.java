package io.github.argod2213.policyrag.document;

import io.github.argod2213.policyrag.access.Audience;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PolicyDocumentRepository {

    private static final RowMapper<PolicyDocument> MAPPER = (rs, i) -> new PolicyDocument(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            Audience.valueOf(rs.getString("audience")),
            rs.getString("source_name"),
            rs.getString("content_type"),
            rs.getString("content_sha256"),
            rs.getInt("chunk_count"),
            rs.getString("created_by"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcClient jdbc;

    public PolicyDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(PolicyDocument doc) {
        jdbc.sql("""
                        INSERT INTO policy_document
                            (id, title, audience, source_name, content_type, content_sha256, chunk_count, created_by, created_at)
                        VALUES (:id, :title, :audience, :sourceName, :contentType, :sha, :chunkCount, :createdBy, :createdAt)
                        """)
                .param("id", doc.id())
                .param("title", doc.title())
                .param("audience", doc.audience().name())
                .param("sourceName", doc.sourceName())
                .param("contentType", doc.contentType())
                .param("sha", doc.contentSha256())
                .param("chunkCount", doc.chunkCount())
                .param("createdBy", doc.createdBy())
                .param("createdAt", Timestamp.from(doc.createdAt()))
                .update();
    }

    public Optional<PolicyDocument> findById(UUID id) {
        return jdbc.sql("SELECT * FROM policy_document WHERE id = :id").param("id", id).query(MAPPER).optional();
    }

    public Optional<PolicyDocument> findBySha256(String sha) {
        return jdbc.sql("SELECT * FROM policy_document WHERE content_sha256 = :sha").param("sha", sha)
                .query(MAPPER).optional();
    }

    public List<PolicyDocument> findByAudienceIn(Collection<Audience> audiences) {
        return jdbc.sql("SELECT * FROM policy_document WHERE audience IN (:audiences) ORDER BY title")
                .param("audiences", audiences.stream().map(Audience::name).toList())
                .query(MAPPER).list();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM policy_document").query(Long.class).single();
    }

    public void deleteById(UUID id) {
        jdbc.sql("DELETE FROM policy_document WHERE id = :id").param("id", id).update();
    }
}
