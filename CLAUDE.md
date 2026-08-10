# CLAUDE.md

Migration chain inventory: `(69 script)`.

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Dự án

CineBook — website bán vé xem phim. Đang trong quá trình chuyển giao diện từ JSP sang Next.js,
nên **tồn tại song song hai tầng front-end** và cả hai đều còn được phát triển:

| Tầng | Vị trí | Trạng thái |
|---|---|---|
| JSP + JSTL + JS thuần | `src/main/webapp/WEB-INF/views/` (51 `.jsp` + 9 `.jspf`), `src/main/webapp/assets/` | Vẫn là giao diện chính |
| Next.js 16 + React 19 + TS | `web/` | Đang xây dần, GĐ1 xong (`/phim`, `/phim/[id]`) |

**Tính năng mới phải được thêm vào cả hai tầng.** Khi sửa nghiệp vụ ở Java, kiểm tra xem
JSP và Next.js có cùng bị ảnh hưởng không.

**Mã nguồn nằm trong Git repository độc lập tại thư mục dự án này.** Remote công khai là
`https://github.com/ngminhtamtech-cmd/Projects-Cinematic-PolySchool`. Không thao tác Git từ
repository cấp thư mục home cũ và không stage dữ liệu bên ngoài thư mục chứa `pom.xml`.
Nhánh mặc định là `main`; đây là dự án cá nhân phát triển với hỗ trợ của AI.

## Nguồn sự thật về trạng thái — đọc trước khi làm bất cứ gì

Repository đã được tinh gọn còn hai tài liệu Markdown. `README.md` là hướng dẫn công khai để cài
đặt và chạy dự án; file này là ghi chú kỹ thuật dành cho người phát triển. Trạng thái thực tế phải
được xác minh từ mã nguồn, migration, test tự động và lịch sử Git. Không dựa vào tên hoặc số liệu
từ các báo cáo Markdown lịch sử vì chúng đã được loại khỏi cây nguồn công khai.

**Cạm bẫy đã trả giá hai lần — trạng thái ghi vào cột phải nằm trong `CHECK` constraint của cột đó.**
`deleteRoom` ghi `Rooms.Status='deleted'` trong khi `fix34` chỉ cho `'active'`/`'inactive'`, nên
nhánh soft delete **chưa bao giờ chạy được**; `SQLException` bị `catch` đổi thành *"Vui lòng kiểm
tra dữ liệu liên quan"* nên nhìn y như lỗi dữ liệu người dùng (`fix40` đã nới). Cùng lớp lỗi với
`Orders.IsUserHidden` thiếu migration. Thêm giá trị trạng thái mới thì **luôn** kiểm
`sys.check_constraints` của bảng đó trước.

Mốc hồi quy lịch sử gần nhất là đợt sửa D-01…D-07 ngày 07/08/2026 với 348 test unit và
integration, 0 failure/error, 4 skip, Checkstyle 0 vi phạm, route check không hồi quy,
CSRF sweep 34 chặn / 0 lọt lưới và health endpoint HTTP 200. Đây chỉ là mốc tham khảo;
luôn chạy lại test và build hiện hành thay vì tin số liệu cũ.

**Ba file không bao giờ đọc cả:** `service/AdminService.java` (5.064 dòng),
`controller/admin/ManagerPortalServlet.java` (1.123 dòng), `assets/js/jsQR.js` (1.611 dòng thư viện).
Luôn `Grep` tìm số dòng rồi `Read offset/limit`. Toàn bộ code base ≈ 400k token nếu đọc hết.

## Ràng buộc bắt buộc

- **Servlet API là `javax.servlet.*` (Jakarta EE 8) → chỉ chạy được trên Tomcat 9.x.**
  Không dùng `jakarta.servlet.*`. Tomcat 10+ sẽ không chạy được ứng dụng này.
- **`mvn` không có trong PATH.** Dùng bản đi kèm NetBeans:
  `"C:\Program Files\NetBeans-25\netbeans\java\maven\bin\mvn.cmd"`. Máy chỉ có **jdk-25**;
  project biên dịch `maven.compiler.release=17`.
