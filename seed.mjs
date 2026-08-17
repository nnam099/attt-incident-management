import fs from 'fs';

const API_URL = 'http://localhost:8080/api';

function randomPastDate(daysAgoStart, daysAgoEnd) {
    const start = new Date();
    start.setDate(start.getDate() - daysAgoStart);
    const end = new Date();
    end.setDate(end.getDate() - daysAgoEnd);
    return new Date(start.getTime() + Math.random() * (end.getTime() - start.getTime()));
}

const dummyIncidents = [
    { title: "Phát hiện lưu lượng mạng bất thường truy cập ra ngoài", categoryId: 2, severity: "HIGH", affectedSystem: "Core Switch", description: "Hệ thống IDS phát hiện nhiều kết nối SSH liên tục từ IP lạ vào dải mạng nội bộ. Nghi ngờ có nỗ lực scan port và brute-force.", daysAgo: 5 },
    { title: "Rò rỉ mã nguồn trên GitHub công khai", categoryId: 3, severity: "CRITICAL", affectedSystem: "GitLab Nội bộ", description: "Một đoạn mã chứa API Key của AWS đã bị một nhân viên commit nhầm lên public repo trên GitHub. Cần thu hồi Key ngay lập tức.", daysAgo: 2 },
    { title: "Người dùng báo cáo nhận email lừa đảo trúng thưởng", categoryId: 4, severity: "MEDIUM", affectedSystem: "Mail Server", description: "Nhiều nhân viên phòng Marketing báo cáo nhận được email giả mạo từ bộ phận IT yêu cầu đổi mật khẩu. Đã block domain gửi đến.", daysAgo: 10 },
    { title: "Không thể truy cập hệ thống ERP", categoryId: 6, severity: "HIGH", affectedSystem: "ERP System", description: "Hệ thống ERP báo lỗi 500 liên tục từ 8h sáng. Không có dấu hiệu tấn công, có thể do lỗi database deadlock.", daysAgo: 1 },
    { title: "Cảnh báo Ransomware trên máy trạm của HR", categoryId: 1, severity: "CRITICAL", affectedSystem: "Laptop-HR-04", description: "Máy tính của nhân sự báo toàn bộ file .docx đã bị đổi đuôi thành .encrypted. Đã ngắt kết nối mạng ngay lập tức.", daysAgo: 15 },
    { title: "Tài khoản Giám đốc đăng nhập từ quốc gia lạ", categoryId: 5, severity: "CRITICAL", affectedSystem: "VPN/AD", description: "Log hệ thống ghi nhận tài khoản giám đốc đăng nhập VPN từ IP thuộc Nga lúc 2h sáng. Đã khóa tài khoản tạm thời.", daysAgo: 3 },
    { title: "Website công ty bị tấn công từ chối dịch vụ (DDoS)", categoryId: 2, severity: "HIGH", affectedSystem: "Public Website", description: "Lưu lượng truy cập tăng đột biến gấp 50 lần bình thường làm web không thể truy cập. Cần bật Cloudflare Under Attack mode.", daysAgo: 4 },
    { title: "Lỗ hổng SQL Injection trên trang tuyển dụng", categoryId: 2, severity: "HIGH", affectedSystem: "Careers Portal", description: "Báo cáo từ chuyên gia bảo mật bên ngoài: Form tìm kiếm việc làm không lọc ký tự đặc biệt, có thể dump database.", daysAgo: 20 },
    { title: "Nhân viên cài phần mềm crack chứa Trojan", categoryId: 1, severity: "MEDIUM", affectedSystem: "PC-Dev-12", description: "Antivirus phát hiện Trojan.Win32 trong bộ cài Adobe Premiere crack. Phần mềm đã bị cách ly.", daysAgo: 7 },
    { title: "Mất điện toàn bộ Data Center chi nhánh", categoryId: 6, severity: "CRITICAL", affectedSystem: "Data Center", description: "Hệ thống UPS hỏng, máy phát điện không tự kích hoạt dẫn đến sập toàn bộ máy chủ tại chi nhánh.", daysAgo: 0.5 },
    { title: "Rò rỉ thông tin khách hàng VIP", categoryId: 3, severity: "CRITICAL", affectedSystem: "CRM System", description: "Phát hiện file excel chứa danh sách khách hàng VIP bị đẩy lên một diễn đàn hacker. Đang điều tra nguồn rò rỉ.", daysAgo: 8 },
    { title: "Cảnh báo lỗi SSL Certificate hết hạn", categoryId: 6, severity: "MEDIUM", affectedSystem: "API Gateway", description: "Chứng chỉ SSL của domain api.company.com sẽ hết hạn trong 24h tới.", daysAgo: 0.1 }
];

