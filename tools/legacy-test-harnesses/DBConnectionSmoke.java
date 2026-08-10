// Archived manual harness: excluded from Maven test compilation.
package com.mycompany.website.ban.ve.xem.phim;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.sql.Connection;

public class DBConnectionSmoke {
    public static void main(String[] args) throws Exception {
        try (Connection connection = DBConnection.getConnection()) {
            System.out.println("Connected to SQL Server: " + !connection.isClosed());
        } finally {
            DBConnection.shutdown();
        }
    }
}
