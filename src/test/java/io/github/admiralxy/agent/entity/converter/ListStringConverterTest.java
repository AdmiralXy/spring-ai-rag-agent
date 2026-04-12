package io.github.admiralxy.agent.entity.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ListStringConverterTest {

    private final ListStringConverter converter = new ListStringConverter();

    @Test
    void convertToDatabaseColumnReturnsNullForEmptyList() {
        assertNull(converter.convertToDatabaseColumn(List.of()));
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void convertToDatabaseColumnNormalizesValues() {
        String dbValue = converter.convertToDatabaseColumn(List.of(" code ", "docs", "code", " "));
        assertEquals("code,docs", dbValue);
    }

    @Test
    void convertToEntityAttributeReturnsEmptyForBlank() {
        assertEquals(List.of(), converter.convertToEntityAttribute(null));
        assertEquals(List.of(), converter.convertToEntityAttribute("  "));
    }

    @Test
    void convertToEntityAttributeNormalizesValues() {
        List<String> values = converter.convertToEntityAttribute(" code , docs,code,  ");
        assertEquals(List.of("code", "docs"), values);
    }
}