- **Giờ của SQL Server là nguồn thời gian duy nhất.** Không dùng `LocalDateTime.now()` để ghi
  hay so sánh mốc thời gian nghiệp vụ (`HeldUntil`, hạn đơn, khoảng ngày khuyến mãi, báo cáo) —
  dùng `util/BusinessClock.now()` (đo độ lệch với `GETDATE()` mỗi 60s) cho code Java, và so sánh
  **trong SQL** cho các chốt thật sự (khoá ghế, cutoff đặt vé, `redeemTicket`).
  Trộn giờ app với `GETDATE()` là bug đã có thật ở `markHeld` vs `releaseExpiredHolds`.
- **Không được nuốt exception.** `catch` chỉ hợp lệ nếu (a) rethrow, hoặc (b) log kèm ngữ cảnh
  **và** hành vi thay thế là fail-safe (chặn, không phải cho qua). `unit/NoSwallowedExceptionTest`
  quét cả `AdminService`/`BookingService`/`LoyaltyService` và sẽ đỏ nếu mất `cause`.
  Trong khối transaction, bắt `catch (SQLException | RuntimeException)` — chỉ bắt `RuntimeException`
  từng làm `deleteRoom`/`deleteShowtime` **mất sạch ghế** vì không rollback (N-02).
- **Không mở connection mới trong khi một transaction đang giữ khoá.** Pool chỉ 10–20; lồng
  connection trong lúc giữ `UPDLOCK` là tự deadlock. Truyền `Connection` xuống hàm con.
- **Mọi form POST phải có CSRF token.** `CsrfFilter` map `/*` và nhận cả header `X-CSRF-Token`
  (REST/AJAX) lẫn form param `_csrf` (JSP). Trong JSP dùng `<cb:csrf/>` (cần
  `<%@ taglib prefix="cb" tagdir="/WEB-INF/tags" %>`) hoặc include `shared/csrf.jspf`.
- **Escape mọi EL in ra HTML** bằng `${fn:escapeXml(...)}`; miễn trừ mới cần lý do.
- **Mọi danh sách phải phân trang.** Không thêm `SELECT *` không giới hạn, không N+1 trong vòng lặp.
  Lọc **trong SQL trước khi phân trang** — lọc sau khi phân trang từng làm trang 1 rỗng (N-13).

## Kiến trúc backend

`Servlet → Service → JDBC DAO → SQL Server`. Không Spring, không JPA. HikariCP làm pool.

**Không sửa nghiệp vụ trong `BookingService`, `AdminService` hay tầng DAO khi thêm REST API.**
Chỉ thêm controller mỏng dưới `api/v1/`. Khoá ghế (`UPDLOCK/HOLDLOCK`), chống trùng lịch chiếu
và audit log đang đúng — đụng vào là rủi ro thừa.

Nền chạy sẵn khi Tomcat khởi động (`config/AppContextListener`):

- `verifySchemaFailFast()` — thiếu bảng/cột bắt buộc thì **không cho ứng dụng lên**, thay vì
  `try/catch ignored` khi đọc cột.
- `service/HoldSweeper.sweepOnce()` chạy mỗi 30 giây trên thread `cinebook-hold-sweeper`:
  thu hồi hold quá hạn, huỷ đơn mồ côi (`CancelReason = "auto-expired"`). Công tắc
  `SystemSettings.sweeper.enabled` đọc lại mỗi vòng, không cần restart.
- `EmailService` gửi bất đồng bộ; `mail.mode=logfile` ghi vào `${catalina.base}/logs/cinebook-mail.log`.

### Bật/tắt gửi email thật

**`mail.mode` có HAI nguồn và `SystemSettings` trong DB THẮNG file cấu hình.** Sửa mỗi
`db.properties` rồi tưởng đã bật là bẫy chắc chắn vấp — phải chạy
`UPDATE SystemSettings SET SettingValue='smtp' WHERE SettingKey='mail.mode'` (nhớ cờ `-I`).
Giá trị được cache 60 giây trong process; test gọi `EmailService.clearCache()` để khỏi chờ.

