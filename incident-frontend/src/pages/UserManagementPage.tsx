import React, { useEffect, useState } from 'react';
import { Table, Button, Space, Modal, Form, Input, Select, Tag, Switch, message, Typography, Popconfirm, Tooltip } from 'antd';
import { PlusOutlined, EditOutlined, KeyOutlined, DeleteOutlined, UserOutlined, UnlockOutlined, StopOutlined } from '@ant-design/icons';
import api from '../services/api';
import { useAuth } from '../context/auth';
import { format } from 'date-fns';

const { Title, Text } = Typography;
const { Option } = Select;

interface UserResponse {
    id: number;
    username: string;
    email: string;
    fullName: string;
    department: string;
    enabled: boolean;
    failedLoginAttempts: number;
    accountLockedUntil?: string;
    temporarilyLocked: boolean;
    roles: string[];
    createdAt: string;
}

const ROLE_COLOR_MAP: Record<string, string> = {
    ADMIN: 'red',
    MANAGER: 'purple',
    ANALYST: 'cyan',
    HELPDESK: 'orange',
    REPORTER: 'blue',
};

const UserManagementPage: React.FC = () => {
    const { user: currentUser } = useAuth();
    const [users, setUsers] = useState<UserResponse[]>([]);
    const [loading, setLoading] = useState(false);
    const [searchText, setSearchText] = useState('');

    // Create/Edit Modal state
    const [isModalVisible, setIsModalVisible] = useState(false);
    const [isEditMode, setIsEditMode] = useState(false);
    const [editingUserId, setEditingUserId] = useState<number | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [form] = Form.useForm();

    // Reset Password Modal state
    const [isResetPwdModalOpen, setIsResetPwdModalOpen] = useState(false);
    const [resetPwdUser, setResetPwdUser] = useState<UserResponse | null>(null);
    const [resetPwdSubmitting, setResetPwdSubmitting] = useState(false);
    const [resetPwdForm] = Form.useForm();

    const fetchUsers = async () => {
        setLoading(true);
        try {
            const res = await api.get<UserResponse[]>('/admin/users');
            setUsers(res.data);
        } catch {
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
        form.setFieldsValue({
            enabled: true,
            roles: ['REPORTER']
        });
        setIsModalVisible(true);
    };

    const showEditModal = (user: UserResponse) => {
        setIsEditMode(true);
        setEditingUserId(user.id);
        const cleanRoles = (user.roles || []).map(r => r.replace('ROLE_', ''));
        form.setFieldsValue({
            username: user.username,
            email: user.email,
            fullName: user.fullName,
            department: user.department,
            roles: cleanRoles,
            enabled: user.enabled,
        });
        setIsModalVisible(true);
    };

    const handleCancel = () => {
        setIsModalVisible(false);
    };

    const onFinish = async (values: any) => {
        setSubmitting(true);
        try {
            if (isEditMode && editingUserId) {
                // Update user and roles in one request
                await api.put(`/admin/users/${editingUserId}`, {
                    fullName: values.fullName,
                    email: values.email,
                    department: values.department,
                    enabled: values.enabled,
                    roles: values.roles,
                });
                message.success('Cập nhật người dùng thành công');
            } else {
                // Create new user
                await api.post('/admin/users', {
                    username: values.username,
                    password: values.password,
                    email: values.email,
                    fullName: values.fullName,
                    department: values.department,
                    roles: values.roles,
                });
                message.success('Tạo người dùng mới thành công');
            }
            setIsModalVisible(false);
            fetchUsers();
        } catch (error: any) {
            if (error.response?.data?.errors) {
                const errMap = error.response.data.errors;
                const errMsgs = Object.values(errMap).join('; ');
                message.error(`Lỗi dữ liệu: ${errMsgs}`);
            } else if (error.response?.data?.message) {
                message.error(error.response.data.message);
            } else {
                message.error('Có lỗi xảy ra, vui lòng thử lại');
            }
        } finally {
            setSubmitting(false);
        }
    };

    // Quick Lock / Unlock
    const handleToggleLock = async (user: UserResponse) => {
        if (user.username === currentUser?.username) {
            message.warning('Không thể tự khóa tài khoản của chính mình');
            return;
        }
        try {
            const targetEnabled = !user.enabled;
            await api.post(`/admin/users/${user.id}/lock`, { enabled: targetEnabled });
            message.success(targetEnabled ? `Đã mở khóa tài khoản "${user.username}"` : `Đã khóa tài khoản "${user.username}"`);
            fetchUsers();
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Lỗi khi cập nhật trạng thái');
        }
    };

    // Delete User
    const handleDeleteUser = async (user: UserResponse) => {
        if (user.username === currentUser?.username) {
            message.warning('Không thể xóa tài khoản của chính mình');
            return;
        }
        try {
            await api.delete(`/admin/users/${user.id}`);
            message.success(`Đã xóa người dùng "${user.username}" thành công`);
            fetchUsers();
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Lỗi khi xóa người dùng');
        }
    };

    const handleUnlockAttempts = async (user: UserResponse) => {
        try {
            await api.post(`/admin/users/${user.id}/unlock`);
            message.success(`Đã mở khóa đăng nhập cho "${user.username}"`);
            fetchUsers();
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Không thể mở khóa đăng nhập');
        }
    };

    const handleRevokeSessions = async (user: UserResponse) => {
        try {
            await api.post(`/admin/users/${user.id}/revoke-sessions`);
            message.success(`Đã thu hồi toàn bộ phiên của "${user.username}"`);
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Không thể thu hồi phiên');
        }
    };

    // Reset Password Modal
    const showResetPasswordModal = (user: UserResponse) => {
        setResetPwdUser(user);
        resetPwdForm.resetFields();
        setIsResetPwdModalOpen(true);
    };

    const handleResetPasswordSubmit = async (values: any) => {
        if (!resetPwdUser) return;
        setResetPwdSubmitting(true);
        try {
            await api.post(`/admin/users/${resetPwdUser.id}/reset-password`, {
                newPassword: values.newPassword
            });
            message.success(`Đặt lại mật khẩu cho "${resetPwdUser.username}" thành công`);
            setIsResetPwdModalOpen(false);
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Thất bại khi đặt lại mật khẩu');
        } finally {
            setResetPwdSubmitting(false);
        }
    };

    const columns = [
        {
            title: 'Tên đăng nhập',
            dataIndex: 'username',
            key: 'username',
            render: (text: string) => {
                const isCurrent = text === currentUser?.username;
                return (
                    <Space>
                        <UserOutlined />
                        <strong>{text}</strong>
                        {isCurrent && <Tag color="gold">Bạn</Tag>}
                    </Space>
                );
            },
        },
        {
            title: 'Họ tên',
            dataIndex: 'fullName',
            key: 'fullName',
            render: (val: string) => val || <Text type="secondary">Chưa cập nhật</Text>,
        },
        {
            title: 'Email',
            dataIndex: 'email',
            key: 'email',
        },
        {
            title: 'Phòng ban',
            dataIndex: 'department',
            key: 'department',
            render: (val: string) => val || <Text type="secondary">—</Text>,
        },
        {
            title: 'Vai trò',
            dataIndex: 'roles',
            key: 'roles',
            render: (roles: string[]) => (
                <Space size={[0, 4]} wrap>
                    {(roles || []).map(role => {
                        const cleanRole = role.replace('ROLE_', '');
                        const color = ROLE_COLOR_MAP[cleanRole] || 'blue';
                        return (
                            <Tag color={color} key={role}>
                                {cleanRole}
                            </Tag>
                        );
                    })}
                </Space>
            ),
        },
        {
            title: 'Trạng thái',
            dataIndex: 'enabled',
            key: 'enabled',
            render: (enabled: boolean, record: UserResponse) => {
                const isCurrent = record.username === currentUser?.username;
                return (
                    <Space direction="vertical" size={4}>
                        {record.temporarilyLocked && <Tag color="error">Khóa tạm do đăng nhập sai</Tag>}
                    <Popconfirm
                        title={enabled ? "Vô hiệu hóa tài khoản này?" : "Kích hoạt tài khoản này?"}
                        description={enabled ? "Người dùng sẽ bị thu hồi token và không thể đăng nhập." : "Người dùng sẽ có thể đăng nhập bình thường."}
                        onConfirm={() => handleToggleLock(record)}
                        disabled={isCurrent}
                        okText="Đồng ý"
                        cancelText="Hủy"
                    >
                        <Tooltip title={isCurrent ? "Không thể tự vô hiệu hóa tài khoản" : (enabled ? "Nhấn để vô hiệu hóa" : "Nhấn để kích hoạt")}>
                            <Switch
                                checked={enabled}
                                disabled={isCurrent}
                                checkedChildren="Hoạt động"
                                unCheckedChildren="Vô hiệu"
                            />
                        </Tooltip>
                    </Popconfirm>
                    </Space>
                );
            },
        },
        {
            title: 'Ngày tạo',
            dataIndex: 'createdAt',
            key: 'createdAt',
            render: (val: string) => val ? format(new Date(val), 'dd/MM/yyyy HH:mm') : '',
        },
        {
            title: 'Hành động',
            key: 'action',
            render: (_: any, record: UserResponse) => {
                const isCurrent = record.username === currentUser?.username;
                return (
                    <Space size="small">
                        <Button
                            type="primary"
                            size="small"
                            icon={<EditOutlined />}
                            onClick={() => showEditModal(record)}
                        >
                            Sửa
                        </Button>
                        <Button
                            type="default"
                            size="small"
                            icon={<KeyOutlined />}
                            onClick={() => showResetPasswordModal(record)}
                        >
                            Pass
                        </Button>
                        {record.failedLoginAttempts > 0 && <Button size="small" icon={<UnlockOutlined />}
                            onClick={() => handleUnlockAttempts(record)}>
                            {record.temporarilyLocked ? 'Mở khóa' : `Xóa ${record.failedLoginAttempts} lần lỗi`}
                        </Button>}
                        <Popconfirm title={`Thu hồi toàn bộ phiên của "${record.username}"?`}
                            description="Mọi access token và refresh token hiện tại sẽ mất hiệu lực."
                            onConfirm={() => handleRevokeSessions(record)} okText="Thu hồi" cancelText="Hủy">
                            <Button size="small" icon={<StopOutlined />}>Thu hồi phiên</Button>
                        </Popconfirm>
                        <Popconfirm
                            title={`Xóa tài khoản "${record.username}"?`}
                            description="Hành động này không thể hoàn tác nếu người dùng chưa có dữ liệu lịch sử sự cố."
                            onConfirm={() => handleDeleteUser(record)}
                            disabled={isCurrent}
                            okText="Xóa"
                            cancelText="Hủy"
                            okButtonProps={{ danger: true }}
                        >
                            <Tooltip title={isCurrent ? "Không thể tự xóa tài khoản của chính mình" : "Xóa tài khoản"}>
                                <Button
                                    danger
                                    size="small"
                                    icon={<DeleteOutlined />}
                                    disabled={isCurrent}
                                />
                            </Tooltip>
                        </Popconfirm>
                    </Space>
                );
            },
        },
    ];

    const isCurrentAdminEditingSelf = isEditMode && users.find(u => u.id === editingUserId)?.username === currentUser?.username;
    const filteredUsers = users.filter(user => {
        const query = searchText.trim().toLowerCase();
        if (!query) return true;
        return [user.username, user.email, user.fullName, user.department]
            .some(value => value?.toLowerCase().includes(query));
    });

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <div>
                    <Title level={3} style={{ margin: 0 }}>Quản trị Người dùng</Title>
                    <Text type="secondary">Quản lý danh sách tài khoản, phân quyền vai trò và bảo mật hệ thống</Text>
                </div>
                <Space>
                    <Input.Search allowClear placeholder="Tìm tài khoản, email, họ tên" style={{ width: 280 }}
                        onSearch={setSearchText} onChange={event => { if (!event.target.value) setSearchText(''); }} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={showCreateModal} size="large">
                        Thêm Người dùng
                    </Button>
                </Space>
            </div>

            <Table
                columns={columns}
                dataSource={filteredUsers}
                rowKey="id"
                loading={loading}
                scroll={{ x: 1250 }}
                pagination={{ pageSize: 10, showTotal: (total) => `Tổng số ${total} người dùng` }}
            />

            {/* Modal Tạo / Sửa người dùng */}
            <Modal
                title={isEditMode ? "Sửa thông tin Người dùng" : "Thêm Người dùng mới"}
                open={isModalVisible}
                onCancel={handleCancel}
                footer={null}
                destroyOnClose
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={onFinish}
                    initialValues={{ enabled: true, roles: ['REPORTER'] }}
                >
                    <Form.Item
                        name="username"
                        label="Tên đăng nhập"
                        rules={[
                            { required: true, message: 'Vui lòng nhập tên đăng nhập!' },
                            { min: 4, message: 'Tên đăng nhập phải có ít nhất 4 ký tự!' }
                        ]}
                    >
                        <Input disabled={isEditMode} placeholder="Ví dụ: nguyenvan_a" />
                    </Form.Item>

                    {!isEditMode && (
                        <Form.Item
                            name="password"
                            label="Mật khẩu"
                            rules={[
                                { required: true, message: 'Vui lòng nhập mật khẩu!' },
                                { min: 12, max: 72, message: 'Mật khẩu phải có 12-72 ký tự!' },
                                { pattern: /[A-Z]/, message: 'Mật khẩu cần ít nhất một chữ hoa!' },
                                { pattern: /[a-z]/, message: 'Mật khẩu cần ít nhất một chữ thường!' },
                                { pattern: /\d/, message: 'Mật khẩu cần ít nhất một chữ số!' },
                                { pattern: /[^A-Za-z0-9]/, message: 'Mật khẩu cần ít nhất một ký tự đặc biệt!' }
                            ]}
                        >
                            <Input.Password placeholder="Tối thiểu 12 ký tự, đủ 4 nhóm ký tự" />
                        </Form.Item>
                    )}

                    <Form.Item
                        name="email"
                        label="Email"
                        rules={[
                            { required: true, message: 'Vui lòng nhập email!' },
                            { type: 'email', message: 'Email không đúng định dạng!' }
                        ]}
                    >
                        <Input placeholder="name@domain.com" />
                    </Form.Item>

                    <Form.Item
                        name="fullName"
                        label="Họ và tên"
                    >
                        <Input placeholder="Ví dụ: Nguyễn Văn A" />
                    </Form.Item>

                    <Form.Item
                        name="department"
                        label="Phòng ban / Đơn vị"
                    >
                        <Input placeholder="Ví dụ: SOC, ATTT, CNTT" />
                    </Form.Item>

                    <Form.Item
                        name="roles"
                        label="Vai trò (Roles)"
                        rules={[{ required: true, message: 'Vui lòng chọn ít nhất 1 vai trò!' }]}
                    >
                        <Select
                            mode="multiple"
                            placeholder="Chọn vai trò"
                            onChange={(selectedRoles: string[]) => {
                                // Nếu admin đang sửa tài khoản của chính mình, không cho phép bỏ ADMIN
                                if (isCurrentAdminEditingSelf && !selectedRoles.includes('ADMIN')) {
                                    message.warning('Bạn không thể tự gỡ vai trò ADMIN của chính mình');
                                    form.setFieldsValue({ roles: [...selectedRoles, 'ADMIN'] });
                                }
                            }}
                        >
                            <Option value="ADMIN">Quản trị viên (ADMIN)</Option>
                            <Option value="MANAGER">Quản lý (MANAGER)</Option>
                            <Option value="ANALYST">Chuyên viên xử lý (ANALYST)</Option>
                            <Option value="HELPDESK">Tiếp nhận sự cố (HELPDESK)</Option>
                            <Option value="REPORTER">Người báo cáo (REPORTER)</Option>
                        </Select>
                    </Form.Item>

                    {isEditMode && (
                        <Form.Item
                            name="enabled"
                            label="Trạng thái tài khoản"
                            valuePropName="checked"
                        >
                            <Switch
                                checkedChildren="Hoạt động"
                                unCheckedChildren="Khóa"
                                disabled={isCurrentAdminEditingSelf}
                            />
                        </Form.Item>
                    )}

                    <Form.Item style={{ marginBottom: 0, marginTop: 24 }}>
                        <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
                            <Button onClick={handleCancel}>Hủy</Button>
                            <Button type="primary" htmlType="submit" loading={submitting}>
                                {isEditMode ? 'Lưu thay đổi' : 'Tạo mới'}
                            </Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Modal>

            {/* Modal Đặt lại mật khẩu */}
            <Modal
                title={`Đặt lại mật khẩu: ${resetPwdUser?.username || ''}`}
                open={isResetPwdModalOpen}
                onCancel={() => setIsResetPwdModalOpen(false)}
                footer={null}
                destroyOnClose
            >
                <Form
                    form={resetPwdForm}
                    layout="vertical"
                    onFinish={handleResetPasswordSubmit}
                >
                    <Form.Item
                        name="newPassword"
                        label="Mật khẩu mới"
                        rules={[
                            { required: true, message: 'Vui lòng nhập mật khẩu mới!' },
                            { min: 12, max: 72, message: 'Mật khẩu phải có 12-72 ký tự!' },
                            { pattern: /[A-Z]/, message: 'Mật khẩu cần ít nhất một chữ hoa!' },
                            { pattern: /[a-z]/, message: 'Mật khẩu cần ít nhất một chữ thường!' },
                            { pattern: /\d/, message: 'Mật khẩu cần ít nhất một chữ số!' },
                            { pattern: /[^A-Za-z0-9]/, message: 'Mật khẩu cần ít nhất một ký tự đặc biệt!' }
                        ]}
                    >
                        <Input.Password placeholder="Tối thiểu 12 ký tự, đủ 4 nhóm ký tự" />
                    </Form.Item>

                    <Form.Item
                        name="confirmPassword"
                        label="Xác nhận mật khẩu mới"
                        dependencies={['newPassword']}
                        rules={[
                            { required: true, message: 'Vui lòng xác nhận lại mật khẩu!' },
                            ({ getFieldValue }) => ({
                                validator(_, value) {
                                    if (!value || getFieldValue('newPassword') === value) {
                                        return Promise.resolve();
                                    }
                                    return Promise.reject(new Error('Mật khẩu xác nhận không khớp!'));
                                },
                            }),
                        ]}
                    >
                        <Input.Password placeholder="Nhập lại mật khẩu mới" />
                    </Form.Item>

                    <Form.Item style={{ marginBottom: 0, marginTop: 20 }}>
                        <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
                            <Button onClick={() => setIsResetPwdModalOpen(false)}>Hủy</Button>
                            <Button type="primary" htmlType="submit" loading={resetPwdSubmitting}>
                                Đổi mật khẩu
                            </Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default UserManagementPage;
