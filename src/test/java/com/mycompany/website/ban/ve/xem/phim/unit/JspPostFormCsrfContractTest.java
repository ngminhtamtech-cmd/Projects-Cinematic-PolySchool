package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JSP POST forms submit a CSRF token")
class JspPostFormCsrfContractTest {

    private static final Path WEB_ROOT = Path.of("src", "main", "webapp");
    private static final Pattern POST_FORM = Pattern.compile(
            "<form\\b(?=[^>]*\\bmethod\\s*=\\s*[\\\"']post[\\\"'])[^>]*>.*?</form>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    @DisplayName("Every server-rendered POST form contains the shared token field")
    void everyPostFormContainsCsrfToken() throws IOException {
        try (Stream<Path> paths = Files.walk(WEB_ROOT)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(JspPostFormCsrfContractTest::isJspSource)
                    .toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                Matcher matcher = POST_FORM.matcher(source);
                while (matcher.find()) {
                    String form = matcher.group();
                    assertTrue(hasToken(form), () -> WEB_ROOT.relativize(path) + ":"
                            + lineNumber(source, matcher.start())
                            + " POST form must render the shared CSRF token");
                }
            }
        }
    }

    private static boolean isJspSource(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".jsp") || name.endsWith(".jspf");
    }

    private static boolean hasToken(String form) {
        return form.contains("<cb:csrf")
                || form.contains("/WEB-INF/views/shared/csrf.jspf");
    }

    private static int lineNumber(String source, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) {
            if (source.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }
}
