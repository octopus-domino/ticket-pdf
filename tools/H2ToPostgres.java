import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Offline, transactional transfer. Never creates, truncates or overwrites a target table. */
public class H2ToPostgres {
    record Column(String source, String target, int type, String typeName) {}
    static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
    static String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }
    public static void main(String[] args) throws Exception {
        String sourceUrl = env("H2_URL", "jdbc:h2:file:/legacy/ticket-db;IFEXISTS=TRUE;ACCESS_MODE_DATA=r;FILE_LOCK=NO");
        if (!sourceUrl.startsWith("jdbc:h2:") || !sourceUrl.contains("IFEXISTS=TRUE") || !sourceUrl.contains("ACCESS_MODE_DATA=r"))
            throw new IllegalArgumentException("H2_URL must specify IFEXISTS=TRUE and ACCESS_MODE_DATA=r");
        try (Connection source = DriverManager.getConnection(sourceUrl, env("H2_USER", "sa"), env("H2_PASSWORD", ""));
             Connection target = DriverManager.getConnection(env("DATABASE_URL", "jdbc:postgresql://postgres:5432/codoc"), env("DATABASE_USER", "codoc"), env("DATABASE_PASSWORD", ""))) {
            source.setReadOnly(true);
            target.setAutoCommit(false);
            try {
                var sourceTables = new LinkedHashMap<String, String>();
                try (var tables = source.getMetaData().getTables(null, "PUBLIC", "%", new String[]{"TABLE"})) {
                    while (tables.next()) sourceTables.put(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT), tables.getString("TABLE_NAME"));
                }
                if (sourceTables.isEmpty()) throw new IllegalStateException("No H2 tables found");
                var targetTables = new HashSet<String>();
                try (var tables = target.getMetaData().getTables(null, "public", "%", new String[]{"TABLE"})) {
                    while (tables.next()) targetTables.add(tables.getString("TABLE_NAME"));
                }
                if (!targetTables.containsAll(sourceTables.keySet())) throw new IllegalStateException("Initialize the empty PostgreSQL schema with the application before migration");
                var pending = new LinkedHashSet<>(sourceTables.keySet());
                var ordered = new ArrayList<String>();
                while (!pending.isEmpty()) {
                    boolean progress = false;
                    for (String table : List.copyOf(pending)) {
                        boolean blocked = false;
                        try (var keys = target.getMetaData().getImportedKeys(null, "public", table)) {
                            while (keys.next()) {
                                String parent = keys.getString("PKTABLE_NAME");
                                if (!parent.equals(table) && pending.contains(parent)) blocked = true;
                            }
                        }
                        if (!blocked) { ordered.add(table); pending.remove(table); progress = true; }
                    }
                    if (!progress) throw new IllegalStateException("Cyclic foreign keys require a reviewed migration plan");
                }
                for (String table : ordered) {
                    try (var sql = target.createStatement()) {
                        sql.execute("LOCK TABLE " + quote(table) + " IN ACCESS EXCLUSIVE MODE");
                        try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + quote(table))) {
                            rows.next();
                            if (rows.getLong(1) != 0) throw new IllegalStateException("Target is not empty: " + table);
                        }
                    }
                }
                for (String table : ordered) copyTable(source, target, sourceTables.get(table), table);
                target.commit();
                System.out.println("Migration committed. Source H2 was opened read-only.");
            } catch (Exception failure) { target.rollback(); throw failure; }
        }
    }
    static void copyTable(Connection source, Connection target, String sourceTable, String table) throws SQLException {
        var sourceColumns = new HashMap<String, String>();
        try (var columns = source.getMetaData().getColumns(null, "PUBLIC", sourceTable, "%")) {
            while (columns.next()) sourceColumns.put(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), columns.getString("COLUMN_NAME"));
        }
        var columns = new ArrayList<Column>();
        try (var metadata = target.getMetaData().getColumns(null, "public", table, "%")) {
            while (metadata.next()) {
                String name = metadata.getString("COLUMN_NAME");
                if (sourceColumns.containsKey(name)) columns.add(new Column(sourceColumns.get(name), name, metadata.getInt("DATA_TYPE"), metadata.getString("TYPE_NAME")));
            }
        }
        if (columns.size() != sourceColumns.size()) throw new IllegalStateException("Column mismatch in " + table);
        String select = "SELECT " + String.join(",", columns.stream().map(c -> quote(c.source())).toList()) + " FROM " + quote(sourceTable);
        String insert = "INSERT INTO " + quote(table) + " (" + String.join(",", columns.stream().map(c -> quote(c.target())).toList()) + ") OVERRIDING SYSTEM VALUE VALUES (" + String.join(",", Collections.nCopies(columns.size(), "?")) + ")";
        long copied = 0;
        try (var read = source.createStatement(); var write = target.prepareStatement(insert)) {
            read.setFetchSize(100);
            try (var rows = read.executeQuery(select)) {
                while (rows.next()) {
                    for (int i = 0; i < columns.size(); i++) {
                        var column = columns.get(i);
                        Object value = rows.getObject(i + 1);
                        if (value == null) write.setNull(i + 1, column.type());
                        else if (column.typeName().equals("json") || column.typeName().equals("jsonb")) {
                            String json = value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : rows.getString(i + 1);
                            write.setObject(i + 1, json, Types.OTHER);
                        } else if (value instanceof Clob clob) write.setString(i + 1, clob.getSubString(1, Math.toIntExact(clob.length())));
                        else if (value instanceof Blob blob) write.setBytes(i + 1, blob.getBytes(1, Math.toIntExact(blob.length())));
                        else write.setObject(i + 1, value);
                    }
                    write.addBatch();
                    if (++copied % 100 == 0) write.executeBatch();
                }
                write.executeBatch();
            }
        }
        try (var sql = target.createStatement(); var count = sql.executeQuery("SELECT COUNT(*) FROM " + quote(table))) {
            count.next();
            if (count.getLong(1) != copied) throw new IllegalStateException("Row-count mismatch in " + table);
        }
        System.out.println(table + ": " + copied + " rows verified");
    }
}