Bí mật SMTP chỉ nằm trong file cấu hình ngoài WAR (`mail.smtp.host/port/username/password/
starttls`, `mail.from`), **không bao giờ trong `SystemSettings`** — WAR đã loại `db.properties`
qua `packagingExcludes`. Ba khoá `mail.smtp.connectiontimeout` / `timeout` / `writetimeout`
mặc định 10.000 ms: JavaMail mặc định chờ **vô hạn** mà pool gửi chỉ có 2 thread, nên bỏ trống
là một SMTP treo làm chết im lặng toàn bộ đường gửi.

Bật SMTP là bật chung cho cả **5 loại thư** (xác thực, đặt lại mật khẩu, vé điện tử, hủy suất
chiếu, nhắc đơn tại quầy) — chúng dùng chung `EmailService.send()`. Khi SMTP hỏng hẳn sau 3 lần
thử, nội dung thư vẫn được ghi vào `cinebook-mail.log` kèm nhãn `[SMTP THẤT BẠI]` để còn lấy lại
được link xác thực / link đặt lại mà xử lý tay.

Hai cooldown chống lạm dụng phải cùng bật: `security.verifyResendCooldownSeconds` (gửi lại thư
xác thực) và `security.resetRequestCooldownSeconds` (xin phiếu đặt lại mật khẩu, `fix41`).
Ở chế độ `logfile` thiếu chúng vô hại; bật SMTP thì đó là đường dội bom hộp thư và đốt hạn ngạch.

**Bẫy khi chạy code ngoài Tomcat.** `mvn dependency:build-classpath` đặt
`jakarta.jakartaee-api` (scope `provided`, API rỗng) **đầu** classpath, che mất `javax.mail`
thật và làm mọi lần gửi chết bằng `NoClassDefFoundError: com/sun/mail/util/DefaultProvider`.
Trong WAR không dính vì `provided` không được đóng gói. Chạy tay thì phải lọc jar đó ra.

### Phân quyền — hai chiều

**Chiều 1: thứ bậc vai trò** (`RoleUtil.isAtLeast`, cộng dồn):
`guest(0) < member(1) < staff(2) < manager(3) < admin(4)`. Bảng ánh xạ route → vai nằm trong
`filter/AuthFilter.requiredRole()`.

**Chiều 2: phạm vi cụm rạp** (`Users.CinemaId`): `NULL` = toàn hệ thống, có giá trị = chỉ rạp đó.
Áp cho cả `staff` và `manager` qua `ScopeUtil.assertCinemaScope` / `ScopeUtil.scopeFilter`;
admin có `CinemaId=NULL` và toàn hệ thống. Mọi thao tác quản trị liên quan
rạp/phòng/ghế/suất chiếu/đơn/báo cáo phải kiểm **cả hai chiều** — kiểm role mà quên scope là lỗ IDOR.

Nhiều hàm `AdminService` có **cặp overload** `f(x)` và `f(x, User actor)`: bản có `actor` là bản
áp scope. Gọi bản không có `actor` từ đường quản trị là bỏ qua kiểm tra cụm rạp.

### Vòng đời đơn hàng

`OrderStatus`: `created` → `confirmed` → `redeemed`, hoặc → `cancelled`.
`PaymentStatus`: `pending` → `paid` → `refunded`, hoặc `failed`/`cancelled`.

- `createDraftOrder` khoá ghế bằng `UPDLOCK`, giữ **10 phút** (`BookingService.HOLD_MINUTES`),
  chặn ghế bảo trì, ép ghế đôi đặt cả cặp, và kiểm tra combo theo đúng rạp của suất chiếu.
- `payOrder` — `card` đi qua `SimulatedGateway` và thành `paid` ngay; `counter` giữ `pending`
  kèm `CounterExpiresAt` (`counter.expiryMinutes`, mặc định 30) cho tới khi quầy thu tiền.
  Có `idempotencyKey` để retry không tạo đơn/điểm trùng.
