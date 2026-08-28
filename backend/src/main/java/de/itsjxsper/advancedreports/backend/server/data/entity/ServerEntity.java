package de.itsjxsper.advancedreports.backend.server.data.entity;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "server_entity")
public class ServerEntity {
    @Id
    // Kein @GeneratedValue: ein Minecraft-Server registriert sich unter seiner eigenen,
    // konfigurierten UUID. Ein Generator wuerde die mitgeschickte UUID beim Persistieren
    // ueberschreiben, obwohl ServerDto#serverUUID @NotNull ist und der Client sie kennt.
    @Column(name = "server_uuid", nullable = false)
    private UUID serverUuid;

    @Column(name = "ip_address", nullable = false)
    private InetAddress ipAddress;

    @Column(name = "port", nullable = false)
    private Integer port;

    @OneToMany(mappedBy = "server", orphanRemoval = true)
    private Set<ReportsEntity> reportsEntitiesEntities = new LinkedHashSet<>();
}