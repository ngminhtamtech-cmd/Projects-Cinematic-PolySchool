package com.mycompany.website.ban.ve.xem.phim.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class ShowtimeSeatViewerStateTest {

    @Test
    void onlyOwnerSeesHeldByMeAndDraftOrderId() {
        ShowtimeSeat seat = new ShowtimeSeat();
        seat.setStatus("held");
        seat.setHeldByUserId(7);
        seat.setClaimedByOrderId(91);

        assertEquals("heldByMe", seat.viewerState(7));
        assertEquals(91, seat.heldOrderIdFor(7));
        assertEquals("heldByOther", seat.viewerState(8));
        assertNull(seat.heldOrderIdFor(8));
    }
}
