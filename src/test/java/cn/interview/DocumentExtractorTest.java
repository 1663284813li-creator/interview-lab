package cn.interview;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentExtractorTest {
    @Test void extractsTextPdfWithoutRequiringOcr() throws Exception {
        byte[] pdf;
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
            var page=new PDPage();doc.addPage(page);
            try(var stream=new PDPageContentStream(doc,page)) {
                stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                stream.newLineAtOffset(60,700);stream.showText("Synthetic resume: Java Spring Boot PostgreSQL Redis and testing.");stream.endText();
            }
            doc.save(out);pdf=out.toByteArray();
        }
        assertTrue(new DocumentExtractor().extract("resume.pdf",pdf).contains("Synthetic resume"));
    }
    @Test void rejectsBrokenPdfWithAnActionableMessage() {
        var e=assertThrows(IllegalArgumentException.class,()->new DocumentExtractor().extract("bad.pdf","not a PDF".getBytes(StandardCharsets.UTF_8)));
        assertTrue(e.getMessage().contains("无法解析"));
    }
    @Test void rejectsMoreThanTenPagesBeforeStartingOcr() throws Exception {
        byte[] pdf;
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){for(int i=0;i<11;i++)doc.addPage(new PDPage());doc.save(out);pdf=out.toByteArray();}
        final byte[] bytes=pdf;
        assertTrue(assertThrows(IllegalArgumentException.class,()->new DocumentExtractor().extract("long.pdf",bytes)).getMessage().contains("10页"));
    }
}
