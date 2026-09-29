import React, { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Descriptions, Tag, Button, Space, Timeline, Typography, Select, message, Form, Input, Divider, Upload, List, Progress, Alert, Row, Col } from 'antd';
import { ArrowLeftOutlined, SaveOutlined, SendOutlined, DownloadOutlined, UploadOutlined, FileOutlined } from '@ant-design/icons';
import { format } from 'date-fns';
import api from '../services/api';
import type { IncidentResponse } from '../types';
import { useAuth } from '../context/auth';

const { Title, Text } = Typography;
const { Option } = Select;
const { TextArea } = Input;

interface LogResponse {
    id: number;
    actionType: string;
    oldValue: string;
    newValue: string;
    note: string;
    actor: string;
    timestamp: string;
}

interface AttachmentResponse {
    id: number;
    fileName: string;
    contentType: string;
    fileSize: number;
    uploadedBy: string;
    uploadedAt: string;
}

interface AssigneeResponse {
    id: number;
    username: string;
    fullName?: string;
}

interface AuditIntegrityResponse {
    valid: boolean;
    checkedRecords: number;
    firstInvalidLogId?: number;
}

const ALLOWED_STATUS_TRANSITIONS: Record<string, string[]> = {
    NEW: ['TRIAGE', 'CLOSED'],
    TRIAGE: ['INVESTIGATING', 'CLOSED'],
    INVESTIGATING: ['CONTAINED', 'RECOVERED', 'RESOLVED'],
    CONTAINED: ['RECOVERED', 'INVESTIGATING'],
    RECOVERED: ['RESOLVED', 'INVESTIGATING'],
    RESOLVED: ['CLOSED', 'INVESTIGATING'],
    CLOSED: ['REOPENED'],
    REOPENED: ['INVESTIGATING', 'TRIAGE'],
};

const STATUS_LABELS: Record<string, string> = {
    NEW: 'Mới tạo (NEW)',
    TRIAGE: 'Phân loại (TRIAGE)',
    INVESTIGATING: 'Đang điều tra (INVESTIGATING)',
    CONTAINED: 'Ngăn chặn (CONTAINED)',
    RECOVERED: 'Khôi phục (RECOVERED)',
    RESOLVED: 'Đã giải quyết (RESOLVED)',
    CLOSED: 'Đã đóng (CLOSED)',
    REOPENED: 'Tái mở (REOPENED)',
};

