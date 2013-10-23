package edu.virginia.lib.wsls.spreadsheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

public class GoogleFormSpreadsheetPBCoreRow extends PBCoreSpreadsheetRow {

    public GoogleFormSpreadsheetPBCoreRow(Row row, ColumnMapping m) {
        super(row, m);
    }

    public String getId() {
        return getString(m.getColumnForLabel("File number"), "MISSING ID");
    }

    public Date getAssetDate() {
        return null;
    }

    public String getTitle() {
        return capitalize(getString(m.getColumnForLabel("Title"), null));
    }

    private static String capitalize(String sentence) {
        if (sentence == null || sentence.length() == 0) {
            return sentence;
        } else {
            return sentence.substring(0, 1).toUpperCase() + sentence.substring(1);
        }
    }

    public String getAbstract() {
        return null;
    }

    public List<String> getPlaces() {
        String place = getString(m.getColumnForLabel("Geographic subject"), null);
        if (place == null) {
            return null;
        } else {
            return Collections.singletonList(place);
        }
    }

    public List<String> getTopics() {
        List<String> subjects = new ArrayList<String>();
        String subject = getString(m.getColumnForLabel("Subject"), null);
        if (subject != null) {
            subjects.add(subject);
        }
        String categoriesValue = getString(m.getColumnForLabel("Category"), null);
        if (categoriesValue != null) {
            if (categoriesValue.contains(",")) {
                if (categoriesValue.equals("Associations, institutions, etc.") || categoriesValue.equals("Corporations, American")) {
                    subjects.add(categoriesValue);
                } else if (categoriesValue.equals("Associations, institutions, etc., Virginia Association of Press Women")) {
                    subjects.add("Associations, institutions, etc.");
                    subjects.add("Virginia Association of Press Women");
                } else if (categoriesValue.equals("Associations, institutions, etc., United States--Politics and government, Virginia--Politics and government, Virginia--Social life and customs--20th century")) {
                    subjects.add("Associations, institutions, etc.");
                    subjects.add("United States--Politics and government");
                    subjects.add("Virginia--Politics and government");
                    subjects.add("Virginia--Social life and customs--20th century");
                } else if (categoriesValue.equals("Associations, institutions, etc., Public health, Community Hospital of Roanoke Valley")) {
                    subjects.add("Associations, institutions, etc.");
                    subjects.add("Public health");
                    subjects.add("Community Hospital of Roanoke Valley");
                } else if (categoriesValue.equals("Associations, institutions, etc., Public health, ")) {
                    subjects.add("Associations, institutions, etc.");
                    subjects.add("Public health");
                } else {
                    throw new RuntimeException("Unexpected category: \"" + categoriesValue + "\"");
                }
               // skip it for now...
            } else {
                subjects.add(categoriesValue);
            }
        }
        return subjects;
    }

    public List<String> getEntitiesLCSH() {
        return null;
    }

    public String getInstantiationLocation() {
        return null;
    }

    public String getInstantiationDuration() {
        return null;
    }

    public String getInstantiationColors() {
        return null;
    }

    public String getInstantiationAnnotation() {
        return null;
    }

    public int getProcessingCode() {
        return 0;
    }

    /**
     * Overrides superclass implementation to return the default value when
     * a value of "unknown" (case insensitive) is found.
     */
    protected String getString(int colIndex, String defaultValue) {
        String value = super.getString(colIndex, defaultValue);
        if (value == null) {
            return null;
        }
        if (value.equalsIgnoreCase("unknown")) {
            return defaultValue;
        } else {
            return value;
        }
    }

    public boolean equals(Object o) {
        if (!(o instanceof GoogleFormSpreadsheetPBCoreRow)) {
            return false;
        }
        GoogleFormSpreadsheetPBCoreRow other = (GoogleFormSpreadsheetPBCoreRow) o;
        for (Cell c : row) {
            Cell otherC = other.row.getCell(c.getColumnIndex());
            if (c.getCellType() == Cell.CELL_TYPE_STRING) {
                if (!otherC.getStringCellValue().equals(c.getStringCellValue())) {
                    return false;
                }
            }
        }
        return true;
    }

    public int hashCode() {
        StringBuffer val = new StringBuffer();
        for (Cell c : row) {
            if (c.getCellType() == Cell.CELL_TYPE_STRING) {
                val.append(c.getStringCellValue());
            }
        }
        return val.toString().hashCode();
    }
}
