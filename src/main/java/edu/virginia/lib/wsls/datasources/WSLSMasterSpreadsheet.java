package edu.virginia.lib.wsls.datasources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import edu.virginia.lib.wsls.spreadsheet.ColumnMapping;
import edu.virginia.lib.wsls.spreadsheet.ColumnNameBasedPBCoreRow;
import edu.virginia.lib.wsls.spreadsheet.PBCoreSpreadsheetRow;

public class WSLSMasterSpreadsheet implements Iterable<PBCoreSpreadsheetRow> {

    protected Workbook master;

    private File origFile;

    public WSLSMasterSpreadsheet(File spreadsheet) throws IOException {
        origFile = spreadsheet;
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
    }

    public Workbook getWorkbook() {
        return master;
    }

    public void writeOutWorkbook(String suffix) throws FileNotFoundException, IOException {
        String name = origFile.getName().substring(0, origFile.getName().lastIndexOf('.'));
        String extension = origFile.getName().substring(origFile.getName().lastIndexOf('.'));
        File outputFile = new File(origFile.getParent(), name + suffix + extension);
        master.write(new FileOutputStream(outputFile));
    }

    public Iterator<PBCoreSpreadsheetRow> iterator() {
        return new PBCoreSpreadsheetRowIterator(master);
    }

    public static final int ALL = -1;
    public static final int CLIPS_WITH_SCRIPTS = 1;
    public static final int CLIPS_WITHOUT_SCRIPTS = 2;
    public static final int SCRIPTS_WITHOUT_CLIPS = 3;
    public static final int PROBLEMS = 4;
    public List<String> getIdsFromMaster(List<Integer> codes) {
        List<String> results = new ArrayList<String>();
        int idCol = -1;
        int codeCol = -1;
        for (Row r : master.getSheetAt(0)) {
            if (idCol == -1 || codeCol == -1) {
                for (Cell c : r) {
                    if (c.getCellType() == Cell.CELL_TYPE_STRING && c.getStringCellValue().equals("pbcoreIdentifier source=UVA")) {
                        idCol = c.getColumnIndex();
                    }
                    if (c.getCellType() == Cell.CELL_TYPE_STRING && c.getStringCellValue().equals("Processing category code")) {
                        codeCol = c.getColumnIndex();
                    }
                }
            } else {
                String id = r.getCell(idCol).getStringCellValue();
                if (id.length() > 0 && (codes.isEmpty() || codes.contains((int) r.getCell(codeCol).getNumericCellValue()))) {
                    if (id.endsWith("L")) {
                        results.add(id.substring(0, id.length() - 1));
                        System.out.println("Stripped L from " + id);
                    } else {
                        results.add(id);
                    }
                }
            }
        }
        return results;
    }

    private static class PBCoreSpreadsheetRowIterator implements Iterator<PBCoreSpreadsheetRow> {

        private PBCoreSpreadsheetRow next = null;

        private ColumnMapping mapping = null;

        private Iterator<Row> rowIt = null;

        public PBCoreSpreadsheetRowIterator(Workbook wb) {
            rowIt = wb.getSheetAt(0).iterator();

            // Parse column Headers
            if (rowIt.hasNext()) {
                Row r = rowIt.next();
                mapping = new ColumnMapping(r);
            }

            fetchNext();
        }

        private void fetchNext() {
            if (rowIt.hasNext()) {
                next = new ColumnNameBasedPBCoreRow(rowIt.next(), mapping);
            } else {
                next = null;
            }
        }

        public boolean hasNext() {
            return next != null;
        }

        public PBCoreSpreadsheetRow next() {
            try {
                return next;
            } finally {
                fetchNext();
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
}
