package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.model.BookingCommandType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Transaction-local command ledger.  The unique database key serializes the
 * first request; a replay reads the committed result instead of re-running a
 * payment, seat claim, or other side effect.
 */
public final class BookingCommandStore {
    public record Claim(long executionId, boolean replay, Integer orderId, String responseJson) {
    }

    private BookingCommandStore() {
    }

    public static Claim begin(Connection connection, String actorScope, BookingCommandType command,
            String idempotencyKey, String requestHash) throws SQLException {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String select = """
                SELECT Id, RequestHash, Status, OrderId, ResponseJson
                FROM BookingCommandExecutions WITH (UPDLOCK, HOLDLOCK)
                WHERE ActorScope=? AND CommandType=? AND IdempotencyKey=?
                """;
        try (PreparedStatement ps = connection.prepareStatement(select)) {
            ps.setString(1, actorScope);
            ps.setString(2, command.name());
            ps.setString(3, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("RequestHash");
                    Integer existingOrderId = rs.getObject("OrderId") == null
                            ? null : rs.getInt("OrderId");
                    // Test fixtures and data-retention jobs may remove the
                    // aggregate while retaining the append-only row.  A
                    // command with no surviving aggregate cannot be replayed;
                    // safely recycle that tombstone for a new aggregate.
                    if (existingOrderId == null && "succeeded".equalsIgnoreCase(rs.getString("Status"))) {
                        try (PreparedStatement recycle = connection.prepareStatement("""
                                UPDATE BookingCommandExecutions
                                SET RequestHash=?, Status='processing', ResponseStatus=NULL,
                                    ResponseJson=NULL, CompletedAt=NULL, CreatedAt=GETDATE()
                                WHERE Id=? AND OrderId IS NULL
                                """)) {
                            recycle.setString(1, requestHash);
                            recycle.setLong(2, rs.getLong("Id"));
                            recycle.executeUpdate();
                        }
                        return new Claim(rs.getLong("Id"), false, null, null);
                    }
                    if (!requestHash.equalsIgnoreCase(storedHash)) {
                        throw new BookingException(409, "IDEMPOTENCY_CONFLICT");
                    }
                    String status = rs.getString("Status");
                    if (!"succeeded".equalsIgnoreCase(status) || rs.getObject("OrderId") == null) {
                        throw new BookingException(409, "STATE_CONFLICT: command is already processing");
                    }
                    return new Claim(rs.getLong("Id"), true, existingOrderId,
                            rs.getString("ResponseJson"));
                }
            }
        }

        String insert = """
                INSERT INTO BookingCommandExecutions
                    (ActorScope, CommandType, IdempotencyKey, RequestHash, Status, CreatedAt)
                VALUES (?, ?, ?, ?, 'processing', GETDATE())
                """;
        try (PreparedStatement ps = connection.prepareStatement(insert, new String[] {"Id"})) {
            ps.setString(1, actorScope);
            ps.setString(2, command.name());
            ps.setString(3, idempotencyKey);
            ps.setString(4, requestHash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("BookingCommandExecutions did not return Id");
                }
                return new Claim(keys.getLong(1), false, null, null);
            }
        }
    }

    public static void complete(Connection connection, Claim claim, int orderId,
            int responseStatus, String responseJson) throws SQLException {
        if (claim == null || claim.replay()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE BookingCommandExecutions
                SET Status='succeeded', OrderId=?, ResponseStatus=?, ResponseJson=?,
                    CompletedAt=GETDATE()
                WHERE Id=? AND Status='processing'
                """)) {
            ps.setInt(1, orderId);
            ps.setInt(2, responseStatus);
            ps.setString(3, responseJson);
            ps.setLong(4, claim.executionId());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Booking command completion conflict");
            }
        }
    }

    public static void transition(Connection connection, Claim claim, int orderId,
            String beforeState, String afterState, String beforeOrderStatus, String beforePaymentStatus,
            String afterOrderStatus, String afterPaymentStatus, String actorScope, String reason)
            throws SQLException {
        if (claim == null) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO BookingStateTransitions
                    (OrderId, CommandExecutionId, CommandType, BeforeState, AfterState,
                     BeforeOrderStatus, BeforePaymentStatus, AfterOrderStatus, AfterPaymentStatus,
                     ActorScope, Reason, CreatedAt)
                SELECT ?, ?, CommandType, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE()
                FROM BookingCommandExecutions WHERE Id=?
                """)) {
            ps.setInt(1, orderId);
            ps.setLong(2, claim.executionId());
            ps.setString(3, beforeState);
            ps.setString(4, afterState);
            ps.setString(5, beforeOrderStatus);
            ps.setString(6, beforePaymentStatus);
            ps.setString(7, afterOrderStatus);
            ps.setString(8, afterPaymentStatus);
            ps.setString(9, actorScope);
            ps.setString(10, reason);
            ps.setLong(11, claim.executionId());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Booking transition was not recorded");
            }
        }
    }

    public static String sha256(String canonicalRequest) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
