package com.mycompany.website.ban.ve.xem.phim.config;

import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

public final class DatabaseConfig {
    private static final String DEFAULT_CLASSPATH_CONFIG = "db.properties";
    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());

    private DatabaseConfig() {
    }

    public static Properties load() {
        Properties props = new Properties();
        String externalPath = System.getProperty("cinebook.db.config");
        if (externalPath == null || externalPath.isBlank()) {
            externalPath = System.getenv("CINEBOOK_CONFIG");
        }
        if (externalPath == null || externalPath.isBlank()) {
            externalPath = System.getenv("CINEBOOK_DB_CONFIG");
        }

        try (InputStream in = openConfig(externalPath)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Missing database config. Copy db.properties.example to db.properties "
                                + "or set -Dcinebook.db.config=/path/to/db.properties.");
            }
            props.load(in);
            return props;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load database config", ex);
        }
    }

    private static InputStream openConfig(String externalPath) throws IOException {
        if (externalPath != null && !externalPath.isBlank()) {
            File externalFile = new File(externalPath);
            if (externalFile.isFile()) {
                return new FileInputStream(externalFile);
            }
            throw new IOException("Configured database file does not exist: " + externalFile.getAbsolutePath());
        }
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null && !catalinaBase.isBlank()) {
            File tomcatConfig = new File(catalinaBase, "conf/cinebook/db.properties");
            if (tomcatConfig.isFile()) {
                return new FileInputStream(tomcatConfig);
            }
        }
        File projectConfig = new File("db.properties");
        if (projectConfig.isFile()) {
            LOGGER.warning("Dang dung db.properties trong thu muc du an (chi danh cho dev). "
                    + "Production phai dung CINEBOOK_CONFIG hoac catalina.base/conf/cinebook/db.properties.");
            return new FileInputStream(projectConfig);
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            InputStream in = loader.getResourceAsStream(DEFAULT_CLASSPATH_CONFIG);
            if (in != null) {
                LOGGER.warning("Dang dung db.properties trong classpath (chi danh cho dev).");
                return in;
            }
        }
        InputStream fallback = DatabaseConfig.class.getClassLoader().getResourceAsStream(DEFAULT_CLASSPATH_CONFIG);
        if (fallback != null) {
            LOGGER.warning("Dang dung db.properties trong classpath (chi danh cho dev).");
        }
        return fallback;
    }
}
