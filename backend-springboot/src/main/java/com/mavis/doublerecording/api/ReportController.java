package com.mavis.doublerecording.api;

import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.report.ReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * 录制报告导出 API
 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportExportService reportExportService;

    /**
     * 导出 HTML 报告
     */
    @GetMapping(value = "/{sessionId}/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> exportHtml(@PathVariable String sessionId) {
        String html = reportExportService.exportHtmlReport(sessionId);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        headers.setContentDispositionFormData("attachment",
            "双录报告-" + sessionId + ".html");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * 在线查看报告(浏览器直接打开)
     */
    @GetMapping(value = "/{sessionId}/view", produces = MediaType.TEXT_HTML_VALUE)
    public String viewReport(@PathVariable String sessionId) {
        return reportExportService.exportHtmlReport(sessionId);
    }
}
