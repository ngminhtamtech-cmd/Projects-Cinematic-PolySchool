package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.model.BookingCommandType;
import com.mycompany.website.ban.ve.xem.phim.model.CompositeBookingState;
import com.mycompany.website.ban.ve.xem.phim.model.ReplayPolicy;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical transition registry for the booking lifecycle.
 *
 * <p>All write services must use this registry before issuing a conditional
 * SQL update.  The old string columns remain for compatibility, but their
 * valid combinations and command replay behaviour are defined here.</p>
 */
public final class BookingStateMachine {
    public record TransitionSpec(BookingCommandType command, Set<CompositeBookingState> from,
            CompositeBookingState to, ReplayPolicy replayPolicy) {
        public boolean accepts(CompositeBookingState state) {
            return from.contains(state);
        }
    }

    private static final List<TransitionSpec> SPECS = List.of(
            spec(BookingCommandType.CREATE_DRAFT, set(CompositeBookingState.UNKNOWN),
                    CompositeBookingState.DRAFT_HELD, ReplayPolicy.REPLAY_SAME_RESULT),
            spec(BookingCommandType.PAYMENT_FAILED, set(CompositeBookingState.DRAFT_HELD),
                    CompositeBookingState.PAYMENT_FAILED_RETRYABLE, ReplayPolicy.REPLAY_SAME_RESULT),
            spec(BookingCommandType.PAY_CARD, set(CompositeBookingState.DRAFT_HELD,
                    CompositeBookingState.PAYMENT_FAILED_RETRYABLE), CompositeBookingState.PAID_CONFIRMED,
                    ReplayPolicy.REPLAY_SAME_RESULT),
            spec(BookingCommandType.PLACE_COUNTER, set(CompositeBookingState.DRAFT_HELD),
                    CompositeBookingState.COUNTER_AWAITING_PAYMENT, ReplayPolicy.REPLAY_SAME_RESULT),
            spec(BookingCommandType.COLLECT_COUNTER, set(CompositeBookingState.COUNTER_AWAITING_PAYMENT),
                    CompositeBookingState.PAID_CONFIRMED, ReplayPolicy.REPLAY_SAME_RESULT),
            spec(BookingCommandType.CANCEL, set(CompositeBookingState.DRAFT_HELD,
                    CompositeBookingState.PAYMENT_FAILED_RETRYABLE,
                    CompositeBookingState.COUNTER_AWAITING_PAYMENT), CompositeBookingState.CANCELLED_UNPAID,
                    ReplayPolicy.TERMINAL_NOOP),
            spec(BookingCommandType.EXPIRE, set(CompositeBookingState.DRAFT_HELD,
                    CompositeBookingState.PAYMENT_FAILED_RETRYABLE,
                    CompositeBookingState.COUNTER_AWAITING_PAYMENT), CompositeBookingState.CANCELLED_UNPAID,
                    ReplayPolicy.TERMINAL_NOOP),
            spec(BookingCommandType.CHECK_IN, set(CompositeBookingState.PAID_CONFIRMED),
                    CompositeBookingState.PAID_REDEEMED, ReplayPolicy.REPLAY_SAME_RESULT),
            spec(BookingCommandType.CHECK_IN_OVERRIDE, set(CompositeBookingState.PAID_CONFIRMED),
                    CompositeBookingState.PAID_REDEEMED, ReplayPolicy.REPLAY_SAME_RESULT),
            spec(BookingCommandType.REFUND_FULL, set(CompositeBookingState.PAID_CONFIRMED,
                    CompositeBookingState.PAID_REDEEMED, CompositeBookingState.COMPLETED_LEGACY),
                    CompositeBookingState.FULLY_REFUNDED, ReplayPolicy.REPLAY_SAME_RESULT),
            spec(BookingCommandType.REJECT_REFUND, EnumSet.allOf(CompositeBookingState.class),
                    CompositeBookingState.UNKNOWN, ReplayPolicy.REPLAY_SAME_RESULT),
            spec(BookingCommandType.RESOLVE_APPEAL, EnumSet.allOf(CompositeBookingState.class),
                    CompositeBookingState.UNKNOWN, ReplayPolicy.REPLAY_SAME_RESULT));

    private BookingStateMachine() {
    }

    public static CompositeBookingState classify(String orderStatus, String paymentStatus,
            String paymentMethod) {
        return CompositeBookingState.classify(orderStatus, paymentStatus, paymentMethod);
    }

    public static List<TransitionSpec> specifications() {
        return SPECS;
    }

    public static TransitionSpec specification(BookingCommandType command) {
        return SPECS.stream().filter(spec -> spec.command() == command).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown booking command: " + command));
    }

    public static boolean canTransition(BookingCommandType command, CompositeBookingState from,
            CompositeBookingState to) {
        TransitionSpec spec = specification(command);
        return spec.to() == to && spec.accepts(from);
    }

    public static void requireTransition(BookingCommandType command, CompositeBookingState from,
            CompositeBookingState to) {
        if (!canTransition(command, from, to)) {
            throw new BookingException(409, "INVALID_TRANSITION: " + command + " " + from + " -> " + to);
        }
    }

    private static TransitionSpec spec(BookingCommandType command, Set<CompositeBookingState> from,
            CompositeBookingState to, ReplayPolicy replayPolicy) {
        return new TransitionSpec(command, Set.copyOf(from), to, replayPolicy);
    }

    private static Set<CompositeBookingState> set(CompositeBookingState... values) {
        return EnumSet.copyOf(List.of(values));
    }
}
