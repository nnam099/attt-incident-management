import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Modal, Form, Input, Select, Tag, Switch, message, Typography, Popconfirm } from 'antd';
import { PlusOutlined, EditOutlined, KeyOutlined } from '@ant-design/icons';
import api from '../services/api';
import { format } from 'date-fns';

const { Title } = Typography;
const { Option } = Select;

interface UserResponse {
    id: number;
    username: string;
    email: string;
    fullName: string;
    department: string;
    enabled: boolean;
    roles: string[];
    createdAt: string;
}

const UserManagementPage: React.FC = () => {
    const [users, setUsers] = useState<UserResponse[]>([]);
    const [loading, setLoading] = useState(false);
    
    // Modal states
    const [isModalVisible, setIsModalVisible] = useState(false);
    const [isEditMode, setIsEditMode] = useState(false);
    const [editingUserId, setEditingUserId] = useState<number | null>(null);
    const [form] = Form.useForm();

    const fetchUsers = async () => {
        setLoading(true);
        try {
            const res = await api.get<UserResponse[]>('/admin/users');
            setUsers(res.data);
        } catch (error) {
            message.error('Lỗi khi tải danh sách người dùng.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchUsers();
    }, []);

    const showCreateModal = () => {
        setIsEditMode(false);
        setEditingUserId(null);
        form.resetFields();
        setIsModalVisible(true);
    };

    const showEditModal = (user: UserResponse) => {
        setIsEditMode(true);
        setEditingUserId(user.id);
        form.setFieldsValue({
            username: user.username,
            email: user.email,
            fullName: user.fullName,
            department: user.department,
            roles: user.roles,
            enabled: user.enabled,
        });
        setIsModalVisible(true);
    };

    const handleCancel = () => {
        setIsModalVisible(false);
    };

    const onFinish = async (values: any) => {
        try {
            if (isEditMode && editingUserId) {
                // Update basic info
                await api.put(`/admin/users/${editingUserId}`, {
                    fullName: values.fullName,
                    department: values.department,
                    enabled: values.enabled,
                });
                // Update roles
                await api.post(`/admin/users/${editingUserId}/roles`, {
                    roles: values.roles
                });
                message.success('Cập nhật thành công');
            } else {
                // Create new user
                await api.post('/admin/users', values);
                message.success('Tạo người dùng thành công');
            }
            setIsModalVisible(false);
            fetchUsers();
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Có lỗi xảy ra');
        }
    };

    const handleResetPassword = async (id: number) => {
        const newPassword = prompt("Nhập mật khẩu mới cho tài khoản này:");
        if (newPassword) {
            try {
                await api.post(`/admin/users/${id}/reset-password`, { newPassword });
                message.success('Đặt lại mật khẩu thành công');
            } catch (error) {
                message.error('Thất bại khi đặt lại mật khẩu');
            }
        }
    };

    const columns = [
        {
            title: 'Tên đăng nhập',
            dataIndex: 'username',
            key: 'username',
            render: (text: string) => <strong>{text}</strong>,
        },
        {
            title: 'Họ tên',
            dataIndex: 'fullName',
            key: 'fullName',
        },
        {
            title: 'Phòng ban',
            dataIndex: 'department',
            key: 'department',
        },
        {
            title: 'Vai trò',
            dataIndex: 'roles',
            key: 'roles',
            render: (roles: string[]) => (
                <>
                    {roles.map(role => (
                        <Tag color={role === 'ROLE_ADMIN' ? 'red' : 'blue'} key={role}>
                            {role.replace('ROLE_', '')}
                        </Tag>
                    ))}
                </>
            ),
        },
        {
            title: 'Trạng thái',
            dataIndex: 'enabled',
            key: 'enabled',
            render: (enabled: boolean) => (
                <Tag color={enabled ? 'green' : 'volcano'}>
                    {enabled ? 'Hoạt động' : 'Bị khóa'}
                </Tag>
            ),
        },
        {
            title: 'Ngày tạo',
            dataIndex: 'createdAt',
            key: 'createdAt',
            render: (val: string) => val ? format(new Date(val), 'dd/MM/yyyy') : '',
        },
        {
            title: 'Hành động',
            key: 'action',
            render: (_: any, record: UserResponse) => (
                <Space size="small">
                    <Button type="primary" size="small" icon={<EditOutlined />} onClick={() => showEditModal(record)}>
                        Sửa
                    </Button>
                    <Popconfirm
                        title="Bạn muốn đặt lại mật khẩu?"
                        onConfirm={() => handleResetPassword(record.id)}
                        okText="Có"
                        cancelText="Không"
                    >
                        <Button type="default" size="small" icon={<KeyOutlined />}>Pass</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                <Title level={3} style={{ margin: 0 }}>Quản trị Người dùng</Title>
                <Button type="primary" icon={<PlusOutlined />} onClick={showCreateModal}>
                    Thêm Người dùng
                </Button>
            </div>

            <Table 
                columns={columns} 
                dataSource={users} 
                rowKey="id" 
                loading={loading}
                pagination={{ pageSize: 10 }}
            />

            <Modal
                title={isEditMode ? "Sửa Người dùng" : "Thêm Người dùng mới"}
                visible={isModalVisible}
                onCancel={handleCancel}
                footer={null}
                destroyOnClose
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={onFinish}
                    initialValues={{ enabled: true, roles: ['ROLE_REPORTER'] }}
                >
                    <Form.Item
                        name="username"
                        label="Tên đăng nhập"
                        rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập!' }]}
                    >
                        <Input disabled={isEditMode} />
                    </Form.Item>

                    {!isEditMode && (
                        <Form.Item
                            name="password"
                            label="Mật khẩu"
                            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu!' }]}
                        >
                            <Input.Password />
                        </Form.Item>
                    )}

                    <Form.Item
                        name="email"
                        label="Email"
                        rules={[
                            { required: true, message: 'Vui lòng nhập email!' },
                            { type: 'email', message: 'Email không hợp lệ!' }
                        ]}
                    >
                        <Input disabled={isEditMode} />
                    </Form.Item>

                    <Form.Item
                        name="fullName"
                        label="Họ và tên"
                    >
                        <Input />
                    </Form.Item>

                    <Form.Item
                        name="department"
                        label="Phòng ban"
                    >
                        <Input />
                    </Form.Item>

                    <Form.Item
                        name="roles"
                        label="Vai trò (Roles)"
                        rules={[{ required: true, message: 'Chọn ít nhất 1 vai trò' }]}
                    >
                        <Select mode="multiple" placeholder="Chọn vai trò">
                            <Option value="ROLE_ADMIN">Quản trị viên (ADMIN)</Option>
                            <Option value="ROLE_MANAGER">Quản lý (MANAGER)</Option>
                            <Option value="ROLE_ANALYST">Kỹ thuật viên (ANALYST)</Option>
                            <Option value="ROLE_HELPDESK">Điều phối viên (HELPDESK)</Option>
                            <Option value="ROLE_REPORTER">Người dùng (REPORTER)</Option>
                        </Select>
                    </Form.Item>

                    {isEditMode && (
                        <Form.Item
                            name="enabled"
                            label="Trạng thái kích hoạt"
                            valuePropName="checked"
                        >
                            <Switch checkedChildren="Hoạt động" unCheckedChildren="Khóa" />
                        </Form.Item>
                    )}

                    <Form.Item>
                        <Button type="primary" htmlType="submit" style={{ width: '100%' }}>
                            {isEditMode ? 'Lưu thay đổi' : 'Tạo mới'}
                        </Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default UserManagementPage;
