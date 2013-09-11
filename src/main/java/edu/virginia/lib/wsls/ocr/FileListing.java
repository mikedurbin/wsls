package edu.virginia.lib.wsls.ocr;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileListing {

    Pattern p = Pattern.compile("(\\d\\d\\d\\d).*\\.\\w\\w\\w");

    private Map<Integer, List<File>> idToFileMap;

    public FileListing(File pdfRootDir, File textRootDir) {
        idToFileMap = new HashMap<Integer, List<File>>();
        processDirectory(pdfRootDir);
        processDirectory(textRootDir);
    }
    
    private void processDirectory(File dir) {
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                Matcher m = p.matcher(f.getName());
                if (m.matches()) {
                    Integer id = Integer.parseInt(m.group(1));
                    List<File> files = idToFileMap.get(id);
                    if (files == null) {
                        files = new ArrayList<File>();
                        idToFileMap.put(id,  files);
                    }
                    files.add(f);
                }
            } else {
                processDirectory(f);
            }
        }
    }
    
    public Collection<Integer> getIds() {
        List<Integer> ids = new ArrayList<Integer>(idToFileMap.keySet());
        Collections.sort(ids);
        return ids;
    }

    public List<File> getFilesForNumber(int number) {
        return idToFileMap.get(number);
    }

    public static String[] getFilenames(List<File> files) {
        String[] filenames = new String[files.size()];
        for (int i = 0; i < files.size(); i ++) {
            filenames[i] = files.get(i).getPath();
        }
        return filenames;
    }

    private static final Pattern SPLIT_PDF_PATTERN = Pattern.compile("(\\d\\d\\d\\d)_(\\d).*\\.pdf");
    /**
     * Determines if a set of files needs to be split based on the presence of
     * a part pdf file.
     * @param files a set of files (pdf) for a given identifier.  This
     * is typically the result of getFilesForNumber()
     * @return true if more than one part exists.
     */
    public static boolean requiresSplitting(List<File> files) {
        Set<Integer> parts = new HashSet<Integer>();
        for (File f : files) {
            Matcher m = SPLIT_PDF_PATTERN.matcher(f.getName());
            if (m.matches()) {
                parts.add(Integer.parseInt(m.group(2)));
            }
        }
        return parts.size() > 1;
    }
}
