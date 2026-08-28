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

    // Vorher final, weshalb Lombok keinen Setter erzeugte und MapStruct das Feld nicht schreiben
    // konnte: eine Kategorie war dauerhaft aktiv und CategoryDto#active war faktisch read-only.
    @Column(name = "active", nullable = false)
    private Boolean active = true;
    // Ohne den Type-Code liegt ein Long auf bigint statt auf einer 4-Byte-Spalte.
    @Column(name = "cooldown_sec")
    private Long cooldownSec;
    // Kein orphanRemoval: es haette das Loeschen einer Kategorie zu einem stillen Loeschen aller
    // zugehoerigen Reports gemacht. reports_entity.category_entity_id ist NOT NULL, ein Abhaengen
    // ist also nicht moeglich - CategoryService lehnt das Loeschen stattdessen mit 409 ab.
    @OneToMany(mappedBy = "categoryEntity")
    private Set<ReportsEntity> reportsEntities = new LinkedHashSet<>();

}