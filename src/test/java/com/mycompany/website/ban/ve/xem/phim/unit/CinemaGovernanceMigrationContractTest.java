package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cinema governance migration contracts")
class CinemaGovernanceMigrationContractTest {

    @Test
    @DisplayName("fix43 restores history without granting a current cinema-film assignment")
    void historicalShowtimeRecoveryStaysInactive() throws IOException {
        String migration = Files.readString(
                Path.of("database", "fix43_cinema_governance_core.sql"),
                StandardCharsets.UTF_8);

        assertTrue(migration.contains("s.EndTime IS NULL OR s.EndTime>=SYSDATETIME()"));
        assertTrue(migration.contains("s.EndTime<SYSDATETIME()"));
        assertTrue(migration.contains("s.CinemaId, s.FilmId, N''inactive''"));
        assertTrue(migration.contains("A current or future showtime film is not assigned"));
        assertTrue(migration.contains("FK_Showtimes_CinemaFilm"));
    }
}
