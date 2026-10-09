# 🛡️ Hệ thống Frontend SOC (Incident Management)

Đây là giao diện người dùng (Frontend) của hệ thống Quản lý Sự cố An toàn Thông tin chuẩn SOC, được xây dựng bằng **React**, **TypeScript**, và **Vite**.

## 🎨 Điểm nhấn Giao diện (UI/UX)
- **Dark Mode Premium:** Giao diện được tối ưu hóa cho các chuyên gia phân tích SOC làm việc trong bóng tối, giảm mỏi mắt và làm nổi bật các cảnh báo Đỏ/Xanh. (Sử dụng Ant Design `ConfigProvider` kết hợp Glassmorphism).
- **Google Fonts:** Tích hợp phông chữ `Plus Jakarta Sans` mang lại cảm giác hiện đại và chuyên nghiệp.
- **Biểu đồ thời gian thực (Recharts):** Biểu diễn trực quan tỷ lệ tuân thủ SLA, chất lượng cảnh báo (Resolution Breakdown) và xu hướng sự cố.
- **Tương tác Real-time:** Kết nối WebSocket / STOMP với Backend để tự động làm mới số liệu Dashboard mà không cần F5.

## 🚀 Hướng dẫn cài đặt và chạy (Development)

### 1. Yêu cầu môi trường
- Node.js 20.19+ hoặc 22.12+ (theo yêu cầu của Vite 8)
- Trình quản lý gói `npm` (hoặc `yarn`/`pnpm`)

### 2. Cài đặt các thư viện phụ thuộc
Di chuyển vào thư mục `incident-frontend` và chạy lệnh:
```bash
npm install
```

### 3. Cấu hình biến môi trường
Mặc định hệ thống sẽ gọi tới Backend ở cổng `8080` với cùng hostname của trang frontend (`localhost` hoặc `127.0.0.1`). Nếu Backend chạy ở địa chỉ khác, tạo file `.env.local` và đặt biến `VITE_API_BASE_URL`, ví dụ:

```dotenv
VITE_API_BASE_URL=https://soc.example.com/api
```

### 4. Khởi chạy Server
```bash
npm run dev
```
Giao diện sẽ được khởi chạy tại: `http://localhost:5173` hoặc `http://127.0.0.1:5173`.

## 📦 Tính năng nổi bật cho Người dùng
- **Trích xuất dữ liệu:** Xuất danh sách sự cố ra định dạng Excel và PDF ngay lập tức với React và API Backend.
- **Báo cáo sự cố:** Cho phép người báo cáo chọn thời gian phát hiện (Detected At) để tính toán chuẩn xác MTTA.
- **Quản lý IoC & Playbook:** Giao diện điều tra sự cố chia form rõ ràng để kỹ sư an ninh mạng (Analyst) phân tích và đóng case.

## 🛠 Lệnh Build (Production)
Để build thư mục tĩnh (`dist/`) triển khai lên Nginx hoặc Apache:
```bash
npm run build
```

Kiểm tra trước khi bàn giao:

```bash
npm run lint
npm test
npm run build
```