- **Cutoff đặt vé là `now + cutoff <= StartTime`** (`booking.cutoffMinutes`, mặc định 15).
  Trước đây điều kiện ngược chiều — đừng "sửa lại" theo trí nhớ cũ.
- Check-in: `StaffService` mở cổng **60 phút trước** giờ chiếu, đóng khi phim kết thúc;
  quyết định thật nằm trong transaction `UPDLOCK` của `AdminService.redeemTicket`
  (hai nhân viên quét cùng lúc → một người 409 "Vé đã sử dụng").

### Báo cáo doanh thu — một nguồn duy nhất

Ba hằng số trong `AdminService` là nguồn duy nhất của mọi số liệu doanh thu; báo cáo mới
**phải dùng lại chúng**, đừng viết điều kiện lọc riêng — đó chính là gốc của FLOW-REPORT-001/002/003:

- `REVENUE_ORDER_PREDICATE` — đơn tính vào doanh thu (`paid`/`refunded`, chưa huỷ)
- `NET_REVENUE_EXPRESSION` — tổng trừ số đã hoàn, không âm
- `SOLD_SEAT_ORDER_PREDICATE` — vé đã bán (tập con, loại đơn đã hoàn)

Đã biết và chấp nhận: đơn **hoàn một phần** vẫn tính đủ doanh thu combo, phần hoàn trừ hết vào vé.

## REST API (`/api/v1/*`)

- Kế thừa `BaseApiServlet`; nó tự bọc response và dịch exception sang HTTP status.
- **Servlet đăng ký khai báo trong `web.xml`**, không dùng `@WebServlet` (chỉ `EncodingFilter`
  dùng annotation). Thêm endpoint mới phải khai `servlet` + `servlet-mapping` trong `web.xml`.
- Envelope: `{"data":…,"meta":…}` khi thành công, `{"error":{"code","message"}}` khi lỗi.
- **Không serialize model trực tiếp** — luôn qua `api/dto/Dtos.java` + `DtoMapper`.
  `User` chứa `passwordHash`; `Film`/`Showtime` chứa alias chỉ dành cho JSP.
- Mọi request ghi bắt buộc header `X-CSRF-Token` (lấy từ `GET /api/v1/auth/csrf`).
  Không endpoint nào được miễn trừ.
- Đường dẫn ảnh trong DB có nhúng context path cũ; `DtoMapper.asset()` chuẩn hoá lại.
  Giữ nguyên hành vi này — JSP vẫn đọc giá trị thô từ DB.

**Xác thực — chỉ còn hai lớp:**

1. **Session `JSESSIONID`** là thứ duy nhất `AuthFilter` chấp nhận. Mọi route bảo vệ (JSP lẫn API)
   đều đi qua nó.
2. **Refresh token** opaque, hash SHA-256 trong bảng `RefreshTokens`, xoay vòng theo *family*,
   phát hiện replay thì thu hồi cả family. Cookie `refresh_token` phát ở **hai** `Path`
   (`/api/v1/auth` và `<contextPath>/api/v1/auth`) vì ứng dụng nằm dưới context path.
   `POST /api/v1/auth/logout-all` nhận diện người gọi bằng session, hoặc bằng chính cookie này.

**Không có JWT access token.** `AccessTokenService` đã bị xoá và `SessionDto` không còn
`accessToken`/`expiresIn` (BUG-15, 01/08/2026). Lý do: token được phát ra nhưng `AuthFilter`
không bao giờ đọc header `Authorization`, nên nó không mở được route nào — một credential không
ai dùng chỉ là bề mặt tấn công thừa. Nếu sau này cần Bearer thật, **bắt buộc** kèm hai thứ:
kiểm `tokenVersion` trong token với `Users.UpdatedAt`, và chạy `AccountStateGuard` cho đường
Bearer y như đường session. Thiếu chúng là tạo ra đúng lỗ "JWT cũ giữ quyền cũ".

## Cơ sở dữ liệu

