# 🛡️ Hệ thống Quản lý Sự cố ATTT chuẩn SOC (Security Operations Center)

Một nền tảng quản lý sự cố an toàn thông tin toàn diện, được nâng cấp theo tiêu chuẩn của các trung tâm điều hành an ninh mạng (SOC) chuyên nghiệp (tương tự như Splunk, QRadar). Hệ thống giám sát vòng đời sự cố từ khâu tiếp nhận, điều tra, đến xuất báo cáo, với sự nhấn mạnh vào tuân thủ thời gian xử lý (SLA) và tính bất biến của dữ liệu kiểm toán.

## 🌟 Các tính năng SOC Chuyên nghiệp (Mới Nâng cấp)

1. **Dashboard Điều hành (Giao diện Dark Mode/Glassmorphism):**
   - Hỗ trợ hiển thị tỷ lệ tuân thủ **SLA Compliance Rate** thời gian thực.
   - Phân tích chất lượng cảnh báo (**Resolution Breakdown**): Tự động phân loại `TRUE_POSITIVE` và `FALSE_POSITIVE` để đánh giá hiệu suất hệ thống giám sát.
   - Hỗ trợ Dark Mode chuẩn SOC giúp chuyên viên trực 24/7 không bị mỏi mắt.

2. **Quản lý Vòng đời Sự cố chuẩn SANS/NIST:**
   - Luồng trạng thái nghiêm ngặt: `NEW -> TRIAGE -> INVESTIGATING -> CONTAINED -> RESOLVED -> CLOSED`.
   - **SLA Tracking:** Tự động tính toán deadline tiếp nhận (MTTA) và deadline giải quyết (MTTR) dựa trên mức độ nghiêm trọng (Severity).
   - Tự động bôi đỏ cảnh báo khi sự cố vi phạm (Trễ) SLA.

3. **Điều tra & Truy vết (Investigation):**
   - **IoC Tracking:** Cho phép thêm và theo dõi các chỉ số thỏa hiệp (IP độc hại, Hash, Domain) ngay trên sự cố.
   - **Playbook Tasks:** Hỗ trợ tạo danh sách công việc con (Tasks) để phân chia và theo dõi tiến độ ứng phó sự cố (ví dụ: "Block IP trên Firewall", "Reset Password").

4. **Kiểm toán (Audit Trail) & Bảo mật:**
   - Mọi thao tác đều được ghi lại dạng "Append-only" (không thể xóa/sửa) để phục vụ điều tra số (Forensics).
   - Dữ liệu nhạy cảm được mã hóa AES-GCM 256-bit trong Database.
   - Phân quyền chặt chẽ: Chỉ Manager/Admin mới được phép đóng sự cố và đánh giá Resolution Type.

---

## 🚀 Công nghệ sử dụng
### Backend
- **Java 17 + Spring Boot 3.3**
- **Spring Security** (JWT, RBAC)
- **Spring Data JPA + PostgreSQL**
- **WebSocket / STOMP** (Cập nhật Dashboard Real-time)

### Frontend
- **React + TypeScript + Vite**
- **Ant Design (v5)** (Với Design Token riêng cho giao diện SOC hắc ám)
- **Recharts** (Biểu đồ tương tác)

---

## 🛠 Hướng dẫn triển khai

### 1. Cấu hình bí mật và khởi tạo admin

Migration V6 vô hiệu hóa mật khẩu seed `Admin@123`; không có tài khoản demo mặc định có thể đăng nhập. Trước lần chạy đầu, sao chép `.env.example` thành `.env`, thay toàn bộ giá trị `replace-with-...`, sau đó đặt:

```dotenv
APP_BOOTSTRAP_ADMIN_ENABLED=true
APP_BOOTSTRAP_ADMIN_PASSWORD=<mật-khẩu-riêng-12-72-ký-tự-có-hoa-thường-số-đặc-biệt>
```

Không commit tệp `.env`. Sau lần đăng nhập đầu tiên, đặt `APP_BOOTSTRAP_ADMIN_ENABLED=false` và khởi động lại backend. Khi cần khôi phục về sau, dùng [Admin Recovery Runbook](docs/ADMIN_RECOVERY_RUNBOOK.md).

### 2. Khởi động hệ thống

Yêu cầu JDK 17, Maven, Node.js/npm và PostgreSQL 15+ (hoặc Docker/Podman):

```bash
docker compose up -d postgres backend

cd incident-frontend
npm ci
npm run dev
```

Frontend mặc định tại `http://localhost:5173`; API tại `http://localhost:8080`. Nếu chạy local HTTP, đặt `REFRESH_COOKIE_SECURE=false`; môi trường HTTPS phải giữ `true`.

Có thể chạy backend trực tiếp ngoài container (PostgreSQL được publish ở cổng `5433`):

```bash
mvn spring-boot:run
```

### 3. Sinh dữ liệu demo (tùy chọn)

Script không còn chứa credential mặc định. Truyền credential qua biến môi trường và xóa biến sau khi chạy:

```bash
SEED_USERNAME=admin SEED_PASSWORD='<mật-khẩu-admin>' node seed.mjs
unset SEED_PASSWORD
```

### 4. Kiểm tra trước khi bàn giao

```bash
JAVA_HOME=/path/to/jdk-17 mvn test
cd incident-frontend
npm run lint
npm run build
```

---

## 📦 Các tính năng mở rộng khác
- **Export Báo cáo:** Hỗ trợ xuất dữ liệu sự cố ra tệp **Excel (.xlsx)** và **PDF** cực kỳ nhanh chóng.
- **Toggle Lọc SLA:** Hỗ trợ bật/tắt công tắc trên màn hình danh sách để lọc thần tốc các sự cố đang vi phạm thời gian.
