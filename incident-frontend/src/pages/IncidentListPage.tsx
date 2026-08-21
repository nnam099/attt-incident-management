import React, { useEffect, useState } from 'react';
import { Table, Tag, Button, Select, Typography, Row, Col, Switch, Space, message } from 'antd';
import { EyeOutlined, FileExcelOutlined, FilePdfOutlined, DownloadOutlined } from '@ant-design/icons';
import { format } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import type { IncidentResponse, PageResponse } from '../types';
import { useAuth } from '../context/AuthContext';

const { Title } = Typography;
const { Option } = Select;

const IncidentListPage: React.FC = () => {
    const [data, setData] = useState<IncidentResponse[]>([]);
    const [loading, setLoading] = useState(false);
    const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
    
    // Filters
    const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
    const [severityFilter, setSeverityFilter] = useState<string | undefined>(undefined);
    const [showOnlyOverdue, setShowOnlyOverdue] = useState<boolean>(false);

    const filteredData = React.useMemo(() => {
        if (!showOnlyOverdue) return data;
        return data.filter(record => {
            const isAckOverdue = !record.acknowledgedAt && record.ackDueAt && new Date(record.ackDueAt) < new Date();
            const isResolveOverdue = record.status !== 'RESOLVED' && record.status !== 'CLOSED' && record.resolveDueAt && new Date(record.resolveDueAt) < new Date();
            return isAckOverdue || isResolveOverdue;
        });
    }, [data, showOnlyOverdue]);

    const navigate = useNavigate();
    const { user } = useAuth();
    const canExport = user?.roles.some(role => role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER');

    const fetchIncidents = async (page = 1, size = 10, status?: string, severity?: string) => {
        setLoading(true);
        try {
            const params = new URLSearchParams({
                page: (page - 1).toString(),
                size: size.toString(),
            });
            if (status) params.append('status', status);
            if (severity) params.append('severity', severity);

            const res = await api.get<PageResponse<IncidentResponse>>(`/incidents?${params.toString()}`);
            setData(res.data.content);
            setPagination({
                current: res.data.number + 1,
                pageSize: res.data.size,
                total: res.data.totalElements,
            });
        } catch (error) {
            console.error('Failed to fetch incidents', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchIncidents(pagination.current, pagination.pageSize, statusFilter, severityFilter);
    }, [pagination.current, pagination.pageSize, statusFilter, severityFilter]);

    const handleExport = async (type: 'excel' | 'pdf') => {
        try {
            message.loading({ content: 'Đang trích xuất dữ liệu...', key: 'export' });
            const res = await api.get(`/reports/export/${type}`, { responseType: 'blob' });
            const url = window.URL.createObjectURL(new Blob([res.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `incidents_report.${type === 'excel' ? 'xlsx' : 'pdf'}`);
            document.body.appendChild(link);
            link.click();
            link.parentNode?.removeChild(link);
            message.success({ content: 'Trích xuất thành công!', key: 'export', duration: 2 });
        } catch (error) {
            message.error({ content: 'Lỗi khi trích xuất dữ liệu', key: 'export', duration: 2 });
        }
    };

    const handleTableChange = (newPagination: any) => {
        setPagination({
            ...pagination,
            current: newPagination.current,
            pageSize: newPagination.pageSize,
        });
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

    const columns = [
        {
            title: 'Mã',
            dataIndex: 'incidentCode',
            key: 'incidentCode',
            render: (text: string) => <strong>{text}</strong>,
        },
        {
            title: 'Tiêu đề',
            dataIndex: 'title',
            key: 'title',
            ellipsis: true,
        },
        {
            title: 'Mức độ',
            dataIndex: 'severity',
            key: 'severity',
            render: (severity: string) => (
                <Tag color={getSeverityColor(severity)}>
                    {severity}
                </Tag>
            ),
        },
        {
            title: 'Trạng thái',
            dataIndex: 'status',
            key: 'status',
            render: (status: string) => (
                <Tag color={getStatusColor(status)}>
                    {status}
                </Tag>
            ),
        },
        {
            title: 'Người báo cáo',
            dataIndex: 'reportedByUsername',
            key: 'reportedByUsername',
        },
        {
            title: 'Người xử lý',
            dataIndex: 'assignedToUsername',
            key: 'assignedToUsername',
            render: (val: string | null) => val || <span style={{ color: '#ccc' }}>Chưa phân công</span>
        },
        {
            title: 'Hạn tiếp nhận (MTTA)',
            dataIndex: 'ackDueAt',
            key: 'ackDueAt',
            render: (val: string, record: IncidentResponse) => {
                if (!val) return '';
                const isOverdue = !record.acknowledgedAt && new Date(val) < new Date();
                return (
                    <span style={{ color: isOverdue ? 'red' : 'inherit', fontWeight: isOverdue ? 'bold' : 'normal' }}>
                        {format(new Date(val), 'HH:mm - dd/MM')}
                        {isOverdue && ' (Trễ)'}
                    </span>
                );
            },
        },
        {
            title: 'Hạn xử lý (MTTR)',
            dataIndex: 'resolveDueAt',
            key: 'resolveDueAt',
            render: (val: string, record: IncidentResponse) => {
                if (!val) return '';
                const isOverdue = record.status !== 'RESOLVED' && record.status !== 'CLOSED' && new Date(val) < new Date();
                return (
                    <span style={{ color: isOverdue ? 'red' : 'inherit', fontWeight: isOverdue ? 'bold' : 'normal' }}>
                        {format(new Date(val), 'HH:mm - dd/MM')}
                        {isOverdue && ' (Trễ)'}
                    </span>
                );
            },
        },
        {
            title: 'Hành động',
            key: 'action',
            render: (_: any, record: IncidentResponse) => (
                <Button 
                    type="primary" 
                    icon={<EyeOutlined />} 
                    onClick={() => navigate(`/incidents/${record.id}`)}
                    size="small"
                >
                    Xem
                </Button>
            ),
        },
    ];

    return (
        <div>
            <Title level={3}>Danh sách Sự cố</Title>
            
            <Row gutter={16} style={{ marginBottom: 16 }}>
                <Col>
                    <Select 
                        placeholder="Lọc theo Trạng thái" 
                        style={{ width: 200 }} 
                        allowClear 
                        onChange={setStatusFilter}
                    >
                        <Option value="NEW">Mới tạo (NEW)</Option>
                        <Option value="TRIAGE">Phân loại (TRIAGE)</Option>
                        <Option value="INVESTIGATING">Đang điều tra (INVESTIGATING)</Option>
                        <Option value="CONTAINED">Ngăn chặn (CONTAINED)</Option>
                        <Option value="RECOVERED">Khôi phục (RECOVERED)</Option>
                        <Option value="RESOLVED">Đã giải quyết (RESOLVED)</Option>
                        <Option value="CLOSED">Đã đóng (CLOSED)</Option>
                    </Select>
                </Col>
                <Col>
                    <Select 
                        placeholder="Lọc theo Mức độ" 
                        style={{ width: 200 }} 
                        allowClear 
                        onChange={setSeverityFilter}
                    >
                        <Option value="CRITICAL">Nghiêm trọng (CRITICAL)</Option>
                        <Option value="HIGH">Cao (HIGH)</Option>
                        <Option value="MEDIUM">Trung bình (MEDIUM)</Option>
                        <Option value="LOW">Thấp (LOW)</Option>
                    </Select>
                </Col>
                <Col>
                    <Space>
                        <Button type="primary" onClick={() => fetchIncidents(1, pagination.pageSize, statusFilter, severityFilter)}>
                            Làm mới
                        </Button>
                        {canExport && <Button style={{ background: '#107c41', color: 'white' }} icon={<FileExcelOutlined />} onClick={() => handleExport('excel')}>
                            Xuất Excel
                        </Button>}
                        {canExport && <Button danger icon={<FilePdfOutlined />} onClick={() => handleExport('pdf')}>
                            Xuất PDF
                        </Button>}
                    </Space>
                </Col>
                <Col style={{ display: 'flex', alignItems: 'center' }}>
                    <Space>
                        <Switch checked={showOnlyOverdue} onChange={setShowOnlyOverdue} />
                        <span style={{ color: showOnlyOverdue ? 'red' : 'inherit', fontWeight: showOnlyOverdue ? 'bold' : 'normal' }}>
                            Chỉ hiện ca Trễ SLA
                        </span>
                    </Space>
                </Col>
            </Row>

            <Table 
                columns={columns} 
                dataSource={filteredData} 
                rowKey="id" 
                loading={loading}
                pagination={{
                    current: pagination.current,
                    pageSize: pagination.pageSize,
                    total: pagination.total,
                    showSizeChanger: true,
                }}
                onChange={handleTableChange}
            />
        </div>
    );
};

export default IncidentListPage;
