package de.itsjxsper.advancedreports.backend.category.data.entity;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "categories_entity")
public class CategoryEntity {
    @Column(name = "active")
    private final Boolean active = true;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    @Column(name = "name", nullable = false, unique = true, length = 64)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private String name;
    @Column(name = "displayname", nullable = false, length = 64)
    private String displayName;
    @Column(name = "description")
    private String description;
    @Column(name = "cooldown_sec")
    @JdbcTypeCode(SqlTypes.INTEGER)
    private Long cooldownSec;
    @OneToMany(mappedBy = "categoryEntity", orphanRemoval = true)
    private Set<ReportsEntity> reportsEntities = new LinkedHashSet<>();

}