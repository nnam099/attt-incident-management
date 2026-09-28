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
2. **Sai lệch hoặc thiếu cột**: Thiếu cột bắt buộc, sai độ dài `VARCHAR`, sai kiểu dữ liệu (ví dụ `incident_id` là `INTEGER` thay vì `BIGINT`, `is_completed` là `INT` thay vì `BOOLEAN`), hoặc sai `NULL / NOT NULL`.
3. **Composite Primary Key bất thường**: Bảng có composite primary key như `(id, incident_id)` thay vì single-column PK trên `id`.
4. **Thiếu cơ chế sinh ID**: Cột `id` thiếu sequence/identity (`BIGSERIAL` hoặc `GENERATED AS IDENTITY`).
5. **Index cùng tên nhưng khác định nghĩa**: Index bị đánh tráo bởi partial index (có mệnh đề `WHERE`), expression index hoặc sai thứ tự cột.
6. **Multi-schema poisoning**: Tồn tại bảng cùng tên ở schema khác gây nhập nhằng nếu câu truy vấn không gắn chặt với `current_schema()`.

Vì vậy, script migration `V4_1__create_iocs_and_tasks.sql` đã được tích hợp khối **PL/pgSQL Validation Fail-Closed**: gắn chặt với `current_schema()`, nếu các bảng đã tồn tại nhưng có cấu trúc sai lệch (không tương thích), migration sẽ **lập tức ném exception và rollback toàn bộ transaction**, kiên quyết không âm thầm ghi nhận `V4.1` vào bảng `flyway_schema_history`.

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

Trước khi thực hiện migration trên hệ thống legacy, DBA **bắt buộc** chạy script truy vấn sau (sử dụng schema hiện hành) để phát hiện sớm các sai lệch schema:

