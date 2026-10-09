package com.attt.incident.service;

import com.attt.incident.dto.DashboardResponse;
import com.attt.incident.entity.Incident;
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
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = switch (timeFrame.toLowerCase(java.util.Locale.ROOT)) {
            case "week" -> endDate.minusDays(7);
            case "month" -> endDate.minusDays(30);
            case "year" -> endDate.minusDays(365);
            default -> throw new com.attt.incident.exception.BadRequestException(
                    "timeFrame chỉ chấp nhận week, month hoặc year");
        };
        List<Incident> incidents = incidentRepository.findIncidentsByTimeFrame(startDate, endDate);

        long totalOpen = incidents.stream()
                .filter(incident -> incident.getStatus() != com.attt.incident.entity.IncidentStatus.CLOSED)
                .count();
        Map<String, Long> bySeverity = incidents.stream()
                .filter(incident -> incident.getStatus() != com.attt.incident.entity.IncidentStatus.CLOSED)
                .collect(Collectors.groupingBy(incident -> incident.getSeverity().name(), Collectors.counting()));
        Map<String, Long> byStatus = incidents.stream()
                .collect(Collectors.groupingBy(incident -> incident.getStatus().name(), Collectors.counting()));
        Map<String, Long> byCategory = incidents.stream()
                .collect(Collectors.groupingBy(
                        incident -> incident.getCategory() == null ? "Khác" : incident.getCategory().getName(),
                        Collectors.counting()));

        List<Incident> closedIncidents = incidents.stream()
                .filter(incident -> incident.getStatus() == com.attt.incident.entity.IncidentStatus.CLOSED)
                .toList();
        Map<String, Double> avgResolutionTime = new HashMap<>();
        Map<String, Long> countResolution = new HashMap<>();
        Map<String, Double> sumResolutionHours = new HashMap<>();

        for (Incident inc : closedIncidents) {
            LocalDateTime completedAt = inc.getResolvedAt() != null ? inc.getResolvedAt() : inc.getClosedAt();
            if (completedAt != null && inc.getCreatedAt() != null) {
                double hours = Duration.between(inc.getCreatedAt(), completedAt).toMinutes() / 60.0;
                String sev = inc.getSeverity().name();
                sumResolutionHours.put(sev, sumResolutionHours.getOrDefault(sev, 0.0) + hours);
                countResolution.put(sev, countResolution.getOrDefault(sev, 0L) + 1);
            }
        }
        for (String sev : sumResolutionHours.keySet()) {
            avgResolutionTime.put(sev, sumResolutionHours.get(sev) / countResolution.get(sev));
        }

        Map<String, Long> byDate = new TreeMap<>(); // TreeMap để tự động sắp xếp tăng dần theo ngày
        for (Incident inc : incidents) {
            String dateStr = inc.getCreatedAt().format(DATE_FORMATTER);
            byDate.put(dateStr, byDate.getOrDefault(dateStr, 0L) + 1);
        }

        // Tính SLA Compliance và Resolution Breakdown
        long totalClosedWithSla = 0;
        long slaMetCount = 0;
        Map<String, Long> resolutionBreakdown = new HashMap<>();

        for (Incident inc : closedIncidents) {
            // SLA Calculation
            LocalDateTime completedAt = inc.getResolvedAt() != null ? inc.getResolvedAt() : inc.getClosedAt();
            if (completedAt != null && inc.getResolveDueAt() != null) {
                totalClosedWithSla++;
                if (!completedAt.isAfter(inc.getResolveDueAt())) {
                    slaMetCount++;
                }
            }

            // Resolution Breakdown
            if (inc.getResolutionType() != null) {
                String resType = inc.getResolutionType().name();
                resolutionBreakdown.put(resType, resolutionBreakdown.getOrDefault(resType, 0L) + 1);
            }
        }
        
        double slaComplianceRate = totalClosedWithSla > 0
                ? (double) slaMetCount / totalClosedWithSla * 100.0 : 0.0;

        Map<String, Map<String, Long>> slaBySeverity = new HashMap<>();
        Map<String, Map<String, Long>> slaByAnalyst = new HashMap<>();
        Map<String, Map<String, Long>> slaByCategory = new HashMap<>();
        long ackOnTime = 0, ackOverdue = 0, resolveOnTime = 0, resolveOverdue = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Incident incident : incidents) {
            LocalDateTime acknowledgement = incident.getAcknowledgedAt();
            if (acknowledgement == null && incident.getStatus() == com.attt.incident.entity.IncidentStatus.CLOSED) {
                acknowledgement = incident.getClosedAt();
            }
            boolean ackLate = false;
            if (incident.getAckDueAt() != null) {
                LocalDateTime comparedAt = acknowledgement != null ? acknowledgement : now;
                ackLate = comparedAt.isAfter(incident.getAckDueAt());
                if (ackLate) ackOverdue++; else ackOnTime++;
            }

            LocalDateTime completion = incident.getResolvedAt() != null
                    ? incident.getResolvedAt() : incident.getClosedAt();
            boolean resolveLate = false;
            if (incident.getResolveDueAt() != null) {
                LocalDateTime comparedAt = completion != null ? completion : now;
                resolveLate = comparedAt.isAfter(incident.getResolveDueAt());
                if (resolveLate) resolveOverdue++; else resolveOnTime++;
            }
            String severity = incident.getSeverity().name();
            String analyst = incident.getAssignedTo() == null ? "UNASSIGNED" : incident.getAssignedTo().getUsername();
            String category = incident.getCategory() == null ? "Khác" : incident.getCategory().getName();
            addSlaMetric(slaBySeverity, severity, ackLate || resolveLate);
            addSlaMetric(slaByAnalyst, analyst, ackLate || resolveLate);
            addSlaMetric(slaByCategory, category, ackLate || resolveLate);
        }

        return DashboardResponse.builder()
                .totalOpenIncidents(totalOpen)
                .incidentsBySeverity(bySeverity)
                .incidentsByStatus(byStatus)
                .incidentsByCategory(byCategory)
                .averageResolutionTimeHoursBySeverity(avgResolutionTime)
                .incidentsByDate(byDate)
                .slaComplianceRate(Math.round(slaComplianceRate * 100.0) / 100.0)
                .resolutionTypeBreakdown(resolutionBreakdown)
                .ackOnTimeCount(ackOnTime).ackOverdueCount(ackOverdue)
                .resolveOnTimeCount(resolveOnTime).resolveOverdueCount(resolveOverdue)
                .slaBySeverity(slaBySeverity).slaByAnalyst(slaByAnalyst).slaByCategory(slaByCategory)
                .build();
    }

    private void addSlaMetric(Map<String, Map<String, Long>> metrics, String key, boolean overdue) {
        Map<String, Long> counts = metrics.computeIfAbsent(key, ignored -> new HashMap<>());
        String state = overdue ? "OVERDUE" : "ON_TIME";
        counts.put(state, counts.getOrDefault(state, 0L) + 1);
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
