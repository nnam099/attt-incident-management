package com.attt.incident.controller;

import com.attt.incident.dto.DashboardResponse;
import com.attt.incident.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_MANAGER') or hasRole('ADMIN') or hasRole('MANAGER')")
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboardStats(
            @RequestParam(defaultValue = "month") String timeFrame) {
        return ResponseEntity.ok(reportService.getDashboardStats(timeFrame));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_MANAGER') or hasRole('ADMIN') or hasRole('MANAGER')")
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportToExcel() {
        byte[] data = reportService.exportToExcel();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "incidents_report.xlsx");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_MANAGER') or hasRole('ADMIN') or hasRole('MANAGER')")
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportToPdf() {
        byte[] data = reportService.exportToPdf();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "incidents_report.pdf");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }
}
