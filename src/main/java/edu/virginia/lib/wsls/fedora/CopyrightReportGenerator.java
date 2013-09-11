package edu.virginia.lib.wsls.fedora;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import edu.virginia.lib.wsls.datasources.GoogleMetadata;
import edu.virginia.lib.wsls.spreadsheet.PBCoreSpreadsheetRow;

public class CopyrightReportGenerator {

    public static void main(String [] args) throws Exception {
        GoogleMetadata m = new GoogleMetadata(new File(args[0]));
        File spreadsheet = new File(args[1]);
        Workbook master = null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(spreadsheet);
            master = new HSSFWorkbook(fis);
            fis.close();
        } catch (OfficeXmlFileException ex) {
            fis = new FileInputStream(spreadsheet);
            master = new XSSFWorkbook(fis);
            fis.close();
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
        Set<String> catalogedIds = new HashSet<String>();
        Map<String, String> idToCopyrightMap = new HashMap<String, String>();
        int idCol = -1;
        int copyrightCol = -1;
        int titleCol = -1;
        for (Row r : master.getSheetAt(0)) {
            if (idCol == -1 || copyrightCol == -1 || titleCol == -1) {
                for (Cell c : r) {
                    if (c.getCellType() == Cell.CELL_TYPE_STRING && c.getStringCellValue().equals("pbcoreIdentifier source=UVA")) {
                        idCol = c.getColumnIndex();
                    }
                    if (c.getCellType() == Cell.CELL_TYPE_STRING && c.getStringCellValue().equals("Copyright status if known  (T = Telenews; L=Local; C = all others)")) {
                        copyrightCol = c.getColumnIndex();
                    }
                    if (c.getCellType() == Cell.CELL_TYPE_STRING && c.getStringCellValue().equals("pbcoreDescription type=Title")) {
                        titleCol = c.getColumnIndex();
                    }
                }
            } else {
                String id = r.getCell(idCol).getStringCellValue();
                if (id.length() > 0) {
                    if (id.endsWith("L")) {
                        id = id.substring(0, id.length() - 1);
                    }
                    idToCopyrightMap.put(id, getCellValue(r.getCell(copyrightCol)));
                    String title = getCellValue(r.getCell(titleCol));
                    if (title != null && title.trim().length() > 1) {
                        catalogedIds.add(id);
                    }
                }
            }
        }
        
        List<String> copyrighted = new ArrayList<String>();
        List<String> nonCopyrighted = new ArrayList<String>();
        System.out.println(catalogedIds.size() + " items cataloged in master");
        for (PBCoreSpreadsheetRow r : m) {
            catalogedIds.add(r.getId());
        }
        System.out.println(catalogedIds.size() + " counting the google spreadsheet");
        for (String id : catalogedIds) {
            if (idToCopyrightMap.containsKey(id)) {
                if ("L".equals(idToCopyrightMap.get(id))) {
                    nonCopyrighted.add(id);
                    //System.out.println(id + " is NOT copyrighted.");
                } else {
                    copyrighted.add(id);
                    //System.out.println(id + " is COPYRIGHTED.");
                }
            } else {
                //System.err.println(id + " is UNKNOWN ID");
            }
        }
        System.out.println(copyrighted.size() + "/" +  (copyrighted.size() + nonCopyrighted.size()) + " items copyrighted.");
    }    
    
    public static final String getCellValue(Cell c) {
        return (c == null ? null : c.getStringCellValue());
    }
}
