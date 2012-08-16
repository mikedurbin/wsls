package edu.virginia.lib.wsls.spreadsheet;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;

public class ColumnNameBasedPBCoreRow extends PBCoreRow {

    private ColumnMapping m;
    
    public ColumnNameBasedPBCoreRow(Row row, ColumnMapping m) {
        super(row);
        this.m = m;
    }
    
    public String getId() {
        return getString(m.getColumnForLabel("pbcoreIdentifier source=UVA"), "MISSING ID");
    }
    
    public String getAssetDate() {
        return getString(m.getColumnForLabel("pbcoreAssetDate type=Content"));
    }
    
    public String getTitle() {
        return getString(m.getColumnForLabel("pbcoreTitle"));
    }
    
    public String getAbstract() {
        return getString(m.getColumnForLabel("pbcoreDescription type=Abstract"));
    }
    
    public String getPlace() {
        return getString(m.getColumnForLabel("pbcoreSubject type=Place source=LCSH"));
    }
    
    public List<String> getTopics() {
        List<String> topics = new ArrayList<String>();
        for (int i : m.getColumnsForLabel("pbcoreSubject type=Topic source=LCSH")) {
            String topic = getString(i);
            if (topic != null) {
                topics.add(topic);
            }
        }
        return topics;
    }
    
    public List<String> getEntitiesLCSH() {
        List<String> entities = new ArrayList<String>();
        for (int i : m.getColumnsForLabel("pbcoreSubject type=Entity source=LCSH")) {
            String entity = getString(i);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }
    
    public List<String> getEntities() {
        List<String> entities = new ArrayList<String>();
        for (int i : m.getColumnsForLabel("pbcoreSubject type=Entity")) {
            String entity = getString(i);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }  
}
