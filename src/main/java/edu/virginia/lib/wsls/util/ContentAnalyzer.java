package edu.virginia.lib.wsls.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility to tell us:
 * 1.  How many videos don't have scripts
 * 2.  How many scripts don't have videos
 * 3.  URLs for videos
 */
public class ContentAnalyzer {

    public static void main(String args[]) throws Exception {
        String pdfRootPath = args[0];
        String uploadedVideosCsv = args[1];
        String output = args[2];
        ContentAnalyzer a = new ContentAnalyzer(new File(pdfRootPath), new File(uploadedVideosCsv));
        FileOutputStream fos = new FileOutputStream(output);
        a.outputReportCSV(fos);
    }
    
    private Map<Id, File> numberToScriptMap;
    
    private Map<Id, String> numberToVideoFileMap;
    
    private Map<String, String> videoFileToIDMap;
    
    public ContentAnalyzer(File pdfRootDir, File videoCSVFile) throws IOException {
        numberToScriptMap = new HashMap<Id, File>();
        populateMap(pdfRootDir);
        
        numberToVideoFileMap = new HashMap<Id, String>();
        videoFileToIDMap = new HashMap<String, String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(videoCSVFile)));
        String line = null;
        while ((line = r.readLine()) != null) {
            String[] row = line.split(",");
            Id id = new Id(row[0]);
            System.out.println(id + ": " + row[0]);
            
            numberToVideoFileMap.put(id, row[0]);
            videoFileToIDMap.put(row[0], row[1]);
        }
    }
    
    /**
     * Outputs a CSV with the following columns:
     * number, brokenscript, video file, video id, streamingURL
     */
    public void outputReportCSV(OutputStream os) {
        PrintWriter p = new PrintWriter(os);
        HashSet<Id> dedupe = new HashSet<Id>();
        dedupe.addAll(numberToVideoFileMap.keySet());
        dedupe.addAll(numberToScriptMap.keySet());
        List<Id> representedIds = new ArrayList<Id>(dedupe);
        Collections.sort(representedIds);
        for (Id id : representedIds) {
            File script = numberToScriptMap.get(id);
            if (!numberToVideoFileMap.containsKey(id)) {
                if (script != null) {
                    // script, no videos
                    p.println(id + "," + script.getName() + ",,");
                } else {
                    // no script or video
                }
            } else {
                String videoFile = numberToVideoFileMap.get(id);
                p.println(id + "," + (script != null ? script.getName() : "") + "," + videoFile + "," + videoFileToIDMap.get(videoFile));
                // video maybe with script
            }
        }
        p.flush();
        p.close();
        System.out.println(numberToScriptMap.size() + " scripts");
        System.out.println(numberToVideoFileMap.size() + " video files");
    }
    
    private void populateMap(File file) {
        if (file.isFile()) {
            Matcher m = Pattern.compile("(\\d+)([a-d])?(_(\\d+)([a-z])?)?\\.[pP][dD][fF]").matcher(file.getName());
            if (m.matches()) {
                String character = m.group(2);
                Id id = new Id(Integer.parseInt(m.group(1)), (character != null ? character.charAt(0) : null), m.group(4) != null ? Integer.parseInt(m.group(4)): 1, m.group(5) != null ? m.group(5).charAt(0) : null);
                if (numberToScriptMap.containsKey(id)) {
                    System.out.println("Two files with matching numbers: " + file.getAbsolutePath() + " and " + numberToScriptMap.get(id).getAbsolutePath());
                    //throw new RuntimeException("Two files with matching numbers: " + file.getAbsolutePath() + " and " + numberToScriptMap.get(id).getAbsolutePath());
                }
                numberToScriptMap.put(id, file);
            }
        } else {
            for (File f : file.listFiles()) {
                populateMap(f);
            }
        }
    }
    
    
    private static class Id implements Comparable {
        public String first;
        public String second;
        
        DecimalFormat f = new DecimalFormat("0000");
        
        public Id(String id) {
            Matcher m = Pattern.compile("(\\d+)([a-d]?)([-_]((\\d+)([a])?))?([_X]+L)?(\\._L)?\\.mov").matcher(id);
            if (m.matches()) {
                first = f.format(new Integer(m.group(1))) + m.group(2);
                second = m.group(4) != null ? m.group(4) : "1";
            } else {
                throw new RuntimeException(id + " doesn't match pattern!");
            }
        }
        
        public Id(int i, Character c, int j, Character d) {
            first = f.format(i) + (c != null ? c : "");
            second = String.valueOf(j) + (d != null ? d : "");
        }
        
        public int hashCode() {
            return (String.valueOf(first) + "-" + String.valueOf(second)).hashCode();
        }
        
        public boolean equals(Object other) {
            return (other instanceof Id && ((Id) other).first.equals(first) && ((Id) other).second.equals(second));
        }
        
        public String toString() {
            
            return first + "_" + second;
        }
        
        public int compareTo(Object o) {
            return toString().compareTo(o.toString());
        }
    }
}
