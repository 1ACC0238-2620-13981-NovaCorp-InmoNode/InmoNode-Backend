package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jdbc;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectImage;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectImageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ProjectImageRepository} with plain JDBC, like the document requests: an image is a single row that is only
 * inserted and read.
 */
@Repository
public class JdbcProjectImageRepository implements ProjectImageRepository {

    private static final RowMapper<RegisteredImage> ROW = (row, number) -> new RegisteredImage(
            new ProjectImage(row.getLong("project_id"), row.getObject("image_id", UUID.class),
                    row.getString("content_type"), row.getLong("size_bytes")),
            row.getTimestamp("registered_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcProjectImageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(ProjectImage image, Instant registeredAt) {
        jdbc.update("""
                INSERT INTO financial_document_control.project_images
                    (project_id, image_id, content_type, size_bytes, object_key, registered_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, image.projectId(), image.imageId(), image.contentType(), image.sizeBytes(), image.objectKey(),
                Timestamp.from(registeredAt));
    }

    @Override
    public Optional<RegisteredImage> findByImageId(UUID imageId) {
        return jdbc.query("SELECT * FROM financial_document_control.project_images WHERE image_id = ?", ROW, imageId)
                .stream().findFirst();
    }

    @Override
    public List<RegisteredImage> findByProjectId(Long projectId) {
        return jdbc.query("""
                SELECT * FROM financial_document_control.project_images
                WHERE project_id = ?
                ORDER BY registered_at, id
                """, ROW, projectId);
    }
}
