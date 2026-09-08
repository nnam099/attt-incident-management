import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic, message, Typography } from 'antd';
import { AlertOutlined, CheckCircleOutlined, InfoCircleOutlined } from '@ant-design/icons';
import {
  PieChart, Pie, Cell, Tooltip, Legend, XAxis, YAxis, CartesianGrid, ResponsiveContainer, LineChart, Line
} from 'recharts';
import api, { getAccessToken } from '../services/api';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const { Title } = Typography;

interface DashboardData {
    totalOpenIncidents: number;
    incidentsBySeverity: Record<string, number>;
    incidentsByStatus: Record<string, number>;
    incidentsByCategory: Record<string, number>;
    averageResolutionTimeHoursBySeverity: Record<string, number>;
    incidentsByDate: Record<string, number>;
    slaComplianceRate: number;
    resolutionTypeBreakdown: Record<string, number>;
}

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884d8'];

const DashboardPage: React.FC = () => {
    const [data, setData] = useState<DashboardData | null>(null);
    const [loading, setLoading] = useState(true);

    const fetchDashboard = async () => {
        try {
            const res = await api.get<DashboardData>('/reports/dashboard');
            setData(res.data);
        } catch (error) {
            message.error('Không thể tải dữ liệu thống kê.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchDashboard();

        // Cấu hình WebSocket / STOMP để cập nhật realtime
        const token = getAccessToken();
        const client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
            connectHeaders: {
                Authorization: `Bearer ${token}`
            },
            onConnect: () => {
                console.log('Connected to WebSocket');
                client.subscribe('/topic/incidents', (msg) => {
                    // Khi có bản tin đẩy về, reload lại biểu đồ
                    console.log('Có thay đổi sự cố, cập nhật dashboard...', msg.body);
                    fetchDashboard();
                });
            },
            onStompError: (frame) => {
                console.error('Broker reported error: ' + frame.headers['message']);
            }
        });
        
        client.activate();

        return () => {
            client.deactivate();
        };
    }, []);

    if (loading || !data) {
        return <p>Đang tải dữ liệu dashboard...</p>;
    }

    // Transform data for charts
    const severityData = Object.keys(data.incidentsBySeverity).map(key => ({
        name: key, value: data.incidentsBySeverity[key]
    }));



    const dateData = Object.keys(data.incidentsByDate).map(key => ({
        date: key, count: data.incidentsByDate[key]
    }));

    const resolutionData = data.resolutionTypeBreakdown ? Object.keys(data.resolutionTypeBreakdown).map(key => ({
        name: key, value: data.resolutionTypeBreakdown[key]
    })) : [];

    return (
        <div>
            <Title level={3} style={{ marginBottom: 24 }}>Tổng quan Hệ thống</Title>
            <Row gutter={[24, 24]}>
                <Col span={6}>
                    <Card style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <Statistic
                            title="Sự cố Đang mở"
                            value={data.totalOpenIncidents}
                            valueStyle={{ color: '#cf1322' }}
                            prefix={<AlertOutlined />}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <Statistic
                            title="Mới / Đang xử lý"
                            value={(data.incidentsByStatus['NEW'] || 0) + (data.incidentsByStatus['TRIAGE'] || 0) + (data.incidentsByStatus['INVESTIGATING'] || 0)}
                            valueStyle={{ color: '#1677ff' }}
                            prefix={<InfoCircleOutlined />}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <Statistic
                            title="Đã giải quyết/Đóng"
                            value={(data.incidentsByStatus['RESOLVED'] || 0) + (data.incidentsByStatus['CLOSED'] || 0)}
                            valueStyle={{ color: '#3f8600' }}
                            prefix={<CheckCircleOutlined />}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)', border: data.slaComplianceRate < 80 ? '1px solid #cf1322' : '1px solid #3f8600' }}>
                        <Statistic
                            title="Tuân thủ SLA"
                            value={data.slaComplianceRate}
                            precision={2}
                            suffix="%"
                            valueStyle={{ color: data.slaComplianceRate < 80 ? '#cf1322' : '#3f8600' }}
                        />
                    </Card>
                </Col>
            </Row>

            <Row gutter={[24, 24]}>
                <Col span={12}>
                    <Card title="Phân bổ Sự cố theo Mức độ" bordered={false} style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <div style={{ width: '100%', height: 300 }}>
                            <ResponsiveContainer>
                                <PieChart>
                                    <Pie
                                        data={severityData}
                                        cx="50%"
                                        cy="50%"
                                        labelLine={false}
                                        label={({ name, percent }) => `${name} ${((percent || 0) * 100).toFixed(0)}%`}
                                        outerRadius={100}
                                        fill="#8884d8"
                                        dataKey="value"
                                    >
                                        {severityData.map((_, index) => (
                                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                        ))}
                                    </Pie>
                                    <Tooltip />
                                    <Legend />
                                </PieChart>
                            </ResponsiveContainer>
                        </div>
                    </Card>
                </Col>

                <Col span={12}>
                    <Card title="Chất lượng Cảnh báo (Resolution Breakdown)" bordered={false} style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <div style={{ width: '100%', height: 300 }}>
                            <ResponsiveContainer>
                                <PieChart>
                                    <Pie
                                        data={resolutionData}
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={60}
                                        outerRadius={100}
                                        fill="#8884d8"
                                        dataKey="value"
                                        label={({ name, percent }) => `${name} ${((percent || 0) * 100).toFixed(0)}%`}
                                    >
                                        {resolutionData.map((_, index) => (
                                            <Cell key={`cell-${index}`} fill={['#d4380d', '#3f8600', '#1677ff', '#8c8c8c'][index % 4]} />
                                        ))}
                                    </Pie>
                                    <Tooltip />
                                    <Legend />
                                </PieChart>
                            </ResponsiveContainer>
                        </div>
                    </Card>
                </Col>

                <Col span={24}>
                    <Card title="Xu hướng Sự cố Mới (7 ngày gần đây)" bordered={false} style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                        <div style={{ width: '100%', height: 300 }}>
                            <ResponsiveContainer>
                                <LineChart data={dateData} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" />
                                    <XAxis dataKey="date" />
                                    <YAxis />
                                    <Tooltip />
                                    <Legend />
                                    <Line type="monotone" dataKey="count" stroke="#8884d8" activeDot={{ r: 8 }} />
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default DashboardPage;
