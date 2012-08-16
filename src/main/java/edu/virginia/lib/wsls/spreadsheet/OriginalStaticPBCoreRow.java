package edu.virginia.lib.wsls.spreadsheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;

public class OriginalStaticPBCoreRow extends PBCoreRow {
    
    public OriginalStaticPBCoreRow(Row row) {
        super(row);
    }
    
    public String getId() {
        return getString(0, "MISSING ID");
    }
    
    public String getAssetDate() {
        return getString(1);
    }
    
    public String getTitle() {
        return getString(4, "");
    }
    
    public String getAbstract() {
        return getString(5);
    }
    
    public String getPlace() {
        return getString(13);
    }
    
    public List<String> getTopics() {
        List<String> topics = new ArrayList<String>();
        for (int i : new int[] { 6, 11, 12 }) {
            String topic = getString(i);
            if (topic != null) {
                topics.add(topic);
            }
        }
        return topics;
    }
    
    public List<String> getEntities() {
        return Collections.emptyList();
    }
    
    public List<String> getEntitiesLCSH() {
        List<String> entities = new ArrayList<String>();
        for (int i = 14; i < 22; i ++) {
            String entity = getString(i);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }
    
    public String getDuration() {
        return getString(23);
    }
    
    public String getColors() {
        return getString(24);
    }
}
