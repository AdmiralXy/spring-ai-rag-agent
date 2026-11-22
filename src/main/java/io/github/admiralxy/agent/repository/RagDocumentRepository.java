package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.domain.RagSearchHit;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunks of the spaces with their embeddings. The vector column has no fixed dimension, so users can run
 * embedding models of different sizes: every query casts the vectors to the dimension of the caller's model
 * and only looks at rows of that dimension, which is exactly what the partial HNSW index of that dimension
 * covers.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RagDocumentRepository {

    public static final int MAX_DIMENSIONS = 16_000;

    private static final String COLUMNS = "c_id::text AS id, c_content AS content, c_metadata::text AS metadata";
    private static final String SPACE_IN = "c_space_id IN (%s)";
    private static final String VECTOR_SEARCH = "SELECT " + COLUMNS + " FROM t_documents WHERE " + SPACE_IN
            + " AND c_dimensions = %2$d ORDER BY c_embedding::vector(%2$d) <=> ?::vector(%2$d) LIMIT ?";
    private static final String KEYWORD_SEARCH = "SELECT " + COLUMNS + " FROM t_documents, to_tsquery('simple', ?) q"
            + " WHERE " + SPACE_IN + " AND to_tsvector('simple', c_content) @@ q"
            + " ORDER BY ts_rank_cd(to_tsvector('simple', c_content), q) DESC LIMIT ?";
    private static final String NEIGHBOURS = "SELECT " + COLUMNS + " FROM t_documents"
            + " WHERE c_space_id = ? AND c_metadata ->> 'doc' = ? AND c_metadata ->> 'source' = ?"
            + " AND c_metadata ->> 'part' IN (%s)";
    private static final String LIST = "SELECT " + COLUMNS + " FROM t_documents WHERE c_space_id = ?"
            + " ORDER BY c_metadata ->> 'doc', (c_metadata ->> 'number')::int, (c_metadata ->> 'part')::int, c_id"
            + " LIMIT ?";
    private static final String INSERT = "INSERT INTO t_documents (c_id, c_space_id, c_dimensions, c_content,"
            + " c_metadata, c_embedding) VALUES (?, ?, ?, ?, ?::jsonb, ?::vector)";
    private static final String DELETE_DOC = "DELETE FROM t_documents WHERE c_space_id = ? AND c_metadata ->> 'doc' = ?";
    private static final String DELETE_CHUNK = DELETE_DOC + " AND c_metadata ->> 'chunk' = ?";
    private static final String DELETE_OF_USER = "DELETE FROM t_documents d USING t_spaces s"
            + " WHERE d.c_space_id = s.c_id AND s.c_user_id = ?";
    private static final String COUNT_OF_USER = "SELECT count(*) FROM t_documents d JOIN t_spaces s"
            + " ON d.c_space_id = s.c_id WHERE s.c_user_id = ?";
    private static final String CREATE_VECTOR_INDEX = "CREATE INDEX IF NOT EXISTS idx_documents_embedding_%1$d"
            + " ON t_documents USING hnsw ((c_embedding::vector(%1$d)) vector_cosine_ops) WHERE c_dimensions = %1$d";
    private static final String SET_EF_SEARCH = "SELECT set_config('hnsw.ef_search', ?, true)";
    private static final String SET_ITERATIVE_SCAN = "SELECT set_config('hnsw.iterative_scan', 'relaxed_order', true)";
    private static final String VECTOR_VERSION = "SELECT extversion FROM pg_extension WHERE extname = 'vector'";
    private static final String PLACEHOLDER = "?";
    private static final String PLACEHOLDER_SEPARATOR = ", ";
    private static final String VERSION_SEPARATOR = "\\.";
    private static final String ID_COLUMN = "id";
    private static final String CONTENT_COLUMN = "content";
    private static final String METADATA_COLUMN = "metadata";
    private static final String KEYWORD_SEARCH_FAILED = "Full-text search failed, using the vector search only: {}";
    private static final String NEIGHBOURS_FAILED = "Neighbour lookup failed for chunk {}: {}";
    private static final String ITERATIVE_SCAN = "pgvector iterative index scans {}";
    private static final String ENABLED = "enabled";
    private static final String UNAVAILABLE = "unavailable (pgvector < 0.8)";
    private static final String INDEX_CREATED = "HNSW index for {}-dimensional embeddings is ready";
    private static final String HNSW_SKIPPED = "No HNSW index for {} dimensions (max {}), searches scan the space";
    private static final String INDEX_FAILED = "Failed to create the HNSW index for {} dimensions: {}";
    private static final int MIN_EF_SEARCH = 200;
    private static final int MAX_EF_SEARCH = 1000;
    private static final int EF_SEARCH_PER_RESULT = 4;
    private static final int HNSW_MAX_DIMENSIONS = 2000;
    private static final int ITERATIVE_SCAN_MAJOR = 0;
    private static final int ITERATIVE_SCAN_MINOR = 8;
    private static final int INSERT_BATCH_SIZE = 200;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final JsonMapper jsonMapper;
    private final Set<Integer> indexedDimensions = ConcurrentHashMap.newKeySet();

    private volatile Boolean iterativeScanSupported;

    public record NewChunk(UUID id, String content, Map<String, Object> metadata, float[] embedding) {
    }

    public void insert(UUID spaceId, int dimensions, List<NewChunk> chunks) {
        jdbcTemplate.batchUpdate(INSERT, chunks, INSERT_BATCH_SIZE, (ps, chunk) -> {
            ps.setObject(1, chunk.id());
            ps.setObject(2, spaceId);
            ps.setInt(3, dimensions);
            ps.setString(4, chunk.content());
            ps.setString(5, jsonMapper.writeValueAsString(chunk.metadata()));
            ps.setString(6, vectorLiteral(chunk.embedding()));
        });
    }

    public List<RagSearchHit> vectorSearch(List<UUID> spaceIds, int dimensions, float[] embedding, int limit) {
        List<Object> params = new ArrayList<>(spaceIds);
        params.add(vectorLiteral(embedding));
        params.add(limit);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        List<RagSearchHit> hits = tx.execute(status -> {
            jdbcTemplate.queryForObject(SET_EF_SEARCH, String.class,
                    String.valueOf(Math.clamp((long) limit * EF_SEARCH_PER_RESULT, MIN_EF_SEARCH, MAX_EF_SEARCH)));
            if (isIterativeScanSupported()) {
                jdbcTemplate.queryForObject(SET_ITERATIVE_SCAN, String.class);
            }
            return jdbcTemplate.query(VECTOR_SEARCH.formatted(placeholders(spaceIds.size()), dimensions), hitMapper(),
                    params.toArray());
        });
        return hits == null ? List.of() : hits;
    }

    public List<RagSearchHit> keywordSearch(List<UUID> spaceIds, String tsQuery, int limit) {
        List<Object> params = new ArrayList<>();
        params.add(tsQuery);
        params.addAll(spaceIds);
        params.add(limit);
        try {
            return jdbcTemplate.query(KEYWORD_SEARCH.formatted(placeholders(spaceIds.size())), hitMapper(),
                    params.toArray());
        } catch (RuntimeException e) {
            log.warn(KEYWORD_SEARCH_FAILED, e.getMessage());
            return List.of();
        }
    }

    public List<RagSearchHit> neighbours(RagSearchHit hit, Collection<Integer> parts) {
        UUID space = parseUuid(hit.space());
        if (hit.source() == null || hit.doc() == null || space == null || parts.isEmpty()) {
            return List.of();
        }
        List<Object> params = new ArrayList<>(List.of(space, hit.doc(), hit.source()));
        parts.forEach(part -> params.add(String.valueOf(part)));
        try {
            return jdbcTemplate.query(NEIGHBOURS.formatted(placeholders(parts.size())), hitMapper(), params.toArray());
        } catch (RuntimeException e) {
            log.warn(NEIGHBOURS_FAILED, hit.id(), e.getMessage());
            return List.of();
        }
    }

    public List<RagSearchHit> list(UUID spaceId, int limit) {
        return jdbcTemplate.query(LIST, hitMapper(), spaceId, limit);
    }

    public void deleteDocument(UUID spaceId, String docId) {
        jdbcTemplate.update(DELETE_DOC, spaceId, docId);
    }

    public void deleteChunk(UUID spaceId, String docId, String chunkId) {
        jdbcTemplate.update(DELETE_CHUNK, spaceId, docId, chunkId);
    }

    public int deleteAllOfUser(UUID userId) {
        return jdbcTemplate.update(DELETE_OF_USER, userId);
    }

    public long countOfUser(UUID userId) {
        Long count = jdbcTemplate.queryForObject(COUNT_OF_USER, Long.class, userId);
        return count == null ? 0 : count;
    }

    /**
     * Creates the partial HNSW index for vectors of this dimension once; searches work without it, only slower.
     */
    public void ensureVectorIndex(int dimensions) {
        if (dimensions <= 0 || indexedDimensions.contains(dimensions)) {
            return;
        }
        if (dimensions > HNSW_MAX_DIMENSIONS) {
            log.info(HNSW_SKIPPED, dimensions, HNSW_MAX_DIMENSIONS);
            indexedDimensions.add(dimensions);
            return;
        }
        try {
            jdbcTemplate.execute(CREATE_VECTOR_INDEX.formatted(dimensions));
            indexedDimensions.add(dimensions);
            log.info(INDEX_CREATED, dimensions);
        } catch (RuntimeException e) {
            log.warn(INDEX_FAILED, dimensions, e.getMessage());
        }
    }

    static boolean isAtLeast(String version, int major, int minor) {
        String[] parts = version.split(VERSION_SEPARATOR);
        try {
            int actualMajor = Integer.parseInt(parts[0]);
            int actualMinor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return actualMajor > major || (actualMajor == major && actualMinor >= minor);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isIterativeScanSupported() {
        Boolean supported = iterativeScanSupported;
        if (supported != null) {
            return supported;
        }
        try {
            List<String> versions = jdbcTemplate.queryForList(VECTOR_VERSION, String.class);
            supported = !versions.isEmpty()
                    && isAtLeast(versions.getFirst(), ITERATIVE_SCAN_MAJOR, ITERATIVE_SCAN_MINOR);
        } catch (RuntimeException e) {
            supported = false;
        }
        iterativeScanSupported = supported;
        log.info(ITERATIVE_SCAN, supported ? ENABLED : UNAVAILABLE);
        return supported;
    }

    private RowMapper<RagSearchHit> hitMapper() {
        return (rs, rowNum) -> new RagSearchHit(rs.getString(ID_COLUMN), rs.getString(CONTENT_COLUMN),
                parseMetadata(rs.getString(METADATA_COLUMN)));
    }

    private Map<String, Object> parseMetadata(@Nullable String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            return jsonMapper.readValue(json, MAP_TYPE);
        } catch (RuntimeException e) {
            return Collections.emptyMap();
        }
    }

    @Nullable
    private static UUID parseUuid(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String vectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 10).append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }

    private static String placeholders(int count) {
        return String.join(PLACEHOLDER_SEPARATOR, Collections.nCopies(count, PLACEHOLDER));
    }
}
