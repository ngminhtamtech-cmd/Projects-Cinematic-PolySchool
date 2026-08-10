package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.model.BookingCommandType;
import com.mycompany.website.ban.ve.xem.phim.model.CompositeBookingState;
import com.mycompany.website.ban.ve.xem.phim.model.ReplayPolicy;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingStateMachine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract tests for the single application-level booking lifecycle registry. */
public class BookingStateMachineTest {

    @Test
    public void classifiesOnlyTheDocumentedCompositeStates() {
        assertEquals(CompositeBookingState.DRAFT_HELD,
                BookingStateMachine.classify("created", "pending", "card"));
        assertEquals(CompositeBookingState.DRAFT_HELD,
                BookingStateMachine.classify("pending", "pending", "card"));
        assertEquals(CompositeBookingState.PAYMENT_FAILED_RETRYABLE,
                BookingStateMachine.classify("created", "failed", "card"));
        assertEquals(CompositeBookingState.COUNTER_AWAITING_PAYMENT,
                BookingStateMachine.classify("confirmed", "pending", "counter"));
        assertEquals(CompositeBookingState.PAID_CONFIRMED,
                BookingStateMachine.classify("confirmed", "paid", "card"));
        assertEquals(CompositeBookingState.PAID_REDEEMED,
                BookingStateMachine.classify("redeemed", "paid", "card"));
        assertEquals(CompositeBookingState.CANCELLED_UNPAID,
                BookingStateMachine.classify("cancelled", "cancelled", "card"));
        assertEquals(CompositeBookingState.FULLY_REFUNDED,
                BookingStateMachine.classify("cancelled", "refunded", "card"));
        assertEquals(CompositeBookingState.COMPLETED_LEGACY,
                BookingStateMachine.classify("completed", "paid", "card"));
    }

    @Test
    public void rejectsKnownInvalidCombinations() {
        Set<CompositeBookingState> invalid = EnumSet.of(CompositeBookingState.UNKNOWN);
        assertTrue(invalid.contains(BookingStateMachine.classify("cancelled", "paid", "card")));
        assertTrue(invalid.contains(BookingStateMachine.classify("cancelled", "pending", "card")));
        assertTrue(invalid.contains(BookingStateMachine.classify("redeemed", "pending", "card")));
        assertTrue(invalid.contains(BookingStateMachine.classify("confirmed", "pending", "card")));
    }

    @Test
    public void registryCoversEveryCommandAndDeclaresReplayPolicy() {
        for (BookingCommandType command : BookingCommandType.values()) {
            BookingStateMachine.TransitionSpec spec = BookingStateMachine.specification(command);
            assertNotNull(spec);
            assertFalse(spec.from().isEmpty(), command + " must have an explicit source set");
            assertNotNull(spec.replayPolicy());
        }
        assertEquals(ReplayPolicy.TERMINAL_NOOP,
                BookingStateMachine.specification(BookingCommandType.CANCEL).replayPolicy());
        assertEquals(ReplayPolicy.REPLAY_SAME_RESULT,
                BookingStateMachine.specification(BookingCommandType.PAY_CARD).replayPolicy());
    }

    @Test
    public void allowsHappyPathAndRejectsWrongTargetWithoutMutation() {
        BookingStateMachine.requireTransition(BookingCommandType.PAY_CARD,
                CompositeBookingState.DRAFT_HELD, CompositeBookingState.PAID_CONFIRMED);
        assertThrows(BookingException.class, () -> BookingStateMachine.requireTransition(
                BookingCommandType.PAY_CARD, CompositeBookingState.CANCELLED_UNPAID,
                CompositeBookingState.PAID_CONFIRMED));
        assertFalse(BookingStateMachine.canTransition(BookingCommandType.CANCEL,
                CompositeBookingState.PAID_CONFIRMED, CompositeBookingState.CANCELLED_UNPAID));
    }

    @Test
    public void manifestIsMachineReadableAndContainsTheRegistryCommands() throws IOException {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("booking-state-machine.tsv")) {
            assertNotNull(stream, "state manifest must be packaged");
            String manifest = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(manifest.startsWith("command\tfromState\ttoState\treplayPolicy"));
            for (BookingCommandType command : BookingCommandType.values()) {
                assertTrue(manifest.contains("\n" + command.name() + "\t"),
                        command + " is missing from the state manifest");
            }
        }
    }
}
