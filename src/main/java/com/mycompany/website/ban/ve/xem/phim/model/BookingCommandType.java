package com.mycompany.website.ban.ve.xem.phim.model;

/** Commands that are allowed to mutate the ticket lifecycle. */
public enum BookingCommandType {
    CREATE_DRAFT,
    PAYMENT_FAILED,
    PAY_CARD,
    PLACE_COUNTER,
    COLLECT_COUNTER,
    CANCEL,
    EXPIRE,
    CHECK_IN,
    CHECK_IN_OVERRIDE,
    REFUND_FULL,
    REJECT_REFUND,
    RESOLVE_APPEAL;
}
