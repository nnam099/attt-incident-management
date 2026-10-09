import React, { useCallback, useEffect, useState } from 'react';
import { Table, Tag, Button, Select, Typography, Row, Col, Switch, Space, message, Input } from 'antd';
import { EyeOutlined, FileExcelOutlined, FilePdfOutlined, PlusOutlined } from '@ant-design/icons';
import { format } from 'date-fns';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../services/api';
import type { IncidentResponse, PageResponse } from '../types';
import { useAuth } from '../context/auth';
import { getPrimaryRole, roleExperience } from '../config/roleExperience';

const { Title } = Typography;
const { Option } = Select;
const { Search } = Input;

interface AssigneeResponse { id: number; username: string; fullName?: string; }

const IncidentListPage: React.FC = () => {
    const [data, setData] = useState<IncidentResponse[]>([]);
    const [loading, setLoading] = useState(false);
    const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

    // Filters
    const [severityFilter, setSeverityFilter] = useState<string | undefined>(undefined);
    const [showOnlyOverdue, setShowOnlyOverdue] = useState<boolean>(false);
    const [assigneeFilter, setAssigneeFilter] = useState<number | undefined>();
    const [searchText, setSearchText] = useState('');
    const [assignees, setAssignees] = useState<AssigneeResponse[]>([]);

    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const statusFilter = searchParams.get('status') || undefined;
    const mineParam = searchParams.get('mine');
    const mineFilter = mineParam === 'ASSIGNED' || mineParam === 'REPORTED' ? mineParam : undefined;
    const { user } = useAuth();
    const role = getPrimaryRole(user?.roles);
    const canExport = role === 'ADMIN' || role === 'MANAGER';
    const canFilterAssignee = role === 'ADMIN' || role === 'MANAGER' || role === 'HELPDESK';
    const canFilterOverdue = role !== 'REPORTER';
    const canFilterSeverity = role !== 'REPORTER';
    const viewTitle = role === 'MANAGER' && statusFilter === 'RESOLVED' ? 'Chờ phê duyệt'
        : role === 'HELPDESK' && statusFilter === 'NEW' ? 'Chờ tiếp nhận'
            : role === 'HELPDESK' ? 'Toàn bộ sự cố'
            : role === 'ANALYST' && mineFilter !== 'ASSIGNED' ? 'Sự cố liên quan'
                : roleExperience[role].listTitle;
    const viewSubtitle = role === 'HELPDESK' && statusFilter !== 'NEW'
        ? 'Theo dõi tình trạng các ca sau khi tiếp nhận và phân công.'
        : roleExperience[role].listSubtitle;

    const changeStatusFilter = (status?: string) => {
        setSearchParams(previous => {
            const next = new URLSearchParams(previous);
            if (status) next.set('status', status); else next.delete('status');
            return next;
        });
        setPagination(previous => ({ ...previous, current: 1 }));
    };

    const fetchIncidents = useCallback(async (page = 1, size = 10, status?: string, severity?: string,
        assigneeId?: number, query = '', overdue = false, mine?: string) => {
        setLoading(true);
        try {
            const params = new URLSearchParams({
                page: (page - 1).toString(),
                size: size.toString(),
            });
            if (status) params.append('status', status);
            if (severity) params.append('severity', severity);
            if (assigneeId) params.append('assigneeId', assigneeId.toString());
            if (mine) params.append('mine', mine);
            if (query.trim()) params.append('q', query.trim());
            if (overdue) params.append('overdue', 'true');

            const res = await api.get<PageResponse<IncidentResponse>>(`/incidents?${params.toString()}`);
            setData(res.data.content);
            setPagination({
                current: res.data.number + 1,
                pageSize: res.data.size,
                total: res.data.totalElements,
            });
        } catch {
            message.error('Không thể tải danh sách sự cố');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (!canFilterAssignee) return;
        api.get<AssigneeResponse[]>('/users/assignees')
            .then(response => setAssignees(response.data))
            .catch(() => message.error('Không thể tải danh sách chuyên viên'));
    }, [canFilterAssignee]);

    const { current, pageSize } = pagination;

    useEffect(() => {
        fetchIncidents(current, pageSize, statusFilter, severityFilter, assigneeFilter, searchText, showOnlyOverdue, mineFilter);
    }, [current, pageSize, statusFilter, severityFilter, assigneeFilter, searchText, showOnlyOverdue, mineFilter, fetchIncidents]);

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
            window.URL.revokeObjectURL(url);
            message.success({ content: 'Trích xuất thành công!', key: 'export', duration: 2 });
        } catch {
            message.error({ content: 'Lỗi khi trích xuất dữ liệu', key: 'export', duration: 2 });
        }
    };

    const handleTableChange = (newPagination: any) => {
        setPagination(previous => ({
            ...previous,
            current: newPagination.current,
            pageSize: newPagination.pageSize,
        }));
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
            title: 'Risk score',
            dataIndex: 'riskScore',
            key: 'riskScore',
            render: (score: number, record: IncidentResponse) => <Tag color={getSeverityColor(record.riskLevel)}>{score}/100 · {record.riskLevel}</Tag>,
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
        {
            title: 'Ngày báo cáo',
            dataIndex: 'createdAt',
            key: 'createdAt',
            render: (val: string) => format(new Date(val), 'dd/MM/yyyy HH:mm'),
        },
    ];

    const visibleColumns: Record<typeof role, string[]> = {
        ADMIN: ['incidentCode', 'title', 'severity', 'riskScore', 'status', 'reportedByUsername', 'assignedToUsername', 'resolveDueAt', 'action'],
        MANAGER: ['incidentCode', 'title', 'severity', 'riskScore', 'status', 'assignedToUsername', 'resolveDueAt', 'action'],
        HELPDESK: ['incidentCode', 'title', 'severity', 'status', 'reportedByUsername', 'assignedToUsername', 'ackDueAt', 'action'],
        ANALYST: ['incidentCode', 'title', 'severity', 'riskScore', 'status', 'resolveDueAt', 'action'],
        REPORTER: ['incidentCode', 'title', 'severity', 'status', 'createdAt', 'action'],
    };

    return (
        <div>
            <div className="page-heading"><div><div className="page-eyebrow">{roleExperience[role].label.toUpperCase()} / SỰ CỐ</div><Title level={2} className="page-title">{viewTitle}</Title><div className="page-subtitle">{viewSubtitle}</div></div><div className="list-heading-actions"><span className="page-subtitle">{pagination.total} sự cố</span>{role === 'REPORTER' && <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/incidents/new')}>Khai báo sự cố</Button>}{role === 'ADMIN' && <Button onClick={() => navigate('/users')}>Quản lý người dùng</Button>}</div></div>

            <Row gutter={[10, 10]} className="list-toolbar">
                <Col>
                    <Search placeholder="Tìm mã, tiêu đề, hệ thống" allowClear style={{ width: 250 }}
                        onSearch={value => {
                            setSearchText(value);
                            setPagination(previous => ({ ...previous, current: 1 }));
                        }} />
                </Col>
                <Col>
                    <Select
                        placeholder="Lọc theo Trạng thái"
                        style={{ width: 200 }}
                        allowClear
                        value={statusFilter}
                        onChange={changeStatusFilter}
                    >
                        <Option value="NEW">Mới tạo (NEW)</Option>
                        <Option value="TRIAGE">Phân loại (TRIAGE)</Option>
                        <Option value="INVESTIGATING">Đang điều tra (INVESTIGATING)</Option>
                        <Option value="CONTAINED">Ngăn chặn (CONTAINED)</Option>
                        <Option value="RECOVERED">Khôi phục (RECOVERED)</Option>
                        <Option value="RESOLVED">Đã giải quyết (RESOLVED)</Option>
                        <Option value="CLOSED">Đã đóng (CLOSED)</Option>
                        <Option value="REOPENED">Tái mở (REOPENED)</Option>
                    </Select>
                </Col>
                {canFilterAssignee && <Col>
                    <Select placeholder="Lọc theo người xử lý" allowClear showSearch optionFilterProp="label"
                        style={{ width: 220 }} value={assigneeFilter}
                        onChange={value => {
                            setAssigneeFilter(value);
                            setPagination(previous => ({ ...previous, current: 1 }));
                        }}>
                        {assignees.map(assignee => <Option key={assignee.id} value={assignee.id}
                            label={`${assignee.username} ${assignee.fullName ?? ''}`}>
                            {assignee.fullName ? `${assignee.fullName} (${assignee.username})` : assignee.username}
                        </Option>)}
                    </Select>
                </Col>}
                {canFilterSeverity && <Col>
                    <Select
                        placeholder="Lọc theo Mức độ"
                        style={{ width: 200 }}
                        allowClear
                        onChange={(value) => {
                            setSeverityFilter(value);
                            setPagination(previous => ({ ...previous, current: 1 }));
                        }}
                    >
                        <Option value="CRITICAL">Nghiêm trọng (CRITICAL)</Option>
                        <Option value="HIGH">Cao (HIGH)</Option>
                        <Option value="MEDIUM">Trung bình (MEDIUM)</Option>
                        <Option value="LOW">Thấp (LOW)</Option>
                    </Select>
                </Col>}
                <Col>
                    <Space>
                        <Button type="primary" onClick={() => fetchIncidents(1, pagination.pageSize, statusFilter,
                            severityFilter, assigneeFilter, searchText, showOnlyOverdue, mineFilter)}>
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
                {canFilterOverdue && <Col style={{ display: 'flex', alignItems: 'center' }}>
                    <Space>
                        <Switch checked={showOnlyOverdue} onChange={checked => {
                            setShowOnlyOverdue(checked);
                            setPagination(previous => ({ ...previous, current: 1 }));
                        }} />
                        <span style={{ color: showOnlyOverdue ? 'red' : 'inherit', fontWeight: showOnlyOverdue ? 'bold' : 'normal' }}>
                            Chỉ hiện ca Trễ SLA
                        </span>
                    </Space>
                </Col>}
            </Row>

            <Table
                columns={columns.filter(column => visibleColumns[role].includes(column.key))
                    .sort((left, right) => visibleColumns[role].indexOf(left.key) - visibleColumns[role].indexOf(right.key))}
                dataSource={data}
                rowKey="id"
                loading={loading}
                scroll={{ x: role === 'REPORTER' ? 760 : 1100 }}
                locale={{ emptyText: role === 'REPORTER' ? 'Bạn chưa khai báo sự cố nào' : role === 'ANALYST' ? 'Chưa có ca được giao trong chế độ xem này' : 'Chưa có sự cố trong chế độ xem này' }}
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
