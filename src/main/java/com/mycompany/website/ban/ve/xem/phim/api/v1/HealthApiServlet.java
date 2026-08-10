package com.mycompany.website.ban.ve.xem.phim.api.v1;

import com.mycompany.website.ban.ve.xem.phim.api.ApiException;
import com.mycompany.website.ban.ve.xem.phim.api.BaseApiServlet;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HealthApiServlet extends BaseApiServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String dbTime;
            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement ps = connection.prepareStatement("SELECT GETDATE()");
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("No database time");
                dbTime = rs.getTimestamp(1).toInstant().toString();
            }
            HikariDataSource pool = DBConnection.getDataSource();
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("db", "ok");
            status.put("poolActive", pool.getHikariPoolMXBean() == null
                    ? 0 : pool.getHikariPoolMXBean().getActiveConnections());
            status.put("version", applicationVersion());
            status.put("dbTime", dbTime);
            writeData(response, status);
        } catch (SQLException | RuntimeException ex) {
            log("Health check database failure", ex);
            throw new ApiException(503, "SERVICE_UNAVAILABLE", "Dịch vụ dữ liệu chưa sẵn sàng.");
        }
    }

    private String applicationVersion() {
        Package pkg = HealthApiServlet.class.getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        return version == null || version.isBlank() ? "1.0-SNAPSHOT" : version;
    }
}
