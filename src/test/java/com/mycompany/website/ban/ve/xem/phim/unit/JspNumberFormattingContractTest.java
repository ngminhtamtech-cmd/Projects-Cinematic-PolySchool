package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JSP number formatting contract")
class JspNumberFormattingContractTest {

    private static final Path VIEW_ROOT = Path.of("src", "main", "webapp", "WEB-INF", "views");

    @Test
    @DisplayName("Business views do not use the broken JSTL fmt:formatNumber tag")
    void businessViewsUseTheVerifiedCineBookFormatter() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(VIEW_ROOT)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(item -> item.toString().endsWith(".jsp"))
                    .toList()) {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (int index = 0; index < lines.size(); index++) {
                    if (lines.get(index).contains("<fmt:formatNumber")) {
                        violations.add(VIEW_ROOT.relativize(path) + ":" + (index + 1));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "fmt:formatNumber renders raw BigDecimal values in this deployment; use cbf:whole/decimal: "
                        + violations);
    }
}
