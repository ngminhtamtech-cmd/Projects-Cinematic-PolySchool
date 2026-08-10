// Archived manual harness: excluded from Maven test compilation.
package com.mycompany.website.ban.ve.xem.phim;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;

import java.sql.Connection;
import java.sql.Statement;

public class ExecuteDatabaseMigration {

    public static void main(String[] args) {
        System.out.println("=== THUC HIEN MIGRATION CAU TRUC DATABASE ===");
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Add Status to Rooms
            String alterRoomsSql = """
                IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('Rooms') AND name = 'Status')
                BEGIN
                    ALTER TABLE Rooms ADD Status NVARCHAR(20) NOT NULL DEFAULT 'active';
                END;
                """;
            stmt.executeUpdate(alterRoomsSql);
            System.out.println("✅ Cap nhat cot Status trong bang Rooms thanh cong!");

            // 2. Create AdminNotifications table
            String createNotificationsSql = """
                IF OBJECT_ID('AdminNotifications', 'U') IS NULL
                BEGIN
                    CREATE TABLE AdminNotifications (
                        Id INT IDENTITY PRIMARY KEY,
                        Title NVARCHAR(200) NOT NULL,
                        Message NVARCHAR(MAX) NOT NULL,
                        Category NVARCHAR(50) NOT NULL DEFAULT 'room',
                        Severity NVARCHAR(20) NOT NULL DEFAULT 'info',
                        TargetType NVARCHAR(50) NULL,
                        TargetId NVARCHAR(50) NULL,
                        ActionUrl NVARCHAR(255) NULL,
                        IsRead BIT NOT NULL DEFAULT 0,
                        CreatedAt DATETIME NOT NULL DEFAULT GETDATE()
                    );
                END;
                """;
            stmt.executeUpdate(createNotificationsSql);
            System.out.println("✅ Tao bang AdminNotifications thanh cong!");

        } catch (Exception ex) {
            System.err.println("❌ LOI MIGRATION DATABASE:");
            ex.printStackTrace();
        } finally {
            DBConnection.shutdown();
        }
    }
}
