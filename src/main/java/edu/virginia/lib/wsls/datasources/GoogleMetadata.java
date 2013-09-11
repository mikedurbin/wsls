package edu.virginia.lib.wsls.datasources;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import edu.virginia.lib.wsls.spreadsheet.ColumnMapping;
import edu.virginia.lib.wsls.spreadsheet.GoogleFormSpreadsheetPBCoreRow;
import edu.virginia.lib.wsls.spreadsheet.PBCoreSpreadsheetRow;

public class GoogleMetadata implements Iterable<PBCoreSpreadsheetRow> {

    private Workbook workbook;

    private Map<String, PBCoreSpreadsheetRow> idToRowCache;

    public GoogleMetadata(File spreadsheet) throws IOException, ParserConfigurationException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(spreadsheet);
            workbook = new HSSFWorkbook(fis);
            fis.close();
        } catch (OfficeXmlFileException ex) {
            fis = new FileInputStream(spreadsheet);
            workbook = new XSSFWorkbook(fis);
            fis.close();
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }

    public PBCoreSpreadsheetRow getRowForId(String id) {
        if (idToRowCache == null) {
            idToRowCache = new HashMap<String, PBCoreSpreadsheetRow>();
            for (PBCoreSpreadsheetRow r : this) {
                idToRowCache.put(r.getId(), r);
            }
        }
        return idToRowCache.get(id);
    }

    public Iterator<PBCoreSpreadsheetRow> iterator() {
        return new PBCoreSpreadsheetRowIterator(workbook);
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
                next = new GoogleFormSpreadsheetPBCoreRow(rowIt.next(), mapping);
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
