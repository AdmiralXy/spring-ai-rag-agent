package io.github.admiralxy.agent.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagDocumentRepositoryTest {

    @Test
    void comparesPgvectorVersions() {
        assertTrue(RagDocumentRepository.isAtLeast("0.8.0", 0, 8));
        assertTrue(RagDocumentRepository.isAtLeast("1.0", 0, 8));
        assertFalse(RagDocumentRepository.isAtLeast("0.7.4", 0, 8));
        assertFalse(RagDocumentRepository.isAtLeast("dev", 0, 8));
    }
}
