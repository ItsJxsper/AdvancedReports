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

    // Previously final, so Lombok generated no setter and MapStruct could not write the field:
    // a category was permanently active and CategoryDto#active was effectively read-only.
    @Column(name = "active", nullable = false)
    private Boolean active = true;
    // Without the type code a Long sits on bigint instead of a 4-byte column.
    @Column(name = "cooldown_sec")
    private Long cooldownSec;
    // No orphanRemoval: it would have turned deleting a category into a silent deletion of every
    // report attached to it. reports_entity.category_entity_id is NOT NULL, so detaching is not
    // possible - CategoryService refuses the deletion with 409 instead.
    @OneToMany(mappedBy = "categoryEntity")
    private Set<ReportsEntity> reportsEntities = new LinkedHashSet<>();

}