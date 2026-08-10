package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.UserAppeal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface UserAppealDAO {
    int create(UserAppeal appeal);
    default int create(Connection connection, UserAppeal appeal) {
        throw new UnsupportedOperationException("Connection-aware appeal insert is not implemented");
    }
    default int createRefundAppeal(UserAppeal appeal) {
        return create(appeal);
    }
    Optional<UserAppeal> findById(int id);
    default Optional<UserAppeal> findByIdForUpdate(Connection connection, int id) {
        throw new UnsupportedOperationException("Connection-aware appeal lookup is not implemented");
    }
    Optional<UserAppeal> findPendingByUserId(int userId);
    default Optional<UserAppeal> findPendingByTicketCode(String ticketCode) {
        return Optional.empty();
    }
    List<UserAppeal> findAll(String status);
    default List<UserAppeal> findAll(String status, Integer cinemaId) {
        return findAll(status);
    }
    boolean updateStatus(int appealId, String status, String adminResponse, Integer resolvedByUserId);
    default boolean updateStatus(Connection connection, int appealId, String expectedStatus,
            String status, String adminResponse, Integer resolvedByUserId) {
        throw new UnsupportedOperationException("Connection-aware appeal update is not implemented");
    }
}
