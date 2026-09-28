# Admin Recovery Runbook

**Hệ thống:** ATTT Incident Management<br>
**Tài liệu:** Quy trình khôi phục tài khoản admin<br>
**Phiên bản:** 1.0 — Vòng bảo mật 2<br>
**Phân loại:** 🔒 INTERNAL — Chỉ lưu hành nội bộ, không commit plaintext secret

---

## 1. Bối cảnh

Migration `V6__harden_default_admin_and_token_version.sql` tự động **vô hiệu hóa** tài khoản seed `admin / Admin@123` khi Flyway chạy lần đầu trên database production. Đây là biện pháp bắt buộc để loại bỏ default credential.

Khi cần khôi phục tài khoản admin (ví dụ: sau khi database bị restore, hoặc khi operator quên mật khẩu), hãy thực hiện **đúng quy trình này** — không can thiệp trực tiếp vào database bằng SQL UPDATE.

---

## 2. Điều kiện tiên quyết

| Điều kiện | Kiểm tra |
|-----------|----------|
| Có quyền SSH vào máy chủ application | `ssh user@app-host` |
| Biết đường dẫn file JAR | `ls -la /opt/attt/incident-management-*.jar` |
| Có thể kết nối tới PostgreSQL từ máy chủ app | `psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c '\conninfo'` |
| Không có tiến trình `admin-recovery` nào đang chạy | `pgrep -f "admin-recovery"` |

---

## 3. Quy trình khôi phục (một lần, không thể lặp lại trong cùng session)

### Bước 3.1 — Chuẩn bị mật khẩu mới

```bash
# Tạo mật khẩu ngẫu nhiên đủ mạnh (>= 12 ký tự, chữ hoa/thường/số/đặc biệt)
# Ví dụ sử dụng openssl:
NEW_PASS="$(openssl rand -base64 18 | tr -d '=+/' | head -c 14)@Ax1"
echo "Mật khẩu mới (LƯU VÀO PASSWORD MANAGER NGAY): $NEW_PASS"
```

> [!CAUTION]
> **Không lưu mật khẩu vào file, history shell, hay biến môi trường toàn cục.**
> Lưu ngay vào password manager trước khi tiếp tục.

---

### Bước 3.2 — Chạy công cụ recovery (one-shot)

```bash
export RECOVERY_ADMIN_NEW_PASSWORD="<mật-khẩu-mới>"

java \
  -Dspring.profiles.active=admin-recovery \
  -Dspring.datasource.url="$DB_URL" \
  -Dspring.datasource.username="$DB_USER" \
  -Dspring.datasource.password="$DB_PASS" \
  -jar /opt/attt/incident-management-*.jar
```

**Xác nhận thành công** khi output log chứa:

```
=== ADMIN RECOVERY SUCCESSFUL ===
  • Account enabled: true
  • Password updated and hashed (BCrypt)
  • token_version incremented — all existing JWTs for admin are now INVALID
  • All refresh tokens for admin revoked
  ...
=================================================================
```

Tiến trình sẽ **tự thoát** (`System.exit(0)`) ngay sau khi hoàn thành.

> [!IMPORTANT]
> Exit code khác 0 nghĩa là recovery **thất bại**. Xem bảng mã lỗi ở Mục 5.

---

### Bước 3.3 — Dọn dẹp sau recovery

```bash
# Xóa biến môi trường chứa mật khẩu khỏi shell hiện tại
unset RECOVERY_ADMIN_NEW_PASSWORD

# Xóa lịch sử lệnh gần nhất (bash)
history -d $(history | tail -n 1 | awk '{print $1}')

# Hoặc xóa toàn bộ history của session (nếu không có lệnh quan trọng khác)
# history -c
```

---

### Bước 3.4 — Xác minh sau recovery

```bash
# Kiểm tra trạng thái tài khoản trong database
psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -c \
  "SELECT id, username, enabled, token_version, password_changed_at, failed_login_attempts
   FROM users WHERE username = 'admin';"
```

Kết quả mong đợi:

| Cột | Giá trị mong đợi |
|-----|-----------------|
| `enabled` | `true` |
| `token_version` | Giá trị > 0 (tăng lên 1 so với trước) |
| `password_changed_at` | Timestamp gần đây |
| `failed_login_attempts` | `0` |

```bash
# Kiểm tra không còn refresh token cũ
psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -c \
  "SELECT COUNT(*) FROM refresh_tokens rt
   JOIN users u ON rt.user_id = u.id WHERE u.username = 'admin';"
# Phải là 0
```

