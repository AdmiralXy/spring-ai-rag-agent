package io.github.admiralxy.agent.entity.converter;

import io.github.admiralxy.agent.domain.ChatAttachment;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Converter
public class ChatAttachmentsConverter implements AttributeConverter<List<ChatAttachment>, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<ChatAttachment>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<ChatAttachment> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public List<ChatAttachment> convertToEntityAttribute(String dbData) {
        if (StringUtils.isBlank(dbData)) {
            return null;
        }
        return MAPPER.readValue(dbData, TYPE);
    }
}
