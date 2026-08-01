package com.mavis.doublerecording.report;

import com.mavis.doublerecording.chain.Block;
import com.mavis.doublerecording.chain.Blockchain;
import com.mavis.doublerecording.chain.Transaction;
import com.mavis.doublerecording.video.RecordingComplianceService;
import com.mavis.doublerecording.video.RecordingComplianceService.RecordingState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 录制报告导出服务(HTML,可打印为 PDF)
 */
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final RecordingComplianceService recordingCompliance;
    private final Blockchain blockchain;

    public String exportHtmlReport(String sessionId) {
        RecordingState state = recordingCompliance.getState(sessionId);
        if (state == null) {
            return "<html><body><h1>会话 " + sessionId + " 不存在</h1></body></html>";
        }
        StringBuilder html = new StringBuilder();
        html.append(htmlHeader());
        html.append(htmlBody(sessionId, state));
        html.append(htmlFooter());
        return html.toString();
    }

    private String htmlHeader() {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <title>双录录制报告</title>
            <style>
                body { font-family: -apple-system, sans-serif; margin: 40px; color: #1a1a1a; }
                h1 { color: #1e3a8a; border-bottom: 3px solid #4f8cff; padding-bottom: 8px; }
                h2 { color: #1e3a8a; margin-top: 32px; border-left: 4px solid #4f8cff; padding-left: 12px; }
                .info-table { width: 100%; border-collapse: collapse; margin: 16px 0; }
                .info-table td, .info-table th { padding: 8px 12px; border-bottom: 1px solid #e5e7eb; text-align: left; }
                .info-table th { background: #f3f4f6; font-weight: 600; }
                .info-table td:first-child { background: #f9fafb; font-weight: 600; width: 200px; }
                .pass { color: #16a34a; font-weight: 700; }
                .fail { color: #dc2626; font-weight: 700; }
                .warn { color: #ea580c; font-weight: 700; }
                .footer { margin-top: 40px; padding-top: 16px; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 12px; }
                .badge { display: inline-block; padding: 4px 12px; border-radius: 4px; font-size: 12px; font-weight: 600; }
                .badge-success { background: #dcfce7; color: #166534; }
                .badge-warning { background: #fef3c7; color: #92400e; }
                .badge-info { background: #dbeafe; color: #1e40af; }
            </style>
            </head>
            <body>
            """;
    }

    private String htmlBody(String sessionId, RecordingState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>📋 双录录制报告</h1>");

        // 基本信息
        sb.append("<h2>📌 基本信息</h2>");
        sb.append("<table class='info-table'>");
        sb.append(infoRow("会话ID", sessionId));
        sb.append(infoRow("客户经理", state.getAgentId() == null ? "-" : state.getAgentId()));
        sb.append(infoRow("开始时间", formatTime(state.getStartTime())));
        sb.append(infoRow("结束时间", formatTime(state.getStopTime())));
        sb.append(infoRow("开始节点", "N" + state.getCurrentNodeSeq()));
        sb.append(infoRow("录制状态",
            state.getStopTime() != null
                ? "<span class='badge badge-success'>已完成</span>"
                : "<span class='badge badge-warning'>进行中</span>"));
        sb.append(infoRow("暂停次数", String.valueOf(state.getPauseCount())));
        sb.append(infoRow("累计暂停", state.getTotalPausedSeconds() + " 秒"));
        sb.append(infoRow("节点数", String.valueOf(state.getNodeDurations() == null ? 0 : state.getNodeDurations().size())));
        sb.append("</table>");

        // 节点时长明细
        if (state.getNodeDurations() != null && !state.getNodeDurations().isEmpty()) {
            sb.append("<h2>⏱ 节点时长明细</h2>");
            sb.append("<table class='info-table'><tr><th>节点</th><th>时长(秒)</th><th>状态</th></tr>");
            int total = 0;
            for (Map.Entry<Integer, Integer> entry : state.getNodeDurations().entrySet()) {
                total += entry.getValue();
                sb.append("<tr><td>N").append(String.format("%02d", entry.getKey()))
                    .append("</td><td>").append(entry.getValue())
                    .append("</td><td><span class='badge badge-success'>完成</span></td></tr>");
            }
            sb.append("<tr><td><strong>合计</strong></td><td><strong>").append(total).append("</strong></td><td>-</td></tr>");
            sb.append("</table>");
        }

        // 区块链存证
        sb.append("<h2>🔗 区块链存证</h2>");
        List<Block> blocks = blockchain.getChain();
        if (blocks != null && !blocks.isEmpty()) {
            // 筛选与本会话相关的交易
            long relatedCount = blocks.stream()
                .flatMap(b -> b.getTransactions().stream())
                .filter(t -> sessionId.equals(t.getPayload().get("sessionId")))
                .count();
            sb.append("<p>区块链总高度: <strong>").append(blocks.size()).append("</strong>, 本会话关联交易: <strong>")
                .append(relatedCount).append("</strong> 笔</p>");
            sb.append("<table class='info-table'>");
            sb.append("<tr><th>区块</th><th>哈希</th><th>时间戳</th><th>事件</th><th>PoW</th></tr>");
            for (Block b : blocks) {
                if (b.getTransactions() == null) continue;
                for (Transaction t : b.getTransactions()) {
                    if (!sessionId.equals(t.getPayload().get("sessionId"))) continue;
                    sb.append("<tr><td>#").append(b.getIndex()).append("</td>");
                    sb.append("<td>").append(b.getHash().substring(0, 16)).append("...</td>");
                    sb.append("<td>").append(formatTime(b.getTimestamp())).append("</td>");
                    sb.append("<td>").append(t.getPayload().get("eventType")).append("</td>");
                    sb.append("<td>✓ ").append(b.getNonce()).append("</td></tr>");
                }
            }
            sb.append("</table>");
        } else {
            sb.append("<p>暂无区块链存证记录</p>");
        }

        // 报告时间
        sb.append("<div class='footer'>");
        sb.append("报告生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("<br/>");
        sb.append("AIRecord - 线上线下双录融合系统 v1.5.0");
        sb.append("</div>");

        return sb.toString();
    }

    private String htmlFooter() {
        return "</body></html>";
    }

    private String infoRow(String label, String value) {
        return "<tr><td>" + escapeHtml(label) + "</td><td>" + value + "</td></tr>";
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
