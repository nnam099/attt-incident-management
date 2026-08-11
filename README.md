# Hệ thống tiếp nhận và xử lý sự cố an toàn thông tin

Backend khung ban đầu (Spring Boot 3 + PostgreSQL) cho đề tài:
**Hệ thống tiếp nhận và xử lý sự cố an toàn thông tin** — khai báo sự cố, phân loại,
phân công, cập nhật trạng thái, báo cáo; phân cấp độ sự cố, bảo mật thông tin sự cố,
nhật ký xử lý.

## Công nghệ sử dụng

- **Java 17 + Spring Boot 3.3**
- **Spring Security** — xác thực JWT, phân quyền RBAC (ADMIN / MANAGER / HELPDESK / ANALYST / REPORTER)
- **Spring Data JPA + Hibernate**
- **PostgreSQL** + **Flyway** (quản lý migration)
- **AES-GCM** mã hóa trường mô tả sự cố (dữ liệu nhạy cảm) ngay ở tầng entity
- **Lombok, MapStruct, Swagger/OpenAPI, Apache POI** (xuất Excel)

## Cấu trúc thư mục

```
src/main/java/com/attt/incident/
├── config/          # SecurityConfig, CORS...
├── controller/      # REST API endpoints
├── service/         # Business logic (IncidentService...)
├── repository/       # Spring Data JPA repositories
├── entity/           # User, Role, Incident, IncidentLog, IncidentCategory, IncidentAttachment
├── dto/               # Request/Response objects
├── security/          # JWT filter, JwtService, UserDetailsService, mã hóa AES
├── exception/          # GlobalExceptionHandler
└── util/               # IncidentStatusTransitionValidator (state machine)

src/main/resources/
├── application.yml
└── db/migration/       # V1__init_schema.sql, V2__seed_data.sql (Flyway)
```

## Chạy thử local

### 1. Khởi động PostgreSQL bằng Docker

```bash
docker-compose up -d
```

Postgres sẽ chạy ở `localhost:5432`, database `incident_db`, user/pass `postgres/postgres`.
(Có kèm pgAdmin ở `localhost:5050` để xem dữ liệu trực quan nếu cần.)

### 2. Build và chạy ứng dụng

```bash
mvn clean install
mvn spring-boot:run
```

Ứng dụng chạy ở `http://localhost:8080`. Flyway sẽ tự động tạo schema và seed dữ liệu mẫu
(roles, tài khoản admin, danh mục loại sự cố) khi khởi động lần đầu.

### 3. Tài khoản mặc định

| Username | Password  | Vai trò |
|----------|-----------|---------|
| admin    | Admin@123 | ADMIN   |

> ⚠️ Đổi mật khẩu và các khóa bí mật (`app.jwt.secret`, `app.encryption.key`) trước khi triển khai thật.

### 4. Thử API

**Đăng nhập:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}'
```

**Khai báo sự cố (dùng token nhận được ở bước trên):**
```bash
curl -X POST http://localhost:8080/api/incidents \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Phát hiện email phishing giả mạo ngân hàng",
    "description": "Nhiều nhân viên nhận được email yêu cầu cập nhật thông tin đăng nhập...",
    "categoryId": 4,
    "affectedSystem": "Hệ thống email nội bộ"
  }'
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Đã có trong bản khung này

- [x] Entity + schema: User, Role, Incident, IncidentCategory, IncidentLog, IncidentAttachment
- [x] Đăng nhập JWT + phân quyền RBAC theo vai trò
- [x] Khai báo sự cố, sinh mã tự động (INC-YYYY-XXXX)
- [x] Workflow trạng thái có kiểm soát chuyển trạng thái hợp lệ (state machine)
- [x] Phân công xử lý sự cố
- [x] Nhật ký xử lý (audit log) tự động, không cho sửa/xóa (DB rule + append-only)
- [x] Mã hóa AES-GCM cho trường mô tả sự cố nhạy cảm
- [x] Ẩn/che nội dung sự cố với người không có quyền xem (RBAC ở tầng service)

## Việc tiếp theo (gợi ý lộ trình)

1. Module quản lý người dùng (Admin CRUD user, gán vai trò)
2. Upload/download file đính kèm minh chứng sự cố
3. Dashboard thống kê (biểu đồ theo mức độ, loại, thời gian) + xuất Excel/PDF
4. Cảnh báo SLA sắp hết hạn (scheduled job + email)
5. WebSocket cập nhật trạng thái sự cố real-time trên dashboard
6. Frontend: React + TypeScript + TailwindCSS + Ant Design + TanStack Query

## Frontend đề xuất

React (Vite) + TypeScript + TailwindCSS + Ant Design + TanStack Query + Recharts.
Chưa bao gồm trong bản zip này — có thể khởi tạo riêng bằng:

```bash
npm create vite@latest incident-frontend -- --template react-ts
```
