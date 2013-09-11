package edu.virginia.lib.wsls.datasources;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KalturaVideoSource {

    private static final Pattern LOW_RES_MOVIE_PATTERN = Pattern.compile("(\\d\\d\\d\\d[a-c]?)_(\\d\\d?a?)_L\\.mov");
    private static final Pattern PILOT_MOVIE_PATTERN = Pattern.compile("(\\d\\d\\d\\d)[-_](\\d).mov");
    private static final Pattern PILOT_MOVIE_PATTERN_SHORT = Pattern.compile("(\\d\\d\\d\\d).mov");
    private static final Pattern ACC_MOVIE_PATTERN = Pattern.compile("(\\d\\d\\d\\d)[_-](\\d).mov");

    private Map<String, String> idToKalturaIdMap = new HashMap<String, String>();

    public KalturaVideoSource(File csvFile) throws IOException {
        Pattern p = Pattern.compile("(\\d+)[-_]?(\\d)?(_L)?\\Q.mov\\E");
        DecimalFormat d = new DecimalFormat("0000");
        FileInputStream fos = new FileInputStream(csvFile);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(fos));
            String line = null;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",");
                idToKalturaIdMap.put(getIdFromFilename(cols[0]), cols[1]);
            }
        } finally {
            fos.close();
        }
    }

    public boolean isVideoInKaltura(String id) {
        return idToKalturaIdMap.containsKey(id);
    }

    public String getKalturaUrl(String id) {
        if (idToKalturaIdMap.containsKey(id)) {
            return "http://www.kaltura.com/kwidget/wid/_419852/uiconf_id/10065841/entry_id/" + idToKalturaIdMap.get(id);
        } else {
            return null;
        }
    }

    public void reportMissingVideos(List<String> ids) {
        int missingCount = 0;
        //List<String> ids = getIdsFromMaster(Arrays.asList(new Integer[] {CLIPS_WITH_SCRIPTS, CLIPS_WITHOUT_SCRIPTS}));
        System.out.println(ids.size() + " entries in the master spreadsheet indicate the presence of clips.");
        for (String id : ids) {
            if (!isVideoInKaltura(id)) {
                System.out.println(id + " is not in Kaltura!");
                missingCount ++;
            }
        }
        System.out.println(missingCount + " of " + ids.size() + " clips were not found in Kaltura.");
    }

    public void reportMissingProblemVideos(List<String> ids) {
        int missingCount = 0;
        //List<String> ids = getIdsFromMaster(Arrays.asList(new Integer[] {PROBLEMS}));
        System.out.println(ids.size() + " entries in the master spreadsheet indicate problems.");
        for (String id : ids) {
            if (!isVideoInKaltura(id)) {
                System.out.println(id + " is not in Kaltura!");
                missingCount ++;
            }
        }
        System.out.println(missingCount + " of " + ids.size() + " clips were not found in Kaltura.");
    }

    private String getIdFromFilename(String filename) {
        Map<String, String> renameMap = new HashMap<String, String>();
        renameMap.put("1037_3._L.mov", "1037_3_L.mov");
        renameMap.put("123_L.mov", "0123_1_L.mov");
        renameMap.put("2952__L.mov", "2952_1_L.mov");
        renameMap.put("2221_X_L.mov", "2221_3_L.mov");
        renameMap.put("6774_L.mov", "6774_1_L.mov");
        renameMap.put("103.mov", "0103_1.mov");
        renameMap.put("23-2.mov", "0023_2.mov");
        renameMap.put("23_1.mov", "0023_1.mov");
        
        if (renameMap.containsKey(filename)) {
            filename = renameMap.get(filename);
        }
        
/*
        if (filename.equals("5720_1L.mov")) {
            return "5720_1L";
        }
        if (filename.equals("5720_2L.mov")) {
            return "5720_2L";
        }
        if (filename.equals("5720_3L.mov")) {
            return "5720_3L";
        }
        if (filename.equals("5701_1L.mov")) {
            return "5701_1L";
        }
        if (filename.equals("5701_2L.mov")) {
            return "5701_2L";
        }
*/
        Matcher m = LOW_RES_MOVIE_PATTERN.matcher(filename);
        if (m.matches()) {
            return m.group(1) + "_" + m.group(2);
        }
        m = PILOT_MOVIE_PATTERN.matcher(filename);
        if (m.matches()) {
            return m.group(1) + "_" + m.group(2);
        }
        m = PILOT_MOVIE_PATTERN_SHORT.matcher(filename);
        if (m.matches()) {
            return m.group(1) + "_1";
        }
        m = ACC_MOVIE_PATTERN.matcher(filename);
        if (m.matches()) {
            return m.group(1) + "_" + m.group(2);
        }

        throw new IllegalArgumentException("No id could be determined for \"" + filename +"\"!");
    }
}
