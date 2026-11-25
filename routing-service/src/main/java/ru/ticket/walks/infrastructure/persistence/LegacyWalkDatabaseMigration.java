package ru.ticket.walks.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;

/** Copies walking catalogs and saved itineraries from a stopped legacy H2 instance. */
public final class LegacyWalkDatabaseMigration {
    private static final List<String> TABLES = List.of("BUILDING_HISTORY", "MYSTIC_PLACES");

    private LegacyWalkDatabaseMigration() {}

    public static void main(String[] args) throws Exception {
        transfer(Path.of(args[0]), Path.of(args[1]), System.getenv().getOrDefault("H2_USER", "sa"),
                System.getenv().getOrDefault("H2_PASSWORD", ""));
    }

    public static void transfer(Path legacy, Path destination, String user, String password) throws Exception {
        Path database = Path.of(destination + ".mv.db");
        if (Files.exists(database)) {
            copyTripDrafts(legacy, destination, user, password);
            return;
        }

        Path staging = Files.createTempDirectory(database.toAbsolutePath().getParent(), "walks-migration-");
        try {
            Path stagedDatabase = staging.resolve("walks");
            try (var source = DriverManager.getConnection("jdbc:h2:file:" + legacy
                    + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r;FILE_LOCK=NO", user, password);
                 var target = DriverManager.getConnection("jdbc:h2:file:" + stagedDatabase, "sa", "");
                 var commands = target.createStatement()) {
                commands.execute("CREATE SCHEMA ROUTING");
                for (String table : TABLES) {
                    try (var tables = source.getMetaData().getTables(null, "PUBLIC", table, new String[]{"TABLE"})) {
                        if (!tables.next()) continue;
                    }
                    try (var read = source.createStatement();
                         var script = read.executeQuery("SCRIPT NOPASSWORDS NOSETTINGS TABLE PUBLIC." + table)) {
                        while (script.next()) {
                            String sql = script.getString(1);
                            if (sql.startsWith("--") || sql.startsWith("CREATE USER ")) continue;
                            // H2 emits INSERT statements with literal data after VALUES. Rewrite only the identifiers.
                            int values = sql.startsWith("INSERT INTO ") ? sql.indexOf(" VALUES") : -1;
                            if (values >= 0) {
                                commands.execute(inRoutingSchema(sql.substring(0, values)) + sql.substring(values));
                            } else {
                                commands.execute(inRoutingSchema(sql));
                            }
                        }
                    }
                    if (count(source, "PUBLIC", table) != count(target, "ROUTING", table)) {
                        throw new IOException("Incomplete H2 copy for " + table);
                    }
                }
            }
            copyTripDrafts(legacy, stagedDatabase, user, password);
            Files.move(Path.of(stagedDatabase + ".mv.db"), database, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(staging.resolve("walks.mv.db"));
            Files.deleteIfExists(staging.resolve("walks.trace.db"));
            Files.delete(staging);
        }
    }

    private static void copyTripDrafts(Path legacy, Path destination, String user, String password) throws Exception {
        try (var source = DriverManager.getConnection("jdbc:h2:file:" + legacy
                + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r;FILE_LOCK=NO", user, password);
             var tables = source.getMetaData().getTables(null, "PUBLIC", "JOURNEY_TRIP_DRAFTS", new String[]{"TABLE"})) {
            if (!tables.next()) return;
            try (var target = DriverManager.getConnection("jdbc:h2:file:" + destination, "sa", "");
                 var commands = target.createStatement()) {
                commands.execute("""
                    CREATE TABLE IF NOT EXISTS ROUTING.JOURNEY_TRIP_DRAFTS (
                      OWNER_ID VARCHAR(36) NOT NULL, TRIP_ID VARCHAR(64) NOT NULL, PAYLOAD TEXT NOT NULL, VERSION BIGINT,
                      TITLE VARCHAR(180), START_DATE DATE, END_DATE DATE, UPDATED_AT TIMESTAMP WITH TIME ZONE,
                      PRIMARY KEY (OWNER_ID, TRIP_ID)
                    )
                    """);
                target.setAutoCommit(false);
                var mapper = new ObjectMapper();
                try (var read = source.createStatement();
                     var rows = read.executeQuery("SELECT OWNER_ID, TRIP_ID, PAYLOAD, COALESCE(VERSION, 0) FROM PUBLIC.JOURNEY_TRIP_DRAFTS");
                     var insert = target.prepareStatement("""
                         INSERT INTO ROUTING.JOURNEY_TRIP_DRAFTS
                           (OWNER_ID, TRIP_ID, PAYLOAD, VERSION, TITLE, START_DATE, END_DATE, UPDATED_AT)
                         SELECT ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
                         WHERE NOT EXISTS (SELECT 1 FROM ROUTING.JOURNEY_TRIP_DRAFTS WHERE OWNER_ID = ? AND TRIP_ID = ?)
                         """)) {
                    while (rows.next()) {
                        String payload = rows.getString(3);
                        var trip = mapper.readTree(payload);
                        String title = trip.path("title").asText("");
                        insert.setString(1, rows.getString(1));
                        insert.setString(2, rows.getString(2));
                        insert.setString(3, payload);
                        insert.setLong(4, rows.getLong(4));
                        insert.setString(5, title.substring(0, Math.min(title.length(), 180)));
                        insert.setObject(6, LocalDate.parse(trip.path("startDate").asText()));
                        insert.setObject(7, LocalDate.parse(trip.path("endDate").asText()));
                        insert.setString(8, rows.getString(1));
                        insert.setString(9, rows.getString(2));
                        insert.executeUpdate();
                    }
                }
                target.commit();
            }
        }
    }

    private static String inRoutingSchema(String sql) {
        return sql.replace("\"PUBLIC\".", "\"ROUTING\".");
    }

    private static long count(java.sql.Connection connection, String schema, String table) throws Exception {
        try (var query = connection.createStatement();
             var rows = query.executeQuery("SELECT COUNT(*) FROM " + schema + "." + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
