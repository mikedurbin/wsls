package edu.virginia.lib.wsls.ocr;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.pdfbox.io.IOUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import edu.virginia.lib.wsls.ocr.ProcessedDocument.OCRPage;

/**
 * Walks through the processed PDF directories to make an attempt to split up
 * the keyed text files to match the split PDFs.  
 */
public class SplitterAnalyzer {

    public static void main(String[] args) throws Exception {
        System.out.println("Identifying files...");
        FileListing fl = new FileListing(new File(args[0]), new File(args[1]));
        Collection<Integer> ids = fl.getIds();
        System.out.println(ids.size() + " ids detected.");

        File output = new File(args[2]);
        output.mkdirs();

        int rowNum = 0;
        Workbook wb = new HSSFWorkbook();
        Sheet s = null;
        Set<Integer> completed = new HashSet<Integer>();
        try {
            File oldOutput = new File("output/report.xls");
            if (oldOutput.exists()) {
                wb = new HSSFWorkbook(new FileInputStream(oldOutput));
                System.out.println("Loaded existing workbook...");
                s = wb.getSheetAt(0);
                for (Row r : s) {
                    rowNum ++;
                    String value = r.getCell(0).getStringCellValue();
                    int completedNumber = Integer.parseInt(value.substring(0, value.length() >= 4 ? 4 : value.length()));
                    if (!"ERROR".equals(r.getCell(1).getStringCellValue())) {
                        completed.add(completedNumber);
                    }
                }
            } else {
                s = wb.createSheet();
            }
        } catch (Throwable t) {
            // no workbook loaded
        }

        // create a new workbook just for errors
        wb = new HSSFWorkbook();
        s = wb.createSheet();
        rowNum = 0;

        Runtime.getRuntime().addShutdownHook(new Thread(new WorkbookSaver(wb, new File(output, "error-report.xls"))));
        DecimalFormat format = new DecimalFormat("0000");
        for (Integer i : ids) {
            if (!completed.contains(i)) {
                Row r = s.createRow(rowNum++);
                if (FileListing.requiresSplitting(fl.getFilesForNumber(i))) {
                    System.out.println(format.format(i) + ": analyzing");
                    try {
                        OCRTextSplitter splitter = new OCRTextSplitter(FileListing.getFilenames(fl.getFilesForNumber(i)));
                        for (ProcessedDocument d : splitter.getBestMatches()) {
                            addTextCell(r, 0, d.getFilename());
                            StringBuffer text = new StringBuffer();
                            int score = 0;
                            for (OCRPage page : d.getOrderedPages()) {
                                text.append(page.getKeyedText().match);
                                score += page.getKeyedText().bestScore;
                            }
                            File textFile = new File(output, d.getFilename().replace(".pdf", "-guessed.txt"));
                            FileOutputStream os = new FileOutputStream(textFile);
                            IOUtils.copy(new ByteArrayInputStream(text.toString().getBytes("UTF-8")), os);
                            os.close();
                            addTextCell(r, 1, String.valueOf(score));
                        }
                    } catch (IllegalArgumentException ex) {
                        addTextCell(r, 0, String.valueOf(i));
                        addTextCell(r, 1, "ERROR");
                        addTextCell(r, 2, ex.getClass().getName() + (ex.getMessage() != null ? " - " + ex.getMessage() : ""));
                    } catch (Throwable t) {
                        t.printStackTrace();
                        addTextCell(r, 0, String.valueOf(i));
                        addTextCell(r, 1, "ERROR");
                        addTextCell(r, 2, t.getClass().getName() + (t.getMessage() != null ? " - " + t.getMessage() : ""));
                    }
                } else {
                    System.out.println(format.format(i) + ": nothing to do");
                    addTextCell(r, 0, format.format(i) + ".pdf");
                    addTextCell(r, 1, "No Split Required");
                }
            } else {
                System.out.println("Already done " + i);
            }
        }
    }
    
    private static void addTextCell(Row r, int column, String text) {
        Cell idCell = r.createCell(column);
        idCell.setCellType(Cell.CELL_TYPE_STRING);
        idCell.setCellValue(text);
    }

    private static class WorkbookSaver implements Runnable {

        private Workbook wb;
        
        private File f;
        
        public WorkbookSaver(Workbook wb2, File file) {
            wb = wb2;
            f = file;
        }

        public void run() {
            try {
                System.out.println("Shutdown detected... saving workbook.");
                FileOutputStream os = new FileOutputStream(f);
                wb.write(os);
                os.close();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        
    }
}
