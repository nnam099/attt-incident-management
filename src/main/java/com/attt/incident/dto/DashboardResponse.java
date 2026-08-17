package com.attt.incident.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class DashboardResponse {
    private long totalOpenIncidents;
    private Map<String, Long> incidentsBySeverity;
    private Map<String, Long> incidentsByStatus;
    private Map<String, Long> incidentsByCategory;
    
    // Thống kê thời gian xử lý trung bình theo mức độ (đơn vị: giờ)
    private Map<String, Double> averageResolutionTimeHoursBySeverity;
    
    // Thống kê số lượng sự cố theo ngày (cho biểu đồ đường)
    private Map<String, Long> incidentsByDate;

    // Tỷ lệ tuân thủ SLA (Phần trăm sự cố giải quyết đúng hạn)
    private double slaComplianceRate;

    // Thống kê loại kết luận (TRUE_POSITIVE, FALSE_POSITIVE...)
    private Map<String, Long> resolutionTypeBreakdown;
}
