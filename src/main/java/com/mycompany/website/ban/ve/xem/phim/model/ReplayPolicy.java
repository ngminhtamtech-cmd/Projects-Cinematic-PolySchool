package com.mycompany.website.ban.ve.xem.phim.model;

/** Observable policy for a repeated command. */
public enum ReplayPolicy {
    REPLAY_SAME_RESULT,
    TERMINAL_NOOP,
    CONFLICT_NO_MUTATION;
}
