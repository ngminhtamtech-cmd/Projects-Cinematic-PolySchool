package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.sql.Connection;
import java.sql.SQLException;

public abstract class BaseDAO {
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }
}
