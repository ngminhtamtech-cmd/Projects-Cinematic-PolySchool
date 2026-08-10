package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.model.BookingCommandType;
import com.mycompany.website.ban.ve.xem.phim.model.CompositeBookingState;
import com.mycompany.website.ban.ve.xem.phim.model.TransitionResult;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * The transition kernel shared by booking writers.
 *
 * <p>Legacy status columns are intentionally accepted at the boundary, but a
 * mutation is not recorded until both composite states and the command agree
 * with the canonical registry.  The command ledger and transition history
 * are written in the caller's transaction, so a rejected transition cannot
 * leave a partial audit row behind.</p>
 */
public final class BookingTransitionService {

    private BookingTransitionService() {
    }

    public static TransitionResult validate(int orderId, BookingCommandType command,
            String beforeOrderStatus, String beforePaymentStatus, String beforePaymentMethod,
            String afterOrderStatus, String afterPaymentStatus, String afterPaymentMethod,
            boolean replayed) {
        CompositeBookingState before = BookingStateMachine.classify(
                beforeOrderStatus, beforePaymentStatus, beforePaymentMethod);
        CompositeBookingState after = BookingStateMachine.classify(
                afterOrderStatus, afterPaymentStatus, afterPaymentMethod);
        BookingStateMachine.requireTransition(command, before, after);
        return new TransitionResult(orderId, before, after, replayed,
                BookingStateMachine.specification(command).replayPolicy());
    }

    public static TransitionResult record(Connection connection,
            BookingCommandStore.Claim claim, int orderId, BookingCommandType command,
            String beforeOrderStatus, String beforePaymentStatus, String beforePaymentMethod,
            String afterOrderStatus, String afterPaymentStatus, String afterPaymentMethod,
            String actorScope, String reason) throws SQLException {
        TransitionResult result = validate(orderId, command, beforeOrderStatus,
                beforePaymentStatus, beforePaymentMethod, afterOrderStatus,
                afterPaymentStatus, afterPaymentMethod, false);
        BookingCommandStore.transition(connection, claim, orderId,
                result.before().code(), result.after().code(), beforeOrderStatus,
                beforePaymentStatus, afterOrderStatus, afterPaymentStatus,
                actorScope, reason);
        return result;
    }
}
