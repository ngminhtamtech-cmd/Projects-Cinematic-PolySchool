package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.ApprovalRequest;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.model.Room;
import com.mycompany.website.ban.ve.xem.phim.model.Seat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Transactional manager-to-admin approval workflow.
 *
 * <p>The approval row is the source of truth. Notifications are projections and are therefore
 * written in the same transaction as the state transition.</p>
 */
public class ApprovalService {
    public static final String FILM_ASSIGN = "FILM_ASSIGN";
    public static final String FILM_CREATE = "FILM_CREATE";
    public static final String FILM_UPDATE = "FILM_UPDATE";
    public static final String FILM_UNASSIGN = "FILM_UNASSIGN";
    public static final String ROOM_CREATE = "ROOM_CREATE";

    public Map<Integer, String> listCategories() {
        Map<Integer, String> categories = new LinkedHashMap<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT Id,Title FROM Categories ORDER BY Title");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) categories.put(result.getInt(1), result.getString(2));
            return categories;
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải thể loại phim.", ex);
        }
    }

    public List<Integer> filmCategoryIds(int filmId) {
        List<Integer> ids = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT CategoryId FROM FilmCategories WHERE FilmId=? ORDER BY CategoryId")) {
            statement.setInt(1, filmId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) ids.add(result.getInt(1));
            }
            return ids;
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải thể loại của phim.", ex);
        }
    }

    public List<ApprovalRequest> listRequests(User actor, String requestedStatus,
            Integer adminCinemaContext) {
        String status = normalizeStatusFilter(requestedStatus);
        boolean admin = CinemaCapabilityPolicy.isAdmin(actor);
        int managerCinema = admin ? 0 : CinemaCapabilityPolicy.requireManagerCinema(actor);
        StringBuilder sql = new StringBuilder("""
                SELECT TOP (200) ar.Id,ar.RequestType,ar.CinemaId,c.Name AS CinemaName,
                       ar.RequestedByUserId,requester.FullName AS RequestedByName,
                       ar.RequestKey,ar.Status,ar.RequestedAt,ar.ReviewedByUserId,
                       reviewer.FullName AS ReviewedByName,ar.ReviewedAt,ar.ReviewNote,
                       ar.ResolvedEntityType,ar.ResolvedEntityId,fd.ExistingFilmId,
                       COALESCE(fd.Title,existingFilm.Title,rd.RoomName) AS SubjectName,
                       rd.RoomType,rd.LayoutRows,rd.SeatsPerRow
                FROM ApprovalRequests ar
                JOIN Cinemas c ON c.Id=ar.CinemaId
                JOIN Users requester ON requester.Id=ar.RequestedByUserId
                LEFT JOIN Users reviewer ON reviewer.Id=ar.ReviewedByUserId
                LEFT JOIN FilmRequestDetails fd ON fd.RequestId=ar.Id
                LEFT JOIN Films existingFilm ON existingFilm.Id=fd.ExistingFilmId
                LEFT JOIN RoomRequestDetails rd ON rd.RequestId=ar.Id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (status != null) {
            sql.append(" AND ar.Status=?");
            params.add(status);
        }
        if (admin) {
            if (adminCinemaContext != null && adminCinemaContext > 0) {
                sql.append(" AND ar.CinemaId=?");
                params.add(adminCinemaContext);
            }
        } else {
            sql.append(" AND ar.CinemaId=? AND ar.RequestedByUserId=?");
            params.add(managerCinema);
            params.add(actor.getId());
        }
        sql.append(" ORDER BY CASE ar.Status WHEN 'PENDING' THEN 0 ELSE 1 END, ar.RequestedAt DESC");
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindObjects(statement, params);
            try (ResultSet result = statement.executeQuery()) {
                List<ApprovalRequest> requests = new ArrayList<>();
                while (result.next()) {
                    requests.add(mapRequest(result));
                }
                return requests;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải danh sách yêu cầu phê duyệt.", ex);
        }
    }

    public ApprovalRequest findRequest(int requestId, User actor, Integer adminCinemaContext) {
        return listRequests(actor, null, adminCinemaContext).stream()
                .filter(request -> request.getId() == requestId)
                .findFirst()
                .orElseThrow(() -> new BookingException(404, "Không tìm thấy yêu cầu phê duyệt."));
    }

    public int requestFilmAssignment(int filmId, User actor) {
        int cinemaId = CinemaCapabilityPolicy.requireManagerCinema(actor);
        return createFilmSnapshotRequest(FILM_ASSIGN, filmId, null, List.of(),
                cinemaId, actor, "film:" + filmId);
    }

    public int requestFilmUnassignment(int filmId, User actor) {
        int cinemaId = CinemaCapabilityPolicy.requireManagerCinema(actor);
        return createFilmSnapshotRequest(FILM_UNASSIGN, filmId, null, List.of(),
                cinemaId, actor, "film:" + filmId);
    }

    public int requestFilmUpdate(Film film, List<Integer> categoryIds, User actor) {
        if (film == null || film.getId() <= 0) {
            throw new BookingException(400, "Vui lòng chọn phim cần cập nhật.");
        }
        int cinemaId = CinemaCapabilityPolicy.requireManagerCinema(actor);
        return createFilmSnapshotRequest(FILM_UPDATE, film.getId(), film, categoryIds,
                cinemaId, actor, "film:" + film.getId());
    }

    public int requestFilmCreation(Film film, List<Integer> categoryIds, User actor) {
        if (film == null) {
            throw new BookingException(400, "Dữ liệu đề xuất phim không hợp lệ.");
        }
        validateFilmPayload(film);
        int cinemaId = CinemaCapabilityPolicy.requireManagerCinema(actor);
        String key = "title:" + normalizedKey(film.getTitle()) + ":"
                + (film.getReleaseDate() == null ? "none" : film.getReleaseDate());
        return createFilmSnapshotRequest(FILM_CREATE, null, film, categoryIds,
                cinemaId, actor, key);
    }

    private int createFilmSnapshotRequest(String requestType, Integer filmId, Film payload,
            List<Integer> categoryIds, int cinemaId, User actor, String requestKey) {
        validateCategoryIds(categoryIds);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                assertManagerAssignment(connection, actor.getId(), cinemaId);
                if (filmId != null) {
                    assertFilmRequestAllowed(connection, requestType, filmId, cinemaId);
                }
                Film snapshot = payload == null ? readFilm(connection, filmId) : payload;
                validateFilmPayload(snapshot);
                LocalDateTime sourceUpdatedAt = filmId == null
                        ? null : readFilmUpdatedAt(connection, filmId);
                int requestId = insertRequestHeader(connection, requestType, cinemaId,
                        actor.getId(), requestKey);
                insertFilmDetails(connection, requestId, filmId, snapshot, sourceUpdatedAt);
                insertRequestCategories(connection, requestId, categoryIds);
                notifyUsers(connection, requestId, cinemaId, actor.getId(),
                        "Yêu cầu mới cần duyệt",
                        actor.getFullName() + " đã gửi " + requestTypeDisplay(requestType)
                                + " cho " + snapshot.getTitle() + ".",
                        "APPROVAL_REQUESTED", adminRecipientSql());
                insertAudit(connection, actor.getId(), "CREATE_APPROVAL_REQUEST",
                        "ApprovalRequest", requestId,
                        "{\"type\":\"" + requestType + "\",\"cinemaId\":" + cinemaId + "}");
                connection.commit();
                return requestId;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw translateWriteFailure(ex, "Không thể gửi yêu cầu phim.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể gửi yêu cầu phim.", ex);
        }
    }

    public int requestRoomCreation(Room room, int layoutRows, int seatsPerRow,
            List<Seat> requestedSeats, User actor) {
        int cinemaId = CinemaCapabilityPolicy.requireManagerCinema(actor);
        if (room == null || room.getName() == null || room.getName().isBlank()) {
            throw new BookingException(400, "Tên phòng chiếu không được để trống.");
        }
        if (layoutRows < 1 || layoutRows > 26 || seatsPerRow < 1 || seatsPerRow > 50) {
            throw new BookingException(400, "Sơ đồ phòng phải có 1–26 hàng và 1–50 ghế mỗi hàng.");
        }
        List<Seat> seats = requestedSeats == null || requestedSeats.isEmpty()
                ? generateStandardSeats(layoutRows, seatsPerRow) : validateSeats(requestedSeats);
        String roomName = room.getName().trim();
        String roomType = normalizeRoomType(room.getRoomType());
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                assertManagerAssignment(connection, actor.getId(), cinemaId);
                if (roomNameExists(connection, cinemaId, roomName)) {
                    throw new BookingException(409, "Tên phòng này đã tồn tại tại rạp của bạn.");
                }
                int requestId = insertRequestHeader(connection, ROOM_CREATE, cinemaId,
                        actor.getId(), "room:" + normalizedKey(roomName));
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO RoomRequestDetails
                            (RequestId,RoomName,RoomType,LayoutRows,SeatsPerRow)
                        VALUES(?,?,?,?,?)
                        """)) {
                    statement.setInt(1, requestId);
                    statement.setString(2, roomName);
                    statement.setString(3, roomType);
                    statement.setInt(4, layoutRows);
                    statement.setInt(5, seatsPerRow);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO RoomRequestSeats
                            (RequestId,SeatKey,RowLabel,SeatNumber,SeatType,PriceSurcharge)
                        VALUES(?,?,?,?,?,?)
                        """)) {
                    for (Seat seat : seats) {
                        statement.setInt(1, requestId);
                        statement.setString(2, seat.getSeatKey());
                        statement.setString(3, seat.getRowLabel());
                        statement.setInt(4, seat.getSeatNumber());
                        statement.setString(5, normalizeSeatType(seat.getSeatType()));
                        statement.setBigDecimal(6, nonNegative(seat.getPriceSurcharge()));
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                notifyUsers(connection, requestId, cinemaId, actor.getId(),
                        "Yêu cầu phòng mới",
                        actor.getFullName() + " đề nghị tạo phòng " + roomName + ".",
                        "APPROVAL_REQUESTED", adminRecipientSql());
                insertAudit(connection, actor.getId(), "CREATE_APPROVAL_REQUEST",
                        "ApprovalRequest", requestId,
                        "{\"type\":\"ROOM_CREATE\",\"cinemaId\":" + cinemaId + "}");
                connection.commit();
                return requestId;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw translateWriteFailure(ex, "Không thể gửi yêu cầu tạo phòng.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể gửi yêu cầu tạo phòng.", ex);
        }
    }

    public void approve(int requestId, Integer duplicateFilmId, String note, User actor) {
        CinemaCapabilityPolicy.requireAdmin(actor);
        review(requestId, true, duplicateFilmId, note, actor);
    }

    public void reject(int requestId, String note, User actor) {
        CinemaCapabilityPolicy.requireAdmin(actor);
        if (note == null || note.isBlank()) {
            throw new BookingException(400, "Vui lòng nhập lý do từ chối.");
        }
        review(requestId, false, null, note, actor);
    }

    private void review(int requestId, boolean approved, Integer duplicateFilmId,
            String note, User actor) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LockedRequest request = lockPendingRequest(connection, requestId);
                assertManagerAssignment(connection, request.requestedByUserId(), request.cinemaId());
                Resolution resolution = approved
                        ? applyApproval(connection, request, duplicateFilmId, actor.getId())
                        : new Resolution(null, null);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE ApprovalRequests
                        SET Status=?,ReviewedByUserId=?,ReviewedAt=SYSDATETIME(),ReviewNote=?,
                            ResolvedEntityType=?,ResolvedEntityId=?
                        WHERE Id=? AND Status=N'PENDING'
                        """)) {
                    statement.setString(1, approved ? "APPROVED" : "REJECTED");
                    statement.setInt(2, actor.getId());
                    setNullableString(statement, 3, trimToNull(note));
                    setNullableString(statement, 4, resolution.entityType());
                    setNullableInteger(statement, 5, resolution.entityId());
                    statement.setInt(6, requestId);
                    if (statement.executeUpdate() != 1) {
                        throw new BookingException(409, "Yêu cầu đã được xử lý bởi một quản trị viên khác.");
                    }
                }
                String subject = requestSubject(connection, requestId);
                if (approved && (FILM_ASSIGN.equals(request.requestType())
                        || FILM_CREATE.equals(request.requestType()))) {
                    notifyUsers(connection, requestId, request.cinemaId(), actor.getId(),
                            "Rạp có phim mới", "Admin đã thêm phim " + subject + " vào rạp của bạn.",
                            "FILM_ASSIGNED", "SELECT Id FROM Users WHERE Role=N'manager'"
                                    + " AND CinemaId=" + request.cinemaId()
                                    + " AND ISNULL(Deleted,0)=0 AND ISNULL(IsLocked,0)=0");
                }
                String title = approved ? "Yêu cầu đã được duyệt" : "Yêu cầu bị từ chối";
                String message = approved
                        ? requestTypeDisplay(request.requestType()) + " cho " + subject + " đã được duyệt."
                        : requestTypeDisplay(request.requestType()) + " cho " + subject
                                + " bị từ chối: " + note.trim();
                notifyUsers(connection, requestId, request.cinemaId(), actor.getId(), title,
                        message, approved ? "APPROVAL_APPROVED" : "APPROVAL_REJECTED",
                        "SELECT Id FROM Users WHERE Id=" + request.requestedByUserId());
                insertAudit(connection, actor.getId(), approved ? "APPROVE_REQUEST" : "REJECT_REQUEST",
                        "ApprovalRequest", requestId,
                        "{\"type\":\"" + request.requestType() + "\",\"cinemaId\":"
                                + request.cinemaId() + "}");
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw translateWriteFailure(ex, "Không thể xử lý yêu cầu.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể xử lý yêu cầu.", ex);
        }
    }

    public void cancel(int requestId, User actor) {
        int cinemaId = CinemaCapabilityPolicy.requireManagerCinema(actor);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE ApprovalRequests
                    SET Status=N'CANCELLED',ReviewedAt=SYSDATETIME(),ReviewNote=N'Người gửi đã hủy'
                    WHERE Id=? AND Status=N'PENDING' AND CinemaId=? AND RequestedByUserId=?
                    """)) {
                statement.setInt(1, requestId);
                statement.setInt(2, cinemaId);
                statement.setInt(3, actor.getId());
                if (statement.executeUpdate() != 1) {
                    throw new BookingException(409, "Chỉ có thể hủy yêu cầu đang chờ do chính bạn gửi.");
                }
                insertAudit(connection, actor.getId(), "CANCEL_APPROVAL_REQUEST",
                        "ApprovalRequest", requestId, "{\"cinemaId\":" + cinemaId + "}");
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw translateWriteFailure(ex, "Không thể hủy yêu cầu.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể hủy yêu cầu.", ex);
        }
    }

    /** Notify every active manager of a cinema after an admin assigns a film directly. */
    public void notifyFilmAssigned(int cinemaId, int filmId, String filmTitle, User actor) {
        CinemaCapabilityPolicy.requireAdmin(actor);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                notifyUsers(connection, null, cinemaId, actor.getId(), "Rạp có phim mới",
                        "Admin đã thêm phim " + filmTitle + " vào rạp của bạn.",
                        "FILM_ASSIGNED", "SELECT Id FROM Users WHERE Role=N'manager'"
                                + " AND CinemaId=" + cinemaId
                                + " AND ISNULL(Deleted,0)=0 AND ISNULL(IsLocked,0)=0");
                insertAudit(connection, actor.getId(), "NOTIFY_FILM_ASSIGNMENT",
                        "Film", filmId, "{\"cinemaId\":" + cinemaId + "}");
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw translateWriteFailure(ex, "Không thể gửi thông báo gán phim.");
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể gửi thông báo gán phim.", ex);
        }
    }

    private Resolution applyApproval(Connection connection, LockedRequest request,
            Integer duplicateFilmId, int adminId) throws SQLException {
        return switch (request.requestType()) {
            case FILM_ASSIGN -> {
                int filmId = requiredExistingFilm(connection, request.id());
                activateCinemaFilm(connection, request.cinemaId(), filmId, adminId);
                yield new Resolution("Film", filmId);
            }
            case FILM_UNASSIGN -> {
                int filmId = requiredExistingFilm(connection, request.id());
                if (hasFutureShowtimes(connection, request.cinemaId(), filmId)) {
                    throw new BookingException(409,
                            "Phim còn suất chiếu tương lai tại rạp; hãy xử lý suất chiếu trước khi gỡ.");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE CinemaFilms SET Status=N'inactive',UnassignedAt=SYSDATETIME(),
                            UnassignedByUserId=?
                        WHERE CinemaId=? AND FilmId=? AND Status=N'active'
                        """)) {
                    statement.setInt(1, adminId);
                    statement.setInt(2, request.cinemaId());
                    statement.setInt(3, filmId);
                    if (statement.executeUpdate() != 1) {
                        throw new BookingException(409, "Phim không còn được gán ở rạp này.");
                    }
                }
                yield new Resolution("Film", filmId);
            }
            case FILM_CREATE -> {
                if (duplicateFilmId != null && duplicateFilmId > 0) {
                    assertFilmExists(connection, duplicateFilmId);
                    activateCinemaFilm(connection, request.cinemaId(), duplicateFilmId, adminId);
                    yield new Resolution("Film", duplicateFilmId);
                }
                int filmId = createFilmFromRequest(connection, request.id());
                copyRequestCategories(connection, request.id(), filmId);
                activateCinemaFilm(connection, request.cinemaId(), filmId, adminId);
                yield new Resolution("Film", filmId);
            }
            case FILM_UPDATE -> {
                int filmId = updateFilmFromRequest(connection, request.id());
                replaceFilmCategoriesFromRequest(connection, request.id(), filmId);
                yield new Resolution("Film", filmId);
            }
            case ROOM_CREATE -> new Resolution("Room",
                    createRoomFromRequest(connection, request.id(), request.cinemaId()));
            default -> throw new BookingException(400, "Loại yêu cầu không được hỗ trợ.");
        };
    }

    private int createFilmFromRequest(Connection connection, int requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO Films(Title,OtherTitles,Actors,Directors,Rating,ReleaseDate,EndDate,
                    DurationMinutes,AgeRating,TrailerUrl,Thumbnail,Banner,Language,Subtitles,
                    Description,Country,Format,Status)
                SELECT Title,OtherTitles,Actors,Directors,Rating,ReleaseDate,EndDate,
                    DurationMinutes,AgeRating,TrailerUrl,Thumbnail,Banner,Language,Subtitles,
                    Description,Country,Format,COALESCE(FilmStatus,N'showing')
                FROM FilmRequestDetails WHERE RequestId=?
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, requestId);
            if (statement.executeUpdate() != 1) {
                throw new BookingException(409, "Yêu cầu không còn dữ liệu phim hợp lệ.");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new BookingException(500, "Không lấy được mã phim vừa tạo.");
    }

    private int updateFilmFromRequest(Connection connection, int requestId) throws SQLException {
        int filmId = requiredExistingFilm(connection, requestId);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE f SET f.Title=d.Title,f.OtherTitles=d.OtherTitles,f.Actors=d.Actors,
                    f.Directors=d.Directors,f.Rating=d.Rating,f.ReleaseDate=d.ReleaseDate,
                    f.EndDate=d.EndDate,f.DurationMinutes=d.DurationMinutes,f.AgeRating=d.AgeRating,
                    f.TrailerUrl=d.TrailerUrl,f.Thumbnail=d.Thumbnail,f.Banner=d.Banner,
                    f.Language=d.Language,f.Subtitles=d.Subtitles,f.Description=d.Description,
                    f.Country=d.Country,f.Format=d.Format,f.Status=d.FilmStatus,f.UpdatedAt=GETDATE()
                FROM Films f JOIN FilmRequestDetails d ON d.ExistingFilmId=f.Id
                WHERE d.RequestId=? AND f.UpdatedAt=d.SourceFilmUpdatedAt
                """)) {
            statement.setInt(1, requestId);
            if (statement.executeUpdate() != 1) {
                throw new BookingException(409,
                        "Phim đã thay đổi sau khi yêu cầu được gửi; hãy từ chối và yêu cầu gửi lại.");
            }
        }
        return filmId;
    }

    private int createRoomFromRequest(Connection connection, int requestId, int cinemaId)
            throws SQLException {
        String roomName;
        String roomType;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT RoomName,RoomType FROM RoomRequestDetails WHERE RequestId=?")) {
            statement.setInt(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new BookingException(409, "Yêu cầu thiếu dữ liệu phòng.");
                roomName = result.getString(1);
                roomType = result.getString(2);
            }
        }
        if (roomNameExists(connection, cinemaId, roomName)) {
            throw new BookingException(409, "Tên phòng đã tồn tại tại rạp này.");
        }
        int roomId;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO Rooms(CinemaId,Name,RoomType,Status) VALUES(?,?,?,N'active')",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, cinemaId);
            statement.setString(2, roomName);
            statement.setString(3, roomType);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new BookingException(500, "Không lấy được mã phòng vừa tạo.");
                roomId = keys.getInt(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO Seats(RoomId,RowLabel,SeatNumber,SeatType,SeatKey,PriceSurcharge)
                SELECT ?,RowLabel,SeatNumber,SeatType,SeatKey,PriceSurcharge
                FROM RoomRequestSeats WHERE RequestId=? ORDER BY RowLabel,SeatNumber
                """)) {
            statement.setInt(1, roomId);
            statement.setInt(2, requestId);
            if (statement.executeUpdate() == 0) {
                throw new BookingException(409, "Yêu cầu phòng không có sơ đồ ghế.");
            }
        }
        return roomId;
    }

    private void activateCinemaFilm(Connection connection, int cinemaId, int filmId, int adminId)
            throws SQLException {
        assertFilmExists(connection, filmId);
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE CinemaFilms SET Status=N'active',AssignedAt=SYSDATETIME(),
                    AssignedByUserId=?,UnassignedAt=NULL,UnassignedByUserId=NULL
                WHERE CinemaId=? AND FilmId=?
                """)) {
            update.setInt(1, adminId);
            update.setInt(2, cinemaId);
            update.setInt(3, filmId);
            if (update.executeUpdate() == 0) {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO CinemaFilms(CinemaId,FilmId,Status,AssignedAt,AssignedByUserId)
                        VALUES(?,?,N'active',SYSDATETIME(),?)
                        """)) {
                    insert.setInt(1, cinemaId);
                    insert.setInt(2, filmId);
                    insert.setInt(3, adminId);
                    insert.executeUpdate();
                }
            }
        }
    }

    private void assertFilmRequestAllowed(Connection connection, String type, int filmId, int cinemaId)
            throws SQLException {
        assertFilmExists(connection, filmId);
        boolean active = false;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT Status FROM CinemaFilms WHERE CinemaId=? AND FilmId=?")) {
            statement.setInt(1, cinemaId);
            statement.setInt(2, filmId);
            try (ResultSet result = statement.executeQuery()) {
                active = result.next() && "active".equalsIgnoreCase(result.getString(1));
            }
        }
        if (FILM_ASSIGN.equals(type) && active) {
            throw new BookingException(409, "Phim đã được gán cho rạp của bạn.");
        }
        if (!FILM_ASSIGN.equals(type) && !active) {
            throw new BookingException(403, "Chỉ được đề nghị sửa hoặc gỡ phim đang thuộc rạp của bạn.");
        }
    }

    private void assertFilmExists(Connection connection, int filmId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM Films WHERE Id=? AND DeletedAt IS NULL")) {
            statement.setInt(1, filmId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new BookingException(404, "Không tìm thấy phim.");
            }
        }
    }

    private Film readFilm(Connection connection, int filmId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT Title,OtherTitles,Actors,Directors,Rating,ReleaseDate,EndDate,
                       DurationMinutes,AgeRating,TrailerUrl,Thumbnail,Banner,Language,Subtitles,
                       Description,Country,Format,Status
                FROM Films WHERE Id=? AND DeletedAt IS NULL
                """)) {
            statement.setInt(1, filmId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new BookingException(404, "Không tìm thấy phim.");
                Film film = new Film();
                film.setId(filmId);
                film.setTitle(result.getString("Title"));
                film.setOtherTitles(result.getString("OtherTitles"));
                film.setActors(result.getString("Actors"));
                film.setDirectors(result.getString("Directors"));
                double rating = result.getDouble("Rating");
                film.setRating(result.wasNull() ? null : rating);
                Date release = result.getDate("ReleaseDate");
                film.setReleaseDate(release == null ? null : release.toLocalDate());
                Date end = result.getDate("EndDate");
                film.setEndDate(end == null ? null : end.toLocalDate());
                int duration = result.getInt("DurationMinutes");
                film.setDurationMinutes(result.wasNull() ? null : duration);
                film.setAgeRating(result.getString("AgeRating"));
                film.setTrailerUrl(result.getString("TrailerUrl"));
                film.setThumbnail(result.getString("Thumbnail"));
                film.setBanner(result.getString("Banner"));
                film.setLanguage(result.getString("Language"));
                film.setSubtitles(result.getString("Subtitles"));
                film.setDescription(result.getString("Description"));
                film.setCountry(result.getString("Country"));
                film.setFormat(result.getString("Format"));
                film.setStatus(result.getString("Status"));
                return film;
            }
        }
    }

    private LocalDateTime readFilmUpdatedAt(Connection connection, int filmId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT UpdatedAt FROM Films WHERE Id=?")) {
            statement.setInt(1, filmId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new BookingException(404, "Không tìm thấy phim.");
                Timestamp timestamp = result.getTimestamp(1);
                return timestamp == null ? null : timestamp.toLocalDateTime();
            }
        }
    }

    private void insertFilmDetails(Connection connection, int requestId, Integer existingFilmId,
            Film film, LocalDateTime sourceUpdatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO FilmRequestDetails(RequestId,ExistingFilmId,Title,OtherTitles,Actors,
                    Directors,Rating,ReleaseDate,EndDate,DurationMinutes,AgeRating,TrailerUrl,
                    Thumbnail,Banner,Language,Subtitles,Description,Country,Format,FilmStatus,
                    SourceFilmUpdatedAt)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setInt(1, requestId);
            setNullableInteger(statement, 2, existingFilmId);
            statement.setString(3, film.getTitle());
            setNullableString(statement, 4, film.getOtherTitles());
            setNullableString(statement, 5, film.getActors());
            setNullableString(statement, 6, film.getDirectors());
            if (film.getRating() == null) statement.setNull(7, Types.DOUBLE);
            else statement.setDouble(7, film.getRating());
            setNullableDate(statement, 8, film.getReleaseDate());
            setNullableDate(statement, 9, film.getEndDate());
            setNullableInteger(statement, 10, film.getDurationMinutes());
            setNullableString(statement, 11, film.getAgeRating());
            setNullableString(statement, 12, film.getTrailerUrl());
            setNullableString(statement, 13, film.getThumbnail());
            setNullableString(statement, 14, film.getBanner());
            setNullableString(statement, 15, film.getLanguage());
            setNullableString(statement, 16, film.getSubtitles());
            setNullableString(statement, 17, film.getDescription());
            setNullableString(statement, 18, film.getCountry());
            setNullableString(statement, 19, film.getFormat());
            setNullableString(statement, 20, film.getRawStatus());
            if (sourceUpdatedAt == null) statement.setNull(21, Types.TIMESTAMP);
            else statement.setTimestamp(21, Timestamp.valueOf(sourceUpdatedAt));
            statement.executeUpdate();
        }
    }

    private int insertRequestHeader(Connection connection, String type, int cinemaId,
            int actorId, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ApprovalRequests(RequestType,CinemaId,RequestedByUserId,RequestKey)
                VALUES(?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, type);
            statement.setInt(2, cinemaId);
            statement.setInt(3, actorId);
            statement.setString(4, key);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new BookingException(500, "Không lấy được mã yêu cầu vừa tạo.");
    }

    private void insertRequestCategories(Connection connection, int requestId,
            List<Integer> categoryIds) throws SQLException {
        if (categoryIds == null || categoryIds.isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO FilmRequestCategories(RequestId,CategoryId)
                SELECT ?,Id FROM Categories WHERE Id=?
                """)) {
            for (Integer categoryId : new HashSet<>(categoryIds)) {
                statement.setInt(1, requestId);
                statement.setInt(2, categoryId);
                if (statement.executeUpdate() != 1) {
                    throw new BookingException(400, "Thể loại phim không hợp lệ.");
                }
            }
        }
    }

    private void copyRequestCategories(Connection connection, int requestId, int filmId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO FilmCategories(FilmId,CategoryId)
                SELECT ?,CategoryId FROM FilmRequestCategories WHERE RequestId=?
                """)) {
            statement.setInt(1, filmId);
            statement.setInt(2, requestId);
            statement.executeUpdate();
        }
    }

    private void replaceFilmCategoriesFromRequest(Connection connection, int requestId, int filmId)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM FilmCategories WHERE FilmId=?")) {
            delete.setInt(1, filmId);
            delete.executeUpdate();
        }
        copyRequestCategories(connection, requestId, filmId);
    }

    private LockedRequest lockPendingRequest(Connection connection, int requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT Id,RequestType,CinemaId,RequestedByUserId
                FROM ApprovalRequests WITH (UPDLOCK,HOLDLOCK)
                WHERE Id=? AND Status=N'PENDING'
                """)) {
            statement.setInt(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new BookingException(409, "Yêu cầu không còn ở trạng thái chờ duyệt.");
                }
                return new LockedRequest(result.getInt("Id"), result.getString("RequestType"),
                        result.getInt("CinemaId"), result.getInt("RequestedByUserId"));
            }
        }
    }

    private int requiredExistingFilm(Connection connection, int requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ExistingFilmId FROM FilmRequestDetails WHERE RequestId=?")) {
            statement.setInt(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    int id = result.getInt(1);
                    if (!result.wasNull() && id > 0) return id;
                }
            }
        }
        throw new BookingException(409, "Yêu cầu thiếu mã phim nguồn.");
    }

    private void assertManagerAssignment(Connection connection, int userId, int cinemaId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM Users
                WHERE Id=? AND Role=N'manager' AND CinemaId=?
                  AND ISNULL(Deleted,0)=0 AND ISNULL(IsLocked,0)=0
                """)) {
            statement.setInt(1, userId);
            statement.setInt(2, cinemaId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new BookingException(409,
                            "Manager không còn hoạt động tại rạp của yêu cầu; yêu cầu không thể xử lý.");
                }
            }
        }
    }

    private boolean hasFutureShowtimes(Connection connection, int cinemaId, int filmId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT TOP (1) 1 FROM Showtimes
                WHERE CinemaId=? AND FilmId=? AND EndTime>GETDATE()
                  AND ISNULL(SaleStatus,N'ON_SALE')<>N'DELETED'
                """)) {
            statement.setInt(1, cinemaId);
            statement.setInt(2, filmId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean roomNameExists(Connection connection, int cinemaId, String roomName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT TOP (1) 1 FROM Rooms
                WHERE CinemaId=? AND LOWER(LTRIM(RTRIM(Name)))=LOWER(LTRIM(RTRIM(?)))
                  AND ISNULL(Status,N'active')<>N'deleted'
                """)) {
            statement.setInt(1, cinemaId);
            statement.setString(2, roomName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private String requestSubject(Connection connection, int requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(fd.Title,f.Title,rd.RoomName,N'yêu cầu')
                FROM ApprovalRequests ar
                LEFT JOIN FilmRequestDetails fd ON fd.RequestId=ar.Id
                LEFT JOIN Films f ON f.Id=fd.ExistingFilmId
                LEFT JOIN RoomRequestDetails rd ON rd.RequestId=ar.Id
                WHERE ar.Id=?
                """)) {
            statement.setInt(1, requestId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : "yêu cầu";
            }
        }
    }

    private void notifyUsers(Connection connection, Integer requestId, int cinemaId,
            Integer createdByUserId, String title, String message, String eventType,
            String recipientSelectSql) throws SQLException {
        int notificationId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO AdminNotifications(Title,Message,Category,Severity,TargetType,TargetId,
                    ActionUrl,IsRead,CinemaId,CreatedByUserId,EventType,ApprovalRequestId)
                VALUES(?,?,N'approval',N'info',N'ApprovalRequest',?,N'/admin/requests',0,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, title);
            statement.setString(2, message);
            statement.setString(3, requestId == null ? null : String.valueOf(requestId));
            statement.setInt(4, cinemaId);
            setNullableInteger(statement, 5, createdByUserId);
            statement.setString(6, eventType);
            setNullableInteger(statement, 7, requestId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing notification generated key");
                notificationId = keys.getInt(1);
            }
        }
        String sql = "INSERT INTO NotificationRecipients(SourceType,NotificationId,UserId) "
                + "SELECT N'admin',?,recipients.Id FROM (" + recipientSelectSql + ") recipients "
                + "WHERE NOT EXISTS (SELECT 1 FROM NotificationRecipients nr "
                + "WHERE nr.SourceType=N'admin' AND nr.NotificationId=? AND nr.UserId=recipients.Id)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, notificationId);
            statement.setInt(2, notificationId);
            statement.executeUpdate();
        }
    }

    private String adminRecipientSql() {
        return "SELECT Id FROM Users WHERE Role=N'admin' AND ISNULL(Deleted,0)=0"
                + " AND ISNULL(IsLocked,0)=0";
    }

    private void insertAudit(Connection connection, Integer actorId, String action,
            String targetType, int targetId, String detailJson) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO AuditLogs(ActorUserId,Action,TargetType,TargetId,DetailJson,AfterJson)
                VALUES(?,?,?,?,?,?)
                """)) {
            setNullableInteger(statement, 1, actorId);
            statement.setString(2, action);
            statement.setString(3, targetType);
            statement.setString(4, String.valueOf(targetId));
            statement.setString(5, detailJson);
            statement.setString(6, detailJson);
            statement.executeUpdate();
        }
    }

    private ApprovalRequest mapRequest(ResultSet result) throws SQLException {
        ApprovalRequest request = new ApprovalRequest();
        request.setId(result.getInt("Id"));
        request.setRequestType(result.getString("RequestType"));
        request.setCinemaId(result.getInt("CinemaId"));
        request.setCinemaName(result.getString("CinemaName"));
        request.setRequestedByUserId(result.getInt("RequestedByUserId"));
        request.setRequestedByName(result.getString("RequestedByName"));
        request.setRequestKey(result.getString("RequestKey"));
        request.setStatus(result.getString("Status"));
        request.setRequestedAt(toLocalDateTime(result.getTimestamp("RequestedAt")));
        request.setReviewedByUserId(nullableInteger(result, "ReviewedByUserId"));
        request.setReviewedByName(result.getString("ReviewedByName"));
        request.setReviewedAt(toLocalDateTime(result.getTimestamp("ReviewedAt")));
        request.setReviewNote(result.getString("ReviewNote"));
        request.setResolvedEntityType(result.getString("ResolvedEntityType"));
        request.setResolvedEntityId(nullableInteger(result, "ResolvedEntityId"));
        request.setExistingFilmId(nullableInteger(result, "ExistingFilmId"));
        request.setSubjectName(result.getString("SubjectName"));
        request.setRoomType(result.getString("RoomType"));
        request.setLayoutRows(nullableInteger(result, "LayoutRows"));
        request.setSeatsPerRow(nullableInteger(result, "SeatsPerRow"));
        return request;
    }

    private List<Seat> generateStandardSeats(int rows, int seatsPerRow) {
        List<Seat> seats = new ArrayList<>(rows * seatsPerRow);
        for (int row = 0; row < rows; row++) {
            String label = String.valueOf((char) ('A' + row));
            for (int number = 1; number <= seatsPerRow; number++) {
                seats.add(new Seat(0, label, number, "standard", label + number, BigDecimal.ZERO));
            }
        }
        return seats;
    }

    private List<Seat> validateSeats(List<Seat> source) {
        List<Seat> seats = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Seat seat : source) {
            if (seat == null || seat.getSeatKey() == null || seat.getSeatKey().isBlank()
                    || seat.getRowLabel() == null || seat.getRowLabel().isBlank()
                    || seat.getSeatNumber() <= 0) {
                throw new BookingException(400, "Sơ đồ ghế chứa vị trí không hợp lệ.");
            }
            String key = seat.getSeatKey().trim().toUpperCase(Locale.ROOT);
            if (!keys.add(key)) throw new BookingException(400, "Mã ghế bị trùng: " + key);
            seat.setSeatKey(key);
            seat.setRowLabel(seat.getRowLabel().trim().toUpperCase(Locale.ROOT));
            seat.setSeatType(normalizeSeatType(seat.getSeatType()));
            seat.setPriceSurcharge(nonNegative(seat.getPriceSurcharge()));
            seats.add(seat);
        }
        if (seats.isEmpty()) throw new BookingException(400, "Sơ đồ phòng phải có ít nhất một ghế.");
        return seats;
    }

    private void validateFilmPayload(Film film) {
        requireText(film.getTitle(), "Tên phim", 255);
        if (film.getDurationMinutes() == null || film.getDurationMinutes() <= 0
                || film.getDurationMinutes() > 1000) {
            throw new BookingException(400, "Thời lượng phim phải từ 1 đến 1000 phút.");
        }
        if (film.getReleaseDate() == null || film.getEndDate() == null
                || film.getEndDate().isBefore(film.getReleaseDate())) {
            throw new BookingException(400, "Ngày kết thúc phải bằng hoặc sau ngày phát hành.");
        }
        if (film.getRating() != null && (film.getRating() < 0 || film.getRating() > 10)) {
            throw new BookingException(400, "Điểm đánh giá phải từ 0 đến 10.");
        }
        if (film.getRawStatus() == null || film.getRawStatus().isBlank()) film.setStatus("showing");
    }

    private void validateCategoryIds(List<Integer> categoryIds) {
        if (categoryIds == null) return;
        for (Integer id : categoryIds) {
            if (id == null || id <= 0) throw new BookingException(400, "Thể loại phim không hợp lệ.");
        }
    }

    private void requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) throw new BookingException(400, label + " không được để trống.");
        if (value.trim().length() > maxLength) throw new BookingException(400, label + " quá dài.");
    }

    private String normalizeRoomType(String value) {
        String normalized = value == null ? "STANDARD" : value.trim().toUpperCase(Locale.ROOT);
        return Set.of("STANDARD", "VIP", "IMAX", "COUPLE").contains(normalized)
                ? normalized : "STANDARD";
    }

    private String normalizeSeatType(String value) {
        String normalized = value == null ? "standard" : value.trim().toLowerCase(Locale.ROOT);
        return Set.of("standard", "vip", "couple").contains(normalized) ? normalized : "standard";
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        if (value.signum() < 0) throw new BookingException(400, "Phụ phí ghế không được âm.");
        return value;
    }

    private String normalizeStatusFilter(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED").contains(status)) {
            throw new BookingException(400, "Trạng thái yêu cầu không hợp lệ.");
        }
        return status;
    }

    private String normalizedKey(String value) {
        String plain = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return plain.length() <= 150 ? plain : plain.substring(0, 150);
    }

    private String requestTypeDisplay(String type) {
        return switch (type) {
            case FILM_ASSIGN -> "yêu cầu gán phim";
            case FILM_CREATE -> "đề xuất phim mới";
            case FILM_UPDATE -> "yêu cầu cập nhật phim";
            case FILM_UNASSIGN -> "yêu cầu gỡ phim";
            case ROOM_CREATE -> "yêu cầu tạo phòng";
            default -> "yêu cầu";
        };
    }

    private RuntimeException translateWriteFailure(Exception ex, String message) {
        if (ex instanceof BookingException bookingException) return bookingException;
        if (ex instanceof SQLException sqlException
                && (sqlException.getErrorCode() == 2601 || sqlException.getErrorCode() == 2627)) {
            return new BookingException(409, "Đã có một yêu cầu giống hệt đang chờ duyệt.", ex);
        }
        return new BookingException(500, message, ex);
    }

    private void bindObjects(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int index = 0; index < params.size(); index++) {
            Object value = params.get(index);
            if (value instanceof Integer integer) statement.setInt(index + 1, integer);
            else statement.setString(index + 1, String.valueOf(value));
        }
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER);
        else statement.setInt(index, value);
    }

    private void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.NVARCHAR);
        else statement.setString(index, value);
    }

    private void setNullableDate(PreparedStatement statement, int index, java.time.LocalDate value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.DATE);
        else statement.setDate(index, Date.valueOf(value));
    }

    private Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record LockedRequest(int id, String requestType, int cinemaId, int requestedByUserId) { }
    private record Resolution(String entityType, Integer entityId) { }
}
