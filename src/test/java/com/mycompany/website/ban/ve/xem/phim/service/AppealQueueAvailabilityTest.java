package com.mycompany.website.ban.ve.xem.phim.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.UserAppealDAO;
import com.mycompany.website.ban.ve.xem.phim.model.UserAppeal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F-005 — hang doi khang cao khong doc duoc phai khac han hang doi trong.
 */
class AppealQueueAvailabilityTest {

    private static UserAppealDAO daoReturning(List<UserAppeal> appeals) {
        return new StubAppealDAO(appeals, null);
    }

    private static UserAppealDAO daoThrowing() {
        return new StubAppealDAO(null, new DaoException("simulated outage", new RuntimeException("no db")));
    }

    private static UserAppeal appeal(int id) {
        UserAppeal item = new UserAppeal();
        item.setId(id);
        item.setStatus("pending");
        return item;
    }

    @Test
    @DisplayName("DAO loi: listAppeals nem loi thay vi tra danh sach rong")
    void listAppealsPropagatesReadFailure() {
        AdminService service = new AdminService(daoThrowing());

        assertThrows(DaoException.class, () -> service.listAppeals(null));
        assertThrows(DaoException.class, () -> service.listAppeals("pending"));
    }

    @Test
    @DisplayName("DAO loi: hang doi bao UNAVAILABLE, khong phai 'khong co don nao'")
    void appealQueueReportsUnavailable() {
        AdminService service = new AdminService(daoThrowing());

        AdminService.AppealQueue queue = service.appealQueue("pending");

        assertTrue(queue.isUnavailable());
        assertFalse(queue.isAvailable());
        assertFalse(queue.isEmptyQueue(), "Doc loi khong duoc coi la hang doi trong");
        assertTrue(queue.getAppeals().isEmpty());
    }

    @Test
    @DisplayName("DAO khoe va co don: hang doi doc duoc, giu du so don")
    void appealQueueReturnsBacklog() {
        AdminService service = new AdminService(daoReturning(List.of(appeal(1), appeal(2))));

        AdminService.AppealQueue queue = service.appealQueue(null);

        assertTrue(queue.isAvailable());
        assertFalse(queue.isUnavailable());
        assertFalse(queue.isEmptyQueue());
        assertEquals(2, queue.getAppeals().size());
    }

    @Test
    @DisplayName("DAO khoe va khong co don: hang doi trong that su, phan biet duoc voi loi doc")
    void appealQueueDistinguishesTrulyEmptyQueue() {
        AdminService service = new AdminService(daoReturning(List.of()));

        AdminService.AppealQueue queue = service.appealQueue(null);

        assertTrue(queue.isAvailable());
        assertTrue(queue.isEmptyQueue());
        assertFalse(queue.isUnavailable());
        assertTrue(queue.getAppeals().isEmpty());
    }

    private static final class StubAppealDAO implements UserAppealDAO {
        private final List<UserAppeal> appeals;
        private final RuntimeException failure;

        private StubAppealDAO(List<UserAppeal> appeals, RuntimeException failure) {
            this.appeals = appeals;
            this.failure = failure;
        }

        @Override
        public int create(UserAppeal appeal) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<UserAppeal> findById(int id) {
            return Optional.empty();
        }

        @Override
        public Optional<UserAppeal> findPendingByUserId(int userId) {
            return Optional.empty();
        }

        @Override
        public List<UserAppeal> findAll(String status) {
            if (failure != null) {
                throw failure;
            }
            return appeals;
        }

        @Override
        public boolean updateStatus(int appealId, String status, String adminResponse, Integer resolvedByUserId) {
            throw new UnsupportedOperationException();
        }
    }
}
