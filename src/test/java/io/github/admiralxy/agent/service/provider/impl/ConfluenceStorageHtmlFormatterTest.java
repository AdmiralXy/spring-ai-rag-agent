package io.github.admiralxy.agent.service.provider.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfluenceStorageHtmlFormatterTest {

    private final ConfluenceStorageHtmlFormatter formatter = new ConfluenceStorageHtmlFormatter();

    @Test
    void format_shouldPreserveUsefulStructureForRag() {
        String html = """
                <h2>Release Notes</h2>
                <p>Summary paragraph.</p>
                <ul><li>First item</li><li>Second item</li></ul>
                <table>
                  <tr><th>Service</th><th>Status</th></tr>
                  <tr><td>Auth</td><td>OK</td></tr>
                </table>
                <pre>curl -X GET /health</pre>
                """;

        String expected = """
                ## Release Notes

                Summary paragraph.

                - First item
                - Second item

                | Service | Status |
                | Auth | OK |

                ```
                curl -X GET /health
                ```
                """.trim();

        assertEquals(expected, formatter.format(html));
    }

    @Test
    void format_shouldReturnEmptyForBlankInput() {
        assertEquals("", formatter.format(" "));
    }
}
