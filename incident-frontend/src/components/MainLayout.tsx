import React from 'react';
import { Layout, Menu, Button, Typography, Space, theme, ConfigProvider, Switch } from 'antd';
import {
    DashboardOutlined,
    UnorderedListOutlined,
    PlusCircleOutlined,
    UserOutlined,
    LogoutOutlined,
    BulbOutlined,
    BulbFilled
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const MainLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const navigate = useNavigate();
    const location = useLocation();
    const { user, logout } = useAuth();
    
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

    const handleLogout = () => {
        logout();
        navigate('/login');
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
                    <Text strong style={{ color: 'white' }}>BusGo ATTT</Text>
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
            </Layout>
            </div>
        </ConfigProvider>
    );
};

export default MainLayout;