SQL Server, database `CineBookDB` — chuỗi migration dựng ra **38 bảng**; `CineBookDB` trên máy dev
hiện có **41** vì còn 3 bảng ngoài chuỗi: `Feedbacks` và `CustomerFeedbacks` (đều rỗng, không có
script tạo, không code Java nào đọc — chỉ còn `views/customer/feedback.jsp` mồ côi không route)
và `sysdiagrams` (SSMS tự tạo). Đừng đếm 3 bảng đó khi đối chiếu ERD.
Production bắt buộc dùng cấu hình ngoài WAR:
`-Dcinebook.db.config` → `%CINEBOOK_CONFIG%`/`%CINEBOOK_DB_CONFIG%` →
`%CATALINA_BASE%\conf\cinebook\db.properties`. File project/classpath chỉ là fallback dev; WAR
loại hẳn `db.properties`. Mẫu: `db.properties.example` / `db.test.properties.example`.

- **`database/schema.sql` chưa đủ.** Phải chạy thêm toàn bộ migration trong `database/`
  (69 script). **Thứ tự:** `schema.sql` → 9 `alter_*.sql` → 3 `migration_*.sql` →
  `alter_and_seed_showtimes_ui.sql` → `fix00` → `fix01`…`fix46` → seed.
  `scripts/init-test-db.ps1` chạy đúng chuỗi này. Thêm script mới mà quên đăng ký vào
  mảng `$migrations` thì `Batch01MigrationChainTest` đỏ ngay — chuỗi từng đứt ở fix23,
  làm mọi máy dựng DB từ đầu thiếu `Orders.RefundReject*`.
- Mọi script mới phải **idempotent** (`IF NOT EXISTS` / `IF COL_LENGTH(...) IS NULL`), nằm trong
  **một** transaction có `THROW` trong `TRY/CATCH`, và có ghi chú `-- ROLLBACK` ở cuối.
  Đừng tái dùng số thứ tự đã có (`fix18` từng bị trùng, phải đổi thành `fix22`).
- **`OrderSeats` và `OrderComboFoods` dùng khoá kép, KHÔNG có cột `Id IDENTITY`.**
  Fixture test chèn nhiều dòng cho cùng một `ShowtimeSeatId` sẽ vi phạm `PK_OrderSeats` —
  đó là dữ liệu không thể tồn tại thật, sửa fixture chứ đừng đổi schema.
- DB tích hợp là database tạm `CineBookIT_<timestamp>_<random>`, tạo và xóa bởi
  `scripts/run-integration-tests.ps1`; tuyệt đối không tái sử dụng database cố định. **Sao lưu `CineBookDB` trước mọi migration**
  (`scripts/backup-cinebook.bat`). Không viết integration test trên `CineBookDB` thật —
  ngoại lệ duy nhất là `SchemaContractIT` chạy chế độ chỉ đọc để đối chiếu hai schema.
