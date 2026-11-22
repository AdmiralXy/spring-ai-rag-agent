package io.github.admiralxy.agent.service.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenAiBaseUrlsTest {

    @ParameterizedTest
    @CsvSource({
        "https://api.openai.com, https://api.openai.com/v1",
        "https://api.openai.com/, https://api.openai.com/v1",
        "https://api.deepseek.com, https://api.deepseek.com/v1",
        "https://openrouter.ai/api, https://openrouter.ai/api/v1",
        "http://localhost:11434, http://localhost:11434/v1",
        "https://api.openai.com/v1, https://api.openai.com/v1",
        "https://api.openai.com/v1/, https://api.openai.com/v1",
        "https://generativelanguage.googleapis.com/v1beta, https://generativelanguage.googleapis.com/v1beta",
        "https://example.com/proxy/v2, https://example.com/proxy/v2"
    })
    void appendsVersionWhenMissing(String input, String expected) {
        assertEquals(expected, OpenAiBaseUrls.normalize(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void blankMeansSdkDefault(String input) {
        assertNull(OpenAiBaseUrls.normalize(input));
    }
}
