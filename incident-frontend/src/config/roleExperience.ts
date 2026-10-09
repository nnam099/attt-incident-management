export type AppRole = 'ADMIN' | 'MANAGER' | 'HELPDESK' | 'ANALYST' | 'REPORTER';

const rolePriority: AppRole[] = ['ADMIN', 'MANAGER', 'HELPDESK', 'ANALYST', 'REPORTER'];

export const getPrimaryRole = (roles: string[] = []): AppRole =>
    rolePriority.find(role => roles.includes(`ROLE_${role}`)) ?? 'REPORTER';

export const roleExperience: Record<AppRole, {
    label: string;
    description: string;
    homePath: string;
    listTitle: string;
    listSubtitle: string;
}> = {
    ADMIN: {
        label: 'Quản trị hệ thống',
        description: 'Tài khoản, phân quyền và giám sát vận hành',
        homePath: '/users',
        listTitle: 'Giám sát sự cố',
        listSubtitle: 'Theo dõi toàn bộ ca phát sinh trong hệ thống.',
    },
    MANAGER: {
        label: 'Quản lý SOC',
        description: 'Giám sát SLA và phê duyệt kết luận',
        homePath: '/dashboard',
        listTitle: 'Điều phối sự cố',
        listSubtitle: 'Theo dõi tiến độ, ưu tiên và chất lượng xử lý.',
    },
    HELPDESK: {
        label: 'Tiếp nhận sự cố',
        description: 'Phân loại và phân công ca mới',
        homePath: '/incidents?status=NEW',
        listTitle: 'Bàn tiếp nhận',
        listSubtitle: 'Kiểm tra sự cố mới và chuyển cho chuyên viên phù hợp.',
    },
    ANALYST: {
        label: 'Chuyên viên SOC',
        description: 'Điều tra và xử lý ca được giao',
        homePath: '/incidents?mine=ASSIGNED',
        listTitle: 'Ca được giao',
        listSubtitle: 'Tập trung vào điều tra, bằng chứng và tiến độ xử lý.',
    },
    REPORTER: {
        label: 'Người báo cáo',
        description: 'Khai báo và theo dõi sự cố của mình',
        homePath: '/incidents?mine=REPORTED',
        listTitle: 'Sự cố của tôi',
        listSubtitle: 'Theo dõi những sự cố bạn đã gửi tới đội SOC.',
    },
};

export const getHomePath = (roles: string[] = []) =>
    roleExperience[getPrimaryRole(roles)].homePath;
