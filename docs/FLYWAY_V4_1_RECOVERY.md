# Hướng Dẫn Vận Hành & Khắc Phục Tương Thích Flyway V4.1 (Migration Recovery Runbook)

Tài liệu này cung cấp quy trình chuẩn kỹ thuật dành cho DevOps / Database Administrator (DBA) để kiểm tra, bảo trì và xử lý tương thích migration `V4_1__create_iocs_and_tasks.sql` trên các môi trường cơ sở dữ liệu khác nhau.

---

## 1. Bối Cảnh Kỹ Thuật & Cơ Chế Hoạt Động

* **Migration `V4.1`**: Khởi tạo bảng nền `incident_iocs` và `incident_tasks` phục vụ cho mô hình SOC Playbook & IoC Management.
* **Tính Idempotent**: Toàn bộ script `V4.1` được thiết kế với cấu trúc idempotent:
  * `CREATE TABLE IF NOT EXISTS incident_iocs (...)`
  * `CREATE TABLE IF NOT EXISTS incident_tasks (...)`
  * `CREATE INDEX IF NOT EXISTS ...`
  * Foreign key `incident_id` sử dụng `ON DELETE CASCADE` (khớp với JPA `CascadeType.ALL, orphanRemoval = true` trên Aggregate Root `Incident`).
  * Foreign key `completed_by` sử dụng `ON DELETE SET NULL` để bảo toàn lịch sử xử lý tác vụ khi tài khoản người dùng bị xóa.
* **Rủi ro trên Legacy Database**: Nếu một cơ sở dữ liệu đã từng được migrate lên version `5` (`V5__ioc_soft_delete.sql`) mà chưa từng ghi nhận version `4.1` trong bảng `flyway_schema_history`, Flyway ở chế độ mặc định (`outOfOrder = false`) sẽ phát hiện trạng thái:
  ```text
  Validate failed: Migrations have failed validation
  Detected resolved migration not applied to database: 4.1.
  To ignore this migration, set -ignoreMigrationPatterns='*:ignored'. To allow executing this migration, set -outOfOrder=true.
  ```

---

## 2. Cách Nhận Biết Trạng Thái Database (Fresh vs. Legacy)

Trước khi thực hiện bất kỳ thao tác nào, truy cập database qua `psql` hoặc công cụ quản trị và kiểm tra bảng lịch sử:

