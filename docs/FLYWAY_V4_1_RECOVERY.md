# Hướng Dẫn Vận Hành & Khắc Phục Tương Thích Flyway V4.1 (Migration Recovery Runbook)

Tài liệu này cung cấp quy trình chuẩn kỹ thuật dành cho DevOps / Database Administrator (DBA) để kiểm tra tiền khả thi (preflight check), bảo trì và xử lý tương thích migration `V4_1__create_iocs_and_tasks.sql` trên các môi trường cơ sở dữ liệu khác nhau.

---

## 1. Bối Cảnh Kỹ Thuật & Giới Hạn Của `IF NOT EXISTS`

### 1.1. Migration V4.1
* **Mục tiêu**: Khởi tạo bảng nền `incident_iocs` và `incident_tasks` phục vụ cho mô hình SOC Playbook & IoC Management.
* **Ownership & Delete Behavior chuẩn**:
  * `incident_id`: `ON DELETE CASCADE` (khớp với JPA `CascadeType.ALL, orphanRemoval = true` trên Aggregate Root `Incident`).
  * `completed_by`: `ON DELETE SET NULL` để bảo toàn lịch sử xử lý tác vụ khi tài khoản người dùng bị xóa.

### 1.2. Giới Hạn Của `CREATE TABLE IF NOT EXISTS` & `CREATE INDEX IF NOT EXISTS`
> [!WARNING]
> Cú pháp `IF NOT EXISTS` **CHỈ** ngăn chặn lỗi trùng tên khi đối tượng đã tồn tại; cú pháp này **KHÔNG** chứng minh hoặc bảo đảm rằng đối tượng hiện có có schema tương thích hoặc tương đương với thiết kế chuẩn.

Một database cũ do Hibernate `ddl-auto: update` hoặc thao tác DDL thủ công tạo ra trong quá khứ có thể gặp phải các dạng trôi schema (schema drift) nguy hiểm:
1. **Sai lệch Foreign Key Delete Action**: Ví dụ `incident_id` có foreign key nhưng là `ON DELETE RESTRICT` hoặc `NO ACTION` (mặc định của PostgreSQL khi không khai báo `ON DELETE`). Khi xóa một `Incident`, thay vì cascade xóa IoC/Task theo thiết kế JPA, database sẽ ném lỗi vi phạm khóa ngoại, làm crash luồng nghiệp vụ.
2. **Sai lệch hoặc thiếu cột**: Thiếu cột bắt buộc, sai độ dài `VARCHAR`, sai kiểu dữ liệu (ví dụ `is_completed` là `INT` thay vì `BOOLEAN`), hoặc sai `NULL / NOT NULL`.
3. **Index cùng tên nhưng khác định nghĩa**: Index thiếu cột hoặc sai thứ tự cột.
4. **Dữ liệu không tương thích**: Dữ liệu có thể vi phạm các ràng buộc nghiệp vụ mới.

Vì vậy, script migration `V4_1__create_iocs_and_tasks.sql` đã được tích hợp khối **PL/pgSQL Validation Fail-Closed**: nếu các bảng đã tồn tại nhưng có cấu trúc sai lệch (không tương thích), migration sẽ **lập tức ném exception và rollback toàn bộ transaction**, kiên quyết không âm thầm ghi nhận `V4.1` vào bảng `flyway_schema_history`.

> [!NOTE]
> **Không có tuyên bố "giữ nguyên 100%" vô điều kiện**: Dữ liệu và cấu trúc chỉ được bảo lưu nguyên vẹn **KHI VÀ CHỈ KHI** schema hiện có đã được kiểm chứng tương thích chuẩn thông qua preflight check và khối validation fail-closed của migration.

---

## 2. Nhận Biết Trạng Thái Cơ Sở Dữ Liệu

Truy cập database qua công cụ quản trị (sử dụng credentials an toàn) và kiểm tra bảng lịch sử:

