package com.mycompany.website.ban.ve.xem.phim.service;

import java.util.List;

/** Typed, non-mutating seat-layout diff shown before save. */
public record SeatLayoutPreview(int roomId, int currentSeatCount, int requestedSeatCount,
        List<String> addedSeatKeys, List<String> removedSeatKeys,
        List<String> referencedSeatKeys, int nextLayoutVersion) {
    public SeatLayoutPreview {
        addedSeatKeys = List.copyOf(addedSeatKeys == null ? List.of() : addedSeatKeys);
        removedSeatKeys = List.copyOf(removedSeatKeys == null ? List.of() : removedSeatKeys);
        referencedSeatKeys = List.copyOf(referencedSeatKeys == null ? List.of() : referencedSeatKeys);
    }
}
