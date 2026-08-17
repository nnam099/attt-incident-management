import fs from 'fs';

const API_URL = 'http://localhost:8080/api';

const dummyIncidents = [
    { title: "Phát hiện lưu lượng mạng bất thường truy cập ra ngoài", categoryId: 2, severity: "HIGH", affectedSystem: "Core Switch", description: "Hệ thống IDS phát hiện nhiều kết nối SSH liên tục từ IP lạ vào dải mạng nội bộ. Nghi ngờ có nỗ lực scan port và brute-force." },
    { title: "Rò rỉ mã nguồn trên GitHub công khai", categoryId: 3, severity: "CRITICAL", affectedSystem: "GitLab Nội bộ", description: "Một đoạn mã chứa API Key của AWS đã bị một nhân viên commit nhầm lên public repo trên GitHub. Cần thu hồi Key ngay lập tức." },
    { title: "Người dùng báo cáo nhận email lừa đảo trúng thưởng", categoryId: 4, severity: "MEDIUM", affectedSystem: "Mail Server", description: "Nhiều nhân viên phòng Marketing báo cáo nhận được email giả mạo từ bộ phận IT yêu cầu đổi mật khẩu. Đã block domain gửi đến." },
    { title: "Không thể truy cập hệ thống ERP", categoryId: 6, severity: "HIGH", affectedSystem: "ERP System", description: "Hệ thống ERP báo lỗi 500 liên tục từ 8h sáng. Không có dấu hiệu tấn công, có thể do lỗi database deadlock." },
    { title: "Cảnh báo Ransomware trên máy trạm của HR", categoryId: 1, severity: "CRITICAL", affectedSystem: "Laptop-HR-04", description: "Máy tính của nhân sự báo toàn bộ file .docx đã bị đổi đuôi thành .encrypted. Đã ngắt kết nối mạng ngay lập tức." },
    { title: "Tài khoản Giám đốc đăng nhập từ quốc gia lạ", categoryId: 5, severity: "CRITICAL", affectedSystem: "VPN/AD", description: "Log hệ thống ghi nhận tài khoản giám đốc đăng nhập VPN từ IP thuộc Nga lúc 2h sáng. Đã khóa tài khoản tạm thời." },
    { title: "Website công ty bị tấn công từ chối dịch vụ (DDoS)", categoryId: 2, severity: "HIGH", affectedSystem: "Public Website", description: "Lưu lượng truy cập tăng đột biến gấp 50 lần bình thường làm web không thể truy cập. Cần bật Cloudflare Under Attack mode." },
    { title: "Lỗ hổng SQL Injection trên trang tuyển dụng", categoryId: 2, severity: "HIGH", affectedSystem: "Careers Portal", description: "Báo cáo từ chuyên gia bảo mật bên ngoài: Form tìm kiếm việc làm không lọc ký tự đặc biệt, có thể dump database." },
    { title: "Nhân viên cài phần mềm crack chứa Trojan", categoryId: 1, severity: "MEDIUM", affectedSystem: "PC-Dev-12", description: "Antivirus phát hiện Trojan.Win32 trong bộ cài Adobe Premiere crack. Phần mềm đã bị cách ly." },
    { title: "Mất điện toàn bộ Data Center chi nhánh", categoryId: 6, severity: "CRITICAL", affectedSystem: "Data Center", description: "Hệ thống UPS hỏng, máy phát điện không tự kích hoạt dẫn đến sập toàn bộ máy chủ tại chi nhánh." },
    { title: "Rò rỉ thông tin khách hàng VIP", categoryId: 3, severity: "CRITICAL", affectedSystem: "CRM System", description: "Phát hiện file excel chứa danh sách khách hàng VIP bị đẩy lên một diễn đàn hacker. Đang điều tra nguồn rò rỉ." },
    { title: "Cảnh báo lỗi SSL Certificate hết hạn", categoryId: 6, severity: "MEDIUM", affectedSystem: "API Gateway", description: "Chứng chỉ SSL của domain api.company.com sẽ hết hạn trong 24h tới." }
];

async function seedData() {
    console.log("Đang đăng nhập hệ thống...");
    try {
        const loginRes = await fetch(`${API_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: 'admin', password: 'Admin@123' })
        });
        
        if (!loginRes.ok) {
            console.error("Đăng nhập thất bại!", await loginRes.text());
            return;
        }

        const data = await loginRes.json();
        const token = data.token;
        console.log("Đăng nhập thành công! Đang tạo dữ liệu mẫu...");

        let count = 0;
        for (const incident of dummyIncidents) {
            const res = await fetch(`${API_URL}/incidents`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify(incident)
            });

            if (res.ok) {
                count++;
                console.log(`[+] Đã tạo: ${incident.title}`);
            } else {
                console.error(`[-] Lỗi khi tạo: ${incident.title}`, await res.text());
            }
        }
        
        console.log(`\nHoàn tất! Đã tạo thành công ${count}/${dummyIncidents.length} sự cố mẫu.`);

    } catch (e) {
        console.error("Lỗi:", e.message);
    }
}

seedData();
