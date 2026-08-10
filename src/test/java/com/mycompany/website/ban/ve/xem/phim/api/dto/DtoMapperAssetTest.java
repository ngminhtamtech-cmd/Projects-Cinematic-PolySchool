package com.mycompany.website.ban.ve.xem.phim.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("REST asset path normalization")
class DtoMapperAssetTest {

    @Test
    @DisplayName("D.5: legacy context-prefixed uploads become context-independent API paths")
    void stripsLegacyContextFromUploadedAssets() {
        assertEquals(
                "/uploads/poster.png",
                DtoMapper.asset("/Website-ban-ve-xem-phim/uploads/poster.png"));
    }

    @Test
    @DisplayName("D.5: canonical upload and asset paths remain stable")
    void keepsCanonicalAssetsStable() {
        assertEquals("/uploads/poster.png", DtoMapper.asset("/uploads/poster.png"));
        assertEquals("/assets/img/default-film.jpg", DtoMapper.asset("/assets/img/default-film.jpg"));
    }
}
