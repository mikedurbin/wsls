package edu.virginia.lib.wsls.datasources;


import edu.virginia.lib.wsls.spreadsheet.PBCoreSpreadsheetRow;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class IngestReport {

    public static final int UNRECOGNIZED_ID = 1;
    public static final int DUPLICATED_ID = 1 << 1;
    public static final int UNKNOWN_TITLE = 1 << 2;
    public static final int MARKED_COPYRIGHTED = 1 << 3;
    public static final int MISSING_VIDEO = 1 << 4;
    public static final int MISSING_PDF = 1 << 5;
    public static final int MISSING_TXT = 1 << 6;
    public static final int INVALID_TXT = 1 << 7;
    public static final int SUSPECTED_COPYRIGHT = 1 << 8;

    private static int[] REASONS = new int[] { UNRECOGNIZED_ID , DUPLICATED_ID, UNKNOWN_TITLE, MARKED_COPYRIGHTED, MISSING_VIDEO, MISSING_PDF, MISSING_TXT, INVALID_TXT, SUSPECTED_COPYRIGHT };

    Map<Integer, List<String>> problemToIdMap;

    StringBuffer report;

    Date ingestStarted;

    int startingCount;
    int endingCount;

    List<String> modifiedInCataloger;
    int addedInCataloger;
    List<String> modifiedInMaster;
    List<String> updatedPDF;
    List<String> updatedTXT;

    StringBuffer ingested;

    public IngestReport() {
        modifiedInMaster = new ArrayList<String>();
        modifiedInCataloger = new ArrayList<String>();
        updatedPDF = new ArrayList<String>();
        updatedTXT = new ArrayList<String>();
        ingested = new StringBuffer();
        report = new StringBuffer();
        ingestStarted = new Date();
        problemToIdMap = new HashMap<Integer, List<String>>();
        for (int reason : REASONS) {
            problemToIdMap.put(reason, new ArrayList<String>());
        }
    }

    public void setStartingCount(int val) {
        startingCount = val;
    }

    public void setEndingCount(int val) {
        endingCount = val;
    }

    public void updatedPDF(String id) {
        updatedPDF.add(id);
    }

    public void updatedTXT(String id) {
        updatedTXT.add(id);
    }


    public void modifiedInMaster(String id) {
        modifiedInMaster.add(id);
    }

    public void modifiedInCatalogerSpreadsheet(String id, PBCoreSpreadsheetRow old, PBCoreSpreadsheetRow current) {
        modifiedInCataloger.add(id);
    }

    public void addedInCatalogerSpreadsheet(String id) {
        addedInCataloger ++;
    }

    public void skip(String id, int reasonBits) {
        for (int reason : REASONS) {
            if ((reasonBits & reason) != 0) {
                problemToIdMap.get(reason).add(id);
            }
        }
    }

    public void ingested(String id, String pid) {
        ingested.append("  " + id + " --> " + pid + "\n");
    }

    public void relationshipsUpdated(long durationInMs) {

    }

    public void indexed(boolean success, long durationInMs) {

    }

    public void sendSuccess() throws IOException {
        File f = new File("ingest-report-" + System.currentTimeMillis() + ".txt");
        FileUtils.writeStringToFile(f, getReport(Integer.MAX_VALUE));
        //System.out.println(getReport(0));
    }

    public void sendFailure() throws IOException {
        File f = new File("ingest-report-" + System.currentTimeMillis() + ".txt");
        FileUtils.writeStringToFile(f, getReport(Integer.MAX_VALUE));
        //System.out.println(getReport(0));
    }

    private String getReport(int max) {
        StringBuffer sb = new StringBuffer();
        sb.append("Ingest report for " + ingestStarted + "\n\n");
        sb.append("Total items ingested: " + startingCount + " => " + endingCount + "\n");
        sb.append("(note, due to manual corrections, some previously ingested items may not be visible in virgo)\n\n");

        sb.append(addedInCataloger + " records added in the cataloger spreadsheet.\n");
        sb.append(modifiedInCataloger.size() + " records changed in the cataloger spreadsheet.\n");
        for (String id : modifiedInCataloger) {
            sb.append("  " + id + "\n");
        }
        sb.append("\n");
        sb.append(modifiedInMaster.size() + " records' descriptive metadata changed in the master spreadsheets.\n");
        for (String id : modifiedInMaster) {
            sb.append("  " + id + "\n");
        }
        sb.append("\n");

        sb.append(updatedPDF.size() + " items had their PDFs updated.\n");
        for (String id : updatedPDF) {
            sb.append("  " + id + "\n");
        }
        sb.append("\n");

        sb.append(updatedTXT.size() + " items had their text files updated.\n");
        for (String id : updatedTXT) {
            sb.append("  " + id + "\n");
        }
        sb.append("\n");

        appendSummary(sb, "unrecognized id", problemToIdMap.get(UNRECOGNIZED_ID), Math.max(50, max));
        appendSummary(sb, "duplicate id", problemToIdMap.get(DUPLICATED_ID), Math.max(50, max));
        appendSummary(sb, "unknown title", problemToIdMap.get(UNKNOWN_TITLE), Math.max(50, max));
        appendSummary(sb, "marked copyrighted (will be ingested once Virgo is updated)", problemToIdMap.get(MARKED_COPYRIGHTED), max);
        appendSummary(sb, "missing video", problemToIdMap.get(MISSING_VIDEO), max);
        appendSummary(sb, "missing PDF", problemToIdMap.get(MISSING_PDF), max);
        appendSummary(sb, "missing (or unsplit) text file", problemToIdMap.get(MISSING_TXT), max);
        appendSummary(sb, "invalid text file", problemToIdMap.get(INVALID_TXT), max);
        appendSummary(sb, "suspected copyright", problemToIdMap.get(SUSPECTED_COPYRIGHT), max);

        sb.append("The following items were ingested or updated:\n");
        sb.append(ingested.toString());

        return sb.toString();
    }

    private static void appendSummary(StringBuffer sb, String condition, List<String> ids, int longestList) {
        if (ids.isEmpty()) {
            return;
        }
        sb.append(ids.size() + " records have " + condition + "\n");
        for (int i = 0; i < ids.size() && i < longestList; i ++) {
            sb.append("  " + ids.get(i) + "\n");
        }
        if (ids.size() > longestList && longestList > 0) {
            sb.append("  ... " + (ids.size() - longestList) + " more\n");
        }
        sb.append("\n");
    }


}
