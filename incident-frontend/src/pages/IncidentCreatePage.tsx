import React, { useEffect, useState } from 'react';
import { Form, Input, Button, Select, Card, Typography, message, Space, DatePicker } from 'antd';
import { SaveOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

const { Title } = Typography;
const { Option } = Select;
const { TextArea } = Input;

interface CategoryResponse {
    id: number;
    name: string;
    description: string;
}

const IncidentCreatePage: React.FC = () => {
    const [form] = Form.useForm();
    const [loading, setLoading] = useState(false);
    const [categories, setCategories] = useState<CategoryResponse[]>([]);
    const navigate = useNavigate();

    useEffect(() => {
        const fetchCategories = async () => {
            try {
                const res = await api.get<CategoryResponse[]>('/categories');
                setCategories(res.data);
            } catch (error) {
                message.error('Không thể tải danh sách loại sự cố.');
            }
        };
        fetchCategories();
    }, []);

    const onFinish = async (values: any) => {
        setLoading(true);
        try {
            const payload = {
                ...values,
                detectedAt: values.detectedAt ? values.detectedAt.toISOString() : undefined,
            };
            await api.post('/incidents', payload);
            message.success('Khai báo sự cố thành công!');
            navigate('/incidents');
        } catch (error: any) {
            if (error.response?.data?.message) {
                message.error(error.response.data.message);
            } else {
                message.error('Có lỗi xảy ra khi lưu sự cố.');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div>
            <Space style={{ marginBottom: 16 }}>
                <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/incidents')}>
                    Quay lại
                </Button>
                <Title level={3} style={{ margin: 0 }}>Khai báo Sự cố Mới</Title>
            </Space>

            <Card style={{ maxWidth: 800 }}>
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={onFinish}
                    initialValues={{ severity: 'MEDIUM' }}
                >
                    <Form.Item
                        name="title"
                        label="Tiêu đề sự cố"
                        rules={[{ required: true, message: 'Vui lòng nhập tiêu đề sự cố!' }]}
                    >
                        <Input placeholder="Ví dụ: Mất kết nối mạng tại tầng 3" size="large" />
                    </Form.Item>

                    <Form.Item
                        name="categoryId"
                        label="Loại sự cố"
                        rules={[{ required: true, message: 'Vui lòng chọn loại sự cố!' }]}
                    >
                        <Select placeholder="Chọn danh mục" size="large">
                            {categories.map((cat) => (
                                <Option key={cat.id} value={cat.id}>{cat.name}</Option>
                            ))}
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="severity"
                        label="Mức độ ảnh hưởng (Mặc định: Trung bình)"
                    >
                        <Select size="large">
                            <Option value="LOW">Thấp (LOW)</Option>
                            <Option value="MEDIUM">Trung bình (MEDIUM)</Option>
                            <Option value="HIGH">Cao (HIGH)</Option>
                            <Option value="CRITICAL">Nghiêm trọng (CRITICAL)</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="affectedSystem"
                        label="Hệ thống/Thiết bị ảnh hưởng"
                    >
                        <Input placeholder="Ví dụ: Switch Core, Website Bán hàng..." size="large" />
                    </Form.Item>

                    <Form.Item
                        name="detectedAt"
                        label="Thời gian phát hiện (Tùy chọn)"
                        tooltip="Nếu không chọn, hệ thống sẽ tự động lấy thời gian hiện tại làm gốc để tính SLA."
                    >
                        <DatePicker showTime format="YYYY-MM-DD HH:mm:ss" size="large" style={{ width: '100%' }} />
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label="Mô tả chi tiết"
                        rules={[{ required: true, message: 'Vui lòng nhập mô tả chi tiết!' }]}
                        tooltip="Ghi rõ hiện tượng, thời điểm phát hiện và các bước đã thử."
                    >
                        <TextArea rows={5} placeholder="Nhập mô tả chi tiết về sự cố..." size="large" />
                    </Form.Item>

                    <Form.Item>
                        <Button 
                            type="primary" 
                            htmlType="submit" 
                            icon={<SaveOutlined />} 
                            size="large" 
                            loading={loading}
                        >
                            Tạo sự cố
                        </Button>
                    </Form.Item>
                </Form>
            </Card>
        </div>
    );
};

export default IncidentCreatePage;
