package edu.virginia.lib.wsls.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * A Utility to detect changed values between spreadsheets...
 *
 */
public class SpreadsheetAnalyzer {

    private Workbook w1;

    private File f2;
    private Workbook w2;

    private int[] columnsToCompareAndImportFromSpreadsheetOne = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34};

    public SpreadsheetAnalyzer(Workbook w1, Workbook w2) {
        this.w1 = w1;
        this.w2 = w2;
    }

    public SpreadsheetAnalyzer(File f1, File f2) throws IOException {
        w1 = loadSpreadsheet(f1);
        w2 = loadSpreadsheet(f2);
        this.f2 = f2;
    }

    public void merge(File outputFile) throws IOException {
        if (f2 == null) {
            throw new RuntimeException("Cannot perform destructive call when the spreadsheet might be shared!");
        }
        try {
            Sheet s = w2.getSheetAt(0);
            for (Row r1 : identifyChanges()) {
                if (r1.getRowNum() == 0) {
                    //skipping heading row
                } else {
                    // copy values over to sheet 2
                    for (int col : columnsToCompareAndImportFromSpreadsheetOne) {
                        copyCellIntoSheet(r1.getRowNum(), col, r1.getCell(col), s);
                    }
                }
            }
            // write out the spreadsheet
            FileOutputStream fos = new FileOutputStream(outputFile);
            try {
                System.out.println("Writing out spreadsheet " + outputFile.getName() + "...");
                w2.write(fos);
            } finally {
                fos.close();
            }
        } finally {
            // reload the spreadsheet
            w2 = loadSpreadsheet(f2);
        }
    }

    /**
     * Copies the cell value (and type) into the same place in the provided
     * sheet.
     */
    private void copyCellIntoSheet(int row, int col, Cell c, Sheet sheet) {
        Cell dest = sheet.getRow(row).getCell(col);
        if (!hasCellChanged(c, dest)) {
            System.out.println("Cell has not changed (" + row + ", " + col + ")");
            return;
        }
        System.out.println("Copying cell (" + c.getRow().getRowNum() + ", " + c.getColumnIndex() + ")");
        if (dest == null) {
            dest = sheet.getRow(c.getRowIndex()).createCell(c.getColumnIndex());
        }

        dest.setCellType(c.getCellType());

        // Set the cell data value
        switch (c.getCellType()) {
            case Cell.CELL_TYPE_BLANK:
                dest.setCellValue(c.getStringCellValue());
                break;
            case Cell.CELL_TYPE_BOOLEAN:
                dest.setCellValue(c.getBooleanCellValue());
                break;
            case Cell.CELL_TYPE_ERROR:
                dest.setCellErrorValue(c.getErrorCellValue());
                break;
            case Cell.CELL_TYPE_FORMULA:
                dest.setCellFormula(c.getCellFormula());
                break;
            case Cell.CELL_TYPE_NUMERIC:
                dest.setCellValue(c.getNumericCellValue());
                break;
            case Cell.CELL_TYPE_STRING:
                dest.setCellValue(c.getRichStringCellValue());
                break;
        }
    }

    public List<String> getValueForColumnOfChangedRows(int column) {
        ArrayList<String> values = new ArrayList<String>();
        for (Row r : identifyChanges()) {
            values.add(getCellValueAsString(r.getCell(column)));
        }
        return values;
    }

    public List<Row> identifyChanges() {
        return identifyChanges(w1.getSheetAt(0), w2.getSheetAt(0));
    }

    private List<Row> identifyChanges(Sheet s1, Sheet s2) {
        List<Row> rowsChanged = new ArrayList<Row>();
        for (Row r1 : s1) {
            Row r2 = s2.getRow(r1.getRowNum());
            if (r2 == null) {
                System.err.println("Spreadsheet 2 does not have a row " + r1.getRowNum());
            } else {
                for (int col : columnsToCompareAndImportFromSpreadsheetOne) {
                    Cell c1 = r1.getCell(col);
                    Cell c2 = r2.getCell(col);
                    if (hasCellChanged(c1, c2)) {
                        rowsChanged.add(r1);
                        System.out.println("row " + r1.getRowNum() + " col " + col + ": \"" + getCellValueAsString(c1) + "\" --> \"" + getCellValueAsString(c2) + "\"");
                    }
                }
            }
        }
        return rowsChanged;
    }

    public boolean hasCellChanged(Cell c1, Cell c2) {
        if (c1 == null && c2 == null) {
            return false;
        } else if (c1 == null || c2 == null) {
            return true;
        } else if (c1.getCellType() != c2.getCellType()) {
            return true;
        } else {
            switch (c1.getCellType()) {
                case Cell.CELL_TYPE_STRING :
                    return !c1.getStringCellValue().equals(c2.getStringCellValue());
                case Cell.CELL_TYPE_NUMERIC :
                    return c1.getNumericCellValue() != c2.getNumericCellValue();
                case Cell.CELL_TYPE_BLANK :
                    return false;
                case Cell.CELL_TYPE_BOOLEAN :
                    return c1.getBooleanCellValue() != c2.getBooleanCellValue();
                case Cell.CELL_TYPE_ERROR :
                    return false;
                case Cell.CELL_TYPE_FORMULA :
                    return !c1.getCellFormula().equals(c2.getCellFormula());
                default:
                    throw new RuntimeException("Unknown cell type " + c1.getCellType() + " (" + c1.getRowIndex() + ", " + c1.getColumnIndex() + ")");
            }
        }
    }

    public String getCellValueAsString(Cell c) {
        if (c == null) {
            return "null";
        }
        switch (c.getCellType()) {
        case Cell.CELL_TYPE_STRING :
            return c.getStringCellValue();
        case Cell.CELL_TYPE_NUMERIC :
            return new DataFormatter().formatCellValue(c);
        case Cell.CELL_TYPE_BLANK :
            return "";
        case Cell.CELL_TYPE_BOOLEAN :
            return String.valueOf(c.getBooleanCellValue());
        case Cell.CELL_TYPE_ERROR :
            return String.valueOf("ERROR: " + c.getErrorCellValue());
        case Cell.CELL_TYPE_FORMULA :
            return c.getCellFormula();
        default:
            throw new RuntimeException("Unknown cell type " + c.getCellType() + " (" + c.getRowIndex() + ", " + c.getColumnIndex() + ")");
    }
    }

    private Workbook loadSpreadsheet(File spreadsheet) throws IOException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(spreadsheet);
            try {
                return new HSSFWorkbook(fis);
            } finally {
                fis.close();
            }
        } catch (OfficeXmlFileException ex) {
            fis = new FileInputStream(spreadsheet);
            try {
                return new XSSFWorkbook(fis);
            } finally {
                fis.close();
            }
        }
    }
}
