package io.github.admiralxy.agent.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Converter
public class ListStringConverter implements AttributeConverter<List<String>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        List<String> normalized = normalize(attribute);
        return normalized.isEmpty() ? null : String.join(SEPARATOR, normalized);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (StringUtils.isBlank(dbData)) {
            return Collections.emptyList();
        }
        return normalize(Arrays.stream(dbData.split(SEPARATOR)).toList());
    }

    private List<String> normalize(List<String> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            return Collections.emptyList();
        }
        return spaces.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }
}
