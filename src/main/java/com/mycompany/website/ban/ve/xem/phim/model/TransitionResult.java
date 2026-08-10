package com.mycompany.website.ban.ve.xem.phim.model;

/** Result metadata returned by a lifecycle command. */
public record TransitionResult(int orderId, CompositeBookingState before,
        CompositeBookingState after, boolean replayed, ReplayPolicy replayPolicy) {
}
