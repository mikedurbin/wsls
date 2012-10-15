package edu.virginia.lib.wsls.spreadsheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

/**
 * A class that simply maps column labels (repeatable) to the
 * index of the column that had that label.
 */
public class ColumnMapping {

    private Map<String, List<Integer>> mapping;
    
    /**
     * Initializes this ColumnMapping using the supplied 
     * row (which is assumed to be the header row of 
     * labels).
     * @param row the header row of the spreadsheet where
     * the labels are entered.
     */
    public ColumnMapping(Row row) {
        mapping = new HashMap<String, List<Integer>>();
        for (Cell cell : row) {
            String key = cell.getStringCellValue();
            List<Integer> cols = mapping.get(key);
            if (cols == null) {
                cols = new ArrayList<Integer>();
                mapping.put(key,  cols);
            }
            cols.add(cell.getColumnIndex());
            System.out.println("\"" + key + "\" --> " + cell.getColumnIndex());
        }
    }
    
    public int getColumnForLabel(String label) {
        if (!mapping.containsKey(label)) {
            throw new IllegalArgumentException("No column is labeled \"" + label + "\"!");
        }
        List<Integer> result = mapping.get(label);
        if (result.size() != 1) {
            throw new IllegalArgumentException("There are " + result.size() + " columns labeled \"" + label + "\"!");
        }
        return result.get(0);
    }
    
    public List<Integer> getColumnsForLabel(String label) {
        if (!mapping.containsKey(label)) {
            throw new IllegalArgumentException("No column is labeled \"" + label + "\"!");
        }
        return mapping.get(label);
    }
    
}