```sql
-- Thiết lập schema cần kiểm tra (hoặc mặc định current_schema())
SET search_path TO public;

-- 1. Kiểm tra sự tồn tại của 2 bảng trong schema hiện tại
SELECT table_name
FROM information_schema.tables
WHERE table_schema = current_schema() AND table_name IN ('incident_iocs', 'incident_tasks');

-- 2. Kiểm tra cột, kiểu dữ liệu, nullability, default và identity
SELECT table_name, column_name, data_type, character_maximum_length, is_nullable, column_default, is_identity
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name IN ('incident_iocs', 'incident_tasks')
ORDER BY table_name, ordinal_position;

-- Tiêu chuẩn:
-- - incident_iocs: id (BIGINT, not null, nextval/identity), incident_id (BIGINT, not null),
--   type (VARCHAR>=30, not null), value (VARCHAR>=255, not null), description (text/varchar, nullable),
--   created_at (timestamp without time zone, not null, has default).
-- - incident_tasks: id (BIGINT, not null, nextval/identity), incident_id (BIGINT, not null),
--   task_name (VARCHAR>=255, not null), is_completed (BOOLEAN, not null, default false),
--   completed_at (timestamp without time zone, nullable), completed_by (BIGINT, nullable).

-- 3. Kiểm tra Primary Key (phải là PK đơn trên cột id)
SELECT tbl.relname AS table_name, c.conname, array_length(c.conkey, 1) AS col_count, att.attname AS pk_column
FROM pg_constraint c
JOIN pg_class tbl ON c.conrelid = tbl.oid
JOIN pg_namespace ns ON tbl.relnamespace = ns.oid
JOIN pg_attribute att ON att.attrelid = tbl.oid AND att.attnum = c.conkey[1]
WHERE ns.nspname = current_schema()
  AND tbl.relname IN ('incident_iocs', 'incident_tasks')
  AND c.contype = 'p';

-- 4. BẮT BUỘC: Kiểm tra Foreign Key, Delete Action (confdeltype) và target schema
SELECT
    src_tbl.relname AS source_table,
    src_att.attname AS source_column,
    tgt_ns.nspname AS target_schema,
    tgt_tbl.relname AS target_table,
    tgt_att.attname AS target_column,
    c.confdeltype,
    c.conname
FROM pg_constraint c
JOIN pg_class src_tbl ON c.conrelid = src_tbl.oid
JOIN pg_namespace src_ns ON src_tbl.relnamespace = src_ns.oid
JOIN pg_attribute src_att ON src_att.attrelid = src_tbl.oid AND src_att.attnum = c.conkey[1]
JOIN pg_class tgt_tbl ON c.confrelid = tgt_tbl.oid
JOIN pg_namespace tgt_ns ON tgt_tbl.relnamespace = tgt_ns.oid
JOIN pg_attribute tgt_att ON tgt_att.attrelid = tgt_tbl.oid AND tgt_att.attnum = c.confkey[1]
WHERE src_ns.nspname = current_schema()
  AND src_tbl.relname IN ('incident_iocs', 'incident_tasks')
  AND c.contype = 'f';

-- Tiêu chuẩn chấp nhận:
-- - incident_iocs.incident_id -> incidents.id: confdeltype PHẢI LÀ 'c' (CASCADE)
-- - incident_tasks.incident_id -> incidents.id: confdeltype PHẢI LÀ 'c' (CASCADE)
-- - incident_tasks.completed_by -> users.id: confdeltype PHẢI LÀ 'n' (SET NULL)
-- - target_schema PHẢI trùng với current_schema()

-- 5. Kiểm tra định nghĩa Indexes qua PostgreSQL Catalog
SELECT
    tbl_cls.relname AS table_name,
    idx_cls.relname AS index_name,
    att.attname AS column_name,
    i.indisvalid,
    i.indisready,
    i.indnkeyatts,
    (i.indpred IS NOT NULL) AS is_partial,
    (i.indexprs IS NOT NULL) AS is_expression
FROM pg_index i
JOIN pg_class idx_cls ON i.indexrelid = idx_cls.oid
JOIN pg_class tbl_cls ON i.indrelid = tbl_cls.oid
JOIN pg_namespace ns ON tbl_cls.relnamespace = ns.oid
JOIN pg_attribute att ON att.attrelid = tbl_cls.oid AND att.attnum = i.indkey[0]
WHERE ns.nspname = current_schema()
  AND tbl_cls.relname IN ('incident_iocs', 'incident_tasks')
ORDER BY table_name, index_name;

-- Tiêu chuẩn: indisvalid=true, indisready=true, indnkeyatts=1, is_partial=false, is_expression=false.
```

