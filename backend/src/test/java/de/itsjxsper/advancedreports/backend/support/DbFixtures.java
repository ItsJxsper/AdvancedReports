package de.itsjxsper.advancedreports.backend.support;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Inserts server rows directly, bypassing the REST API.
 * <p>
 * Only used for servers: seeding them over REST would couple unrelated tests to the server slice (see
 * {@code ServerE2ETest}), and reports reference a server. Going through SQL here keeps the report
 * end-to-end tests meaningful instead of blocking them on an unrelated defect.
 */
public final class DbFixtures {

    private DbFixtures() {
    }

    public static UUID insertServer(DataSource dataSource) {
        UUID serverUuid = UUID.randomUUID();

        try (Connection connection = dataSource.getConnection();
             // Hibernate maps InetAddress to the Postgres type "inet", so a String parameter has to
             // be cast explicitly.
             PreparedStatement statement = connection.prepareStatement(
                     "insert into server_entity (server_uuid, ip_address, port) "
                             + "values (?, cast(? as inet), ?)")) {

            statement.setObject(1, serverUuid);
            statement.setString(2, "127.0.0.1");
            statement.setInt(3, 25565);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert a test server", e);
        }

        return serverUuid;
    }
}
