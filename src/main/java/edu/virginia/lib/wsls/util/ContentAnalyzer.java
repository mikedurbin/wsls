package edu.virginia.lib.wsls.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
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
        ContentAnalyzer a = new ContentAnalyzer(new File(args[0]), new File(args[2]));
        FileOutputStream fos = new FileOutputStream(args[3]);
        a.outputReportCSV(fos);
    }
    
    private Map<Integer, File> numberToScriptMap;  
    
    private Map<Integer, List<String>> numberToVideoFileMap;
    
    private Map<String, String> videoFileToIDMap;
    
    public ContentAnalyzer(File pdfRootDir, File videoCSVFile) throws IOException {
        numberToScriptMap = new HashMap<Integer, File>();
        populateMap(pdfRootDir);
        
        numberToVideoFileMap = new HashMap<Integer, List<String>>();
        videoFileToIDMap = new HashMap<String, String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(videoCSVFile)));
        String line = null;
        while ((line = r.readLine()) != null) {
            String[] row = line.split(",");
            
            Integer number = new Integer(row[0].substring(0, 4));
            List<String> filenames = numberToVideoFileMap.get(number);
            if (filenames == null) {
                filenames = new ArrayList<String>();
                numberToVideoFileMap.put(number, filenames);
            }
            filenames.add(row[0]);
            videoFileToIDMap.put(row[0], row[1]);
        }
    }
    
    /**
     * Outputs a CSV with the following columns:
     * number, unbrokenscript, video file, video id, streamingURL
     */
    public void outputReportCSV(OutputStream os) {
        PrintWriter p = new PrintWriter(os);
        for (int i = 0; i < 10000; i ++) {
            File script = numberToScriptMap.get(i);
            if (!numberToVideoFileMap.containsKey(i)) {
                if (script != null) {
                    // script, no videos
                    p.println(i + "," + script.getName() + ",,,");
                } else {
                    // no script or video
                }
            } else {
                for (String videoFile : numberToVideoFileMap.get(i)) {
                    p.println(i + "," + (script != null ? script.getName() : "") + "," + videoFile + "," + videoFileToIDMap.get(videoFile) + ",http://www.kaltura.com/kwidget/wid/_419852/uiconf_id/10065841/entry_id/" + videoFileToIDMap.get(videoFile));
                }
            }
        }
    }
    
    private void populateMap(File file) {
        if (file.isFile()) {
            Matcher m = Pattern.compile("(\\d+)\\.[pP][dD][fF]").matcher(file.getName());
            if (m.matches()) {
                Integer number = new Integer(m.group(1));
                if (numberToScriptMap.containsKey(number)) {
                    throw new RuntimeException("Two files with matching numbers: " + file.getAbsolutePath() + " and " + numberToScriptMap.get(number).getAbsolutePath());
                }
                numberToScriptMap.put(number, file);
            }
        } else {
            for (File f : file.listFiles()) {
                populateMap(f);
            }
        }
    }
    
}
