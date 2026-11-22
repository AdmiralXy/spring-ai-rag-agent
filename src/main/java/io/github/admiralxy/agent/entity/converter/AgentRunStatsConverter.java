package io.github.admiralxy.agent.entity.converter;

import io.github.admiralxy.agent.domain.AgentRunStats;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.json.JsonMapper;

@Converter
public class AgentRunStatsConverter implements AttributeConverter<AgentRunStats, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    public String convertToDatabaseColumn(AgentRunStats attribute) {
        return attribute == null ? null : MAPPER.writeValueAsString(attribute);
    }

    @Override
    public AgentRunStats convertToEntityAttribute(String dbData) {
        return StringUtils.isBlank(dbData) ? null : MAPPER.readValue(dbData, AgentRunStats.class);
    }
}
