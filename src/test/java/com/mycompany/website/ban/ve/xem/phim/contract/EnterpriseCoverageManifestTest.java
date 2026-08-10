package com.mycompany.website.ban.ve.xem.phim.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Mechanical guard for the contract-first inventory. A newly mapped route or
 * persisted table cannot silently bypass the enterprise test matrix.
 */
@DisplayName("Enterprise route/table coverage manifest")
@Tag("it")
public class EnterpriseCoverageManifestTest {
    private static final File MANIFEST =
            new File("src/test/resources/enterprise-flow-coverage.tsv");
    private static final Pattern LITERAL_JSP_ACTION = Pattern.compile(
            "(?is)<(?:input|button)\\b[^>]*\\bname\\s*=\\s*[\\\"']action[\\\"']"
                    + "[^>]*?\\bvalue\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*>");

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    @BeforeAll
    static void configureDatabase() {
        DBConnection.shutdown();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("FLOW-COVERAGE-001: every servlet mapping has role, flow and evidence")
    void everyServletMappingIsInManifest() throws Exception {
        List<Row> rows = readManifest();
        Set<String> coveredRoutes = artifacts(rows, "ROUTE");
        Set<String> actualRoutes = new HashSet<>();

        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder()
                .parse(new File("src/main/webapp/WEB-INF/web.xml"));
        NodeList mappings = document.getElementsByTagName("servlet-mapping");
        for (int index = 0; index < mappings.getLength(); index++) {
            Element mapping = (Element) mappings.item(index);
            NodeList patterns = mapping.getElementsByTagName("url-pattern");
            for (int patternIndex = 0; patternIndex < patterns.getLength(); patternIndex++) {
                actualRoutes.add(patterns.item(patternIndex).getTextContent().trim());
            }
        }

        assertEquals(actualRoutes, coveredRoutes,
                "Update enterprise-flow-coverage.tsv whenever web.xml routing changes");
    }

    @Test
    @DisplayName("FLOW-COVERAGE-002: every live database table has role, flow and evidence")
    void everyDatabaseTableIsInManifest() throws Exception {
        List<Row> rows = readManifest();
        Set<String> coveredTables = artifacts(rows, "TABLE");
        Set<String> actualTables = new HashSet<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement database = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet databaseResult = database.executeQuery()) {
            assertTrue(databaseResult.next());
            assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), databaseResult.getString(1));
        }
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement tables = connection.prepareStatement(
                     "SELECT name FROM sys.tables");
             ResultSet result = tables.executeQuery()) {
            while (result.next()) {
                actualTables.add(result.getString(1));
            }
        }

        assertEquals(actualTables, coveredTables,
                "Update enterprise-flow-coverage.tsv whenever the physical schema changes");
    }

    @Test
    @DisplayName("FLOW-COVERAGE-003: every literal JSP action has role, flow and evidence")
    void everyLiteralJspActionIsInManifest() throws Exception {
        Set<String> coveredActions = artifacts(readManifest(), "UI_ACTION");
        Set<String> actualActions = new HashSet<>();
        var root = new File("src/main/webapp/WEB-INF/views").toPath();
        try (var views = Files.walk(root)) {
            for (var view : views.filter(path -> path.toString().endsWith(".jsp")).toList()) {
                Matcher matcher = LITERAL_JSP_ACTION.matcher(Files.readString(view, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String relative = root.relativize(view).toString().replace(File.separatorChar, '/');
                    actualActions.add(relative + "#" + matcher.group(1));
                }
            }
        }

        assertEquals(actualActions, coveredActions,
                "Update enterprise-flow-coverage.tsv whenever a JSP action is added or removed");
    }

    @Test
    @DisplayName("FLOW-COVERAGE-004: manifest rows are actionable contracts")
    void everyManifestRowHasActionableMetadata() throws Exception {
        List<Row> rows = readManifest();
        assertFalse(rows.isEmpty());
        for (Row row : rows) {
            assertFalse(row.artifact().isBlank(), "blank artifact");
            assertFalse(row.role().isBlank(), "blank role for " + row.artifact());
            assertTrue(row.flowId().startsWith("FLOW-"), "invalid flow id for " + row.artifact());
            assertFalse(row.evidence().isBlank(), "missing test evidence for " + row.artifact());
        }
    }

    private Set<String> artifacts(List<Row> rows, String kind) {
        Set<String> result = new HashSet<>();
        rows.stream().filter(row -> kind.equals(row.kind())).forEach(row -> {
            assertTrue(result.add(row.artifact()),
                    "duplicate " + kind + " artifact in manifest: " + row.artifact());
        });
        return result;
    }

    private List<Row> readManifest() throws Exception {
        List<String> lines = Files.readAllLines(MANIFEST.toPath(), StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty(), "coverage manifest is empty");
        assertEquals("kind\tartifact\trole\tflowId\tevidence", lines.get(0));
        List<Row> result = new ArrayList<>();
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("\t", -1);
            assertEquals(5, columns.length,
                    "manifest line " + (lineNumber + 1) + " must have exactly five TSV columns");
            result.add(new Row(columns[0], columns[1], columns[2], columns[3], columns[4]));
        }
        return result;
    }

    private record Row(String kind, String artifact, String role, String flowId, String evidence) {
    }
}