- Chạy script SQL **phải ép UTF-8**, nếu không tiếng Việt sẽ hỏng thành mojibake:
  `sqlcmd -S localhost -U sa -P <pw> -C -d CineBookDB -f 65001 -i script.sql`
  (`sqlcmd` ở `C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\`)
- Với `sqlcmd -Q`, **bắt buộc thêm `-I`** vì bảng `Users` có filtered index; thiếu cờ này làm
  `UPDATE`/`INSERT` lỗi Msg 1934. Không pipe output qua `Out-Null`.

### Cấu hình nghiệp vụ nằm trong bảng `SystemSettings`

Không hard-code các ngưỡng này; đọc qua `AdminService.getSettingValue` hoặc `config/SettingsReader`:

`booking.cutoffMinutes` · `booking.maxOpenDraftsPerShowtime` (kẹp `[1, 20]`, mặc định 3) ·
`counter.expiryMinutes` · `seat_hold_minutes` · `payment.mode`
(`simulated`/`live`) · `vat.rate` · `sweeper.enabled` · `sweeper.orphanOrderMinutes` ·
`showtime.cleanupBufferMinutes` · `film.expiringSoonDays` · `security.maxLoginAttempts` ·
`security.lockMinutes` · `security.passwordMinLength` · `security.resetTokenMinutes` ·
`security.verifyResendCooldownSeconds` · `security.resetRequestCooldownSeconds` ·
`mail.mode` · `backup.directory` · `company.*` ·
`cinetags_data`/`corner_items_data`/`events_data`/`special_cinemas_data` (JSON nội dung trang).

### Bẫy DAO mapper — đã làm hỏng tính năng hai lần

Các cột do migration thêm sau **không tự động có trong mọi mapper**. Khi một mapper quên đọc
cột, model trả về giá trị mặc định và tính năng hỏng âm thầm, không có lỗi nào:

- `JdbcPromotionDAO` từng quên `VoucherType/TargetTier/PointsRequired`
  → `getVoucherType()` luôn trả `"PUBLIC"` → voucher giới hạn hạng thành viên không bao giờ bị chặn.
- `JdbcFilmDAO` từng quên `Country/Format/Status/Banner`
  → mọi trang public mất 4 trường dù DB có dữ liệu.
- `AdminService.listCombos()` vẫn dùng `SELECT c.*` kèm `GROUP BY` liệt kê tay — thêm cột vào
  `ComboFoods` mà quên sửa `GROUP BY` sẽ ra lỗi 500. Nợ kỹ thuật đã biết.

Khi thêm cột mới: cập nhật **tất cả** mapper đọc bảng đó, và kiểm chứng bằng dữ liệu thật.

## Kiểm thử

- `*Test.java` = unit (không cần DB), `*IT.java` hoặc test gắn `@Tag("it")` = integration
  (chạy trên `CineBookDB_Test`). Surefire chạy unit trong `mvn test/package`; Failsafe chỉ chạy
  integration ở `mvn verify`. `Run/Debug` trong NetBeans bỏ cả compile test qua `nbactions.xml`.
- **`src/test/resources/enterprise-flow-coverage.tsv` là bản đồ `route|table → vai trò → flowId →
  test`.** Thêm route hoặc bảng mới mà quên cập nhật file này thì `EnterpriseCoverageManifestTest`
  đỏ ngay. Đây cũng là chỗ tra nhanh "ca sử dụng này được test ở đâu".
- Sửa xong một bug thì **tạm hoàn nguyên code và chạy lại test** để chứng minh test bắt được lỗi.
  Không nới assertion, không skip test để lấy màu xanh.
- Chạy một lớp: `&$MVN test -Dtest=BookingFlowIT` · một ca: `-Dtest=BookingFlowIT#tenCa`.

## Lệnh thường dùng

`MVN="C:\Program Files\NetBeans-25\netbeans\java\maven\bin\mvn.cmd"`. Bình thường build + deploy
qua NetBeans, dòng lệnh chỉ để kiểm tra.

**Backend (WAR):**
- Build: `&$MVN package` → chạy unit test và sinh `target/Website-ban-ve-xem-phim-1.0-SNAPSHOT.war`.
- Unit test: `&$MVN test`.
- Full verify (unit + integration trên `CineBookDB_Test`): `&$MVN verify`.
- Lint: `&$MVN checkstyle:check` — Checkstyle **không** gắn vào build (tránh làm chậm deploy),
  phải chạy tay. Luật ở `checkstyle.xml`, `failOnViolation=false` nên chỉ cảnh báo, không chặn.
  Trọng tâm là bug thật (nuốt exception, so sánh chuỗi bằng `==`, fall-through), không ép style.

**Next.js (`web/`):** `npm run dev` (`:3000`) · `npm run build` · `npm start` · `npm run lint`
(ESLint 9). Hook `PostToolUse` tự chạy `eslint --fix` sau mỗi Write/Edit (`.claude/hooks/eslint-fix.sh`).

**Script kiểm chứng (`scripts/`):** `route-check.ps1` (hồi quy route, có `-AdminEmail`) ·
`route-baseline.bat` · `csrf-sweep.bat` · `concurrent-hold.bat` · `load-test.bat` ·
`init-test-db.ps1` · `backup-cinebook.bat` · `test-restore.bat` · `scan-mojibake.sql` ·
`sqlcmd-env.bat`. Skill `/verify-cinebook` chạy trọn bộ hồi quy đầu-cuối.

## Chạy Tomcat 9 cục bộ

Tomcat rời (không đăng ký trong NetBeans), dùng khi cần deploy và kiểm chứng bằng dòng lệnh:

```
TOMCAT = D:\App-download\CODE\tomcat9\apache-tomcat-9.0.117-windows-x64\apache-tomcat-9.0.117
```

```bat
cd /d "%TOMCAT%\bin"
startup.bat      :: mở cửa sổ riêng rồi trả quyền điều khiển ngay — dùng cái này
shutdown.bat     :: dừng
```

Không dùng `catalina.bat run` — nó chiếm terminal và treo agent.

Deploy hiện hành **không copy WAR vào `webapps`**. File
`conf/Catalina/localhost/Website-ban-ve-xem-phim.xml` trỏ `docBase` trực tiếp vào
`target/Website-ban-ve-xem-phim-1.0-SNAPSHOT`; chạy `mvn package` rồi restart Tomcat là đủ.
Trước `startup.bat` phải set cả `JAVA_HOME` và `CATALINA_HOME`; khi dùng `Start-Process`, đặt
`-WorkingDirectory "$TOMCAT\bin"`.

Base URL: `http://localhost:8080/Website-ban-ve-xem-phim` · Log: `%TOMCAT%\logs\catalina.*.log`
và `localhost.*.log` (lỗi JSP/Jasper nằm ở đây, không hiện lúc build). `ROOT.xml` để lại đúng
1 dòng `SEVERE` lúc khởi động — đã biết, đã quyết định không đụng tới.

## Next.js (`web/`)

- Trước khi sửa Next.js, kiểm tra phiên bản trong `web/package.json` và đọc tài liệu đi kèm tại
  `web/node_modules/next/dist/docs/`; phiên bản này có các thay đổi không tương thích.
- `params`, `searchParams`, `cookies()` đều async — phải `await`.
- Ở bề mặt public Next, Server Component dùng `lib/api-server.ts`; Client Component
  dùng `lib/api-client.ts` đi qua `app/api/proxy/[...path]/route.ts`. Sau khi Next
  xác thực `showtimeId`, `/dat-ve` handoff sang JSP/Tomcat để tiếp tục flow có
  session, giữ ghế và thanh toán.
- Proxy phải rewrite cookie `Path=/` và dùng `getSetCookie()` — `Headers.forEach` gộp nhiều
  `Set-Cookie` thành một chuỗi làm hỏng cookie.
- `web/.env.local` và `.env.example` phải cùng trỏ context local thật:
  `CINEBOOK_API_BASE=http://localhost:8080/Website-ban-ve-xem-phim/api/v1`,
  `NEXT_PUBLIC_ASSET_BASE=http://localhost:8080/Website-ban-ve-xem-phim`, và
  `NEXT_PUBLIC_JSP_BASE=http://localhost:8080/Website-ban-ve-xem-phim`.
- Design token nằm trong `app/globals.css` (`@theme`), không có `tailwind.config.ts` (Tailwind 4).
- App Router nằm thẳng ở `web/app/`, **không có thư mục `src/`**.
- **Chỉ mới cài `next`/`react`/`react-dom`/`tailwindcss`.** Chưa có TanStack Query, Zustand,
  React Hook Form, Zod, shadcn/ui, TanStack Table, Recharts — đừng `import` chúng khi chưa cài.

## Giao diện quản trị

Mọi trang `/admin/*` và `/system/*` phải tiếp tục theo pattern hiện có trong JSP/CSS: bộ màu
ngữ nghĩa, font `Be Vietnam Pro` (chỉ 400/600), badge trạng thái bo tròn có dot, tối đa **1 nút
Primary** mỗi trang, nút xoá dạng outline đỏ kèm modal xác nhận. Chỉ đổi lớp trình bày — giữ
nguyên 100% `name`/`id` của input, payload và endpoint.