> [!IMPORTANT]
> Nếu `confdeltype` đang là `r` (RESTRICT) hoặc `a` (NO ACTION), DBA cần cập nhật constraint trước khi chạy migration:
> ```sql
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

### Bước 2: Sao Lưu & Kiểm Chứng Khả Năng Khôi Phục (Backup & Restore Verification)

1. **Khởi tạo biến tên file sao lưu duy nhất**:
   ```bash
   BACKUP_FILE="pre_v4_1_backup_$(date +%Y%m%d_%H%M%S).dump"
   ```

2. **Thực hiện sao lưu**:
   ```bash
   # Sử dụng file ~/.pgpass hoặc biến PGPASSFILE có quyền hạn 0600; KHÔNG truyền password qua tham số CLI
   export PGPASSFILE="/path/to/.secure_pgpass"
   pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -F c -b -v -f "$BACKUP_FILE" "$DB_NAME"
   ```

3. **Kiểm tra tính toàn vẹn của file archive (Archive Readability)**:
   ```bash
   # Lệnh pg_restore -l CHỈ kiểm tra danh mục lưu trữ của file dump có đọc được hay không
   pg_restore -l "$BACKUP_FILE" > /dev/null
   ```

4. **Kiểm chứng khả năng phục hồi thực tế (Real Restore Verification)**:
   > [!IMPORTANT]
   > `pg_restore -l` **KHÔNG PHẢI** là restore verification. Để chứng minh bản backup thực sự khôi phục được dữ liệu nghiệp vụ, bắt buộc phải thử nghiệm restore vào một database độc lập/cô lập (không restore đè lên database production).

   ```bash
   RESTORE_VERIFY_DB="incident_db_verify_$(date +%Y%m%d_%H%M%S)"

   # Xác thực tên database kiểm tra trước khi thực hiện thao tác
   if [[ -z "$RESTORE_VERIFY_DB" || "$RESTORE_VERIFY_DB" != incident_db_verify_* ]]; then
       echo "Lỗi: Tên database kiểm chứng không hợp lệ!" >&2
       exit 1
   fi

   # Tạo database cô lập để kiểm tra phục hồi (lưu ý owner, collation, extensions tương ứng)
   createdb -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" "$RESTORE_VERIFY_DB"

   # Phục hồi dữ liệu vào database cô lập
   pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$RESTORE_VERIFY_DB" -v "$BACKUP_FILE"

   # Chạy sanity queries kiểm tra tính toàn vẹn dữ liệu
   psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$RESTORE_VERIFY_DB" -c "
       SELECT count(*) AS total_incidents FROM incidents;
       SELECT count(*) AS total_users FROM users;
       SELECT count(*) AS total_iocs FROM incident_iocs;
       SELECT count(*) AS total_tasks FROM incident_tasks;
   "

   # Dọn dẹp an toàn database kiểm chứng sau khi xác nhận thành công
   dropdb -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" "$RESTORE_VERIFY_DB"
   ```

### Bước 3: Chạy Tiến Trình Maintenance Chuyên Dụng (One-Shot Lifecycle)
Chỉ thực hiện qua công cụ bảo trì chuyên dụng có vòng đời đóng ngay sau khi hoàn thành (One-shot Job / Flyway CLI / Flyway Container).

#### Phương án A: Sử dụng Docker Flyway CLI (Khuyến nghị cho Container Platform)
```bash
# Tạo file cấu hình tạm thời với quyền hạn chặt chẽ (0600)
TEMP_CONF="$(mktemp)"
chmod 600 "$TEMP_CONF"

# Thiết lập cleanup trap để luôn xóa file cấu hình ngay cả khi tiến trình bị ngắt hoặc lỗi
cleanup() {
    rm -f -- "$TEMP_CONF"
}
trap cleanup EXIT INT TERM

# Ghi cấu hình (KHÔNG bật shell tracing set -x và KHÔNG log nội dung file secret)
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

# Xóa file cấu hình qua hàm cleanup (trap sẽ tự động gọi)
cleanup
trap - EXIT INT TERM
```

> [!NOTE]
> Lưu ý về bảo mật file tạm: Các công cụ như `shred` không đảm bảo xóa vật lý hoàn toàn trên SSD hiện đại (do wear-leveling) hoặc overlay filesystem của container. Do đó, nguyên tắc cốt lõi là lưu file tạm trên tmpfs (RAM-backed, ví dụ `/tmp` hoặc `mktemp`) với quyền hạn `0600` và xóa ngay lập tức thông qua shell trap.

#### Phương án B: Sử dụng Kubernetes One-Shot Migration Job
Tạo một Kubernetes Job chuyên dụng với `restartPolicy: Never`, nạp credentials từ Kubernetes Secret (không log secret ra stdout), cấu hình flag `outOfOrder=true` duy nhất cho job này.

---

## 5. Xác Minh Sau Bảo Trì (Post-Maintenance Verification)

Sau khi tiến trình one-shot hoàn tất, DBA bắt buộc kiểm tra các mục sau trước khi đưa ứng dụng hoạt động trở lại:

### 1. Tắt chế độ `outOfOrder` & Dọn dẹp Secrets
* Đảm bảo cấu hình production (`application.yml` và môi trường runtime) **KHÔNG** chứa `FLYWAY_OUT_OF_ORDER=true`. Thuộc tính phải ở giá trị mặc định (`false`).
* Xóa/unset các biến môi trường chứa secret tạm thời (`unset DB_PASSWORD`, `unset PGPASSFILE`).

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

### 4. Chạy Flyway Validate Độc Lập & Khởi Động Lại Hệ Thống
1. Chạy `flyway validate` độc lập với `outOfOrder=false` mặc định:
   ```bash
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
   pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" --clean --if-exists -v "$BACKUP_FILE"
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
