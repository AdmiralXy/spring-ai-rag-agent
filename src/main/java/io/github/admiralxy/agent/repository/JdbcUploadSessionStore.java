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
    private static final String SESSION_COLUMNS =
            "c_id, c_user_id, c_name, c_chunk_size, c_status, c_created_at, c_expires_at, c_key_salt";
    private static final String FILE_COLUMNS = "c_index, c_path, c_size, c_chunk_count";
    private static final String INSERT_SESSION = "insert into t_upload_sessions (" + SESSION_COLUMNS
            + ") values (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String INSERT_FILE = "insert into t_upload_session_files (c_session_id, " + FILE_COLUMNS
            + ") values (?, ?, ?, ?, ?)";
    private static final String SELECT_SESSION = "select " + SESSION_COLUMNS + " from t_upload_sessions where c_id = ?";
    private static final String SELECT_FILE = "select " + FILE_COLUMNS
            + " from t_upload_session_files where c_session_id = ? and c_index = ?";
    private static final String SELECT_FILES = "select " + FILE_COLUMNS
            + " from t_upload_session_files where c_session_id = ? order by c_index";
    private static final String TOUCH = "update t_upload_sessions set c_expires_at = ?"
            + " where c_id = ? and c_status = ? and c_expires_at > ?";
    private static final String UPDATE_STATUS = "update t_upload_sessions set c_status = ?, c_expires_at = ? where c_id = ?";
    private static final String DELETE = "delete from t_upload_sessions where c_id = ?";
    private static final String DELETE_IF_EXPIRED = DELETE + " and c_expires_at <= ?";
    private static final String SELECT_EXPIRED = "select c_id from t_upload_sessions where c_expires_at <= ?"
            + " order by c_expires_at limit ?";
    private static final String EXISTS = "select exists(select 1 from t_upload_sessions where c_id = ?)";
    private static final String ID = "c_id";
    private static final String USER_ID = "c_user_id";
    private static final String NAME = "c_name";
    private static final String CHUNK_SIZE = "c_chunk_size";
    private static final String STATUS = "c_status";
    private static final String CREATED_AT = "c_created_at";
    private static final String EXPIRES_AT = "c_expires_at";
    private static final String KEY_SALT = "c_key_salt";
    private static final String INDEX = "c_index";
    private static final String PATH = "c_path";
    private static final String SIZE = "c_size";
    private static final String CHUNK_COUNT = "c_chunk_count";

    private static final RowMapper<UploadSession> SESSION_MAPPER = (rs, rowNum) -> new UploadSession(
            rs.getObject(ID, UUID.class),
            rs.getObject(USER_ID, UUID.class),
            rs.getString(NAME),
            rs.getLong(CHUNK_SIZE),
            UploadSessionStatus.valueOf(rs.getString(STATUS)),
            rs.getObject(CREATED_AT, OffsetDateTime.class).toInstant(),
            rs.getObject(EXPIRES_AT, OffsetDateTime.class).toInstant(),
            rs.getBytes(KEY_SALT)
    );
    private static final RowMapper<UploadSessionFile> FILE_MAPPER = (rs, rowNum) -> new UploadSessionFile(
            rs.getInt(INDEX),
            rs.getString(PATH),
            rs.getLong(SIZE),
            rs.getInt(CHUNK_COUNT)
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void create(UploadSession session, List<UploadSessionFile> files) {
        jdbcTemplate.update(INSERT_SESSION, session.id(), session.userId(), session.name(), session.chunkSize(),
                session.status().name(),
                ts(session.createdAt()), ts(session.expiresAt()), session.keySalt());
        jdbcTemplate.batchUpdate(INSERT_FILE, files, INSERT_BATCH_SIZE, (ps, file) -> {
            ps.setObject(1, session.id());
            ps.setInt(2, file.index());
            ps.setString(3, file.path());
            ps.setLong(4, file.size());
            ps.setInt(5, file.chunkCount());
        });
    }

    @Override
    public Optional<UploadSession> find(UUID id) {
        return jdbcTemplate.query(SELECT_SESSION, SESSION_MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<UploadSessionFile> findFile(UUID id, int fileIndex) {
        return jdbcTemplate.query(SELECT_FILE, FILE_MAPPER, id, fileIndex).stream().findFirst();
    }

    @Override
    public List<UploadSessionFile> findFiles(UUID id) {
        return jdbcTemplate.query(SELECT_FILES, FILE_MAPPER, id);
    }

    @Override
    public boolean touch(UUID id, Instant now, Instant expiresAt) {
        return jdbcTemplate.update(TOUCH, ts(expiresAt), id, UploadSessionStatus.ACTIVE.name(), ts(now)) == 1;
    }

    @Override
    public boolean updateStatus(UUID id, UploadSessionStatus status, Instant expiresAt) {
        return jdbcTemplate.update(UPDATE_STATUS, status.name(), ts(expiresAt), id) == 1;
    }

    @Override
    public boolean delete(UUID id) {
        return jdbcTemplate.update(DELETE, id) == 1;
    }

    @Override
    public boolean deleteIfExpired(UUID id, Instant now) {
        return jdbcTemplate.update(DELETE_IF_EXPIRED, id, ts(now)) == 1;
    }

    @Override
    public List<UUID> findExpiredIds(Instant now, int limit) {
        return jdbcTemplate.queryForList(SELECT_EXPIRED, UUID.class, ts(now), limit);
    }

    @Override
    public boolean exists(UUID id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(EXISTS, Boolean.class, id));
    }

    private static OffsetDateTime ts(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
