package edu.virginia.lib.wsls.datasources;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

public class PDFSource {

    private Map<String, File> idToPDFFileMap = new HashMap<String, File>();

    private File changeDir;

    public PDFSource(File dir, File changedDir) {
        includePDFDirectory(dir);
        includePDFDirectory(changedDir);
        changeDir = changedDir;
    }

    private void includePDFDirectory(File dir) {
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                includePDFDirectory(f);
            }
        } else {
            if (dir.getName().toLowerCase().endsWith(".pdf")) {
                if (!Pattern.matches("\\d\\d\\d\\d\\Q.pdf\\E", dir.getName())) {
                    idToPDFFileMap.put(dir.getName().toLowerCase().replace(".pdf", ""), dir);
                }
            }
        }
    }

    public Collection<String> getKnownPDFIds() {
        return idToPDFFileMap.keySet();
    }

    public boolean isPDFPresent(String id) {
        return idToPDFFileMap.containsKey(id);
    }

    public boolean hasPDFChanged(String id) {
        File pdf = getPDFFile(id);
        return pdf.getParentFile().equals(changeDir);
    }

    public List<String> getIdsWithChangedPDFs() {
        List<String> ids = new ArrayList<String>();
        for (String id : this.getKnownPDFIds()) {
            if (hasPDFChanged(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    public File getPDFFile(String id) {
        return idToPDFFileMap.get(id);
    }

    public void reportMissingPDFs(List<String> ids) {
        int missingCount = 0;
        //List<String> ids = getIdsFromMaster(Arrays.asList(new Integer[] {CLIPS_WITH_SCRIPTS, SCRIPTS_WITHOUT_CLIPS}));
        System.out.println(ids.size() + " entries in the master spreadsheet indicate the presence of scripts.");
        for (String id : ids) {
            if (!isPDFPresent(id)) {
                System.out.println(id + " has no PDF!");
                missingCount ++;
            }
        }
        System.out.println(missingCount + " of " + ids.size() + " script pdf files were not found");
    }

    public void reportExtraPDFs(List<String> ids) {
        int missingCount = 0;
        Collection<String> pdfIds = new HashSet<String>(idToPDFFileMap.keySet());
        //List<String> ids = getIdsFromMaster(Arrays.asList(new Integer[] {CLIPS_WITH_SCRIPTS, SCRIPTS_WITHOUT_CLIPS, PROBLEMS}));
        System.out.println(pdfIds.size() + " PDFs found.");
        for (String id : ids) {
            pdfIds.remove(id);
        }
        for (String id : pdfIds) {
            System.out.println(id);
        }
        System.out.println(pdfIds.size() + " pdfs not referenced in the spreadsheet.");
    }
}
