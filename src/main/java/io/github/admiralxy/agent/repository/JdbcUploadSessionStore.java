package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.domain.UploadSession;
import io.github.admiralxy.agent.domain.UploadSessionFile;
import io.github.admiralxy.agent.domain.UploadSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcUploadSessionStore implements UploadSessionStore {

    private static final int INSERT_BATCH_SIZE = 1000;

    private static final String SESSION_COLUMNS = "c_id, c_name, c_chunk_size, c_status, c_created_at, c_expires_at";
    private static final String FILE_COLUMNS = "c_index, c_path, c_size, c_chunk_count";

    private static final RowMapper<UploadSession> SESSION_MAPPER = (rs, rowNum) -> new UploadSession(
            rs.getObject("c_id", UUID.class),
            rs.getString("c_name"),
            rs.getLong("c_chunk_size"),
            UploadSessionStatus.valueOf(rs.getString("c_status")),
            rs.getObject("c_created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("c_expires_at", OffsetDateTime.class).toInstant()
    );

    private static final RowMapper<UploadSessionFile> FILE_MAPPER = (rs, rowNum) -> new UploadSessionFile(
            rs.getInt("c_index"),
            rs.getString("c_path"),
            rs.getLong("c_size"),
            rs.getInt("c_chunk_count")
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void create(UploadSession session, List<UploadSessionFile> files) {
        jdbcTemplate.update(
                "insert into t_upload_sessions (" + SESSION_COLUMNS + ") values (?, ?, ?, ?, ?, ?)",
                session.id(), session.name(), session.chunkSize(), session.status().name(),
                ts(session.createdAt()), ts(session.expiresAt())
        );
        jdbcTemplate.batchUpdate(
                "insert into t_upload_session_files (c_session_id, " + FILE_COLUMNS + ") values (?, ?, ?, ?, ?)",
                files,
                INSERT_BATCH_SIZE,
                (ps, file) -> {
                    ps.setObject(1, session.id());
                    ps.setInt(2, file.index());
                    ps.setString(3, file.path());
                    ps.setLong(4, file.size());
                    ps.setInt(5, file.chunkCount());
                }
        );
    }

    @Override
    public Optional<UploadSession> find(UUID id) {
        return jdbcTemplate.query("select " + SESSION_COLUMNS + " from t_upload_sessions where c_id = ?", SESSION_MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public Optional<UploadSessionFile> findFile(UUID id, int fileIndex) {
        return jdbcTemplate.query(
                "select " + FILE_COLUMNS + " from t_upload_session_files where c_session_id = ? and c_index = ?",
                FILE_MAPPER, id, fileIndex
        ).stream().findFirst();
    }

    @Override
    public List<UploadSessionFile> findFiles(UUID id) {
        return jdbcTemplate.query(
                "select " + FILE_COLUMNS + " from t_upload_session_files where c_session_id = ? order by c_index",
                FILE_MAPPER, id
        );
    }

    @Override
    public boolean touch(UUID id, Instant now, Instant expiresAt) {
        return jdbcTemplate.update(
                "update t_upload_sessions set c_expires_at = ? where c_id = ? and c_status = ? and c_expires_at > ?",
                ts(expiresAt), id, UploadSessionStatus.ACTIVE.name(), ts(now)
        ) == 1;
    }

    @Override
    public boolean updateStatus(UUID id, UploadSessionStatus status, Instant expiresAt) {
        return jdbcTemplate.update(
                "update t_upload_sessions set c_status = ?, c_expires_at = ? where c_id = ?",
                status.name(), ts(expiresAt), id
        ) == 1;
    }

    @Override
    public boolean delete(UUID id) {
        return jdbcTemplate.update("delete from t_upload_sessions where c_id = ?", id) == 1;
    }

    @Override
    public boolean deleteIfExpired(UUID id, Instant now) {
        return jdbcTemplate.update("delete from t_upload_sessions where c_id = ? and c_expires_at <= ?", id, ts(now)) == 1;
    }

    @Override
    public List<UUID> findExpiredIds(Instant now, int limit) {
        return jdbcTemplate.queryForList(
                "select c_id from t_upload_sessions where c_expires_at <= ? order by c_expires_at limit ?",
                UUID.class, ts(now), limit
        );
    }

    @Override
    public boolean exists(UUID id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from t_upload_sessions where c_id = ?)", Boolean.class, id
        ));
    }

    private static OffsetDateTime ts(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
