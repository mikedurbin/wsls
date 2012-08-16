package edu.virginia.lib.wsls.spreadsheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

public class ColumnMapping {

    private Map<String, List<Integer>> mapping;
    
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
