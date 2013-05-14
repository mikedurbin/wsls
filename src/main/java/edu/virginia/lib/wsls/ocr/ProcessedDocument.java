package edu.virginia.lib.wsls.ocr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;

import edu.virginia.lib.wsls.proc.ImageMagickProcess;
import edu.virginia.lib.wsls.proc.TesseractProcess;

public class ProcessedDocument {

    private File pdfFile;

    private String[] ocrText;

    private UncertainMatch[] keyedText;

    private int pageCount;

    public ProcessedDocument(File f) throws IOException, InterruptedException {
        ImageMagickProcess convert = new ImageMagickProcess();
        TesseractProcess tesseract = new TesseractProcess();
        
        pdfFile = f;
        pageCount = getPageCount(pdfFile);
        System.out.println(f.getName());
        ocrText = new String[pageCount];
        keyedText = new UncertainMatch[pageCount];
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

    public List<OCRPage> getOrderedPages() {
        ArrayList<OCRPage> pages = new ArrayList<OCRPage>();
        for (int i = 0; i < pageCount; i ++) {
            pages.add(new OCRPage(i));
        }
        Collections.sort(pages);
        return pages;
    }
    
    private int getPageCount(File pdfFile) throws IOException {
        PDDocument doc = PDDocument.load(pdfFile);
        return doc.getNumberOfPages();
    }

    public class OCRPage implements Comparable<OCRPage> {

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

        public void setKeyedText(UncertainMatch t) {
            keyedText[pageIndex] = t;
        }

        public UncertainMatch getKeyedText() {
            return keyedText[pageIndex];
        }

        public int compareTo(OCRPage other) {
            return new Integer(pageIndex).compareTo(new Integer(other.pageIndex));
        }
    }

    public static class UncertainMatch {
        public String match;
        public int bestScore;
        public int worst;
        public List<Integer> scores; 

        public float getCertainty() {
          float percentWrong = (float) bestScore / (float) worst;
          return 1 - percentWrong;
        }
        
        public UncertainMatch(String match, List<Integer> scores) {
            this.match = match;
            this.scores = scores;
            if (scores != null) {
                for (Integer score : scores) {
                    if (bestScore == 0 || bestScore > score) {
                        bestScore = score;
                    }
                    if (worst == 0 || worst < score) {
                        worst = score;
                    }
                }
            }
        }
    }
}
