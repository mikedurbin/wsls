package edu.virginia.lib.wsls.spreadsheet;

import org.apache.poi.ss.usermodel.Row;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ColumnNameBasedPBCoreRow extends PBCoreSpreadsheetRow {

    public ColumnNameBasedPBCoreRow(Row row, ColumnMapping m) {
        super(row, m);
    }
    
    public String getId() {
        return getString(m.getColumnForLabel("pbcoreIdentifier source=UVA"), "MISSING ID");
    }
    
    public Date getAssetDate() {
        String assetDateStr = getString(m.getColumnForLabel("pbcoreAssetDate type=Content"));
        if (assetDateStr == null) {
            return null;
        } else if (assetDateStr.equals("n/a")) {
            return null;
        }
        try {
            return new SimpleDateFormat("MM/dd/yy").parse(assetDateStr);
        } catch (ParseException e) {
            try {
                return new SimpleDateFormat("MM/dd/yyyy").parse(assetDateStr);
            } catch (ParseException e1) {
                System.err.println("Bad dates! \"" + assetDateStr + "\"");
                return null;
            }
        }
    }
    
    public String getTitle() {
        try {
            return getString(m.getColumnForLabel("pbcoreTitle"));
        } catch (IllegalArgumentException ex) {
            return getString(m.getColumnForLabel("pbcoreDescription type=Title"));
        }
    }
    
    public String getAbstract() {
        return getString(m.getColumnForLabel("pbcoreDescription type=Abstract"));
    }
    
    public List<String> getPlaces() {
        List<String> places = new ArrayList<String>();
        for (int i : m.getColumnsForLabel("pbcoreSubject type=Place source=LCSH")) {
            String place = getString(i);
            if (place != null) {
                places.add(place);
            }
        }
        return places;
    }
    
    public List<String> getTopics() {
        List<String> topics = new ArrayList<String>();
        /* The topics from the master spreadsheet are no good... so we shouldn't
         *bother parsing them...
         
        for (int i : m.getColumnsForLabel("pbcoreSubject type=Topic source=LCSH")) {
            String topic = getString(i);
            if (topic != null) {
                topics.add(topic);
            }
        }
        */
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

    public String getInstantiationLocation() {
        return getString(m.getColumnForLabel("instantiationLocation"));
    }

    public String getInstantiationDuration() {
        return getString(m.getColumnForLabel("instantiationDuration"));
    }

    public String getInstantiationColors() {
        return getString(m.getColumnForLabel("instantiationColors"));
    }

    public String getInstantiationAnnotation() {
        return getString(m.getColumnForLabel("instantiationAnnotation"));
    }

    public int getProcessingCode() {
        String strVal = getString(m.getColumnForLabel("Processing category code"));
        if (strVal == null) {
            return 0;
        }
        return Integer.parseInt(strVal);
    }

}
