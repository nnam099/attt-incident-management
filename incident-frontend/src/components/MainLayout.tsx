import React from 'react';
import { Layout, Menu, Button, theme, ConfigProvider, Switch, Modal, Form, Input, message } from 'antd';
import {
    DashboardOutlined,
    UnorderedListOutlined,
    PlusCircleOutlined,
    UserOutlined,
    LogoutOutlined,
    BulbOutlined,
    BulbFilled,
    KeyOutlined,
    SafetyCertificateOutlined,
    RightOutlined,
    InboxOutlined,
    AuditOutlined
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/auth';
import api, { BACKEND_BASE_URL, getAccessToken, refreshSession } from '../services/api';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { getPrimaryRole, roleExperience } from '../config/roleExperience';

const { Header, Sider, Content } = Layout;

const MainLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const navigate = useNavigate();
    const location = useLocation();
    const { user, logout } = useAuth();
    const activeRole = getPrimaryRole(user?.roles);
    const experience = roleExperience[activeRole];
    const [passwordModalOpen, setPasswordModalOpen] = React.useState(false);
    const [passwordSubmitting, setPasswordSubmitting] = React.useState(false);
    const [passwordForm] = Form.useForm();

    React.useEffect(() => {
        if (!user) return;
        const client = new Client({
            webSocketFactory: () => new SockJS(`${BACKEND_BASE_URL}/ws`),
            beforeConnect: async () => {
                let token = getAccessToken();
                try {
                    token = (await refreshSession()).token;
                } catch {
                    // Fall back to the current token while it is still valid.
                }
                client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
            },
            onConnect: () => client.subscribe('/user/queue/notifications', frame => {
                message.info({ content: frame.body, duration: 8 });
            }),
            reconnectDelay: 5000,
        });
        client.activate();
        return () => { void client.deactivate(); };
    }, [user]);
    
    // Keep the user's saved preference; use the light command center as the first view.
    const [isDarkMode, setIsDarkMode] = React.useState<boolean>(
        localStorage.getItem('theme') === 'dark'
    );

    const toggleTheme = (checked: boolean) => {
        setIsDarkMode(checked);
        localStorage.setItem('theme', checked ? 'dark' : 'light');
    };
    const handleMenuClick = ({ key }: { key: string }) => {
        navigate(key);
    };

    const handleLogout = async () => {
        await logout();
        navigate('/login');
    };

    const handleChangePassword = async (values: { oldPassword: string; newPassword: string }) => {
        setPasswordSubmitting(true);
        try {
            await api.post('/users/change-password', values);
            message.success('Đổi mật khẩu thành công. Vui lòng đăng nhập lại.');
            setPasswordModalOpen(false);
            passwordForm.resetFields();
            await handleLogout();
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Đổi mật khẩu thất bại');
        } finally {
            setPasswordSubmitting(false);
        }
    };

    const menuItems: Array<{ key: string; icon: React.ReactNode; label: string }> = {
        ADMIN: [
            { key: '/users', icon: <UserOutlined />, label: 'Quản lý người dùng' },
            { key: '/dashboard', icon: <DashboardOutlined />, label: 'Tổng quan hệ thống' },
            { key: '/incidents', icon: <UnorderedListOutlined />, label: 'Giám sát sự cố' },
        ],
        MANAGER: [
            { key: '/dashboard', icon: <DashboardOutlined />, label: 'Tổng quan SOC' },
            { key: '/incidents?status=RESOLVED', icon: <AuditOutlined />, label: 'Chờ phê duyệt' },
            { key: '/incidents', icon: <UnorderedListOutlined />, label: 'Tất cả sự cố' },
        ],
        HELPDESK: [
            { key: '/incidents?status=NEW', icon: <InboxOutlined />, label: 'Chờ tiếp nhận' },
            { key: '/incidents', icon: <UnorderedListOutlined />, label: 'Toàn bộ sự cố' },
        ],
        ANALYST: [
            { key: '/incidents?mine=ASSIGNED', icon: <AuditOutlined />, label: 'Ca được giao' },
            { key: '/incidents', icon: <UnorderedListOutlined />, label: 'Sự cố liên quan' },
        ],
        REPORTER: [
            { key: '/incidents?mine=REPORTED', icon: <UnorderedListOutlined />, label: 'Sự cố của tôi' },
            { key: '/incidents/new', icon: <PlusCircleOutlined />, label: 'Khai báo sự cố' },
        ],
    }[activeRole];
    const activeQuery = new URLSearchParams(location.search);
    const selectedMenuKey = menuItems.find(item => {
        const [path, query] = item.key.split('?');
        if (path !== location.pathname || !query) return false;
        return [...new URLSearchParams(query)].every(([key, value]) => activeQuery.get(key) === value);
    })?.key ?? menuItems.find(item => item.key === location.pathname)?.key
        ?? menuItems.find(item => item.key.startsWith(`${location.pathname}?`))?.key;
    return (
        <ConfigProvider theme={{ 
            algorithm: isDarkMode ? theme.darkAlgorithm : theme.defaultAlgorithm,
            token: {
                fontFamily: `'IBM Plex Sans', 'Segoe UI', sans-serif`,
                colorPrimary: '#197a72',
                borderRadius: 10,
                colorBgContainer: isDarkMode ? '#202c31' : '#ffffff',
            }
        }}>
            <div className={isDarkMode ? 'dark-mode-app' : 'light-mode-app'}>
                <Layout className="soc-shell">
                    <Sider className="soc-sider" width={246} breakpoint="lg" collapsedWidth="0">
                        <div className="soc-brand" onClick={() => navigate(experience.homePath)} role="button" tabIndex={0} onKeyDown={event => { if (event.key === 'Enter') navigate(experience.homePath); }}>
                            <span className="soc-brand-mark"><SafetyCertificateOutlined /></span>
                            <span><strong>INCIDENT<span> / </span>HUB</strong><small>SECURITY OPERATIONS</small></span>
                        </div>
                        <div className={`soc-role-card role-${activeRole.toLowerCase()}`}><span>KHÔNG GIAN LÀM VIỆC</span><strong>{experience.label}</strong><small>{experience.description}</small></div>
                        <div className="soc-nav-label">ĐIỀU HƯỚNG</div>
                        <Menu theme="dark" mode="inline" selectedKeys={selectedMenuKey ? [selectedMenuKey] : []} items={menuItems} onClick={handleMenuClick} />
                        <div className="soc-sidebar-bottom">
                            <div className="soc-system-status"><span className="soc-live-dot" /> Hệ thống đang hoạt động</div>
                            <div className="soc-sidebar-caption">TRUNG TÂM ĐIỀU PHỐI SỰ CỐ</div>
                        </div>
                    </Sider>
                    <Layout>
                <Header className="soc-header">
                    <div className="soc-breadcrumb"><span>{experience.label.toUpperCase()}</span><RightOutlined /><strong>{menuItems.find(item => item.key === selectedMenuKey)?.label ?? (location.pathname.endsWith('/new') ? 'Khai báo sự cố' : 'Chi tiết sự cố')}</strong></div>
                    <div className="soc-header-actions">
                        <div className="soc-theme-control">{isDarkMode ? <BulbFilled /> : <BulbOutlined />}<Switch size="small" checked={isDarkMode} onChange={toggleTheme} aria-label="Chuyển giao diện sáng tối" /></div>
                        <div className="soc-profile"><span className="soc-avatar">{user?.username?.slice(0, 1).toUpperCase()}</span><span className="soc-profile-name">{user?.username}</span></div>
                        <Button type="text" icon={<KeyOutlined />} onClick={() => setPasswordModalOpen(true)} title="Đổi mật khẩu" aria-label="Đổi mật khẩu" />
                        <Button type="text" icon={<LogoutOutlined />} onClick={handleLogout} title="Đăng xuất" aria-label="Đăng xuất" />
                    </div>
                </Header>
                <Content className="soc-content">{children}</Content>
                </Layout>
                <Modal title="Đổi mật khẩu" open={passwordModalOpen}
                    onCancel={() => setPasswordModalOpen(false)} footer={null} destroyOnHidden>
                    <Form form={passwordForm} layout="vertical" onFinish={handleChangePassword}>
                        <Form.Item name="oldPassword" label="Mật khẩu hiện tại"
                            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu hiện tại' }]}>
                            <Input.Password />
                        </Form.Item>
                        <Form.Item name="newPassword" label="Mật khẩu mới" rules={[
                            { required: true }, { min: 12, max: 72 },
                            { pattern: /[A-Z]/, message: 'Cần ít nhất một chữ hoa' },
                            { pattern: /[a-z]/, message: 'Cần ít nhất một chữ thường' },
                            { pattern: /\d/, message: 'Cần ít nhất một chữ số' },
                            { pattern: /[^A-Za-z0-9]/, message: 'Cần ít nhất một ký tự đặc biệt' },
                        ]}>
                            <Input.Password />
                        </Form.Item>
                        <Form.Item><Button type="primary" htmlType="submit" loading={passwordSubmitting}>
                            Cập nhật mật khẩu
                        </Button></Form.Item>
                    </Form>
                </Modal>
            </Layout>
            </div>
        </ConfigProvider>
    );
};

export default MainLayout;
