package io.github.admiralxy.agent.entity.converter;

import io.github.admiralxy.agent.domain.ChatMessagePart;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Converter
public class ChatMessagePartsConverter implements AttributeConverter<List<ChatMessagePart>, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<ChatMessagePart>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<ChatMessagePart> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public List<ChatMessagePart> convertToEntityAttribute(String dbData) {
        if (StringUtils.isBlank(dbData)) {
            return null;
        }
        return MAPPER.readValue(dbData, TYPE);
    }
}
