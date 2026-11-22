package io.github.admiralxy.agent;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbChangelogTest {

    private static final String MASTER = "db/changelog/db.changelog-master.yaml";

    @Test
    void changelogParsesWithoutDatabase() throws Exception {
        ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor();
        DatabaseChangeLog changeLog = ChangeLogParserFactory.getInstance().getParser(MASTER, accessor)
                .parse(MASTER, new ChangeLogParameters(), accessor);

        List<ChangeSet> changeSets = changeLog.getChangeSets();
        assertEquals(15, changeSets.size());
        assertTrue(changeSets.stream().allMatch(changeSet -> !changeSet.getChanges().isEmpty()));
        assertEquals("add-pgvector-extension", changeSets.getFirst().getId());
    }
}
