package com.mycompany.website.ban.ve.xem.phim.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Admin transaction and query safety contracts")
class AdminTransactionAuditContractTest {

    private static final Path ADMIN_SERVICE = Path.of(
            "src", "main", "java", "com", "mycompany", "website", "ban", "ve", "xem", "phim",
            "service", "AdminService.java");

    @Test
    @DisplayName("Audit writes happen only after the owning transaction commits")
    void transactionalAdminMutationsDoNotOpenAuditConnectionsBeforeCommit() throws IOException {
        String source = Files.readString(ADMIN_SERVICE, StandardCharsets.UTF_8);
        assertPostCommitAudit(source, "public void saveFilm(Film film, User actor, List<Integer> cinemaIds)");
        assertPostCommitAudit(source, "public void saveCustomRoomSeats(int roomId, List<Seat> seats, User actor)");
        assertPostCommitAudit(source, "public void saveRoom(Room room, int rowCount, int seatsPerRow");
        assertPostCommitAudit(source, "public boolean deletePromotion(int promotionId, User actor)");
        assertPostCommitAudit(source, "public void lockUserForComment(int commentId, String reason, User actor)");
        assertPostCommitAudit(source, "public AppealResolutionResult resolveAppeal(");
    }

    @Test
    @DisplayName("Combo aggregation uses an explicit projection instead of SELECT c.*")
    void comboAggregationHasStableExplicitProjection() throws IOException {
        String source = Files.readString(ADMIN_SERVICE, StandardCharsets.UTF_8);
        String body = methodBody(source, "public List<ComboFood> listCombos(User actor)");
        assertFalse(body.contains("SELECT c.*"),
                "listCombos groups explicit columns, so SELECT c.* will break when ComboFoods gains a column");
    }

    private static void assertPostCommitAudit(String source, String signature) {
        String body = methodBody(source, signature);
        assertFalse(body.contains("logAction("), signature
                + " opens a second connection while the business transaction is still holding locks");
        assertTrue(body.contains("auditAfterCommit("), signature
                + " must retain an audit event after the business transaction commits");
    }

    private static String methodBody(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        assertTrue(signatureStart >= 0, "Cannot find method signature: " + signature);
        int openBrace = source.indexOf('{', signatureStart);
        assertTrue(openBrace >= 0, "Cannot find method body: " + signature);
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, index);
                }
            }
        }
        throw new AssertionError("Unclosed method body: " + signature);
    }
}
