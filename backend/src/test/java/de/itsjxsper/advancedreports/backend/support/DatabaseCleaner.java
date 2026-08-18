package de.itsjxsper.advancedreports.backend.support;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Truncates every application table between tests.
 * <p>
 * The table list is read from {@code information_schema} rather than hard-coded, so a new entity
 * does not silently leak rows into the next test. Note that the schemas differ per entity:
 * {@code PlayerEntity} lives in {@code advancedreports} while everything else is in {@code public}.
 */
public final class DatabaseCleaner {

    private DatabaseCleaner() {
    }

    public static void clean(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            List<String> tables = collectTables(statement);
            if (tables.isEmpty()) {
                return;
            }

            statement.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clean the test database", e);
        }
    }

    private static List<String> collectTables(Statement statement) throws SQLException {
        List<String> tables = new ArrayList<>();

        try (ResultSet resultSet = statement.executeQuery("""
                select table_schema, table_name
                from information_schema.tables
                where table_type = 'BASE TABLE'
                  and table_schema not in ('pg_catalog', 'information_schema')
                """)) {

            while (resultSet.next()) {
                tables.add("\"" + resultSet.getString(1) + "\".\"" + resultSet.getString(2) + "\"");
            }
        }

        return tables;
    }
}