---

### Bước 3.5 — Đăng nhập và đổi mật khẩu lần nữa

1. Đăng nhập vào hệ thống bằng mật khẩu mới.
2. Ngay lập tức vào phần **Đổi mật khẩu** trong UI và đặt mật khẩu vĩnh viễn khác.
3. Cập nhật mục nhập trong password manager.

---

## 4. Lý giải thiết kế

| Quyết định | Lý do |
|-----------|-------|
| Đọc mật khẩu từ env var | Tránh lộ plaintext qua `ps aux`, log file, hay `application.properties` |
| `System.exit(0)` sau khi chạy | JAR không thể tiếp tục hoạt động như HTTP server sau recovery |
| Tăng `token_version` | Vô hiệu hóa tức thì tất cả JWT cũ của admin mà không cần đổi signing key |
| Xóa refresh token | Ngăn tái xác thực qua refresh token cũ sau khi account được enable lại |
| Reject mật khẩu seed | Ngăn admin đặt lại đúng mật khẩu ban đầu `Admin@123` |
| Không có HTTP endpoint | Loại bỏ bề mặt tấn công (attack surface) cho thao tác đặc quyền cao |

---

## 5. Bảng mã lỗi exit code

| Exit Code | Nguyên nhân | Hành động |
|-----------|-------------|-----------|
| `0` | Thành công | Tiến hành Bước 3.3 |
| `2` | `RECOVERY_ADMIN_NEW_PASSWORD` chưa được set | Set env var và thử lại |
| `3` | Mật khẩu không đạt chính sách | Chọn mật khẩu mạnh hơn (≥12 ký tự, chữ hoa/thường/số/đặc biệt) |
| `4` | Mật khẩu mới trùng với seed `Admin@123` | Chọn mật khẩu hoàn toàn khác |
| `5` | Mật khẩu nằm trong danh sách yếu | Chọn mật khẩu không có trong danh sách thông dụng |
| `6` | User `admin` không tồn tại trong DB | Kiểm tra kết nối DB và schema |
| `7` | User `admin` không có role ADMIN | Kiểm tra bảng `user_roles` — cần can thiệp thủ công |
| `≠ 0` | Lỗi khác (exception) | Xem log đầy đủ — có thể là lỗi kết nối DB |

---

## 6. Trường hợp khẩn cấp — Can thiệp trực tiếp DB (chỉ khi JAR không khởi động được)

> [!WARNING]
> Chỉ thực hiện khi không thể chạy được JAR. Mọi thao tác phải được ghi vào biên bản sự cố.

```sql
-- 1. Tạo hash BCrypt cho mật khẩu mới (thực hiện bên ngoài psql, ví dụ bằng htpasswd hoặc Python)
--    python3 -c "import bcrypt; print(bcrypt.hashpw(b'NewPass@123!', bcrypt.gensalt(10)).decode())"

-- 2. Cập nhật trong psql (thay thế <hash> bằng kết quả bước 1)
BEGIN;
UPDATE users
SET password            = '<bcrypt-hash>',
    enabled             = TRUE,
    failed_login_attempts = 0,
    account_locked_until = NULL,
    token_version       = token_version + 1,
    password_changed_at = NOW()
WHERE username = 'admin';

DELETE FROM refresh_tokens WHERE user_id = (SELECT id FROM users WHERE username = 'admin');

-- Xác nhận trước khi commit:
SELECT username, enabled, token_version, password_changed_at FROM users WHERE username = 'admin';
COMMIT;
```

> [!CAUTION]
> Sau khi commit SQL trực tiếp, phải ghi rõ vào biên bản:
> - Thời điểm can thiệp
> - Người thực hiện
> - Lý do không dùng được JAR
> - SHA của `password_changed_at` mới

---

## 7. Liên quan

- Migration: [`V6__harden_default_admin_and_token_version.sql`](../src/main/resources/db/migration/V6__harden_default_admin_and_token_version.sql)
- Recovery runner: [`AdminRecoveryRunner.java`](../src/main/java/com/attt/incident/security/AdminRecoveryRunner.java)
- Token versioning: [`JwtService.java`](../src/main/java/com/attt/incident/security/JwtService.java), [`JwtAuthFilter.java`](../src/main/java/com/attt/incident/security/JwtAuthFilter.java)
- Flyway V4.1 runbook: [`FLYWAY_V4_1_RECOVERY.md`](FLYWAY_V4_1_RECOVERY.md)
