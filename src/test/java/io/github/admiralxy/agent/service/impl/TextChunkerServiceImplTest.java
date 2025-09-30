package io.github.admiralxy.agent.service.impl;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerServiceImplTest {

    private final TextChunkerServiceImpl service = new TextChunkerServiceImpl();

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("cases")
    void chunk(String inputFile, String expectedFile) throws Exception {
        String inputText = readText("/input/%s".formatted(inputFile));
        List<String> actual = service.chunk(inputText, 100, 1500, 50);
        List<String> expected = readExpectedChunks("/expected/%s".formatted(expectedFile));

        Path outDir = Paths.get("build");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("expected-%s.txt".formatted(inputFile));

        String joined = String.join("\n---\n", actual);
        Files.writeString(outFile, joined);

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    private static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("input_1.txt", "expected_1.txt"),
                Arguments.of("input_2.txt", "expected_2.txt"),
                Arguments.of("input_3.txt", "expected_3.txt")
        );
    }

    private static String readText(String resourcePath) {
        if (resourcePath == null) {
            return null;
        }
        try (InputStream is = TextChunkerServiceImplTest.class.getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(is, "Resource not found: " + resourcePath);
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read: " + resourcePath, e);
        }
    }

    private static List<String> readExpectedChunks(String resourcePath) {
        try (InputStream is = TextChunkerServiceImplTest.class.getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(is, "Resource not found: " + resourcePath);
            String full = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            return Stream.of(full.split("\\R---\\R", -1))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read expected: " + resourcePath, e);
        }
    }
}
