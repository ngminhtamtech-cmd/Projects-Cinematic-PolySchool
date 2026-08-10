package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.StaffService;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * C.2 (BUG-07 con mot cua sau) — khong duoc con ban {@code lookupTicket} nao bo qua pham vi.
 *
 * <p>{@code lookupTicket(String)} truyen {@code actor = null}, ma
 * {@code ScopeUtil.isOutOfCinemaScope(null, …)} tra {@code false} — tuc la khong kiem pham vi cum
 * rap gi ca. Hai call site production hien deu truyen {@code actor} nen BUG-07 dang dong, nhung
 * ban ngan van la {@code public}: caller tiep theo goi nham no se mo lai lo ro thong tin don o
 * rap khac, <b>khong loi bien dich, khong test nao do</b>.</p>
 *
 * <p>Cach chac chan nhat de mot API nguy hiem khong bi goi nham la no khong ton tai.</p>
 */
@DisplayName("C.2 — khong con overload lookupTicket bo qua pham vi cum rap")
public class C2LookupTicketScopeTest {

    @Test
    @DisplayName("lookupTicket(String) da bi xoa")
    public void theActorlessOverloadIsGone() {
        assertThrows(NoSuchMethodException.class,
                () -> StaffService.class.getMethod("lookupTicket", String.class),
                "Ban khong co actor van con public: goi nham no la bo qua toan bo kiem tra"
                + " pham vi cum rap ma khong co dau hieu nao bao.");
    }

    @Test
    @DisplayName("ban con lai bat buoc nhan actor")
    public void theRemainingOverloadTakesAnActor() throws NoSuchMethodException {
        Method scoped = StaffService.class.getMethod("lookupTicket", String.class, User.class);
        assertDoesNotThrow(() -> scoped);
        assertFalse(Modifier.isPrivate(scoped.getModifiers()),
                "Duong tra cuu co kiem pham vi phai goi duoc tu controller");
    }

    @Test
    @DisplayName("khong co ban nao khac chi nhan mot chuoi")
    public void noOtherSingleStringOverloadSneaksBackIn() {
        for (Method method : StaffService.class.getMethods()) {
            if (!"lookupTicket".equals(method.getName())) {
                continue;
            }
            assertFalse(method.getParameterCount() == 1,
                    "Xuat hien lai mot ban lookupTicket mot tham so: " + method);
        }
    }
}
