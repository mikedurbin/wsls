package edu.virginia.lib.wsls.datasources;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.MalformedInputException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.io.IOUtils;

public class TXTSource {


    private Map<String, File> idToTXTFileMap = new HashMap<String, File>();
    
    public TXTSource(File dir) {
        includeTextDirectory(dir);
    }

    private void includeTextDirectory(File dir) {
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                includeTextDirectory(f);
            }
        } else {
            if (dir.getName().toLowerCase().endsWith(".txt")) {
                idToTXTFileMap.put(dir.getName().toLowerCase().replace(".txt", ""), dir);
            }
        }
    }

    public Collection<String> getKnownTXTIds() {
        return idToTXTFileMap.keySet();
    }

    public boolean isTXTPresent(String id) {
        return idToTXTFileMap.containsKey(id);
    }

    public File getTXTFile(String id) {
        return idToTXTFileMap.get(id);
    }

    public void reportMissingTXTs(List<String> ids) {
        int missingCount = 0;
        //List<String> ids = getIdsFromMaster(Arrays.asList(new Integer[] {CLIPS_WITH_SCRIPTS, SCRIPTS_WITHOUT_CLIPS}));
        System.out.println(ids.size() + " entries in the master spreadsheet indicate the presence of scripts.");
        for (String id : ids) {
            if (!isTXTPresent(id)) {
                System.out.println(id + " has no Text file!");
                missingCount ++;
            }
        }
        System.out.println(missingCount + " of " + ids.size() + " script txt files were not found");
    }

    public static boolean isFileValidUTF8(File file) throws FileNotFoundException, IOException {
        CharsetDecoder d = Charset.forName("UTF-8").newDecoder();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(file);
        try {
            IOUtils.copy(fis, baos);
            try {
                CharBuffer chars = d.decode(ByteBuffer.wrap(baos.toByteArray()));
                return true;
            } catch (MalformedInputException ex) {
                return false;
            }
        } finally {
            fis.close();
        }
    }

    public boolean isTextProbablyCopyrighted(File txtFile) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(txtFile);
        try {
            IOUtils.copy(fis, baos);
            baos.close();
            return baos.toString("UTF-8").toLowerCase().contains("telenews");
        } finally {
            fis.close();
        }
    }
}