```sql
SELECT installed_rank, version, description, type, script, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

### 2.1. Nhận Diện Database Mới (Fresh Database / Clean Environment)
* **Dấu hiệu**: Bảng `flyway_schema_history` chưa tồn tại, hoặc chỉ mới migrate đến version $\le 4$.
* **Hành vi**: Flyway tự động áp dụng tuần tự `V1 -> V2 -> V3 -> V4 -> V4.1 -> V5`. Không cần bất kỳ thao tác bảo trì thủ công nào.

### 2.2. Nhận Diện Database Cũ (Legacy Database)
* **Dấu hiệu**: Trong `flyway_schema_history` đã có bản ghi `version = '5'`, nhưng **KHÔNG CÓ** bản ghi nào cho `version = '4.1'`.
* **Hành vi**: Khởi động ứng dụng sẽ bị chặn lại ở bước Flyway Validate với lỗi `Detected resolved migration not applied to database: 4.1`.

---

## 3. Lựa Chọn Nhanh Cho Môi Trường Demo / Test / Development

> [!TIP]
> Đối với môi trường dev cục bộ, kiểm thử hoặc demo **không cần bảo lưu dữ liệu cũ**, giải pháp nhanh nhất và sạch sẽ nhất là xóa và khởi tạo lại database từ đầu:
> ```bash
> # Drop và tái tạo schema public
> docker exec -i <postgres-container> psql -U <user> -d <dbname> -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
> ```
> Khi ứng dụng khởi động lại, Flyway sẽ tạo mới hoàn toàn theo đúng thứ tự chuẩn `V1 -> V2 -> V3 -> V4 -> V4.1 -> V5`.

---

## 4. Quy Trình Bảo Trì Một Lần Cho Hệ Thống Legacy (Cần Giữ Dữ Liệu)

Đối với môi trường staging hoặc database có dữ liệu nghiệp vụ bắt buộc phải bảo lưu, thực hiện quy trình sau:

### Bước 1: Sao Lưu Cơ Sở Dữ Liệu (Bắt Buộc)
Luôn luôn tạo bản sao lưu toàn vẹn trước khi bảo trì:
```bash
pg_dump -h <host> -p <port> -U <user> -F c -b -v -f "pre_v4_1_backup_$(date +%Y%m%d_%H%M%S).dump" <dbname>
```

### Bước 2: Chạy Maintenance Migration Với Cờ `outOfOrder=true` (One-Shot)
Kích hoạt Flyway migrate ở chế độ one-shot với tham số `outOfOrder=true` tạm thời (qua CLI, profile riêng hoặc biến môi trường tạm thời cho container migration):

```bash
# Ví dụ chạy qua biến môi trường tạm thời cho lệnh migrate một lần:
FLYWAY_OUT_OF_ORDER=true java -jar app.jar
# Hoặc sử dụng Flyway CLI độc lập:
flyway -url=jdbc:postgresql://<host>:<port>/<dbname> -user=<user> -password=<pass> -outOfOrder=true migrate
```

* **Kết quả**: Flyway phát hiện `V4.1` nằm ngoài thứ tự, thực thi script `V4_1__create_iocs_and_tasks.sql`.
* Nhờ tính chất `IF NOT EXISTS`, các bảng và index đã có từ trước sẽ được giữ nguyên vẹn 100%, không xảy ra lỗi xung đột `already exists`, và không làm biến đổi bất kỳ dữ liệu nào hiện có.
* Bản ghi `version = '4.1'` được chèn hợp lệ vào bảng `flyway_schema_history`.

### Bước 3: Tắt `outOfOrder` Về Mặc Định (`false`)
Ngay sau khi lệnh migrate hoàn tất:
* Gỡ bỏ hoàn toàn biến `FLYWAY_OUT_OF_ORDER` khỏi cấu hình môi trường.
* Đảm bảo cấu hình production và file `application.yml` luôn duy trì mặc định `flyway.out-of-order: false`.

### Bước 4: Xác Minh Toàn Vẹn Schema & Validation
1. **Kiểm tra schema history**:
   ```sql
   SELECT installed_rank, version, description, success
   FROM flyway_schema_history
   WHERE version IN ('4', '4.1', '5')
   ORDER BY installed_rank;
   ```
   Cả 3 version phải có `success = true`.

2. **Kiểm tra tính toàn vẹn dữ liệu bảng**:
   ```sql
   SELECT count(*) FROM incident_iocs;
   SELECT count(*) FROM incident_tasks;
   ```
   Dữ liệu cũ phải còn nguyên vẹn.

3. **Chạy Validate Thông Thường**:
   Khởi động ứng dụng bình thường (`outOfOrder=false`). Ứng dụng phải vượt qua Flyway Validate với mã trạng thái thành công, không còn thông báo lỗi.

---

## 5. Quy Trình Rollback / Phục Hồi Nếu Có Sự Cố

Nếu quá trình bảo trì gặp sự cố ngoài dự kiến:
1. Dừng tiến trình ứng dụng.
2. Khôi phục lại trạng thái database từ bản sao lưu đã tạo ở Bước 1:
   ```bash
   pg_restore -h <host> -p <port> -U <user> -d <dbname> --clean --if-exists -v "pre_v4_1_backup_<timestamp>.dump"
   ```

---

## 6. Các Điều Cấm Kỵ Trong Vận Hành (Anti-Patterns)

> [!CAUTION]
> 1. **KHÔNG chỉnh sửa trực tiếp dữ liệu bảng `flyway_schema_history` bằng câu lệnh SQL thủ công** (như tự ý `INSERT` / `UPDATE` checksum hoặc rank). Việc này làm sai lệch cơ chế kiểm soát mã băm của Flyway và có thể khiến các lần deploy tiếp theo bị từ chối.
> 2. **KHÔNG chạy `flyway repair` một cách mù quáng**. Lệnh `repair` sẽ căn chỉnh lại checksum của các file migration đã thay đổi trên disk, nhưng không giải quyết được vấn đề thiếu migration version trung gian và có thể che giấu các sai khác cấu trúc nghiêm trọng.
> 3. **KHÔNG cấu hình `spring.flyway.out-of-order=true` làm mặc định toàn cục trong `application.yml`**. Việc bật thường trực chế độ out-of-order làm mất tính xác định (non-deterministic) của quy trình nâng cấp schema, dẫn đến việc các môi trường khác nhau có thể áp dụng migration theo thứ tự khác nhau gây trôi schema (schema drift).
