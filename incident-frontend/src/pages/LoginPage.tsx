import React, { useState } from 'react';
import { Form, Input, Button, Typography, message } from 'antd';
import { UserOutlined, LockOutlined, SafetyCertificateOutlined, ArrowRightOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/auth';
import api from '../services/api';

const { Title } = Typography;

const LoginPage: React.FC = () => {
    const [loading, setLoading] = useState(false);
    const { login } = useAuth();
    const navigate = useNavigate();

    const onFinish = async (values: any) => {
        setLoading(true);
        try {
            const response = await api.post('/auth/login', {
                username: values.username,
                password: values.password
            });
            const { token, username, roles } = response.data;
            login(token, username, roles);
            message.success('Đăng nhập thành công!');
            navigate(roles.some((role: string) => role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER')
                ? '/dashboard'
                : '/incidents');
        } catch (error: any) {
            if (error.response && error.response.data && error.response.data.message) {
                message.error(error.response.data.message);
            } else {
                message.error('Đăng nhập thất bại. Vui lòng kiểm tra lại kết nối.');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-screen">
            <div className="login-panel">
                <div className="login-brand"><span className="soc-brand-mark"><SafetyCertificateOutlined /></span> INCIDENT / HUB</div>
                <div className="login-main">
                <span className="login-kicker">SECURITY OPERATIONS PLATFORM</span>
                <Title level={1} className="login-title">Chào mừng<br />trở lại.</Title>
                <p className="login-intro">Đăng nhập để theo dõi, điều phối và xử lý sự cố an toàn thông tin.</p>
                <Form
                    className="login-form"
                    name="login_form"
                    initialValues={{ remember: true }}
                    onFinish={onFinish}
                    layout="vertical"
                >
                    <Form.Item
                        name="username"
                        label="Tên đăng nhập"
                        rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập!' }]}
                    >
                        <Input prefix={<UserOutlined />} placeholder="Nhập tên đăng nhập" size="large" />
                    </Form.Item>

                    <Form.Item
                        name="password"
                        label="Mật khẩu"
                        rules={[{ required: true, message: 'Vui lòng nhập mật khẩu!' }]}
                    >
                        <Input.Password prefix={<LockOutlined />} placeholder="Nhập mật khẩu" size="large" />
                    </Form.Item>

                    <Form.Item>
                        <Button type="primary" htmlType="submit" style={{ width: '100%' }} size="large" loading={loading}>
                            Truy cập hệ thống <ArrowRightOutlined />
                        </Button>
                    </Form.Item>
                </Form>
                </div>
                <div className="login-footer">INCIDENT / HUB · TRUNG TÂM ĐIỀU PHỐI SỰ CỐ</div>
            </div>
            <div className="login-visual">
                <span className="visual-index">SOC / 01 — COMMAND CENTER</span>
                <div><h2>Mọi tín hiệu.<br /><span>Một điểm kiểm soát.</span></h2><p>Không bỏ lỡ diễn biến quan trọng. Quản lý sự cố xuyên suốt từ tiếp nhận đến khôi phục.</p><div className="visual-rule" /><div className="visual-bottom">DETECT&nbsp;&nbsp; / &nbsp;&nbsp;RESPOND&nbsp;&nbsp; / &nbsp;&nbsp;RECOVER</div></div>
            </div>
        </div>
    );
};

export default LoginPage;
