package edu.virginia.lib.wsls.spreadsheet;

import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

public abstract class PBCoreSpreadsheetRow {
    
    protected Row row;
    
    protected ColumnMapping m;
    
    public PBCoreSpreadsheetRow(Row row, ColumnMapping mapping) {
        this.row = row;
        m = mapping;
    }

    protected PBCoreSpreadsheetRow() {
    }

    public abstract String getId();
    
    public abstract String getAssetDate();
    
    public abstract String getTitle();
    
    public abstract String getAbstract();
    
    public abstract List<String> getPlaces();
    
    public abstract List<String> getTopics();
    
    public abstract List<String> getEntitiesLCSH();
    
    public abstract String getInstantiationLocation();
    
    public abstract String getInstantiationDuration();
    
    public abstract String getInstantiationColors();
    
    public abstract String getInstantiationAnnotation();

    private int getOrCreateColumnIndex(String label) {
        try {
            return getMapping().getColumnForLabel(label);
        } catch (IllegalArgumentException ex) {
            return getMapping().addColumn(label);
        }
    }
    public void markAsNotIngested(String status) {
        int statusColIndex = getOrCreateColumnIndex("ingest status");
        row.createCell(statusColIndex).setCellValue(status);
    }

    public void markAsIngested(String pid) {
        int statusColIndex = getOrCreateColumnIndex("ingest status");
        row.createCell(statusColIndex).setCellValue("ingested");

        int pidColIndex = getOrCreateColumnIndex("fedora pid");
        row.createCell(pidColIndex).setCellValue(pid);
    }

    public ColumnMapping getMapping() {
        return m;
    }

    public String getNamedField(String label) {
        return getString(getMapping().getColumnForLabel(label));
    }

    protected String getString(int colIndex) {
        return getString(colIndex, null);
    }
    
    protected String getString(int colIndex, String defaultValue) {
        Cell cell = row.getCell(colIndex);
        if (cell != null && (cell.getCellType() == Cell.CELL_TYPE_STRING)) {
            return cell.getStringCellValue();
        } else if (cell != null && cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
            return new DataFormatter().formatCellValue(cell);
        } else {
            return defaultValue;
        }
    }

}