```sql
SELECT installed_rank, version, description, type, script, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

### 2.1. Fresh Database / Clean Environment
* **Dấu hiệu**: Bảng `flyway_schema_history` chưa tồn tại, hoặc mới chỉ migrate đến version $\le 4$.
* **Hành vi**: Ứng dụng tự động áp dụng tuần tự `V1 -> V2 -> V3 -> V4 -> V4.1 -> V5`. Không cần bất kỳ can thiệp bảo trì nào.

### 2.2. Legacy Database Cần Bảo Trì
* **Dấu hiệu**: Bảng `flyway_schema_history` đã có bản ghi `version = '5'`, nhưng **KHÔNG CÓ** bản ghi nào cho `version = '4.1'`.
* **Hành vi**: Khởi động ứng dụng bình thường (`outOfOrder = false`) sẽ bị chặn tại bước Flyway Validate:
  ```text
  Validate failed: Migrations have failed validation
  Detected resolved migration not applied to database: 4.1.
  To ignore this migration, set -ignoreMigrationPatterns='*:ignored'. To allow executing this migration, set -outOfOrder=true.
  ```

---

## 3. Preflight Check: Kiểm Tra Toàn Vẹn Schema Trước Khi Bảo Trì

Trước khi thực hiện migration trên hệ thống legacy, DBA **bắt buộc** chạy script truy vấn sau để phát hiện sớm các sai lệch schema:

```sql
-- 1. Kiểm tra sự tồn tại của 2 bảng
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public' AND table_name IN ('incident_iocs', 'incident_tasks');

-- 2. Kiểm tra cột, kiểu dữ liệu và nullability
SELECT table_name, column_name, data_type, character_maximum_length, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name IN ('incident_iocs', 'incident_tasks')
ORDER BY table_name, ordinal_position;

-- 3. Kiểm tra Primary Key
SELECT tc.table_name, kcu.column_name, tc.constraint_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
WHERE tc.table_schema = 'public'
  AND tc.table_name IN ('incident_iocs', 'incident_tasks')
  AND tc.constraint_type = 'PRIMARY KEY';

-- 4. BẮT BUỘC: Kiểm tra Foreign Key và Delete Action (delete_rule)
SELECT
    tc.table_name,
    kcu.column_name AS fk_column,
    ccu.table_name AS target_table,
    ccu.column_name AS target_column,
    rc.delete_rule,
    tc.constraint_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
JOIN information_schema.referential_constraints rc
  ON tc.constraint_name = rc.constraint_name AND tc.table_schema = rc.constraint_schema
JOIN information_schema.constraint_column_usage ccu
  ON rc.unique_constraint_name = ccu.constraint_name AND rc.unique_constraint_schema = ccu.constraint_schema
WHERE tc.table_schema = 'public'
  AND tc.table_name IN ('incident_iocs', 'incident_tasks');

-- Tiêu chuẩn chấp nhận:
-- - incident_iocs.incident_id -> incidents.id: delete_rule PHẢI LÀ 'CASCADE'
-- - incident_tasks.incident_id -> incidents.id: delete_rule PHẢI LÀ 'CASCADE'
-- - incident_tasks.completed_by -> users.id: delete_rule PHẢI LÀ 'SET NULL'

