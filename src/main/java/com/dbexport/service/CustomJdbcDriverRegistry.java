package com.dbexport.service;

import com.dbexport.model.DatabaseDriverInfo;
import com.dbexport.model.DatabaseInfo;
import com.dbexport.util.DatabaseDialect;
import com.dbexport.util.SqlSafeUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

@Component
public class CustomJdbcDriverRegistry {

    private final Path driverDir = Paths.get("drivers").toAbsolutePath().normalize();
    private final Map<String, LoadedDriver> loadedDrivers = new ConcurrentHashMap<>();

    public DatabaseDriverInfo uploadDriver(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择 JDBC 驱动 jar 文件");
        }
        String originalName = file.getOriginalFilename();
        String safeName = SqlSafeUtils.sanitizeFileName(originalName == null ? "driver.jar" : originalName);
        if (safeName == null || !safeName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("仅支持上传 .jar 格式的 JDBC 驱动包");
        }

        Files.createDirectories(driverDir);
        String id = UUID.randomUUID().toString().replace("-", "");
        Path target = driverDir.resolve(id + "_" + safeName).normalize();
        if (!target.startsWith(driverDir)) {
            throw new IllegalArgumentException("驱动文件名不合法");
        }
        file.transferTo(target.toFile());

        LoadedDriver loaded = loadDriver(id, safeName, target);
        loadedDrivers.put(id, loaded);
        return loaded.toInfo();
    }

    public DataSource createDataSource(DatabaseInfo dbInfo, String jdbcUrl) {
        LoadedDriver loaded = getLoadedDriver(dbInfo.getCustomDriverId());
        Properties props = new Properties();
        props.setProperty("user", dbInfo.getUsername() == null ? "" : dbInfo.getUsername());
        props.setProperty("password", dbInfo.getPassword() == null ? "" : dbInfo.getPassword());
        return new DriverBackedDataSource(loaded.driver, jdbcUrl, props);
    }

    public Connection openConnection(DatabaseInfo dbInfo, String jdbcUrl) throws SQLException {
        return createDataSource(dbInfo, jdbcUrl).getConnection(dbInfo.getUsername(), dbInfo.getPassword());
    }

    public DatabaseDriverInfo getInfo(String id) {
        return getLoadedDriver(id).toInfo();
    }

    private LoadedDriver getLoadedDriver(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("请先上传数据库 JDBC 驱动包");
        }
        LoadedDriver loaded = loadedDrivers.get(id);
        if (loaded == null) {
            throw new IllegalArgumentException("驱动包未加载或服务已重启，请重新上传 JDBC 驱动包");
        }
        return loaded;
    }

    private LoadedDriver loadDriver(String id, String fileName, Path jarPath) throws IOException {
        URLClassLoader loader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()},
                Thread.currentThread().getContextClassLoader());
        List<Driver> drivers = discoverByServiceLoader(loader);
        if (drivers.isEmpty()) {
            drivers = discoverByScanningJar(loader, jarPath);
        }
        if (drivers.isEmpty()) {
            closeQuietly(loader);
            throw new IllegalArgumentException("未在驱动包中发现 java.sql.Driver 实现，请确认上传的是 JDBC 驱动 jar");
        }

        Driver driver = chooseBestDriver(drivers);
        DatabaseDialect dialect = DatabaseDialect.fromDriverClass(driver.getClass().getName());
        return new LoadedDriver(id, fileName, jarPath, loader, driver, dialect);
    }

    private List<Driver> discoverByServiceLoader(ClassLoader loader) {
        List<Driver> drivers = new ArrayList<>();
        ServiceLoader<Driver> serviceLoader = ServiceLoader.load(Driver.class, loader);
        Iterator<Driver> it = serviceLoader.iterator();
        while (true) {
            try {
                if (!it.hasNext()) {
                    break;
                }
                drivers.add(it.next());
            } catch (Throwable ignored) {
                break;
            }
        }
        return drivers;
    }

    private List<Driver> discoverByScanningJar(ClassLoader loader, Path jarPath) throws IOException {
        List<Driver> drivers = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class")) {
                    continue;
                }
                String className = name.substring(0, name.length() - 6).replace('/', '.');
                String lower = className.toLowerCase(Locale.ROOT);
                if (!lower.contains("driver") && !lower.contains("jdbc")) {
                    continue;
                }
                try {
                    Class<?> clazz = Class.forName(className, true, loader);
                    if (Driver.class.isAssignableFrom(clazz) && !clazz.isInterface()
                            && !Modifier.isAbstract(clazz.getModifiers())) {
                        drivers.add((Driver) clazz.newInstance());
                    }
                } catch (Throwable ignored) {
                    // 部分驱动包会包含依赖缺失的辅助类，跳过即可。
                }
            }
        }
        return drivers;
    }

    private Driver chooseBestDriver(List<Driver> drivers) {
        drivers.sort((a, b) -> Integer.compare(score(b), score(a)));
        return drivers.get(0);
    }

    private int score(Driver driver) {
        DatabaseDialect dialect = DatabaseDialect.fromDriverClass(driver.getClass().getName());
        if (dialect == DatabaseDialect.DM) return 100;
        if (dialect == DatabaseDialect.KINGBASE) return 90;
        if (dialect == DatabaseDialect.MYSQL) return 80;
        if (dialect == DatabaseDialect.POSTGRESQL) return 70;
        if (dialect == DatabaseDialect.ORACLE) return 60;
        if (dialect == DatabaseDialect.SQLSERVER) return 50;
        return 10;
    }

    @PreDestroy
    public void shutdown() {
        for (LoadedDriver loaded : loadedDrivers.values()) {
            closeQuietly(loaded.loader);
        }
        loadedDrivers.clear();
    }

    private void closeQuietly(URLClassLoader loader) {
        try {
            loader.close();
        } catch (IOException ignored) {
        }
    }

    private static class LoadedDriver {
        private final String id;
        private final String fileName;
        private final Path jarPath;
        private final URLClassLoader loader;
        private final Driver driver;
        private final DatabaseDialect dialect;
        private final long uploadedAt;

        private LoadedDriver(String id, String fileName, Path jarPath, URLClassLoader loader,
                             Driver driver, DatabaseDialect dialect) {
            this.id = id;
            this.fileName = fileName;
            this.jarPath = jarPath;
            this.loader = loader;
            this.driver = driver;
            this.dialect = dialect;
            this.uploadedAt = System.currentTimeMillis();
        }

        private DatabaseDriverInfo toInfo() {
            DatabaseDriverInfo info = new DatabaseDriverInfo();
            info.setId(id);
            info.setFileName(fileName);
            info.setDriverClassName(driver.getClass().getName());
            info.setDetectedType(dialect.getType());
            info.setDetectedDialect(dialect.getType());
            info.setDisplayName(dialect.getDisplayName());
            info.setDefaultPort(dialect.getDefaultPort());
            info.setUploadedAt(uploadedAt);
            if (dialect.canBuildUrl()) {
                info.setMessage("已识别 " + dialect.getDisplayName() + " 驱动");
            } else {
                info.setMessage("驱动已上传，但暂未识别出 JDBC URL 规则");
            }
            return info;
        }
    }

    private static class DriverBackedDataSource implements DataSource {
        private final Driver driver;
        private final String jdbcUrl;
        private final Properties baseProperties;
        private PrintWriter logWriter;
        private int loginTimeout;

        private DriverBackedDataSource(Driver driver, String jdbcUrl, Properties baseProperties) {
            this.driver = driver;
            this.jdbcUrl = jdbcUrl;
            this.baseProperties = baseProperties;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return getConnection(baseProperties.getProperty("user"), baseProperties.getProperty("password"));
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Properties props = new Properties();
            props.putAll(baseProperties);
            if (username != null) {
                props.setProperty("user", username);
            }
            if (password != null) {
                props.setProperty("password", password);
            }
            Connection connection = driver.connect(jdbcUrl, props);
            if (connection == null) {
                throw new SQLException("驱动不接受当前 JDBC URL: " + jdbcUrl);
            }
            return connection;
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            this.logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            this.loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("无法 unwrap 为 " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
