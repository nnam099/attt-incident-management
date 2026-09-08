package com.attt.incident.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Arrays;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class FlywayV4_1MigrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    private static String getBaseJdbcUrl() {
        return postgres.getJdbcUrl();
    }

    private static String createIsolatedDatabase(String dbName) throws SQLException {
        try (Connection rootConn = DriverManager.getConnection(getBaseJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            rootConn.setAutoCommit(true);
            try (Statement stmt = rootConn.createStatement()) {
                stmt.execute("DROP DATABASE IF EXISTS " + dbName);
                stmt.execute("CREATE DATABASE " + dbName);
            }
        }
        return getBaseJdbcUrl().substring(0, getBaseJdbcUrl().lastIndexOf("/") + 1) + dbName;
    }

    private static DataSource createDataSource(String jdbcUrl) {
        return DataSourceBuilder.create()
                .url(jdbcUrl)
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    private static Integer resolveV5Checksum(String jdbcUrl) {
        Flyway infoFlyway = Flyway.configure()
                .dataSource(jdbcUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
        for (MigrationInfo info : infoFlyway.info().all()) {
            if ("5".equals(info.getVersion().getVersion())) {
                return info.getChecksum();
            }
        }
        throw new IllegalStateException("Không tìm thấy migration version 5 trong classpath");
    }

    @Test
    @DisplayName("Kịch bản 1 (Fresh Database): Migration tuần tự thành công và Hibernate JPA validate thành công")
    void testFreshDatabaseMigrationAndHibernateValidation() throws Exception {
        String dbUrl = createIsolatedDatabase("db_fresh");

        // 1. Chạy toàn bộ migration tuần tự từ V1 -> V5
        Flyway flyway = Flyway.configure()
                .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("5");
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(6);

        // 2. Kiểm tra Hibernate JPA ddl-auto: validate
        DataSource dataSource = createDataSource(dbUrl);
        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setDataSource(dataSource);
        emfBean.setPackagesToScan("com.attt.incident.entity");
        emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties props = new Properties();
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        emfBean.setJpaProperties(props);
        emfBean.afterPropertiesSet();

        EntityManagerFactory emf = emfBean.getObject();
        assertThat(emf).isNotNull();
        emf.close();
    }

    @Test
    @DisplayName("Kịch bản 2 (Legacy-Compatible): Database có V5 thiếu V4.1, bảng tương thích, outOfOrder=false lỗi, maintenance outOfOrder=true thành công bảo toàn dữ liệu")
    void testLegacyCompatibleDatabaseMigration() throws Exception {
        String dbUrl = createIsolatedDatabase("db_legacy_compat");

        // 1. Migrate đến V4
        Flyway flywayV4 = Flyway.configure()
                .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("4")
                .load();
        flywayV4.migrate();

        // 2. Tạo bảng incident_iocs và incident_tasks với schema tương thích chuẩn
        try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE incident_iocs (
                    id BIGSERIAL PRIMARY KEY,
                    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
                    type VARCHAR(30) NOT NULL,
                    value VARCHAR(255) NOT NULL,
                    description TEXT,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW()
                );
                CREATE INDEX idx_incident_iocs_incident_id ON incident_iocs(incident_id);
                CREATE INDEX idx_incident_iocs_type ON incident_iocs(type);
                CREATE INDEX idx_incident_iocs_value ON incident_iocs(value);

                CREATE TABLE incident_tasks (
                    id BIGSERIAL PRIMARY KEY,
                    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
                    task_name VARCHAR(255) NOT NULL,
                    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
                    completed_at TIMESTAMP,
                    completed_by BIGINT REFERENCES users(id) ON DELETE SET NULL
                );
                CREATE INDEX idx_incident_tasks_incident_id ON incident_tasks(incident_id);
                CREATE INDEX idx_incident_tasks_is_completed ON incident_tasks(is_completed);
            """);

            // 3. Áp dụng các thay đổi của V5
            stmt.execute("""
                ALTER TABLE incident_iocs ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
                ALTER TABLE incident_iocs ADD COLUMN IF NOT EXISTS removed_at TIMESTAMP;
                ALTER TABLE incident_iocs ADD COLUMN IF NOT EXISTS removed_by BIGINT REFERENCES users(id);
            """);

            // 4. Ghi nhận V5 vào flyway_schema_history (giả lập legacy DB đã chạy V5 nhưng thiếu V4.1)
            Integer v5Checksum = resolveV5Checksum(dbUrl);
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO flyway_schema_history (
                    installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
                ) VALUES (5, '5', 'ioc soft delete', 'SQL', 'V5__ioc_soft_delete.sql', ?, 'test', NOW(), 10, TRUE)
            """)) {
                ps.setInt(1, v5Checksum);
                ps.executeUpdate();
            }

            // 5. Chèn sample records vào cả 2 bảng (trước tiên chèn incident cha)
            stmt.execute("""
                INSERT INTO incidents (id, incident_code, title, severity, status, created_at)
                VALUES (1, 'INC-TEST-001', 'Sample Legacy Incident', 'HIGH', 'NEW', NOW());

                INSERT INTO incident_iocs (id, incident_id, type, value, description, status, created_at)
                VALUES (100, 1, 'IP', '198.51.100.23', 'Sample C2 IP from legacy DB', 'ACTIVE', '2026-09-01 10:00:00');

                INSERT INTO incident_tasks (id, incident_id, task_name, is_completed, completed_by)
                VALUES (200, 1, 'Isolate affected host from network', false, NULL);
            """);
        }

        // 6. outOfOrder=false phải fail validation dự kiến
        Flyway regularFlyway = Flyway.configure()
                .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .outOfOrder(false)
                .load();

        assertThatThrownBy(regularFlyway::validate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("4.1");

        // 7. Chạy maintenance với outOfOrder=true thành công
        Flyway maintenanceFlyway = Flyway.configure()
                .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .outOfOrder(true)
                .load();

        MigrateResult maintenanceResult = maintenanceFlyway.migrate();
        assertThat(maintenanceResult.success).isTrue();
        assertThat(maintenanceResult.migrationsExecuted).isEqualTo(1); // Chỉ chạy đúng V4.1

        // 8. Xác minh sample records và các giá trị quan trọng vẫn giữ nguyên vẹn
        try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM incident_iocs WHERE id = 100")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("value")).isEqualTo("198.51.100.23");
                assertThat(rs.getString("type")).isEqualTo("IP");
                assertThat(rs.getString("status")).isEqualTo("ACTIVE");
                assertThat(rs.getString("description")).isEqualTo("Sample C2 IP from legacy DB");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM incident_tasks WHERE id = 200")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("task_name")).isEqualTo("Isolate affected host from network");
                assertThat(rs.getBoolean("is_completed")).isFalse();
                assertThat(rs.getObject("completed_by")).isNull();
            }

            // Kiểm tra V4.1 đã được ghi nhận trong flyway_schema_history
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM flyway_schema_history WHERE version = '4.1'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("success")).isTrue();
            }
        }

        // 9. Tắt outOfOrder (outOfOrder=false), validate lại thành công
        Flyway validateFlyway = Flyway.configure()
                .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .outOfOrder(false)
                .load();

        validateFlyway.validate(); // Không được ném ngoại lệ
    }

    @Test
    @DisplayName("Kịch bản 3 (Legacy-Incompatible): Foreign key sai delete action (RESTRICT/NO ACTION) khiến V4.1 fail-closed, không ghi nhận V4.1, dữ liệu mẫu an toàn")
    void testLegacyIncompatibleDatabaseFailsClosedOnWrongDeleteAction() throws Exception {
        String dbUrl = createIsolatedDatabase("db_legacy_incompat_fk");

        // 1. Migrate đến V4
        Flyway flywayV4 = Flyway.configure()
                .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("4")
                .load();
        flywayV4.migrate();

        // 2. Tạo bảng incident_iocs với sai khác thực tế: FK incident_id là ON DELETE NO ACTION (thay vì CASCADE)
        try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE incident_iocs (
                    id BIGSERIAL PRIMARY KEY,
                    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE NO ACTION, -- SAI KHÁC: NO ACTION thay vì CASCADE
                    type VARCHAR(30) NOT NULL,
                    value VARCHAR(255) NOT NULL,
                    description TEXT,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW()
                );
                CREATE INDEX idx_incident_iocs_incident_id ON incident_iocs(incident_id);
                CREATE INDEX idx_incident_iocs_type ON incident_iocs(type);
                CREATE INDEX idx_incident_iocs_value ON incident_iocs(value);

                CREATE TABLE incident_tasks (
                    id BIGSERIAL PRIMARY KEY,
                    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
                    task_name VARCHAR(255) NOT NULL,
                    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
                    completed_at TIMESTAMP,
                    completed_by BIGINT REFERENCES users(id) ON DELETE SET NULL
                );
                CREATE INDEX idx_incident_tasks_incident_id ON incident_tasks(incident_id);
                CREATE INDEX idx_incident_tasks_is_completed ON incident_tasks(is_completed);
            """);

            // Áp dụng V5 DDL
            stmt.execute("""
                ALTER TABLE incident_iocs ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
                ALTER TABLE incident_iocs ADD COLUMN IF NOT EXISTS removed_at TIMESTAMP;
                ALTER TABLE incident_iocs ADD COLUMN IF NOT EXISTS removed_by BIGINT REFERENCES users(id);
            """);

            // Ghi nhận V5
            Integer v5Checksum = resolveV5Checksum(dbUrl);
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO flyway_schema_history (
                    installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
                ) VALUES (5, '5', 'ioc soft delete', 'SQL', 'V5__ioc_soft_delete.sql', ?, 'test', NOW(), 10, TRUE)
            """)) {
                ps.setInt(1, v5Checksum);
                ps.executeUpdate();
            }

            // Chèn sample data (trước tiên chèn incident cha)
            stmt.execute("""
                INSERT INTO incidents (id, incident_code, title, severity, status, created_at)
                VALUES (1, 'INC-TEST-002', 'Sample Legacy Incident 2', 'CRITICAL', 'NEW', NOW());

                INSERT INTO incident_iocs (id, incident_id, type, value, description, status, created_at)
                VALUES (300, 1, 'DOMAIN', 'malicious-c2-domain.com', 'Sample Incompatible Domain', 'ACTIVE', NOW());
            """);
        }

        // 3. Chạy maintenance với outOfOrder=true: PHẢI FAIL-CLOSED
        Flyway maintenanceFlyway = Flyway.configure()
                .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .outOfOrder(true)
                .load();

        assertThatThrownBy(maintenanceFlyway::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Flyway V4.1 Validation Error")
                .hasMessageContaining("CASCADE");

        // 4. Xác minh V4.1 KHÔNG được ghi nhận vào flyway_schema_history
        try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4.1'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }

            // 5. Xác minh dữ liệu mẫu không bị xóa hoặc thay đổi
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM incident_iocs WHERE id = 300")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("value")).isEqualTo("malicious-c2-domain.com");
                assertThat(rs.getString("type")).isEqualTo("DOMAIN");
            }
        }
    }

    @Test
    @DisplayName("Kịch bản 3 mở rộng (Legacy-Incompatible Wrong Column Type): Sai kiểu dữ liệu cột (VARCHAR thay vì BOOLEAN) khiến V4.1 fail-closed")
    void testLegacyIncompatibleDatabaseFailsClosedOnWrongColumnType() throws Exception {
        String dbUrl = createIsolatedDatabase("db_legacy_incompat_col");

        // 1. Migrate đến V4
        Flyway flywayV4 = Flyway.configure()
                .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("4")
                .load();
        flywayV4.migrate();

        // 2. Tạo bảng incident_tasks thiếu cột bắt buộc 'is_completed'
        try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE incident_iocs (
                    id BIGSERIAL PRIMARY KEY,
                    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
                    type VARCHAR(30) NOT NULL,
                    value VARCHAR(255) NOT NULL,
                    description TEXT,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW()
                );
                CREATE INDEX idx_incident_iocs_incident_id ON incident_iocs(incident_id);
                CREATE INDEX idx_incident_iocs_type ON incident_iocs(type);
                CREATE INDEX idx_incident_iocs_value ON incident_iocs(value);

                CREATE TABLE incident_tasks (
                    id BIGSERIAL PRIMARY KEY,
                    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
                    task_name VARCHAR(255) NOT NULL,
                    is_completed VARCHAR(10) NOT NULL DEFAULT 'false' -- SAI KIỂU DỮ LIỆU: VARCHAR thay vì BOOLEAN
                );
                CREATE INDEX idx_incident_tasks_incident_id ON incident_tasks(incident_id);
                CREATE INDEX idx_incident_tasks_is_completed ON incident_tasks(is_completed);
            """);

            // Áp dụng V5 DDL
            stmt.execute("""
                ALTER TABLE incident_iocs ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
                ALTER TABLE incident_iocs ADD COLUMN IF NOT EXISTS removed_at TIMESTAMP;
                ALTER TABLE incident_iocs ADD COLUMN IF NOT EXISTS removed_by BIGINT REFERENCES users(id);
            """);

            // Ghi nhận V5
            Integer v5Checksum = resolveV5Checksum(dbUrl);
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO flyway_schema_history (
                    installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
                ) VALUES (5, '5', 'ioc soft delete', 'SQL', 'V5__ioc_soft_delete.sql', ?, 'test', NOW(), 10, TRUE)
            """)) {
                ps.setInt(1, v5Checksum);
                ps.executeUpdate();
            }

            // Chèn incident cha và sample task
            stmt.execute("""
                INSERT INTO incidents (id, incident_code, title, severity, status, created_at)
                VALUES (1, 'INC-TEST-003', 'Sample Legacy Incident 3', 'LOW', 'NEW', NOW());

                INSERT INTO incident_tasks (id, incident_id, task_name)
                VALUES (400, 1, 'Incomplete task definition in legacy DB');
            """);
        }

        // 3. Chạy maintenance outOfOrder=true: PHẢI FAIL-CLOSED
        Flyway maintenanceFlyway = Flyway.configure()
                .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .outOfOrder(true)
                .load();

        assertThatThrownBy(maintenanceFlyway::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Flyway V4.1 Validation Error")
                .hasMessageContaining("is_completed");

        // 4. Xác minh V4.1 KHÔNG được ghi nhận
        try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4.1'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }

            // Dữ liệu mẫu vẫn nguyên vẹn
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM incident_tasks WHERE id = 400")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("task_name")).isEqualTo("Incomplete task definition in legacy DB");
            }
        }
    }
}