-- 5. Kiểm tra định nghĩa Indexes
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public' AND tablename IN ('incident_iocs', 'incident_tasks');
```

> [!IMPORTANT]
> Nếu `delete_rule` đang là `RESTRICT` hoặc `NO ACTION`, DBA cần cập nhật constraint trước khi chạy migration:
> ```sql
> -- Ví dụ khắc phục Foreign Key sai delete_rule trên incident_iocs:
> ALTER TABLE incident_iocs DROP CONSTRAINT <tên_constraint_cũ>;
> ALTER TABLE incident_iocs ADD CONSTRAINT fk_incident_iocs_incident
>     FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE;
> ```

---

## 4. Quy Trình Bảo Trì One-Shot Cho Hệ Thống Legacy

> [!CAUTION]
> **TUYỆT ĐỐI KHÔNG SỬ DỤNG LỆNH SAU TRÊN PRODUCTION:**
> ```bash
> # CẤM: Lệnh này khởi động toàn bộ web server, background schedulers và services nghiệp vụ!
> FLYWAY_OUT_OF_ORDER=true java -jar app.jar
> ```
> Việc khởi động ứng dụng đầy đủ với `outOfOrder=true` có nguy cơ gây race condition, cho phép ứng dụng nhận request khi schema chưa được kiểm chứng xong, hoặc kích hoạt các job định kỳ ghi dữ liệu vào database đang bảo trì.

### Bước 1: Thiết Lập Cửa Sổ Bảo Trì & Dừng Ghi Dữ Liệu
1. Thông báo cửa sổ bảo trì (Maintenance Window).
2. **Dừng toàn bộ application replicas và background schedulers** có khả năng ghi dữ liệu vào database:
   ```bash
   # Ví dụ môi trường Kubernetes:
   kubectl scale deployment incident-management-api --replicas=0
   # Ví dụ môi trường Docker Compose:
   docker compose stop backend worker
   ```

### Bước 2: Sao Lưu & Kiểm Chứng Khả Năng Khôi Phục (Backup Verification)
1. Thực hiện sao lưu dữ liệu:
   ```bash
   # Sử dụng biến môi trường PGPASSFILE với quyền hạn chmod 600, KHÔNG đưa mật khẩu vào tham số CLI
   export PGPASSFILE="/path/to/.secure_pgpass"
   pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -F c -b -v -f "pre_v4_1_backup_$(date +%Y%m%d_%H%M%S).dump" "$DB_NAME"
   ```
2. **Kiểm tra khả năng khôi phục (Restore Verification)**:
   Không coi bản backup là hợp lệ nếu chưa kiểm chứng khả năng đọc:
   ```bash
   # Kiểm tra tính nguyên vẹn danh mục archive của bản backup:
   pg_restore -l "pre_v4_1_backup_$(date +%Y%m%d_%H%M%S).dump" > /dev/null
   ```

### Bước 3: Chạy Tiến Trình Maintenance Chuyên Dụng (One-Shot Lifecycle)
Chỉ thực hiện qua công cụ bảo trì chuyên dụng có vòng đời đóng ngay sau khi hoàn thành (One-shot Job / Flyway CLI / Flyway Container).

#### Phương án A: Sử dụng Docker Flyway CLI (Khuyến nghị cho Container Platform)
```bash
# Tạo file cấu hình tạm thời với quyền hạn chặt chẽ (0600)
TEMP_CONF=$(mktemp)
chmod 600 "$TEMP_CONF"

cat <<EOF > "$TEMP_CONF"
flyway.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
flyway.user=${DB_USER}
flyway.password=${DB_PASSWORD}
flyway.locations=filesystem:/migrations
flyway.outOfOrder=true
EOF

# Chạy container migration one-shot
docker run --rm \
  --network host \
  -v "${PWD}/src/main/resources/db/migration:/migrations:ro" \
  -v "${TEMP_CONF}:/flyway/conf/flyway.conf:ro" \
  flyway/flyway:10 migrate

# Xóa an toàn file cấu hình tạm ngay sau khi thực hiện
shred -u "$TEMP_CONF"
```

#### Phương án B: Sử dụng Kubernetes One-Shot Migration Job
Tạo một Kubernetes Job chuyên dụng với `restartPolicy: Never`, nạp credentials từ Kubernetes Secret (không log secret), cấu hình flag `outOfOrder=true` duy nhất cho job này.

---

## 5. Xác Minh Sau Bảo Trì (Post-Maintenance Verification)

Sau khi tiến trình one-shot hoàn tất, DBA bắt buộc kiểm tra các mục sau trước khi đưa ứng dụng hoạt động trở lại:

### 1. Tắt chế độ `outOfOrder`
Đảm bảo cấu hình production (`application.yml` và môi trường runtime) **KHÔNG** chứa `FLYWAY_OUT_OF_ORDER=true`. Thuộc tính phải ở giá trị mặc định (`false`).

### 2. Kiểm tra `flyway_schema_history`
```sql
SELECT installed_rank, version, description, type, script, success, installed_on
FROM flyway_schema_history
WHERE version IN ('4', '4.1', '5')
ORDER BY installed_rank;
```
* Bắt buộc cả 3 version `4`, `4.1`, và `5` đều có `success = true`.

### 3. Kiểm tra tính toàn vẹn dữ liệu mẫu
```sql
-- Xác nhận số lượng bản ghi không bị giảm sút:
SELECT count(*) FROM incident_iocs;
SELECT count(*) FROM incident_tasks;

