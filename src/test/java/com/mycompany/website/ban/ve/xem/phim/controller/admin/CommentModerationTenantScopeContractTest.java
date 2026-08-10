package com.mycompany.website.ban.ve.xem.phim.controller.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Guards the controller/service boundary that prevents forged comment moderation targets. */
@DisplayName("Comment moderation tenant-scope source contract")
class CommentModerationTenantScopeContractTest {
    private static final Path SERVLET = Path.of(
            "src", "main", "java", "com", "mycompany", "website", "ban", "ve", "xem", "phim",
            "controller", "admin", "ManagerPortalServlet.java");
    private static final Path SERVICE = Path.of(
            "src", "main", "java", "com", "mycompany", "website", "ban", "ve", "xem", "phim",
            "service", "AdminService.java");

    @Test
    void getPassesActorIntoTheScopedSqlList() throws IOException {
        String source = read(SERVLET);
        assertTrue(source.contains("adminService.listComments(filmId, reportedOnly, scopedActor)"));
    }

    @Test
    void lockPostNeverTrustsSubmittedUserId() throws IOException {
        String source = read(SERVLET);
        String method = between(source, "private void handleCommentPost", "private void handleAppealPost");

        assertFalse(method.contains("int userId = intParam(request, \"userId\""),
                "The comment row, not a hidden form field, must choose the account to lock");
        assertTrue(method.contains("adminService.lockUserForComment(commentId, reason, actor)"));
    }

    @Test
    void serviceLocksCommentAndScopesItsAuthorBeforeMutation() throws IOException {
        String source = read(SERVICE);

        assertTrue(source.contains(
                "listComments(Integer filmId, Boolean reportedOnly, User actor)"));
        assertTrue(source.contains("lockUserForComment(int commentId, String reason, User actor)"));
        assertTrue(source.contains("WITH (UPDLOCK, HOLDLOCK"),
                "Comment author must be derived from a locked row in the mutation transaction");
        assertTrue(source.contains("u.CinemaId=? OR EXISTS"),
                "Manager comment scope must use the same primary-cinema/order predicate as members");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start, "Cannot isolate source method");
        return source.substring(start, end);
    }
}
