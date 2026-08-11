import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Descriptions, Tag, Button, Space, Timeline, Typography, Select, message, Form, Input, Divider, Upload, List } from 'antd';
import { ArrowLeftOutlined, SaveOutlined, SendOutlined, DownloadOutlined, UploadOutlined, FileOutlined } from '@ant-design/icons';
import { format } from 'date-fns';
import api from '../services/api';
import type { IncidentResponse } from '../types';

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

const IncidentDetailPage: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    
    const [incident, setIncident] = useState<IncidentResponse | null>(null);
    const [logs, setLogs] = useState<LogResponse[]>([]);
    const [attachments, setAttachments] = useState<AttachmentResponse[]>([]);
    const [loading, setLoading] = useState(true);
    
    const [statusForm] = Form.useForm();
    const [commentForm] = Form.useForm();

    const fetchData = async () => {
        setLoading(true);
        try {
            const [incRes, logsRes, attRes] = await Promise.all([
                api.get<IncidentResponse>(`/incidents/${id}`),
                api.get<LogResponse[]>(`/incidents/${id}/logs`),
                api.get<AttachmentResponse[]>(`/incidents/${id}/attachments`)
            ]);
            setIncident(incRes.data);
            setLogs(logsRes.data);
            setAttachments(attRes.data);
            statusForm.setFieldsValue({ newStatus: incRes.data.status });
        } catch (error) {
            message.error('Lỗi khi tải chi tiết sự cố.');
            navigate('/incidents');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (id) {
            fetchData();
        }
    }, [id]);

    const handleStatusChange = async (values: any) => {
        try {
            await api.put(`/incidents/${id}/status`, {
                newStatus: values.newStatus,
                note: values.note
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
            case 'IN_PROGRESS': return 'blue';
            case 'RESOLVED': return 'green';
            case 'CLOSED': return 'default';
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

    const handleFileUpload = async (options: any) => {
        const { onSuccess, onError, file } = options;
        const formData = new FormData();
        formData.append('file', file);
        
        try {
            await api.post(`/incidents/${id}/attachments`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            });
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
        } catch (error) {
            message.error('Tải file thất bại');
        }
    };

    if (loading || !incident) {
        return <p>Đang tải dữ liệu...</p>;
    }

    return (
        <div style={{ paddingBottom: 24 }}>
            <Space style={{ marginBottom: 16 }}>
                <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/incidents')}>
                    Quay lại
                </Button>
                <Title level={3} style={{ margin: 0 }}>Chi tiết: {incident.incidentCode}</Title>
            </Space>

            <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
                <div style={{ flex: '1 1 60%', minWidth: 400 }}>
                    <Card title="Thông tin Sự cố" bordered={false} style={{ marginBottom: 24, boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <Descriptions column={2} bordered size="small">
                            <Descriptions.Item label="Tiêu đề" span={2}><strong>{incident.title}</strong></Descriptions.Item>
                            <Descriptions.Item label="Trạng thái">
                                <Tag color={getStatusColor(incident.status)}>{incident.status}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="Mức độ">
                                <Tag color={getSeverityColor(incident.severity)}>{incident.severity}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="Danh mục">{incident.categoryName || 'N/A'}</Descriptions.Item>
                            <Descriptions.Item label="Hệ thống ảnh hưởng">{incident.affectedSystem || 'N/A'}</Descriptions.Item>
                            <Descriptions.Item label="Người báo cáo">{incident.reportedByUsername}</Descriptions.Item>
                            <Descriptions.Item label="Người xử lý">{incident.assignedToUsername || 'Chưa có'}</Descriptions.Item>
                            <Descriptions.Item label="SLA">
                                {incident.slaDueAt ? format(new Date(incident.slaDueAt), 'HH:mm dd/MM/yyyy') : 'N/A'}
                            </Descriptions.Item>
                            <Descriptions.Item label="Tạo lúc">
                                {format(new Date(incident.createdAt), 'HH:mm dd/MM/yyyy')}
                            </Descriptions.Item>
                            <Descriptions.Item label="Mô tả chi tiết" span={2}>
                                <div style={{ whiteSpace: 'pre-wrap', background: '#f9f9f9', padding: 12, borderRadius: 4 }}>
                                    {incident.description}
                                </div>
                            </Descriptions.Item>
                        </Descriptions>
                        
                        <Divider>Tài liệu đính kèm minh chứng</Divider>
                        <List
                            size="small"
                            bordered
                            dataSource={attachments}
                            renderItem={(item) => (
                                <List.Item
                                    actions={[
                                        <Button 
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

                    <Card title="Cập nhật Trạng thái" bordered={false} style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <Form form={statusForm} layout="vertical" onFinish={handleStatusChange}>
                            <Space align="start" size="large">
                                <Form.Item name="newStatus" label="Trạng thái mới">
                                    <Select style={{ width: 180 }}>
                                        <Option value="NEW">Mới tạo (NEW)</Option>
                                        <Option value="IN_PROGRESS">Đang xử lý (IN_PROGRESS)</Option>
                                        <Option value="RESOLVED">Đã giải quyết (RESOLVED)</Option>
                                        <Option value="CLOSED">Đã đóng (CLOSED)</Option>
                                    </Select>
                                </Form.Item>
                                <Form.Item name="note" label="Ghi chú (bắt buộc đối với thay đổi lớn)">
                                    <Input placeholder="Ghi chú về việc chuyển trạng thái" style={{ width: 300 }} />
                                </Form.Item>
                                <Form.Item label=" ">
                                    <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>Lưu trạng thái</Button>
                                </Form.Item>
                            </Space>
                        </Form>
                    </Card>
                </div>

                <div style={{ flex: '1 1 35%', minWidth: 350 }}>
                    <Card title="Nhật ký xử lý (Timeline)" bordered={false} style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
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
                        
                        <Form form={commentForm} onFinish={handleAddComment} layout="vertical">
                            <Form.Item name="content" rules={[{ required: true, message: 'Vui lòng nhập nội dung' }]}>
                                <TextArea rows={3} placeholder="Nhập bình luận, trao đổi hoặc ghi chú kỹ thuật..." />
                            </Form.Item>
                            <Form.Item>
                                <Button type="default" htmlType="submit" icon={<SendOutlined />} style={{ width: '100%' }}>
                                    Gửi bình luận
                                </Button>
                            </Form.Item>
                        </Form>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default IncidentDetailPage;