-- Truy vấn ngẫu nhiên các bản ghi cũ để đảm bảo dữ liệu quan trọng không bị biến đổi:
SELECT id, incident_id, type, value, status FROM incident_iocs LIMIT 5;
SELECT id, incident_id, task_name, is_completed FROM incident_tasks LIMIT 5;
```

### 4. Chạy Flyway Validate & Khởi Động Lại Hệ Thống
1. Khởi động 1 replica ứng dụng ở chế độ kiểm tra hoặc chạy lệnh validate độc lập:
   ```bash
   # Chạy flyway validate với outOfOrder=false mặc định:
   docker run --rm ... flyway/flyway:10 validate
   ```
   Lệnh phải trả về kết quả thành công mà không có cảnh báo nào.
2. Khôi phục lại số lượng replica của ứng dụng:
   ```bash
   kubectl scale deployment incident-management-api --replicas=3
   ```
3. Kiểm tra log ứng dụng: Hibernate JPA `ddl-auto: validate` phải hoàn thành thành công mà không có exception nào.

---

## 6. Quy Trình Phục Hồi Nếu Gặp Sự Cố (Rollback Procedure)

Nếu tiến trình bảo trì gặp lỗi hoặc validation fail-closed phát hiện schema không hợp lệ:
1. Đảm bảo application replicas vẫn đang ở trạng thái dừng (`replicas=0`).
2. Khôi phục lại database từ bản sao lưu đã tạo ở Bước 2:
   ```bash
   export PGPASSFILE="/path/to/.secure_pgpass"
   pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" --clean --if-exists -v "pre_v4_1_backup_<timestamp>.dump"
   ```
3. Kiểm tra lại trạng thái database sau restore.
4. Điều tra nguyên nhân schema drift dựa trên thông báo chi tiết của `Flyway V4.1 Validation Error` và xử lý triệt để trước khi thử lại.

---

## 7. Các Điều Cấm Kỵ Trong Vận Hành (Operational Anti-Patterns)

> [!CAUTION]
> 1. **TUYỆT ĐỐI KHÔNG sửa trực tiếp bảng `flyway_schema_history` bằng SQL thủ công** (như tự ý `INSERT` / `UPDATE` checksum hoặc version). Hành vi này phá vỡ cơ chế kiểm soát mã băm toàn vẹn của Flyway và sẽ dẫn đến lỗi không thể deploy ở các phiên bản sau.
> 2. **TUYỆT ĐỐI KHÔNG sử dụng `flyway repair` để che giấu schema drift**. Lệnh `repair` căn chỉnh lại checksum trong database theo disk, nhưng hoàn toàn bất lực trong việc sửa sai lệch cấu trúc bảng/khóa ngoại và có thể làm trầm trọng thêm sự cố dữ liệu.
> 3. **TUYỆT ĐỐI KHÔNG bật `spring.flyway.out-of-order=true` toàn cục/thường trực**. Bật thường trực out-of-order làm mất tính tiền định của quy trình nâng cấp schema giữa các môi trường staging và production.
> 4. **TUYỆT ĐỐI KHÔNG truyền mật khẩu database trực tiếp trên command-line argument** (ví dụ `-password=mypass` hoặc `-p secret`). Tham số này sẽ bị lộ qua `ps -ef`, `history` của shell và log hệ thống. Luôn dùng file cấu hình bảo mật hạn chế quyền (0600), biến môi trường bảo vệ hoặc cơ chế secret manager.
