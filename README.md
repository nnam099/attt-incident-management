# Hệ thống tiếp nhận và xử lý sự cố an toàn thông tin (ATTT)

Hệ thống quản lý vòng đời sự cố ATTT toàn diện, từ khâu tiếp nhận (Khai báo), phân loại mức độ, phân công chuyên gia, xử lý, cho đến xuất báo cáo thống kê.
Được xây dựng với trọng tâm bảo mật dữ liệu và kiến trúc hướng Microservices (sẵn sàng mở rộng).

## 🚀 Công nghệ sử dụng
### Backend (Java/Spring Boot)
- **Java 17 + Spring Boot 3.3**
- **Spring Security** — JWT, Role-Based Access Control (RBAC), Phòng chống Brute-force
- **Spring Data JPA + Hibernate** (PostgreSQL)
- **Flyway** (Database Migration)
- **WebSocket / STOMP** (Real-time Push Notifications)
- **AES-256 GCM** (Mã hóa mô tả nhạy cảm ở mức Entity)

### Frontend (React/Vite)
- **React + TypeScript + Vite**
- **Ant Design (v5)** (UI Components)
- **Axios Interceptors** (Auto Refresh Token)
- **Recharts** (Biểu đồ Dashboard)

---

## 🔒 Các tính năng Bảo mật Nổi bật (Core InfoSec)
1. **Mã hóa DB:** Nội dung nhạy cảm của sự cố được mã hóa AES-256 trước khi lưu xuống PostgreSQL.
2. **Audit Logging (Bất biến):** Mọi hành động thao tác (Tạo mới, Đổi trạng thái, Phân công) đều được lưu vào `incident_logs`. Không ai có quyền sửa hoặc xóa log.
3. **Phòng chống Brute-force:** Khóa tài khoản 15 phút nếu nhập sai mật khẩu quá 5 lần (`security_audit_logs`).
4. **Session Management:** Dùng Refresh Token, không lưu Access Token ở LocalStorage để chống XSS.
5. **Data Privacy (RBAC):** `REPORTER` chỉ thấy sự cố của mình. `ADMIN`/`MANAGER` mới có quyền xem toàn hệ thống.

---

## 🛠 Hướng dẫn Triển khai (Deployment) cho Hội đồng / Giáo viên

Hệ thống đã được đóng gói hoàn chỉnh bằng **Docker Compose** và **Multi-stage Dockerfile**. Bạn không cần cài đặt Java hay Maven trên máy chủ.

### Yêu cầu hệ thống:
- Cài đặt **Docker** và **Docker Compose**.

### Các bước chạy hệ thống (Production Mode)

1. Mở Terminal / Command Prompt tại thư mục chứa mã nguồn.
2. Build và khởi động cụm server bằng lệnh sau:
```bash
docker-compose up -d --build
```
3. Chờ khoảng 1-2 phút để Docker tải Postgres, chạy Flyway Migration và khởi động Spring Boot Backend.
4. Kiểm tra trạng thái các container:
```bash
docker-compose ps
```

### Các dịch vụ đang chạy:
- **Backend API:** `http://localhost:8080` (Tài liệu Swagger API: `http://localhost:8080/swagger-ui.html`)
- **Database (PostgreSQL):** `localhost:5432`
- **Database GUI (pgAdmin):** `http://localhost:5050` (Email: `admin@example.com` | Pass: `admin`)

### Cấu hình Môi trường Tùy chỉnh (Tùy chọn)
Mặc định hệ thống sử dụng các khóa cấu hình trong `docker-compose.yml`. Nếu triển khai thật trên máy chủ công cộng, bạn hãy thay đổi các biến môi trường sau trong file `docker-compose.yml`:
- `JWT_SECRET`: Khóa ký Token (Độ dài > 256 bits).
- `APP_ENCRYPTION_KEY`: Khóa mã hóa AES (Base64 encoded).
- `POSTGRES_PASSWORD`: Mật khẩu Database.

---

## 🧑‍💻 Hướng dẫn chạy Frontend (Local)

1. Di chuyển vào thư mục Frontend:
```bash
cd incident-frontend
```
2. Cài đặt thư viện:
```bash
npm install
```
3. Khởi động giao diện web:
```bash
npm run dev
```

### Tài khoản Đăng nhập (Mặc định)
| Username | Password  | Vai trò (Role) | Chức năng |
|----------|-----------|----------------|-----------|
| `admin`  | Admin@123 | ADMIN          | Toàn quyền hệ thống + Quản trị người dùng |
| `manager`| User@123  | MANAGER        | Phân công sự cố, Xem Dashboard |
| `analyst`| User@123  | ANALYST        | Tiếp nhận và xử lý sự cố (Change Status) |
| `user1`  | User@123  | REPORTER       | Người dùng bình thường, báo cáo sự cố |