const IncidentDetailPage: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { user } = useAuth();

    const [incident, setIncident] = useState<IncidentResponse | null>(null);
    const [logs, setLogs] = useState<LogResponse[]>([]);
    const [attachments, setAttachments] = useState<AttachmentResponse[]>([]);
    const [assignees, setAssignees] = useState<AssigneeResponse[]>([]);
    const [auditIntegrity, setAuditIntegrity] = useState<AuditIntegrityResponse | null>(null);
    const [loading, setLoading] = useState(true);

    const [statusForm] = Form.useForm();
    const [commentForm] = Form.useForm();
    const [iocForm] = Form.useForm();
    const [taskForm] = Form.useForm();
    const [assignForm] = Form.useForm();
    const selectedStatus = Form.useWatch('newStatus', statusForm);


    const fetchData = useCallback(async () => {
        setLoading(true);
        try {
            const [incRes, logsRes, attRes, integrityRes] = await Promise.all([
                api.get<IncidentResponse>(`/incidents/${id}`),
                api.get<LogResponse[]>(`/incidents/${id}/logs`),
                api.get<AttachmentResponse[]>(`/incidents/${id}/attachments`),
                api.get<AuditIntegrityResponse>(`/incidents/${id}/audit-integrity`),
            ]);
            setIncident(incRes.data);
            setLogs(logsRes.data);
            setAttachments(attRes.data);
            setAuditIntegrity(integrityRes.data);
            statusForm.setFieldsValue({ newStatus: undefined, resolutionType: undefined });
        } catch {
            message.error('Lỗi khi tải chi tiết sự cố.');
            navigate('/incidents');
        } finally {
            setLoading(false);
        }
    }, [id, navigate, statusForm]);

    useEffect(() => {
        if (id) {
            fetchData();
        }
    }, [id, fetchData]);

    const canAssignIncident = Boolean(incident && (
        user?.roles.some(role => role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER')
        || (user?.roles.includes('ROLE_HELPDESK') && ['NEW', 'TRIAGE'].includes(incident.status))
    ));

    useEffect(() => {
        if (!canAssignIncident) {
            setAssignees([]);
            return;
        }
        api.get<AssigneeResponse[]>('/users/assignees')
            .then(response => setAssignees(response.data))
            .catch(() => message.error('Không thể tải danh sách chuyên viên xử lý'));
    }, [canAssignIncident]);

    const handleStatusChange = async (values: any) => {
        try {
            await api.patch(`/incidents/${id}/status`, {
                newStatus: values.newStatus,
                note: values.note,
                resolutionType: values.resolutionType,
            });
            message.success('Cập nhật trạng thái thành công');
            statusForm.resetFields(['note']);
            fetchData();
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Cập nhật trạng thái thất bại');
        }
    };

    const handleAddComment = async (values: any) => {
        try {
            await api.post(`/incidents/${id}/comments`, {
                content: values.content
            });
            message.success('Đã thêm bình luận');
            commentForm.resetFields();
            fetchData();
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Thêm bình luận thất bại');
        }
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'NEW': return 'cyan';
            case 'TRIAGE': return 'geekblue';
            case 'INVESTIGATING': return 'blue';
            case 'CONTAINED': return 'orange';
            case 'RECOVERED': return 'lime';
            case 'RESOLVED': return 'green';
            case 'CLOSED': return 'default';
            case 'REOPENED': return 'red';
            default: return 'default';
        }
    };

    const getSeverityColor = (severity: string) => {
        switch (severity) {
            case 'CRITICAL': return 'magenta';
            case 'HIGH': return 'red';
            case 'MEDIUM': return 'orange';
            case 'LOW': return 'green';
            default: return 'blue';
        }
    };

    const handleAddIoC = async (values: any) => {
        try {
            await api.post(`/incidents/${id}/iocs`, values);
            message.success('Thêm IoC thành công');
            iocForm.resetFields();
            fetchData();
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Thêm IoC thất bại');
        }
    };

    const handleDeleteIoC = async (iocId: number) => {
        try {
            await api.delete(`/incidents/${id}/iocs/${iocId}`);
            message.success('Xóa IoC thành công');
            fetchData();
        } catch {
            message.error('Xóa IoC thất bại');
        }
    };

    const handleAddTask = async (values: any) => {
        try {
            await api.post(`/incidents/${id}/tasks`, values);
            message.success('Thêm Task thành công');
            taskForm.resetFields();
            fetchData();
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Thêm Task thất bại');
        }
    };

    const handleToggleTask = async (taskId: number) => {
        try {
            await api.patch(`/incidents/${id}/tasks/${taskId}/toggle`);
            message.success('Cập nhật Task thành công');
            fetchData();
        } catch {
            message.error('Cập nhật Task thất bại');
        }
    };

    const handleFileUpload = async (options: any) => {
        const { onSuccess, onError, file } = options;
        const formData = new FormData();
        formData.append('file', file);

        try {
            await api.post(`/incidents/${id}/attachments`, formData);
            message.success('Đính kèm file thành công');
            onSuccess('ok');
            fetchData();
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Tải file lên thất bại');
            onError(error);
        }
    };

    const handleDownload = async (attachment: AttachmentResponse) => {
        try {
            const res = await api.get(`/attachments/${attachment.id}/download`, {
                responseType: 'blob'
            });
            const url = window.URL.createObjectURL(new Blob([res.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', attachment.fileName);
            document.body.appendChild(link);
            link.click();
            link.parentNode?.removeChild(link);
            window.URL.revokeObjectURL(url);
        } catch {
            message.error('Tải file thất bại');
        }
    };

    const handleAssign = async (values: { assigneeUserId: number; note?: string }) => {
        try {
            await api.patch(`/incidents/${id}/assign`, values);
            message.success('Phân công người xử lý thành công');
            assignForm.resetFields();
            fetchData();
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Phân công người xử lý thất bại');
        }
    };

    if (loading || !incident) {
        return <p>Đang tải dữ liệu...</p>;
    }

    const canManage = user?.roles.some(role => role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER')
        || (user?.roles.includes('ROLE_ANALYST') && user.username === incident.assignedToUsername);
    const canTriage = user?.roles.includes('ROLE_HELPDESK') && incident.status === 'NEW';
    const canAssign = canAssignIncident;
    const availableStatuses = canTriage
        ? ['TRIAGE']
        : (ALLOWED_STATUS_TRANSITIONS[incident.status] || []);
    const statusNoteRequired = incident.status === 'RESOLVED'
        || ['CONTAINED', 'RECOVERED', 'RESOLVED', 'CLOSED', 'REOPENED'].includes(selectedStatus);

    return (
        <div style={{ paddingBottom: 24 }}>
            <Space style={{ marginBottom: 16 }}>
                <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/incidents')}>
                    Quay lại
                </Button>
                <Title level={3} style={{ margin: 0 }}>Chi tiết: {incident.incidentCode}</Title>
            </Space>

            <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
                <div style={{ flex: '1 1 60%', minWidth: 320 }}>
                    <Card title="Thông tin Sự cố" bordered={false} style={{ marginBottom: 24, boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <Descriptions column={2} bordered size="small">
                            <Descriptions.Item label="Tiêu đề" span={2}><strong>{incident.title}</strong></Descriptions.Item>
                            <Descriptions.Item label="Trạng thái">
                                <Tag color={getStatusColor(incident.status)}>{incident.status}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="Mức độ">
                                <Tag color={getSeverityColor(incident.severity)}>{incident.severity}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="Risk score">
                                <Tag color={getSeverityColor(incident.riskLevel)}>{incident.riskScore}/100 · {incident.riskLevel}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="Danh mục">{incident.categoryName || 'N/A'}</Descriptions.Item>
                            <Descriptions.Item label="Hệ thống ảnh hưởng">{incident.affectedSystem || 'N/A'}</Descriptions.Item>
                            <Descriptions.Item label="Người báo cáo">{incident.reportedByUsername}</Descriptions.Item>
                            <Descriptions.Item label="Người xử lý">{incident.assignedToUsername || 'Chưa có'}</Descriptions.Item>
                            <Descriptions.Item label="Hạn tiếp nhận (MTTA)">
                                {incident.ackDueAt ? format(new Date(incident.ackDueAt), 'HH:mm dd/MM/yyyy') : 'N/A'}
                            </Descriptions.Item>
                            <Descriptions.Item label="Hạn xử lý (MTTR)">
                                {incident.resolveDueAt ? format(new Date(incident.resolveDueAt), 'HH:mm dd/MM/yyyy') : 'N/A'}
                            </Descriptions.Item>
                            {incident.resolutionType && (
                                <Descriptions.Item label="Kết luận" span={2}>
                                    <Tag color="purple">{incident.resolutionType}</Tag>
                                </Descriptions.Item>
                            )}
                            {incident.resolvedAt && (
                                <Descriptions.Item label="Giải quyết lúc" span={2}>
                                    {format(new Date(incident.resolvedAt), 'HH:mm dd/MM/yyyy')}
                                </Descriptions.Item>
                            )}
                            <Descriptions.Item label="Tạo lúc">
                                {format(new Date(incident.createdAt), 'HH:mm dd/MM/yyyy')}
                            </Descriptions.Item>
                            <Descriptions.Item label="Cập nhật lúc">
                                {format(new Date(incident.updatedAt), 'HH:mm dd/MM/yyyy')}
                            </Descriptions.Item>
                            <Descriptions.Item label="Mô tả chi tiết" span={2}>
                                <div style={{ whiteSpace: 'pre-wrap', background: '#f9f9f9', padding: 12, borderRadius: 4 }}>
                                    {incident.description}
                                </div>
                            </Descriptions.Item>
                        </Descriptions>

                        <Divider>Tiến độ SLA</Divider>
                        <Row gutter={[16, 16]}>
                            <Col xs={24} md={12}>
                                <SlaProgress title="Tiếp nhận (MTTA)" start={incident.createdAt}
                                    due={incident.ackDueAt} completed={incident.acknowledgedAt} />
                            </Col>
                            <Col xs={24} md={12}>
                                <SlaProgress title="Xử lý (MTTR)" start={incident.createdAt}
                                    due={incident.resolveDueAt} completed={incident.resolvedAt} />
                            </Col>
                        </Row>

                        <Divider>Tài liệu đính kèm minh chứng</Divider>
                        <List
                            size="small"
                            bordered
                            dataSource={attachments}
                            renderItem={(item) => (
                                <List.Item
                                    actions={[
                                        <Button
                                            key="download"
                                            type="link"
                                            icon={<DownloadOutlined />}
                                            onClick={() => handleDownload(item)}
                                        >
                                            Tải về ({(item.fileSize / 1024).toFixed(1)} KB)
                                        </Button>
                                    ]}
                                >
                                    <List.Item.Meta
                                        avatar={<FileOutlined style={{ fontSize: 24, color: '#1890ff' }} />}
                                        title={item.fileName}
                                        description={`Tải lên bởi ${item.uploadedBy} lúc ${format(new Date(item.uploadedAt), 'HH:mm dd/MM')}`}
                                    />
                                </List.Item>
                            )}
                            locale={{ emptyText: 'Chưa có file đính kèm' }}
                            style={{ marginBottom: 16 }}
                        />
                        <Upload customRequest={handleFileUpload} showUploadList={false}>
                            <Button icon={<UploadOutlined />}>Đính kèm File mới</Button>
                        </Upload>
                    </Card>

                    <Card title="Artifacts / Indicators of Compromise (IoCs)" bordered={false} style={{ marginBottom: 24, boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <List
                            size="small"
                            bordered
                            dataSource={incident.iocs}
                            renderItem={(item) => (
                                <List.Item
                                    actions={canManage ? [<Button key="delete" danger type="text" onClick={() => handleDeleteIoC(item.id)}>Xóa</Button>] : []}
                                >
                                    <List.Item.Meta
                                        title={<Tag color="blue">{item.type}</Tag>}
                                        description={<span><strong>{item.value}</strong> {item.description && ` - ${item.description}`}</span>}
                                    />
                                </List.Item>
                            )}
                            locale={{ emptyText: 'Chưa có IoC nào' }}
                            style={{ marginBottom: 16 }}
                        />
                        {canManage && <Form form={iocForm} layout="inline" onFinish={handleAddIoC}>
                            <Form.Item name="type" rules={[{ required: true }]}>
                                <Select placeholder="Loại IoC" style={{ width: 120 }}>
                                    <Option value="IPV4">IPv4</Option>
                                    <Option value="DOMAIN">Domain</Option>
                                    <Option value="URL">URL</Option>
                                    <Option value="MD5_HASH">MD5</Option>
                                    <Option value="SHA256_HASH">SHA-256</Option>
                                    <Option value="EMAIL_ADDRESS">Email</Option>
                                    <Option value="FILE_PATH">Đường dẫn tệp</Option>
                                </Select>
                            </Form.Item>
                            <Form.Item name="value" rules={[{ required: true }]}>
                                <Input placeholder="Giá trị (VD: 1.1.1.1)" />
                            </Form.Item>
                            <Form.Item name="description">
                                <Input placeholder="Mô tả thêm" />
                            </Form.Item>
                            <Form.Item>
                                <Button type="primary" htmlType="submit">Thêm IoC</Button>
                            </Form.Item>
                        </Form>}
                    </Card>

                    <Card title="Playbook / Tasks" bordered={false} style={{ marginBottom: 24, boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <List
                            size="small"
                            bordered
                            dataSource={incident.tasks}
                            renderItem={(item) => (
                                <List.Item
                                    actions={[
                                        ...(canManage ? [<Button key="toggle" type={item.isCompleted ? 'default' : 'primary'} size="small" onClick={() => handleToggleTask(item.id)}>
                                            {item.isCompleted ? 'Hủy hoàn thành' : 'Đánh dấu Xong'}
                                        </Button>] : [])
                                    ]}
                                >
                                    <List.Item.Meta
                                        title={<span style={{ textDecoration: item.isCompleted ? 'line-through' : 'none' }}>{item.taskName}</span>}
                                        description={item.isCompleted ? `Hoàn thành bởi ${item.completedByUsername} lúc ${format(new Date(item.completedAt!), 'HH:mm dd/MM')}` : 'Chưa hoàn thành'}
                                    />
                                </List.Item>
                            )}
                            locale={{ emptyText: 'Chưa có Task nào' }}
                            style={{ marginBottom: 16 }}
                        />
                        {canManage && <Form form={taskForm} layout="inline" onFinish={handleAddTask}>
                            <Form.Item name="taskName" rules={[{ required: true }]} style={{ flex: 1 }}>
                                <Input placeholder="Nhập tên công việc cần làm..." />
                            </Form.Item>
                            <Form.Item>
                                <Button type="default" htmlType="submit">Thêm Task</Button>
                            </Form.Item>
                        </Form>}
                    </Card>

                    {canAssign && <Card title="Phân công xử lý" bordered={false} style={{ marginBottom: 24, boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <Form form={assignForm} layout="inline" onFinish={handleAssign}>
                            <Form.Item name="assigneeUserId" rules={[{ required: true, message: 'Vui lòng chọn chuyên viên' }]}>
                                <Select placeholder="Chọn Analyst" style={{ width: 240 }} showSearch optionFilterProp="label">
                                    {assignees.map(assignee => (
                                        <Option key={assignee.id} value={assignee.id} label={`${assignee.username} ${assignee.fullName || ''}`}>
                                            {assignee.fullName ? `${assignee.fullName} (${assignee.username})` : assignee.username}
                                        </Option>
                                    ))}
                                </Select>
                            </Form.Item>
                            <Form.Item name="note">
                                <Input placeholder="Ghi chú phân công" style={{ width: 260 }} />
                            </Form.Item>
                            <Form.Item>
                                <Button type="primary" htmlType="submit">Phân công</Button>
                            </Form.Item>
                        </Form>
                    </Card>}

                    {(canManage || canTriage) && <Card title="Cập nhật Trạng thái" bordered={false} style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <Form form={statusForm} layout="vertical" onFinish={handleStatusChange}>
                            <Space align="start" size="large" wrap>
                                <Form.Item name="newStatus" label="Trạng thái mới" rules={[{ required: true, message: 'Vui lòng chọn trạng thái mới' }]}>
                                    <Select placeholder="Chọn trạng thái" style={{ width: 220 }}>
                                        {availableStatuses.map(status => (
                                            <Option key={status} value={status}>{STATUS_LABELS[status]}</Option>
                                        ))}
                                    </Select>
                                </Form.Item>
                                {selectedStatus === 'CLOSED' && (
                                    <Form.Item name="resolutionType" label="Loại kết luận" rules={[{ required: true, message: 'Vui lòng chọn loại kết luận' }]}>
                                        <Select style={{ width: 150 }}>
                                            <Option value="TRUE_POSITIVE">Sự cố thực sự</Option>
                                            <Option value="FALSE_POSITIVE">Báo động giả</Option>
                                            <Option value="BENIGN">Nhận diện nhầm</Option>
                                            <Option value="NOT_APPLICABLE">Không áp dụng</Option>
                                        </Select>
                                    </Form.Item>
                                )}
                                <Form.Item name="note" label="Ghi chú" rules={[{
                                    required: statusNoteRequired,
                                    message: 'Vui lòng ghi rõ lý do cho thay đổi trạng thái này',
                                }]}>
                                    <Input placeholder="Ghi chú về việc chuyển trạng thái" style={{ width: 300 }} />
                                </Form.Item>
                                <Form.Item label=" ">
                                    <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>Lưu trạng thái</Button>
                                </Form.Item>
                            </Space>
                        </Form>
                    </Card>}
                </div>

                <div style={{ flex: '1 1 35%', minWidth: 320 }}>
                    <Card title="Nhật ký xử lý (Timeline)" bordered={false}
                        extra={auditIntegrity && <Tag color={auditIntegrity.valid ? 'success' : 'error'}>
                            {auditIntegrity.valid ? `Toàn vẹn · ${auditIntegrity.checkedRecords} bản ghi` : 'Phát hiện sai lệch'}
                        </Tag>}
                        style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        {auditIntegrity && !auditIntegrity.valid && <Alert type="error" showIcon style={{ marginBottom: 16 }}
                            message={`Chuỗi audit không hợp lệ từ bản ghi #${auditIntegrity.firstInvalidLogId ?? 'không xác định'}`} />}
                        <Timeline>
                            {logs.map((log) => (
                                <Timeline.Item
                                    key={log.id}
                                    color={log.actionType === 'COMMENT' ? 'green' : 'blue'}
                                >
                                    <div style={{ marginBottom: 4 }}>
                                        <Text strong>{log.actor}</Text> <Text type="secondary" style={{ fontSize: 12 }}>({format(new Date(log.timestamp), 'HH:mm dd/MM')})</Text>
                                    </div>
                                    <div style={{ marginBottom: 4 }}>
                                        <Tag color={log.actionType === 'COMMENT' ? 'green' : 'geekblue'}>{log.actionType}</Tag>
                                        {log.oldValue && log.newValue && (
                                            <Text type="secondary">
                                                [{log.oldValue}] ➔ [{log.newValue}]
                                            </Text>
                                        )}
                                    </div>
                                    {log.note && (
                                        <div style={{ background: '#f0f2f5', padding: '6px 10px', borderRadius: 6, marginTop: 4 }}>
                                            {log.note}
                                        </div>
                                    )}
                                </Timeline.Item>
                            ))}
                        </Timeline>

                        <Divider />

                        {canManage && <Form form={commentForm} onFinish={handleAddComment} layout="vertical">
                            <Form.Item name="content" rules={[{ required: true, message: 'Vui lòng nhập nội dung' }]}>
                                <TextArea rows={3} placeholder="Nhập bình luận, trao đổi hoặc ghi chú kỹ thuật..." />
                            </Form.Item>
                            <Form.Item>
                                <Button type="default" htmlType="submit" icon={<SendOutlined />} style={{ width: '100%' }}>
                                    Gửi bình luận
                                </Button>
                            </Form.Item>
                        </Form>}
                    </Card>
                </div>
            </div>
        </div>
    );
};

const SlaProgress = ({ title, start, due, completed }: {
    title: string; start?: string; due?: string; completed?: string;
}) => {
    if (!start || !due) return <Text type="secondary">{title}: Chưa có dữ liệu</Text>;
    const startMs = new Date(start).getTime();
    const dueMs = new Date(due).getTime();
    const comparedMs = completed ? new Date(completed).getTime() : Date.now();
    const late = comparedMs > dueMs;
    const duration = Math.max(1, dueMs - startMs);
    const percent = completed ? 100 : Math.min(100, Math.max(0, ((comparedMs - startMs) / duration) * 100));
    const status = late ? 'exception' : completed ? 'success' : 'active';
    return <div>
        <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 6 }}>
            <Text strong>{title}</Text>
            <Tag color={late ? 'error' : completed ? 'success' : 'processing'}>
                {late ? 'Quá hạn' : completed ? 'Đúng hạn' : 'Đang theo dõi'}
            </Tag>
        </Space>
        <Progress percent={Math.round(percent)} status={status} />
        <Text type="secondary">Hạn: {format(new Date(due), 'HH:mm dd/MM/yyyy')}</Text>
    </div>;
};

export default IncidentDetailPage;
