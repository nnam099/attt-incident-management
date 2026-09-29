import React from 'react';
import { Layout, Menu, Button, Typography, Space, theme, ConfigProvider, Switch, Modal, Form, Input, message } from 'antd';
import {
    DashboardOutlined,
    UnorderedListOutlined,
    PlusCircleOutlined,
    UserOutlined,
    LogoutOutlined,
    BulbOutlined,
    BulbFilled,
    KeyOutlined
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/auth';
import api, { BACKEND_BASE_URL, getAccessToken } from '../services/api';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const MainLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const navigate = useNavigate();
    const location = useLocation();
    const { user, logout } = useAuth();
    const [passwordModalOpen, setPasswordModalOpen] = React.useState(false);
    const [passwordSubmitting, setPasswordSubmitting] = React.useState(false);
    const [passwordForm] = Form.useForm();

    React.useEffect(() => {
        if (!user) return;
        const client = new Client({
            webSocketFactory: () => new SockJS(`${BACKEND_BASE_URL}/ws`),
            beforeConnect: () => {
                const token = getAccessToken();
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
    
    // Đọc theme từ localStorage hoặc mặc định là dark (vì là SOC)
    const [isDarkMode, setIsDarkMode] = React.useState<boolean>(
        localStorage.getItem('theme') ? localStorage.getItem('theme') === 'dark' : true
    );

    const toggleTheme = (checked: boolean) => {
        setIsDarkMode(checked);
        localStorage.setItem('theme', checked ? 'dark' : 'light');
    };
    const {
        token: { colorBgContainer, borderRadiusLG },
    } = theme.useToken();

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

    const menuItems = [] as Array<{ key: string; icon: React.ReactNode; label: string }>;

    if (user?.roles.some(role => role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER')) {
        menuItems.push({
            key: '/dashboard',
            icon: <DashboardOutlined />,
            label: 'Dashboard',
        });
    }

    menuItems.push(
        {
            key: '/incidents',
            icon: <UnorderedListOutlined />,
            label: 'Danh sách Sự cố',
        },
        {
            key: '/incidents/new',
            icon: <PlusCircleOutlined />,
            label: 'Tạo Sự cố Mới',
        }
    );

    if (user?.roles.includes('ROLE_ADMIN')) {
        menuItems.push({
            key: '/users',
            icon: <UserOutlined />,
            label: 'Quản lý Người dùng',
        });
    }
    return (
        <ConfigProvider theme={{ 
            algorithm: isDarkMode ? theme.darkAlgorithm : theme.defaultAlgorithm,
            token: {
                fontFamily: `'Plus Jakarta Sans', sans-serif`,
                colorPrimary: isDarkMode ? '#8b5cf6' : '#1677ff', // Tím neon hoặc Xanh mượt
                borderRadius: 8,
                colorBgContainer: isDarkMode ? '#1f2937' : '#ffffff',
            }
        }}>
            <div className={isDarkMode ? 'dark-mode-app' : 'light-mode-app'}>
                <Layout style={{ minHeight: '100vh', background: 'transparent' }}>
                    <Sider breakpoint="lg" collapsedWidth="0" style={{ borderRight: isDarkMode ? '1px solid #1f2937' : 'none' }}>
                        <div style={{ height: 32, margin: 16, background: isDarkMode ? 'rgba(255, 255, 255, 0.05)' : 'rgba(255, 255, 255, 0.2)', borderRadius: 6, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Text strong style={{ color: 'white' }}>SOC Incident Hub</Text>
                </div>
                <Menu
                    theme="dark"
                    mode="inline"
                    selectedKeys={[location.pathname]}
                    items={menuItems}
                    onClick={handleMenuClick}
                />
            </Sider>
            <Layout>
                <Header style={{ padding: '0 24px', background: colorBgContainer, display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
                    <Space size="large">
                        <Space>
                            {isDarkMode ? <BulbFilled style={{ color: '#faad14' }} /> : <BulbOutlined />}
                            <Switch checked={isDarkMode} onChange={toggleTheme} checkedChildren="Dark" unCheckedChildren="Light" />
                        </Space>
                        <Space>
                            <Text strong>{user?.username}</Text>
                            <Button type="text" icon={<KeyOutlined />} onClick={() => setPasswordModalOpen(true)}>
                                Đổi mật khẩu
                            </Button>
                            <Button type="text" icon={<LogoutOutlined />} onClick={handleLogout}>
                                Đăng xuất
                            </Button>
                        </Space>
                    </Space>
                </Header>
                <Content style={{ margin: '24px 16px 0', overflow: 'initial' }}>
                    <div
                        style={{
                            padding: 24,
                            background: 'transparent',
                            borderRadius: borderRadiusLG,
                            minHeight: '80vh',
                        }}
                    >
                        {children}
                    </div>
                </Content>
                </Layout>
                <Modal title="Đổi mật khẩu" open={passwordModalOpen}
                    onCancel={() => setPasswordModalOpen(false)} footer={null} destroyOnClose>
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
