import React from 'react';
import { Layout, Menu, Button, Typography, Space, theme } from 'antd';
import {
    DashboardOutlined,
    UnorderedListOutlined,
    PlusCircleOutlined,
    UserOutlined,
    LogoutOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

const MainLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const navigate = useNavigate();
    const location = useLocation();
    const { user, logout } = useAuth();
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

    const menuItems = [
        {
            key: '/dashboard',
            icon: <DashboardOutlined />,
            label: 'Dashboard',
        },
        {
            key: '/incidents',
            icon: <UnorderedListOutlined />,
            label: 'Danh sách Sự cố',
        },
        {
            key: '/incidents/new',
            icon: <PlusCircleOutlined />,
            label: 'Tạo Sự cố Mới',
        },
    ];

    if (user?.roles.includes('ROLE_ADMIN')) {
        menuItems.push({
            key: '/users',
            icon: <UserOutlined />,
            label: 'Quản lý Người dùng',
        });
    }

    return (
        <Layout style={{ minHeight: '100vh' }}>
            <Sider breakpoint="lg" collapsedWidth="0">
                <div style={{ height: 32, margin: 16, background: 'rgba(255, 255, 255, 0.2)', borderRadius: 6, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
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
                    <Space>
                        <Text strong>{user?.username}</Text>
                        <Button type="text" icon={<LogoutOutlined />} onClick={handleLogout}>
                            Đăng xuất
                        </Button>
                    </Space>
                </Header>
                <Content style={{ margin: '24px 16px 0', overflow: 'initial' }}>
                    <div
                        style={{
                            padding: 24,
                            background: colorBgContainer,
                            borderRadius: borderRadiusLG,
                            minHeight: '80vh',
                        }}
                    >
                        {children}
                    </div>
                </Content>
            </Layout>
        </Layout>
    );
};

export default MainLayout;
