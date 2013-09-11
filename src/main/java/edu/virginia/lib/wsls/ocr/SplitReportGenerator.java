package edu.virginia.lib.wsls.ocr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.io.IOUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;

public class SplitReportGenerator {
    
    public static void main(String args[]) throws Exception {
        File pdfRootDir = new File(args[0]);
        File textRootDir = new File(args[1]);
        File output = new File(args[2]);
        String report= args[3];

        // walk through the spreadsheets to get the numbers
        SplitReportGenerator g = new SplitReportGenerator();
        FileListing fl = new FileListing(pdfRootDir, textRootDir);
        g.copyFiles(fl, output, new HSSFWorkbook(new FileInputStream(report)));
    }

    public File outputRootDir;
    
    public SplitReportGenerator() {
        outputRootDir = new File("auto-split-text-output");
    }

    private void copy(File in, File out) throws IOException {
        if (out.exists()) {
            System.out.println(out.getName() + " already exists: skipping");
            return;
        }
        FileInputStream is = new FileInputStream(in);
        FileOutputStream os = new FileOutputStream(out);
        IOUtils.copy(is, os);
        is.close();
        os.close();
    }
    
    public void copyFiles(FileListing fl, File guessedSplitsDir, Workbook errors) throws FileNotFoundException, IOException {
        System.out.println(fl.getIds().size() + " ids loaded");
        Pattern fullPdfPattern = Pattern.compile("^\\d\\d\\d\\d\\.pdf$");
        int noNeedToSplit = 0;
        int filesWritten = 0;
        Map<Integer, String> idToErrorMap = new HashMap<Integer, String>();
        for (Row r : errors.getSheetAt(0)) {
            if (r.getCell(1) != null && r.getCell(0) != null && r.getCell(2) != null && "ERROR".equals(r.getCell(1).getStringCellValue())) {
                idToErrorMap.put(Integer.parseInt(r.getCell(0).getStringCellValue()), r.getCell(2).getStringCellValue());
            }
        }
        System.out.println(idToErrorMap.size() + " errors loaded from spreadsheet...");
        for (int i : fl.getIds()) {
            boolean success = false;
            List<File> files = fl.getFilesForNumber(i);
            
            if (!FileListing.requiresSplitting(files)) {
                // copy the single text file
                for (File f : files) {
                    if (f.getName().endsWith(".txt")) {
                        copy(f, getSingleOutputFile(i));
                        filesWritten ++;
                        noNeedToSplit ++;
                        success = true;
                        break;
                    }
                }
            } else {
                // check for problems apparent from filenames
                for (File f : files) {
                    if (!f.getName().endsWith(".txt") && !f.getName().endsWith(".pdf")) {
                        // unrecognized file extension
                        idToErrorMap.put(i, "Unrecognized file extension: " + f.getName());
                        break;
                    } else if (f.getName().endsWith("duplicate.pdf")) {
                        // duplicate requires special handling
                        idToErrorMap.put(i, "Duplicate part: " + f.getName());
                        break;
                    } else if (f.getName().endsWith(".pdf") && !fullPdfPattern.matcher(f.getName()).matches()) {
                        // test for odd parts
                        if (!Pattern.compile("^\\d\\d\\d\\d_\\d(_annotated)?\\.pdf$").matcher(f.getName()).matches()) {
                            // unrecognized PDF
                            idToErrorMap.put(i, "Unrecognized part file: " + f.getName());
                            break;
                        }
                    }
                }
                // copy split files
                if (!idToErrorMap.containsKey(i)) {
                    for (File f : files) {
                        if (f.getName().endsWith(".pdf") && !fullPdfPattern.matcher(f.getName()).matches()) {
                            File splitText = new File(guessedSplitsDir, f.getName().replace(".pdf", "-guessed.txt"));
                            if (splitText.exists()) {
                                String text = FileUtils.readFileToString(splitText);
                                File out = getPartOutputFile(i, f);
                                if (out.exists()) {
                                    System.out.println(out.getName() + " already exists: skipping");
                                } else {
                                    FileUtils.writeStringToFile(out, text.trim());
                                }
                                filesWritten ++;
                            } else {
                                idToErrorMap.put(i, "Unable to find file: " + splitText);
                            }
                        }
                    }
                }
            }
        }
        System.out.println(filesWritten + " files written.");
        System.out.println(noNeedToSplit + " files required no splitting. (single clip)");
        
        System.out.println(idToErrorMap.size() + " original PDFs with errors.");
        ArrayList<Integer> ids = new ArrayList<Integer>(fl.getIds());
        Collections.sort(ids);
        for (Integer i : ids) {
            if (idToErrorMap.containsKey(i)) {
                System.out.println(i + ": " + idToErrorMap.get(i));
            }
        }
    }

    private File getPartOutputFile(int number, File pdf) {
        return new File(getDirectoryForNumber(number), pdf.getName().replace(".pdf", ".txt"));
    }
    private File getSingleOutputFile(int number) {
        DecimalFormat format = new DecimalFormat("0000");
        return new File(getDirectoryForNumber(number), format.format(number) + "_1.txt");
    }
    
    private File getDirectoryForNumber(int number) {
        File dir = new File(outputRootDir, "other");
        if (number < 1000) {
            dir = new File(outputRootDir, "3 to 999");
        } else if (number < 2000) {
            dir = new File(outputRootDir, "1000 to 1999");
        } else if (number < 3000) {
            dir = new File(outputRootDir, "2000 to 2999");
        } else if (number < 4000) {
            dir = new File(outputRootDir, "3000 to 3999");
        } else if (number < 5000) {
            dir = new File(outputRootDir, "4000 to 4999");
        } else if (number < 6000) {
            dir = new File(outputRootDir, "5000 to 5999");
        } else if (number < 7000) {
            dir = new File(outputRootDir, "6000 to 6999");
        } else if (number < 8000) {
            dir = new File(outputRootDir, "7000 to 7999");
        }
        dir.mkdirs();
        return dir;
    }

}
