import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, Col, Row, Segmented, Statistic, message, Typography } from 'antd';
import { AlertOutlined, CheckCircleOutlined, ClockCircleOutlined, InfoCircleOutlined } from '@ant-design/icons';
import {
    Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart,
    ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import api, { BACKEND_BASE_URL, getAccessToken } from '../services/api';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const { Title } = Typography;
type TimeFrame = 'week' | 'month' | 'year';

interface DashboardData {
    totalOpenIncidents: number;
    incidentsBySeverity: Record<string, number>;
    incidentsByStatus: Record<string, number>;
    incidentsByCategory: Record<string, number>;
    averageResolutionTimeHoursBySeverity: Record<string, number>;
    incidentsByDate: Record<string, number>;
    slaComplianceRate: number;
    resolutionTypeBreakdown: Record<string, number>;
    ackOnTimeCount: number;
    ackOverdueCount: number;
    resolveOnTimeCount: number;
    resolveOverdueCount: number;
    slaBySeverity: Record<string, Record<string, number>>;
}

const COLORS = ['#1677ff', '#52c41a', '#faad14', '#f5222d', '#722ed1', '#13c2c2', '#eb2f96', '#8c8c8c'];
const toSeries = (values: Record<string, number> | undefined) =>
    Object.entries(values ?? {}).map(([name, value]) => ({ name, value }));

const DashboardPage: React.FC = () => {
    const [data, setData] = useState<DashboardData | null>(null);
    const [loading, setLoading] = useState(true);
    const [timeFrame, setTimeFrame] = useState<TimeFrame>('month');

    const fetchDashboard = useCallback(async () => {
        setLoading(true);
        try {
            const response = await api.get<DashboardData>('/reports/dashboard', { params: { timeFrame } });
            setData(response.data);
        } catch {
            message.error('Không thể tải dữ liệu thống kê.');
        } finally {
            setLoading(false);
        }
    }, [timeFrame]);

    useEffect(() => {
        fetchDashboard();
        const client = new Client({
            webSocketFactory: () => new SockJS(`${BACKEND_BASE_URL}/ws`),
            beforeConnect: () => {
                const token = getAccessToken();
                client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
            },
            onConnect: () => client.subscribe('/topic/incidents', fetchDashboard),
            reconnectDelay: 5000,
        });
        client.activate();
        return () => { void client.deactivate(); };
    }, [fetchDashboard]);

    const severityData = useMemo(() => toSeries(data?.incidentsBySeverity), [data]);
    const statusData = useMemo(() => toSeries(data?.incidentsByStatus), [data]);
    const categoryData = useMemo(() => toSeries(data?.incidentsByCategory), [data]);
    const resolutionData = useMemo(() => toSeries(data?.resolutionTypeBreakdown), [data]);
    const mttrData = useMemo(() => toSeries(data?.averageResolutionTimeHoursBySeverity), [data]);
    const dateData = useMemo(() => Object.entries(data?.incidentsByDate ?? {})
        .map(([date, count]) => ({ date, count })), [data]);
    const slaData = data ? [
        { name: 'Tiếp nhận', onTime: data.ackOnTimeCount, overdue: data.ackOverdueCount },
        { name: 'Xử lý', onTime: data.resolveOnTimeCount, overdue: data.resolveOverdueCount },
    ] : [];

    return (
        <div>
            <Row justify="space-between" align="middle" style={{ marginBottom: 24 }} gutter={[16, 16]}>
                <Col><Title level={3} style={{ margin: 0 }}>Tổng quan Hệ thống</Title></Col>
                <Col>
                    <Segmented
                        value={timeFrame}
                        onChange={value => setTimeFrame(value as TimeFrame)}
                        options={[
                            { label: '7 ngày', value: 'week' },
                            { label: '30 ngày', value: 'month' },
                            { label: '1 năm', value: 'year' },
                        ]}
                    />
                </Col>
            </Row>

            <Row gutter={[16, 16]}>
                <Col xs={24} sm={12} xl={6}><Card loading={loading}><Statistic title="Sự cố đang mở" value={data?.totalOpenIncidents ?? 0} valueStyle={{ color: '#cf1322' }} prefix={<AlertOutlined />} /></Card></Col>
                <Col xs={24} sm={12} xl={6}><Card loading={loading}><Statistic title="Mới / đang xử lý" value={(data?.incidentsByStatus.NEW ?? 0) + (data?.incidentsByStatus.TRIAGE ?? 0) + (data?.incidentsByStatus.INVESTIGATING ?? 0)} valueStyle={{ color: '#1677ff' }} prefix={<InfoCircleOutlined />} /></Card></Col>
                <Col xs={24} sm={12} xl={6}><Card loading={loading}><Statistic title="Đã giải quyết / đóng" value={(data?.incidentsByStatus.RESOLVED ?? 0) + (data?.incidentsByStatus.CLOSED ?? 0)} valueStyle={{ color: '#3f8600' }} prefix={<CheckCircleOutlined />} /></Card></Col>
                <Col xs={24} sm={12} xl={6}><Card loading={loading}><Statistic title="Tuân thủ SLA xử lý" value={data?.slaComplianceRate ?? 0} precision={2} suffix="%" prefix={<ClockCircleOutlined />} valueStyle={{ color: (data?.slaComplianceRate ?? 0) < 80 ? '#cf1322' : '#3f8600' }} /></Card></Col>
            </Row>

            <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
                <Col xs={24} xl={12}><ChartCard title="Phân bổ theo mức độ" loading={loading}><PieData data={severityData} /></ChartCard></Col>
                <Col xs={24} xl={12}><ChartCard title="Phân bổ theo trạng thái" loading={loading}><BarData data={statusData} color="#1677ff" /></ChartCard></Col>
                <Col xs={24} xl={12}><ChartCard title="Phân bổ theo danh mục" loading={loading}><BarData data={categoryData} color="#13c2c2" /></ChartCard></Col>
                <Col xs={24} xl={12}><ChartCard title="Kết luận sự cố" loading={loading}><PieData data={resolutionData} donut /></ChartCard></Col>
                <Col xs={24} xl={12}><ChartCard title="MTTR trung bình theo mức độ (giờ)" loading={loading}><BarData data={mttrData} color="#722ed1" /></ChartCard></Col>
                <Col xs={24} xl={12}>
                    <ChartCard title="Tình trạng SLA tiếp nhận / xử lý" loading={loading}>
                        <ResponsiveContainer width="100%" height="100%"><BarChart data={slaData}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="name" /><YAxis allowDecimals={false} /><Tooltip /><Legend /><Bar dataKey="onTime" name="Đúng hạn" stackId="sla" fill="#52c41a" /><Bar dataKey="overdue" name="Quá hạn" stackId="sla" fill="#f5222d" /></BarChart></ResponsiveContainer>
                    </ChartCard>
                </Col>
                <Col span={24}>
                    <ChartCard title="Xu hướng sự cố mới" loading={loading}>
                        <ResponsiveContainer width="100%" height="100%"><LineChart data={dateData}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="date" minTickGap={24} /><YAxis allowDecimals={false} /><Tooltip /><Legend /><Line type="monotone" dataKey="count" name="Sự cố" stroke="#8b5cf6" strokeWidth={2} activeDot={{ r: 6 }} /></LineChart></ResponsiveContainer>
                    </ChartCard>
                </Col>
            </Row>
        </div>
    );
};

const ChartCard = ({ title, loading, children }: { title: string; loading: boolean; children: React.ReactNode }) => (
    <Card title={title} loading={loading}><div style={{ width: '100%', height: 300 }}>{children}</div></Card>
);

const PieData = ({ data, donut = false }: { data: Array<{ name: string; value: number }>; donut?: boolean }) => (
    <ResponsiveContainer width="100%" height="100%"><PieChart><Pie data={data} dataKey="value" nameKey="name" innerRadius={donut ? 58 : 0} outerRadius={100} label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`}>{data.map((item, index) => <Cell key={item.name} fill={COLORS[index % COLORS.length]} />)}</Pie><Tooltip /><Legend /></PieChart></ResponsiveContainer>
);

const BarData = ({ data, color }: { data: Array<{ name: string; value: number }>; color: string }) => (
    <ResponsiveContainer width="100%" height="100%"><BarChart data={data} margin={{ bottom: 20 }}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="name" interval={0} angle={-12} textAnchor="end" height={65} /><YAxis allowDecimals={false} /><Tooltip /><Bar dataKey="value" name="Số lượng" fill={color} /></BarChart></ResponsiveContainer>
);

export default DashboardPage;