async function apiCall(endpoint, method, token, body) {
    const res = await fetch(`${API_URL}${endpoint}`, {
        method,
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(body)
    });
    if (!res.ok) {
        throw new Error(`Call failed: ${res.status} - ${await res.text()}`);
    }
    return res.json().catch(() => ({})); // Some endpoints return empty body
}

async function seedData() {
    console.log("Đang đăng nhập hệ thống...");
    try {
        const loginRes = await fetch(`${API_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: 'admin', password: 'Admin@123' })
        });
        
        if (!loginRes.ok) throw new Error("Đăng nhập thất bại!");
        const { token } = await loginRes.json();
        console.log("Đăng nhập thành công! Đang tạo dữ liệu mẫu SOC...");

        let count = 0;
        for (const inc of dummyIncidents) {
            const createdAt = randomPastDate(inc.daysAgo + 1, inc.daysAgo).toISOString();
            
            // 1. Khởi tạo sự cố
            const incidentRes = await apiCall('/incidents', 'POST', token, {
                title: inc.title,
                description: inc.description,
                categoryId: inc.categoryId,
                severity: inc.severity,
                affectedSystem: inc.affectedSystem,
                createdAt: createdAt
            });
            const incId = incidentRes.id;
            console.log(`[+] Đã tạo: ${inc.title} (ID: ${incId})`);

            // 2. Phân loại (TRIAGE)
            if (Math.random() > 0.1) {
                await apiCall(`/incidents/${incId}/status`, 'PATCH', token, {
                    newStatus: 'TRIAGE', note: 'Phân loại tự động'
                });

                // Thêm IoCs ngẫu nhiên
                if (inc.categoryId === 1 || inc.categoryId === 2 || inc.categoryId === 4) { // Malware, Mạng, Phishing
                    await apiCall(`/incidents/${incId}/iocs`, 'POST', token, { type: 'IPV4', value: `192.168.${Math.floor(Math.random()*255)}.${Math.floor(Math.random()*255)}`, description: 'IP tình nghi' });
                    if (inc.categoryId === 1) {
                        await apiCall(`/incidents/${incId}/iocs`, 'POST', token, { type: 'MD5_HASH', value: 'd41d8cd98f00b204e9800998ecf8427e', description: 'Mã độc' });
                    }
                }

                // Thêm Tasks ngẫu nhiên
                const tasks = ['Thu thập log hệ thống', 'Khóa tài khoản liên quan', 'Phân tích file thực thi', 'Chặn IP trên Firewall'];
                for(let i=0; i<2; i++) {
                    const taskRes = await apiCall(`/incidents/${incId}/tasks`, 'POST', token, { taskName: tasks[Math.floor(Math.random()*tasks.length)] });
                    // Hoàn thành task
                    if (Math.random() > 0.3) {
                        await apiCall(`/incidents/${incId}/tasks/${taskRes.id}/toggle`, 'PATCH', token, {});
                    }
                }

                // 3. Tiến hành điều tra và xử lý
                if (Math.random() > 0.2) {
                    await apiCall(`/incidents/${incId}/status`, 'PATCH', token, {
                        newStatus: 'INVESTIGATING', note: 'Chuyển Tier 2'
                    });

                    // 4. Đóng sự cố đối với một số ca cũ
                    if (inc.daysAgo > 5) {
                        await apiCall(`/incidents/${incId}/status`, 'PATCH', token, {
                            newStatus: 'RESOLVED', note: 'Đã khắc phục xong'
                        });
                        
                        const isTruePositive = Math.random() > 0.3;
                        await apiCall(`/incidents/${incId}/status`, 'PATCH', token, {
                            newStatus: 'CLOSED', 
                            note: 'Nghiệm thu đóng sự cố',
                            resolutionType: isTruePositive ? 'TRUE_POSITIVE' : 'FALSE_POSITIVE'
                        });
                        console.log(`    -> Đã đóng sự cố (Resolution: ${isTruePositive ? 'TRUE_POSITIVE' : 'FALSE_POSITIVE'})`);
                    }
                }
            }
            count++;
        }
        
        console.log(`\nHoàn tất! Đã tạo và mô phỏng thành công ${count}/${dummyIncidents.length} sự cố mẫu.`);

    } catch (e) {
        console.error("Lỗi:", e.message);
    }
}

seedData();
