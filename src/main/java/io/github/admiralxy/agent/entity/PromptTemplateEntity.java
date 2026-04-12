package io.github.admiralxy.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "t_prompt_templates")
@Getter
@Setter
public class PromptTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "c_id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "c_name", nullable = false)
    private String name;

    @Column(name = "c_content", nullable = false)
    private String content;
}
