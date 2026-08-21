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

## 🛠 Hướng dẫn Triển khai (Demo cho Hội đồng)

Để có một bài trình bày hoàn hảo nhất, vui lòng làm đúng theo thứ tự sau:

### 1. Khởi động hệ thống
Mở Terminal / Command Prompt tại thư mục chứa mã nguồn và chạy:
```bash
# Khởi chạy Database bằng Docker
docker-compose up -d postgres

# Chạy Backend (chạy bằng IDE như IntelliJ hoặc Maven)
mvn spring-boot:run

# Mở một Terminal khác, chạy Frontend
cd incident-frontend
npm install
npm run dev
```

### 2. Sinh dữ liệu mẫu (Cực kỳ quan trọng)
Để Dashboard có biểu đồ đẹp, có sự cố bị trễ SLA màu đỏ, có biểu đồ tỷ lệ True/False Positive, bạn **PHẢI** chạy kịch bản sinh dữ liệu mẫu:
```bash
# Đứng tại thư mục gốc của dự án
node seed.mjs
```
*Lưu ý: Khi chạy Backend trực tiếp trên máy, Docker publish PostgreSQL ở cổng `5433`; hãy cấu hình datasource tương ứng hoặc chạy Backend cùng Docker. Bạn nên truncate/drop database cũ và chạy lại ứng dụng để có dữ liệu sạch và đẹp nhất.*

### 3. Đăng nhập và Trải nghiệm
Mở trình duyệt: `http://localhost:5173`

**Tài khoản Đăng nhập:**
| Username | Password  | Vai trò (Role) | Chức năng |
|----------|-----------|----------------|-----------|
| `admin`  | Admin@123 | ADMIN          | Toàn quyền hệ thống + Quản trị người dùng |
| `manager`| User@123  | MANAGER        | Dashboard, Quản lý SLA, Phân công |
| `analyst`| User@123  | ANALYST        | Điều tra (IoC), xử lý Task (Playbook) |

---

## 📦 Các tính năng mở rộng khác
- **Export Báo cáo:** Hỗ trợ xuất dữ liệu sự cố ra tệp **Excel (.xlsx)** và **PDF** cực kỳ nhanh chóng.
- **Toggle Lọc SLA:** Hỗ trợ bật/tắt công tắc trên màn hình danh sách để lọc thần tốc các sự cố đang vi phạm thời gian.
