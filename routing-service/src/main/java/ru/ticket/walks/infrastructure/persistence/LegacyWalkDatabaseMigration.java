package ru.ticket.walks.infrastructure.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.util.List;

/** Copies the two walking catalogs from a stopped legacy H2 instance into an independent database. */
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
            System.out.println("Routing database already initialized");
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
            Files.move(Path.of(stagedDatabase + ".mv.db"), database, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(staging.resolve("walks.mv.db"));
            Files.deleteIfExists(staging.resolve("walks.trace.db"));
            Files.delete(staging);
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
