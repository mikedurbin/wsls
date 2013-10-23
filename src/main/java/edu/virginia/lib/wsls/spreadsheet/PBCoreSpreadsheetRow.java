package edu.virginia.lib.wsls.spreadsheet;

import java.util.Collection;
import java.util.Date;
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
    
    public abstract Date getAssetDate();
    
    public abstract String getTitle();
    
    public abstract String getAbstract();
    
    public abstract List<String> getPlaces();
    
    public abstract List<String> getTopics();
    
    public abstract List<String> getEntitiesLCSH();
    
    public abstract String getInstantiationLocation();
    
    public abstract String getInstantiationDuration();
    
    public abstract String getInstantiationColors();
    
    public abstract String getInstantiationAnnotation();

    public abstract int getProcessingCode();

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

    /**
     * Determines if the two rows (with respect to tracked value types) are
     * equivalent.
     */
    public boolean equals(Object o) {
        if (!(o instanceof PBCoreSpreadsheetRow)) {
             return false;
        }
        PBCoreSpreadsheetRow other = (PBCoreSpreadsheetRow) o;
        return equals(getId(), other.getId())
                && equals(getAssetDate(), other.getAssetDate())
                && equals(getTitle(), other.getTitle())
                && equals(getAbstract(), other.getAbstract())
                && equals(getPlaces(), other.getPlaces())
                && equals(getTopics(), other.getTopics())
                && equals(getEntitiesLCSH(), other.getEntitiesLCSH())
                && equals(getInstantiationLocation(), other.getInstantiationLocation())
                && equals(getInstantiationDuration(), other.getInstantiationDuration())
                && equals(getInstantiationColors(), other.getInstantiationColors())
                && equals(getInstantiationAnnotation(), other.getInstantiationAnnotation())
                && (getProcessingCode() == other.getProcessingCode());
    }

    public int hashCode() {
        return getId().hashCode() + getAssetDate().hashCode() + getTitle().hashCode() + getAbstract().hashCode() + getPlaces().hashCode() + getTopics().hashCode() + getEntitiesLCSH().hashCode() + getInstantiationAnnotation().hashCode() + getInstantiationColors().hashCode() + getInstantiationDuration().hashCode() + getInstantiationAnnotation().hashCode() + getProcessingCode();
    }


    public boolean equals(Object one, Object two) {
        one = emptyToNull(one);
        two = emptyToNull(two);
        if (one == null && two == null) {
            return true;
        } else if (one == null || two == null) {
            return false;
        } else {
            return one.equals(two);
        }
    }

    private Object emptyToNull(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String && ((String) o).trim().length() == 0) {
            return null;
        }
        if (o instanceof Collection && ((Collection) o).isEmpty()) {
            return null;
        }
        return o;
    }



}
