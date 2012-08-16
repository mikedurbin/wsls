package edu.virginia.lib.wsls.spreadsheet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

public abstract class PBCoreRow {
    
    protected Row row;
    
    public PBCoreRow(Row row) {
        this.row = row;
    }
    
    public abstract String getId();
    
    public abstract String getAssetDate();
    
    public abstract String getTitle();
    
    public abstract String getAbstract();
    
    public abstract String getPlace();
    
    public abstract List<String> getTopics();
    
    public abstract List<String> getEntities();
    
    public abstract List<String> getEntitiesLCSH();
    
    protected String getString(int colIndex) {
        return getString(colIndex, null);
    }
    
    protected String getString(int colIndex, String defaultValue) {
        Cell cell = row.getCell(colIndex);
        if (cell != null && (cell.getCellType() == Cell.CELL_TYPE_STRING)) {
            return cell.getStringCellValue();
        } else if (cell != null && cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
            return new SimpleDateFormat("MM/dd/yyyy").format(cell.getDateCellValue());
        } else {
            return defaultValue;
        }
    }

}
