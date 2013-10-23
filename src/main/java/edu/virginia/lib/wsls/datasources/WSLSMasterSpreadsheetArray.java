package edu.virginia.lib.wsls.datasources;

import edu.virginia.lib.wsls.googledrive.DriveHelper;
import edu.virginia.lib.wsls.spreadsheet.PBCoreSpreadsheetRow;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Iterator;
import java.util.List;

public class WSLSMasterSpreadsheetArray implements Iterable<PBCoreSpreadsheetRow> {

    private DriveHelper gdrive;

    public WSLSMasterSpreadsheetArray(DriveHelper d) throws IOException, GeneralSecurityException {
        gdrive = d;
    }

    @Override
    public Iterator<PBCoreSpreadsheetRow> iterator() {
        try {
            return new MultiSpreadsheetIterator(gdrive.getSpreadsheets());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class MultiSpreadsheetIterator implements Iterator<PBCoreSpreadsheetRow> {

        private Iterator<File> files;

        private Iterator<PBCoreSpreadsheetRow> rows;

        public MultiSpreadsheetIterator(List<File> spreadsheetFiles) {
            files = spreadsheetFiles.iterator();
        }

        @Override
        public boolean hasNext() {
            if (rows != null && rows.hasNext()) {
                return true;
            } else if (files.hasNext()) {
                try {
                    rows = new WSLSMasterSpreadsheet(files.next()).iterator();
                    return hasNext();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return false;
            }
        }

        @Override
        public PBCoreSpreadsheetRow next() {
            if (hasNext()) {
                return rows.next();
            } else {
                throw new IllegalStateException();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
