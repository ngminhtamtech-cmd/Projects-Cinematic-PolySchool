package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mycompany.website.ban.ve.xem.phim.util.AssetUrlHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Context-safe asset URLs")
class AssetUrlHelperTest {

    private static final String CONTEXT = "/Website-ban-ve-xem-phim";

    @Test
    @DisplayName("Local root-relative and relative media paths stay inside the servlet context")
    void localMediaPathsGainExactlyOneContextPrefix() {
        assertEquals(CONTEXT + "/assets/img/default-film.jpg",
                AssetUrlHelper.withContext(CONTEXT, "/assets/img/default-film.jpg"));
        assertEquals(CONTEXT + "/uploads/poster.png",
                AssetUrlHelper.withContext(CONTEXT, "uploads/poster.png"));
        assertEquals(CONTEXT + "/uploads/poster.png",
                AssetUrlHelper.withContext(CONTEXT, CONTEXT + "/uploads/poster.png"));
    }

    @Test
    @DisplayName("HTTP(S) media remains absolute and unsupported schemes fail closed")
    void externalMediaIsLimitedToWebUrls() {
        assertEquals("https://cdn.example/poster.png",
                AssetUrlHelper.withContext(CONTEXT, "https://cdn.example/poster.png"));
        assertEquals("//cdn.example/poster.png",
                AssetUrlHelper.withContext(CONTEXT, "//cdn.example/poster.png"));
        assertEquals("", AssetUrlHelper.withContext(CONTEXT, "javascript:alert(1)"));
        assertEquals("", AssetUrlHelper.withContext(CONTEXT, "data:text/html,boom"));
    }

    @Test
    @DisplayName("Blank context and media values are deterministic")
    void blankInputsAreHandledWithoutNullOutput() {
        assertEquals("/assets/poster.png",
                AssetUrlHelper.withContext("/", "/assets/poster.png"));
        assertEquals("", AssetUrlHelper.withContext(CONTEXT, null));
        assertEquals("", AssetUrlHelper.withContext(CONTEXT, "  "));
    }
}
