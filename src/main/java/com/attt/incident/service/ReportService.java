package com.attt.incident.service;

import com.attt.incident.dto.DashboardResponse;
import com.attt.incident.entity.Incident;
import com.attt.incident.entity.IncidentSeverity;
import com.attt.incident.repository.IncidentRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final IncidentRepository incidentRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardStats(String timeFrame) {
        long totalOpen = incidentRepository.countOpenIncidents();

        Map<String, Long> bySeverity = new HashMap<>();
        incidentRepository.countOpenBySeverity().forEach(row -> {
            bySeverity.put(((IncidentSeverity) row[0]).name(), (Long) row[1]);
        });

        Map<String, Long> byStatus = new HashMap<>();
        incidentRepository.countByStatus().forEach(row -> {
            byStatus.put(row[0].toString(), (Long) row[1]);
        });

        Map<String, Long> byCategory = new HashMap<>();
        incidentRepository.countByCategory().forEach(row -> {
            byCategory.put(row[0] != null ? row[0].toString() : "Khác", (Long) row[1]);
        });

        // Tính thời gian xử lý trung bình theo mức độ
        List<Incident> closedIncidents = incidentRepository.findClosedIncidents();
        Map<String, Double> avgResolutionTime = new HashMap<>();
        Map<String, Long> countResolution = new HashMap<>();
        Map<String, Long> sumResolution = new HashMap<>();

        for (Incident inc : closedIncidents) {
            if (inc.getClosedAt() != null && inc.getCreatedAt() != null) {
                long hours = Duration.between(inc.getCreatedAt(), inc.getClosedAt()).toHours();
                String sev = inc.getSeverity().name();
                sumResolution.put(sev, sumResolution.getOrDefault(sev, 0L) + hours);
                countResolution.put(sev, countResolution.getOrDefault(sev, 0L) + 1);
            }
        }
        for (String sev : sumResolution.keySet()) {
            avgResolutionTime.put(sev, sumResolution.get(sev) / (double) countResolution.get(sev));
        }

        // Thống kê theo thời gian (ví dụ: 30 ngày qua)
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(30);
        if ("week".equalsIgnoreCase(timeFrame)) {
            startDate = endDate.minusDays(7);
        } else if ("year".equalsIgnoreCase(timeFrame)) {
            startDate = endDate.minusDays(365);
        }

        List<Incident> timeFrameIncidents = incidentRepository.findIncidentsByTimeFrame(startDate, endDate);
        Map<String, Long> byDate = new TreeMap<>(); // TreeMap để tự động sắp xếp tăng dần theo ngày
        for (Incident inc : timeFrameIncidents) {
            String dateStr = inc.getCreatedAt().format(DATE_FORMATTER);
            byDate.put(dateStr, byDate.getOrDefault(dateStr, 0L) + 1);
        }

        // Tính SLA Compliance và Resolution Breakdown
        long totalClosed = closedIncidents.size();
        long slaMetCount = 0;
        Map<String, Long> resolutionBreakdown = new HashMap<>();

        for (Incident inc : closedIncidents) {
            // SLA Calculation
            if (inc.getClosedAt() != null && inc.getResolveDueAt() != null) {
                if (!inc.getClosedAt().isAfter(inc.getResolveDueAt())) {
                    slaMetCount++;
                }
            } else {
                // Nếu ko có hạn xử lý, coi như đạt
                slaMetCount++;
            }

            // Resolution Breakdown
            if (inc.getResolutionType() != null) {
                String resType = inc.getResolutionType().name();
                resolutionBreakdown.put(resType, resolutionBreakdown.getOrDefault(resType, 0L) + 1);
            }
        }
        
        double slaComplianceRate = totalClosed > 0 ? (double) slaMetCount / totalClosed * 100.0 : 100.0;

        return DashboardResponse.builder()
                .totalOpenIncidents(totalOpen)
                .incidentsBySeverity(bySeverity)
                .incidentsByStatus(byStatus)
                .incidentsByCategory(byCategory)
                .averageResolutionTimeHoursBySeverity(avgResolutionTime)
                .incidentsByDate(byDate)
                .slaComplianceRate(Math.round(slaComplianceRate * 100.0) / 100.0)
                .resolutionTypeBreakdown(resolutionBreakdown)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] exportToExcel() {
        List<Incident> incidents = incidentRepository.findAll();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Incidents");

            // Header
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Mã sự cố");
            headerRow.createCell(1).setCellValue("Tiêu đề");
            headerRow.createCell(2).setCellValue("Mức độ");
            headerRow.createCell(3).setCellValue("Trạng thái");
            headerRow.createCell(4).setCellValue("Người báo cáo");
            headerRow.createCell(5).setCellValue("Người xử lý");
            headerRow.createCell(6).setCellValue("Ngày tạo");
            headerRow.createCell(7).setCellValue("Ngày đóng");

            // Data
            int rowIdx = 1;
            for (Incident inc : incidents) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(inc.getIncidentCode());
                row.createCell(1).setCellValue(inc.getTitle());
                row.createCell(2).setCellValue(inc.getSeverity().name());
                row.createCell(3).setCellValue(inc.getStatus().name());
                row.createCell(4).setCellValue(inc.getReportedBy() != null ? inc.getReportedBy().getUsername() : "");
                row.createCell(5).setCellValue(inc.getAssignedTo() != null ? inc.getAssignedTo().getUsername() : "");
                row.createCell(6).setCellValue(inc.getCreatedAt() != null ? inc.getCreatedAt().format(DATETIME_FORMATTER) : "");
                row.createCell(7).setCellValue(inc.getClosedAt() != null ? inc.getClosedAt().format(DATETIME_FORMATTER) : "");
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi xuất file Excel", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportToPdf() {
        List<Incident> incidents = incidentRepository.findAll();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Bao cao danh sach su co", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(10f);

            // Header
            String[] headers = {"Ma su co", "Tieu de", "Muc do", "Trang thai", "Nguoi bao cao", "Nguoi xu ly", "Ngay tao"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            // Data
            for (Incident inc : incidents) {
                table.addCell(inc.getIncidentCode());
                table.addCell(inc.getTitle());
                table.addCell(inc.getSeverity().name());
                table.addCell(inc.getStatus().name());
                table.addCell(inc.getReportedBy() != null ? inc.getReportedBy().getUsername() : "");
                table.addCell(inc.getAssignedTo() != null ? inc.getAssignedTo().getUsername() : "");
                table.addCell(inc.getCreatedAt() != null ? inc.getCreatedAt().format(DATE_FORMATTER) : "");
            }

            document.add(table);
            document.close();
            
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi xuất file PDF", e);
        }
    }
}
