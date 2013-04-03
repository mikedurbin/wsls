package edu.virginia.lib.wsls.ocr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;

import edu.virginia.lib.wsls.proc.ImageMagickProcess;
import edu.virginia.lib.wsls.proc.TesseractProcess;

public class ProcessedDocument {

    private File pdfFile;

    private String[] ocrText;

    private String[] keyedText;

    private int pageCount;

    public ProcessedDocument(File f) throws IOException, InterruptedException {
        ImageMagickProcess convert = new ImageMagickProcess();
        TesseractProcess tesseract = new TesseractProcess();
        
        pdfFile = f;
        pageCount = getPageCount(pdfFile);
        System.out.println(f.getName());
        ocrText = new String[pageCount];
        keyedText = new String[pageCount];
        for (int i = 0; i < pageCount; i ++) {
            System.out.println("  page " + (i + 1));
            System.out.println("    balancing image...");
            File cleantif = File.createTempFile("ocr-ready", ".tif");
            convert.generateOCRReadyTiff(pdfFile, cleantif, i);
            File ocrtext = File.createTempFile("generated-ocr", ".txt");
            System.out.println("    generating OCR...");
            tesseract.generateOCR(cleantif, ocrtext);
            ocrText[i] = FileUtils.readFileToString(ocrtext);
            cleantif.delete();
            ocrtext.delete();
        }
    }
    
    public String getFilename() {
        return pdfFile.getName();
    }

    public String getOCRText() {
        StringBuffer sb = new StringBuffer();
        for (String t : ocrText) {
            sb.append(t);
        }
        return sb.toString();
    }

    public int getPageCount() {
        return pageCount;
    }

    public List<OCRPage> getPages() {
        ArrayList<OCRPage> pages = new ArrayList<OCRPage>();
        for (int i = 0; i < pageCount; i ++) {
            pages.add(new OCRPage(i));
        }
        return pages;
    }
    
    private int getPageCount(File pdfFile) throws IOException {
        PDDocument doc = PDDocument.load(pdfFile);
        return doc.getNumberOfPages();
    }

    public class OCRPage {

        private int pageIndex;

        private OCRPage(int i) {
            pageIndex = i;
        }

        public ProcessedDocument getDocument() {
            return ProcessedDocument.this;
        }

        public int getPageIndex() {
            return pageIndex;
        }

        public String getOCRText() {
            return ocrText[pageIndex];
        }

        public void setKeyedText(String t) {
            keyedText[pageIndex] = t;
        }

        public String getKeyedText() {
            return keyedText[pageIndex];
        }
    }
}
