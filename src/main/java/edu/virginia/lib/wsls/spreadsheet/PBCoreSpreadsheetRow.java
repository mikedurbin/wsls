package edu.virginia.lib.wsls.spreadsheet;

import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

public abstract class PBCoreSpreadsheetRow {
    
    protected Row row;
    
    public PBCoreSpreadsheetRow(Row row) {
        this.row = row;
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
