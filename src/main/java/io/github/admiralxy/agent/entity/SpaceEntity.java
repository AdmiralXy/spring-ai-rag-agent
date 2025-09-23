package io.github.admiralxy.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_spaces")
@Getter
@Setter
public class SpaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "c_id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "c_name", nullable = false)
    private String name;

    @CreationTimestamp
    @Column(name = "c_created_at", nullable = false)
    private Instant createdAt;
}
