package com.mavis.doublerecording.report;

import com.mavis.doublerecording.chain.Block;
import com.mavis.doublerecording.chain.Blockchain;
import com.mavis.doublerecording.chain.Transaction;
import com.mavis.doublerecording.video.RecordingComplianceService;
import com.mavis.doublerecording.video.RecordingComplianceService.RecordingState;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 录制报告 PDF 导出(OpenPDF)
 *
 * 使用 LGPL 协议的 OpenPDF(原 iText 4.x fork)
 * 输出 A4 报告,中文字体使用 STSong-Light
 */
@Service
@RequiredArgsConstructor
public class PdfReportService {

    private final RecordingComplianceService recordingCompliance;
    private final Blockchain blockchain;

    public byte[] exportPdfReport(String sessionId) {
        RecordingState state = recordingCompliance.getState(sessionId);
        if (state == null) {
            throw new RuntimeException("会话不存在: " + sessionId);
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);

            PdfWriter.getInstance(document, out);
            document.open();

            // 标题
            Font titleFont = FontFactory.getFont("STSong-Light", 20, Font.BOLD, new Color(30, 58, 138));
            Paragraph title = new Paragraph("双录录制报告", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Font subtitleFont = FontFactory.getFont("STSong-Light", 12, Font.NORMAL, new Color(100, 116, 139));
            Paragraph subtitle = new Paragraph("AIRecord · " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitle);
            document.add(Chunk.NEWLINE);

            // 基本信息
            addSectionHeader(document, "📌 录制基本信息");
            addInfoTable(document, Map.of(
                "会话ID", sessionId,
                "客户经理", state.getAgentId() == null ? "-" : state.getAgentId(),
                "开始时间", formatTime(state.getStartTime()),
                "结束时间", formatTime(state.getStopTime()),
                "录制状态", state.getStopTime() != null ? "已完成" : "进行中",
                "暂停次数", String.valueOf(state.getPauseCount()),
                "累计暂停", state.getTotalPausedSeconds() + " 秒",
                "节点数", String.valueOf(state.getNodeDurations() == null ? 0 : state.getNodeDurations().size())
            ));

            // 节点时长明细
            if (state.getNodeDurations() != null && !state.getNodeDurations().isEmpty()) {
                document.add(Chunk.NEWLINE);
                addSectionHeader(document, "⏱ 节点时长明细");
                PdfPTable table = new PdfPTable(3);
                table.setWidthPercentage(100);
                addTableHeader(table, "节点", "时长(秒)", "状态");
                int total = 0;
                for (Map.Entry<Integer, Integer> entry : state.getNodeDurations().entrySet()) {
                    total += entry.getValue();
                    addTableCell(table, "N" + String.format("%02d", entry.getKey()));
                    addTableCell(table, String.valueOf(entry.getValue()));
                    addTableCell(table, "✓ 完成");
                }
                addTableCell(table, "合计");
                addTableCell(table, String.valueOf(total));
                addTableCell(table, "-");
                document.add(table);
            }

            // 区块链存证
            document.add(Chunk.NEWLINE);
            addSectionHeader(document, "🔗 区块链存证");
            List<Block> blocks = blockchain.getChain();
            if (blocks != null && !blocks.isEmpty()) {
                long relatedCount = blocks.stream()
                    .flatMap(b -> b.getTransactions().stream())
                    .filter(t -> sessionId.equals(t.getPayload().get("sessionId")))
                    .count();
                Paragraph summary = new Paragraph(
                    "区块链总高度: " + blocks.size() + ", 本会话关联交易: " + relatedCount + " 笔",
                    FontFactory.getFont("STSong-Light", 11));
                document.add(summary);
                document.add(Chunk.NEWLINE);
                PdfPTable chainTable = new PdfPTable(4);
                chainTable.setWidthPercentage(100);
                addTableHeader(chainTable, "区块", "哈希", "时间", "事件");
                for (Block b : blocks) {
                    if (b.getTransactions() == null) continue;
                    for (Transaction t : b.getTransactions()) {
                        if (!sessionId.equals(t.getPayload().get("sessionId"))) continue;
                        addTableCell(chainTable, "#" + b.getIndex());
                        addTableCell(chainTable, b.getHash().substring(0, 16) + "...");
                        addTableCell(chainTable, formatTime(b.getTimestamp()));
                        addTableCell(chainTable, String.valueOf(t.getPayload().get("eventType")));
                    }
                }
                document.add(chainTable);
            } else {
                document.add(new Paragraph("暂无区块链存证记录",
                    FontFactory.getFont("STSong-Light", 11)));
            }

            // 页脚
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);
            Paragraph footer = new Paragraph(
                "本报告由 AIRecord 系统自动生成 | 版本 v1.6.0",
                FontFactory.getFont("STSong-Light", 10, Font.ITALIC, new Color(107, 114, 128)));
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF 生成失败: " + e.getMessage(), e);
        }
    }

    private void addSectionHeader(Document doc, String text) throws DocumentException {
        Font font = FontFactory.getFont("STSong-Light", 14, Font.BOLD, new Color(30, 58, 138));
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(10);
        p.setSpacingAfter(8);
        doc.add(p);
    }

    private void addInfoTable(Document doc, Map<String, String> info) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{30, 70});
        for (Map.Entry<String, String> entry : info.entrySet()) {
            PdfPCell key = new PdfPCell(new Phrase(entry.getKey(),
                FontFactory.getFont("STSong-Light", 10, Font.BOLD)));
            key.setBackgroundColor(new Color(243, 244, 246));
            key.setPadding(6);
            table.addCell(key);
            PdfPCell val = new PdfPCell(new Phrase(entry.getValue(),
                FontFactory.getFont("STSong-Light", 10)));
            val.setPadding(6);
            table.addCell(val);
        }
        doc.add(table);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h,
                FontFactory.getFont("STSong-Light", 10, Font.BOLD, Color.WHITE)));
            cell.setBackgroundColor(new Color(30, 58, 138));
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addTableCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
            FontFactory.getFont("STSong-Light", 10)));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
