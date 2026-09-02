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
    // No @GeneratedValue: a Minecraft server registers under its own configured UUID. A generator
    // would overwrite the UUID sent by the client while persisting, even though
    // ServerDto#serverUUID is @NotNull and the client already knows it.
    @Column(name = "server_uuid", nullable = false)
    private UUID serverUuid;

    @Column(name = "ip_address", nullable = false)
    private InetAddress ipAddress;

    @Column(name = "port", nullable = false)
    private Integer port;

    // No orphanRemoval: reports_entity.server is nullable, so a deleted server detaches its
    // reports instead of dragging them along.
    @OneToMany(mappedBy = "server")
    private Set<ReportsEntity> reportsEntitiesEntities = new LinkedHashSet<>();
}