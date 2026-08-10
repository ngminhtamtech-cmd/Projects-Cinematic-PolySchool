package com.mycompany.website.ban.ve.xem.phim.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("it")
@DisplayName("SQL diagram versus physical schema contract")
public class SqlDiagramSchemaContractIT {

    private static final Path OVERVIEW = Path.of(
            "docs", "diagrams", "sources", "ERD_01_TongQuan.puml");
    private static final Path MODULES = Path.of(
            "docs", "diagrams", "sources", "ERD_02_CINEBOOK_MODULE.puml");
    private static final Pattern ENTITY = Pattern.compile(
            "(?m)^entity\\s+(?:\"([^\"]+)\"\\s+as\\s+)?([A-Za-z][A-Za-z0-9_]*)\\s*\\{");
    private static final Pattern RELATION = Pattern.compile(
            "(?m)^([A-Za-z][A-Za-z0-9_]*)\\s+\\S*(?:--|\\.\\.)\\S*\\s+"
                    + "([A-Za-z][A-Za-z0-9_]*)\\s*:");

    static {
        System.setProperty("cinebook.db.config",
                System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    @BeforeAll
    static void configure() {
        DBConnection.shutdown();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("DIAGRAM-SCHEMA-001: ERD overview contains every and only live table")
    void overviewTablesMatchPhysicalDatabase() throws Exception {
        Set<String> diagramTables = entities(Files.readString(OVERVIEW, StandardCharsets.UTF_8));
        Set<String> databaseTables = new HashSet<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement query = connection.prepareStatement("SELECT name FROM sys.tables");
             ResultSet result = query.executeQuery()) {
            while (result.next()) databaseTables.add(result.getString(1));
        }
        assertEquals(databaseTables, diagramTables,
                "ERD_01_TongQuan.puml must change in the same delivery as the physical schema");
    }

    @Test
    @DisplayName("DIAGRAM-SCHEMA-002: every physical FK table-pair appears in the module ERD")
    void physicalForeignKeyPairsAppearInModuleDiagram() throws Exception {
        String modules = Files.readString(MODULES, StandardCharsets.UTF_8);
        Set<String> diagramPairs = new HashSet<>();
        Matcher relation = RELATION.matcher(modules);
        while (relation.find()) diagramPairs.add(relation.group(1) + "->" + relation.group(2));

        Set<String> databasePairs = new HashSet<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement query = connection.prepareStatement("""
                     SELECT referenced.name AS ParentTable, parent.name AS ChildTable
                     FROM sys.foreign_keys fk
                     JOIN sys.tables parent ON parent.object_id=fk.parent_object_id
                     JOIN sys.tables referenced ON referenced.object_id=fk.referenced_object_id
                     """);
             ResultSet result = query.executeQuery()) {
            while (result.next()) databasePairs.add(result.getString(1) + "->" + result.getString(2));
        }
        assertTrue(diagramPairs.containsAll(databasePairs),
                "Missing physical FK pairs in ERD: " + difference(databasePairs, diagramPairs));
    }

    @Test
    @DisplayName("DIAGRAM-SCHEMA-003: latest schema deltas are documented")
    void latestMigrationDeltasAreDocumented() throws Exception {
        String modules = Files.readString(MODULES, StandardCharsets.UTF_8);
        assertTrue(modules.contains("IsUserHidden : BIT") && modules.contains("[fix39]"),
                "ERD is missing Orders.IsUserHidden from fix39");
        assertTrue(modules.contains("'active','inactive','deleted'") && modules.contains("[fix40]"),
                "ERD is missing the Rooms.Status deleted state from fix40");
    }

    private static Set<String> entities(String source) {
        Set<String> result = new HashSet<>();
        Matcher entity = ENTITY.matcher(source);
        while (entity.find()) result.add(entity.group(1) == null ? entity.group(2) : entity.group(1));
        return result;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.removeAll(right);
        return result;
    }
}
