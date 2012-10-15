package edu.virginia.lib.wsls.spreadsheet;

import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class SpreadsheetToPBCore {
    
    public static void main(String[] args) throws Exception {
        // parse the spreadsheet
        Workbook wb = null;
        try {
            wb = new HSSFWorkbook(SpreadsheetToPBCore.class.getClassLoader().getResourceAsStream("example/7622.xlsx"));
        } catch (OfficeXmlFileException ex) {
            wb = new XSSFWorkbook(SpreadsheetToPBCore.class.getClassLoader().getResourceAsStream("example/7622.xlsx"));
        }

        Sheet sheet = wb.getSheetAt(0);
        
        FileOutputStream fos = new FileOutputStream("pbcore.zip");
        ZipOutputStream zos = new ZipOutputStream(fos);
        // output the pbCore document
        ColumnMapping m = new ColumnMapping(sheet.getRow(0));
        System.out.println("Processing " + sheet.getLastRowNum() + " records...");
        for (int i = 1; i <= sheet.getLastRowNum(); i ++) {
            PBCoreSpreadsheetRow row = new ColumnNameBasedPBCoreRow(sheet.getRow(i), m);
            String id = row.getId();
            System.out.println(id);
            zos.putNextEntry(new ZipEntry(id + ".xml"));
            PBCoreDocument pbcore = new PBCoreDocument(row);
            pbcore.appendInstantiationIfAvailable(SpreadsheetToPBCore.class.getClassLoader().getResourceAsStream("example/" + row.getId() + "_PBCore_TECH.xml"));
            pbcore.appendInstantiationIfAvailable(SpreadsheetToPBCore.class.getClassLoader().getResourceAsStream("example/" + row.getId() + "_H_PBCore_TECH.xml"));
            pbcore.appendInstantiationIfAvailable(SpreadsheetToPBCore.class.getClassLoader().getResourceAsStream("example/" + row.getId() + "_L_PBCore_TECH.xml"));

            pbcore.writeOutXML(zos);
            zos.closeEntry();
        }
        zos.close();
    }

    
}
