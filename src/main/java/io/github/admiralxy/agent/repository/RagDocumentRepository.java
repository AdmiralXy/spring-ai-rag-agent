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

@Slf4j
@Repository
@RequiredArgsConstructor
public class RagDocumentRepository {

    private static final String COLUMNS = "id::text AS id, content, metadata::text AS metadata";
    private static final String SPACE_IN = "metadata ->> 'space' IN (%s)";
    private static final String VECTOR_SEARCH = "SELECT " + COLUMNS + " FROM documents WHERE " + SPACE_IN
            + " ORDER BY embedding <=> ?::vector LIMIT ?";
    private static final String KEYWORD_SEARCH = "SELECT " + COLUMNS + " FROM documents, to_tsquery('simple', ?) q"
            + " WHERE " + SPACE_IN + " AND to_tsvector('simple', content) @@ q"
            + " ORDER BY ts_rank_cd(to_tsvector('simple', content), q) DESC LIMIT ?";
    private static final String NEIGHBOURS = "SELECT " + COLUMNS + " FROM documents"
            + " WHERE metadata ->> 'space' = ? AND metadata ->> 'doc' = ? AND metadata ->> 'source' = ?"
            + " AND metadata ->> 'part' IN (%s)";
    private static final String LIST = "SELECT " + COLUMNS + " FROM documents WHERE metadata ->> 'space' = ?"
            + " ORDER BY metadata ->> 'doc', (metadata ->> 'number')::int, (metadata ->> 'part')::int, id LIMIT ?";
    private static final String DELETE_SPACE = "DELETE FROM documents WHERE metadata ->> 'space' = ?";
    private static final String DELETE_DOC = DELETE_SPACE + " AND metadata ->> 'doc' = ?";
    private static final String DELETE_CHUNK = DELETE_DOC + " AND metadata ->> 'chunk' = ?";
    private static final String DELETE_ALL = "DELETE FROM documents";
    private static final String DROP_VECTOR_INDEX = "DROP INDEX IF EXISTS documents_embedding_idx";
    private static final String ALTER_DIMENSIONS = "ALTER TABLE documents ALTER COLUMN embedding TYPE vector(%d)";
    private static final String CREATE_VECTOR_INDEX =
            "CREATE INDEX documents_embedding_idx ON documents USING hnsw (embedding vector_cosine_ops)";
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
    private static final String HNSW_SKIPPED = "No HNSW index for {} dimensions (max {}), searches scan the table";
    private static final int MIN_EF_SEARCH = 200;
    private static final int MAX_EF_SEARCH = 1000;
    private static final int EF_SEARCH_PER_RESULT = 4;
    private static final int HNSW_MAX_DIMENSIONS = 2000;
    private static final int ITERATIVE_SCAN_MAJOR = 0;
    private static final int ITERATIVE_SCAN_MINOR = 8;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final JsonMapper jsonMapper;

    private volatile Boolean iterativeScanSupported;

    public List<RagSearchHit> vectorSearch(List<String> spaceIds, float[] embedding, int limit) {
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
            return jdbcTemplate.query(VECTOR_SEARCH.formatted(placeholders(spaceIds.size())), hitMapper(),
                    params.toArray());
        });
        return hits == null ? List.of() : hits;
    }

    public List<RagSearchHit> keywordSearch(List<String> spaceIds, String tsQuery, int limit) {
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
        if (hit.source() == null || hit.doc() == null || hit.space() == null || parts.isEmpty()) {
            return List.of();
        }
        List<Object> params = new ArrayList<>(List.of(hit.space(), hit.doc(), hit.source()));
        parts.forEach(part -> params.add(String.valueOf(part)));
        try {
            return jdbcTemplate.query(NEIGHBOURS.formatted(placeholders(parts.size())), hitMapper(), params.toArray());
        } catch (RuntimeException e) {
            log.warn(NEIGHBOURS_FAILED, hit.id(), e.getMessage());
            return List.of();
        }
    }

    public List<RagSearchHit> list(String spaceId, int limit) {
        return jdbcTemplate.query(LIST, hitMapper(), spaceId, limit);
    }

    public void deleteSpace(String spaceId) {
        jdbcTemplate.update(DELETE_SPACE, spaceId);
    }

    public void deleteDocument(String spaceId, String docId) {
        jdbcTemplate.update(DELETE_DOC, spaceId, docId);
    }

    public void deleteChunk(String spaceId, String docId, String chunkId) {
        jdbcTemplate.update(DELETE_CHUNK, spaceId, docId, chunkId);
    }

    public void recreate(int dimensions) {
        jdbcTemplate.update(DELETE_ALL);
        jdbcTemplate.execute(DROP_VECTOR_INDEX);
        jdbcTemplate.execute(ALTER_DIMENSIONS.formatted(dimensions));
        if (dimensions <= HNSW_MAX_DIMENSIONS) {
            jdbcTemplate.execute(CREATE_VECTOR_INDEX);
        } else {
            log.warn(HNSW_SKIPPED, dimensions, HNSW_MAX_DIMENSIONS);
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

    private static String vectorLiteral(float[] embedding) {
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
