package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.RagSearchHit;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagContextAssemblerTest {

    private final RagContextAssembler assembler = new RagContextAssembler(new TokenizerServiceImpl());

    @Test
    void addsNeighboursOfTheBestHitAndRemovesTheChunkOverlap() {
        RagSearchHit best = hit("2", "Guide", 1, "Source: Guide\n\nstep two continues here. Step three");
        RagSearchHit previous = hit("1", "Guide", 0, "Source: Guide\n\nStep one. step two continues here.");
        AtomicReference<Set<Integer>> requested = new AtomicReference<>();

        String context = assembler.assemble(List.of(best), (hit, parts) -> {
            requested.set(parts);
            return List.of(previous);
        }, Map.of("space-1", "Docs"), 1000, 3);

        assertEquals(Set.of(0, 2), requested.get());
        assertTrue(context.contains("source=\"Guide\" space=\"Docs\""));
        assertTrue(context.contains("Step one. step two continues here.\n Step three"));
        assertEquals(1, context.split("step two continues here", -1).length - 1);
    }

    @Test
    void cutsSingleOversizedChunkInsteadOfReturningNothing() {
        RagSearchHit huge = hit("1", null, 0, "word ".repeat(500));

        String context = assembler.assemble(List.of(huge), (hit, parts) -> List.of(), Map.of(), 50, 0);

        assertFalse(context.isEmpty());
        assertTrue(new TokenizerServiceImpl().countTokens(context) < 120);
    }

    @Test
    void emptyWithoutHits() {
        assertEquals("", assembler.assemble(List.of(), (hit, parts) -> List.of(), Map.of(), 1000, 3));
    }

    @Test
    void overlapFindsTheRepeatedPrefix() {
        assertEquals("gamma delta epsilon".length(),
                RagContextAssembler.overlap("alpha beta gamma delta epsilon", "gamma delta epsilon zeta"));
        assertEquals(0, RagContextAssembler.overlap("abc", "xyz"));
    }

    private static RagSearchHit hit(String id, String source, int part, String content) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("space", "space-1");
        metadata.put("doc", "doc-1");
        if (source != null) {
            metadata.put("source", source);
            metadata.put("part", part);
        }
        return new RagSearchHit(id, content, metadata);
    }
}
