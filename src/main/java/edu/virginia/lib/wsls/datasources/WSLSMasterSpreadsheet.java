package edu.virginia.lib.wsls.datasources;

import edu.virginia.lib.wsls.spreadsheet.ColumnMapping;
import edu.virginia.lib.wsls.spreadsheet.ColumnNameBasedPBCoreRow;
import edu.virginia.lib.wsls.spreadsheet.PBCoreSpreadsheetRow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;

public class WSLSMasterSpreadsheet implements Iterable<PBCoreSpreadsheetRow> {

    protected Workbook master;

    public WSLSMasterSpreadsheet(File spreadsheet) throws IOException {
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

    public Iterator<PBCoreSpreadsheetRow> iterator() {
        return new PBCoreSpreadsheetRowIterator(master);
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
