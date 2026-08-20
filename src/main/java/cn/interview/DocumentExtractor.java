package cn.interview;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

@Component
public class DocumentExtractor {
    String extract(String name,byte[] bytes) {
        try {
            String text;
            if(name.toLowerCase(Locale.ROOT).endsWith(".pdf"))text=pdf(bytes);
            else {Tika tika=new Tika();tika.setMaxStringLength(60001);text=tika.parseToString(new ByteArrayInputStream(bytes));}
            if(text==null||text.isBlank())throw new IllegalArgumentException("文档中没有可识别的正文，请上传清晰文件或粘贴文字。");
            if(text.length()>60000)throw new IllegalArgumentException("文档正文超过60000字符，请减少页数后重试。");
            return text;
        }catch(IllegalArgumentException e){throw e;}
        catch(Exception e){throw new IllegalArgumentException("文档无法解析，请检查文件是否损坏或加密，或改为粘贴正文。");}
    }
    private String pdf(byte[] bytes)throws Exception {
        try(var document=Loader.loadPDF(bytes)) {
            if(document.getNumberOfPages()>10)throw new IllegalArgumentException("PDF最多支持10页，请拆分后上传。");
            var result=new StringBuilder();var stripper=new PDFTextStripper();var renderer=new PDFRenderer(document);
            for(int page=0;page<document.getNumberOfPages();page++) {
                stripper.setStartPage(page+1);stripper.setEndPage(page+1);
                String text=stripper.getText(document);
                if(text.replaceAll("\\s","").length()<30) {
                    var box=document.getPage(page).getCropBox();
                    float dpi=Math.min(200f,2200f*72f/Math.max(box.getWidth(),box.getHeight()));
                    if(!Float.isFinite(dpi)||dpi<=0)throw new IllegalArgumentException("PDF页面尺寸异常，请重新导出文件。");
                    Path dir=Files.createTempDirectory("interview-ocr-");
                    Path png=dir.resolve("page.png"),out=dir.resolve("text.txt"),err=dir.resolve("error.txt");
                    Process process=null;
                    try {
                        ImageIO.write(renderer.renderImageWithDPI(page,dpi),"png",png.toFile());
                        try {
                            process=new ProcessBuilder("tesseract",png.toString(),"stdout","-l","chi_sim+eng","--psm","3")
                                .redirectOutput(out.toFile()).redirectError(err.toFile()).start();
                        }catch(java.io.IOException e){throw new IllegalArgumentException("扫描PDF需要OCR组件，请更新平台容器后重试，或先粘贴简历文字。");}
                        if(!process.waitFor(45,TimeUnit.SECONDS))throw new IllegalArgumentException("扫描页识别超时，请上传更清晰或更少页的PDF。");
                        if(process.exitValue()!=0)throw new IllegalArgumentException("扫描页OCR识别失败，请上传清晰的PDF或粘贴正文。");
                        text=Files.readString(out,StandardCharsets.UTF_8);
                    }finally {
                        if(process!=null&&process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}
                        Files.deleteIfExists(png);Files.deleteIfExists(out);Files.deleteIfExists(err);Files.deleteIfExists(dir);
                    }
                }
                result.append(text).append('\n');
                if(result.length()>60000)throw new IllegalArgumentException("文档正文超过60000字符，请减少页数后重试。");
            }
            return result.toString();
        }
    }
}
