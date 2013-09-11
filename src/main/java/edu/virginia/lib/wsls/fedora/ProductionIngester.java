package edu.virginia.lib.wsls.fedora;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

import edu.virginia.lib.wsls.datasources.FedoraRepository;
import edu.virginia.lib.wsls.datasources.GoogleMetadata;
import edu.virginia.lib.wsls.datasources.KalturaVideoSource;
import edu.virginia.lib.wsls.datasources.PDFSource;
import edu.virginia.lib.wsls.datasources.TXTSource;
import edu.virginia.lib.wsls.datasources.WSLSMasterSpreadsheet;
import edu.virginia.lib.wsls.proc.ImageMagickProcess;
import edu.virginia.lib.wsls.solr.PostSolrDocument;
import edu.virginia.lib.wsls.spreadsheet.PBCoreDocument;
import edu.virginia.lib.wsls.spreadsheet.PBCoreSpreadsheetRow;
import edu.virginia.lib.wsls.util.SpreadsheetAnalyzer;

public class ProductionIngester {

    public static void main(String [] args) throws Exception {
        ProductionIngester p = new ProductionIngester();

        p.ingestRecords(14000);

        p.fedora.fixRelationships();

        PostSolrDocument solr = new PostSolrDocument();
        solr.reindexWSLSCollection(true);
    }

    private List<String> idsToInclude;
    private List<String> changed;
    private List<String> incompleteIdsToAllow;
    private Map<String, String> idToSkipReasonMap;

    private List<String> redo;

    private GoogleMetadata m;

    private GoogleMetadata lastM;

    private WSLSMasterSpreadsheet lastMaster;
    private WSLSMasterSpreadsheet master;

    private PDFSource pdfs;
    private TXTSource txts;
    private KalturaVideoSource videos;

    private FedoraRepository fedora;

    private ImageMagickProcess t;

    public ProductionIngester() throws Exception {
        Properties p = new Properties();
        p.load(getClass().getClassLoader().getResourceAsStream("conf/fedora.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        p.load(getClass().getClassLoader().getResourceAsStream("conf/ingest.properties"));

        fedora = new FedoraRepository(fc, new File(p.getProperty("pid-registry-root")));

        lastMaster = new WSLSMasterSpreadsheet(new File(p.getProperty("last-master")));
        master = new WSLSMasterSpreadsheet(new File(p.getProperty("current-master")));
        lastM = new GoogleMetadata(new File(p.getProperty("last-google")));
        m = new GoogleMetadata(new File(p.getProperty("current-google")));
        pdfs = new PDFSource(new File(p.getProperty("pdf-dir")));
        txts = new TXTSource(new File(p.getProperty("txt-dir")));
        videos = new KalturaVideoSource(new File(p.getProperty("kaltura-id-log")));

        redo = new ArrayList<String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(p.getProperty("redo"))));
        String line = null;
        while ((line = r.readLine()) != null) {
            redo.add(line.trim());
        }
        t = new ImageMagickProcess();
        analyzeMaterials();
    }

    private void analyzeMaterials() throws Exception {
        idToSkipReasonMap = new HashMap<String, String>();
        idsToInclude = new ArrayList<String>();
        changed = new ArrayList<String>();
        Pattern idPattern = Pattern.compile("\\d\\d\\d\\d(a)?_\\d");
        Set<String> dupes = new HashSet<String>();

        // validate categories
        for (PBCoreSpreadsheetRow r : m) {
            r.getTopics();
        }

        // Identify all the records in the google spreadsheet that are new or 
        // have changed since the last ingest
        for (PBCoreSpreadsheetRow r : m) {
            if (!idPattern.matcher(r.getId()).matches()) {
                idToSkipReasonMap.put(r.getId(), "SKIPPED: Unrecognized ID pattern");
            } else {
                if (idsToInclude.contains(r.getId())) {
                    dupes.add(r.getId());
                    idToSkipReasonMap.put(r.getId(), "SKIPPED: ID is cataloged more than once in google spreadsheet");
                } else {
                    if (r.getTitle() == null || r.getTitle().trim().length() == 0) {
                        idToSkipReasonMap.put(r.getId(), "SKIPPED: unknown title");
                    } else {
                        PBCoreSpreadsheetRow lastIngest = lastM.getRowForId(r.getId());
                        if (lastIngest == null || !lastIngest.equals(r)) {
                            changed.add(r.getId());
                        }
                        idsToInclude.add(r.getId());
                    }
                }
            }
        }
        System.out.println(changed.size() + " records changed in form-input metadata");
        idsToInclude.removeAll(dupes);

        // Identify all the records in the master spreadsheet that are new or 
        // have changed since the last ingest
        SpreadsheetAnalyzer sa = new SpreadsheetAnalyzer(lastMaster.getWorkbook(), master.getWorkbook());
        for (String id : sa.getValueForColumnOfChangedRows(6)) {
            changed.add(id);
        }

        List<String> copyrightedIds = new ArrayList<String>();
        for (PBCoreSpreadsheetRow r : master) {

            // Identify the records in the master spreadsheet that are cataloged
            if (isCatalogedByLeigh(r)) {
                idsToInclude.add(r.getId());
            }

            // Remove any copyrighted materials
            if (!"L".equals(r.getNamedField("Copyright status if known  (T = Telenews; L=Local; C = all others)"))) {
                if (idsToInclude.contains(r.getId())) {
                    copyrightedIds.add(r.getId());
                    idsToInclude.remove(r.getId());
                    idToSkipReasonMap.put(r.getId(), "SKIPPED: marked as copyrighted");
                }
            }
        }
        System.out.println(copyrightedIds.size() + " items were removed for copyright restrictions.");

        // Ensure that the records are truly ready for ingest
        // 1.  They have a video URL
        // 2.  They have a PDF
        // 3.  They have a TXT file
        List<String> incompleteIds = new ArrayList<String>();
        for (String id : idsToInclude) {
            boolean hasVideo = videos.getKalturaUrl(id) != null;
            boolean hasPDF = pdfs.getPDFFile(id) != null;
            boolean hasTXT = txts.getTXTFile(id) != null;
            boolean invalidTXT = (hasTXT ? txts.isFileValidUTF8(txts.getTXTFile(id)) : false);
            boolean suspectedCopyright = hasTXT ? txts.isTextProbablyCopyrighted(txts.getTXTFile(id)) : false;
            if (!hasVideo || !hasPDF || !hasTXT || suspectedCopyright) {
                incompleteIds.add(id);
                idToSkipReasonMap.put(id, "SKIPPED:" + (!hasVideo || !hasPDF || !hasTXT ? " missing" + (hasVideo ? "" : " VIDEO") + (hasPDF ? "" : " PDF") + (hasTXT ? "" : " TXT") + (invalidTXT ? " properly encoded TXT" : "") : "") + (suspectedCopyright ? " suspected copyright violation" : ""));
            }
            
        }
        System.out.println(incompleteIds.size() + " of the remaining items were incomplete.");
        idsToInclude.removeAll(incompleteIds);

        System.out.println(idsToInclude.size() + " ids considered.");

        // Ensure that all the required Ids are present
        incompleteIdsToAllow = new ArrayList<String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("required-ids.txt")));
        String line = null;
        int missingRequired = 0;
        while ((line = r.readLine()) != null) {
            String id = line.trim();
            if (!idsToInclude.contains(id)) {
                if (copyrightedIds.contains(id)) {
                    missingRequired ++;
                    System.err.println(line.trim() + " must be included but is copyrighted!");
                } else if (incompleteIds.contains(id)) {
                    System.err.println(line.trim() + " must be included but isn't complete, WILL INGEST WTIHOUT SCRIPT!");
                    incompleteIdsToAllow.add(id);
                    incompleteIds.remove(id);
                    idsToInclude.add(id);
                    if (pdfs.getPDFFile(id) != null && txts.getTXTFile(id) == null) {
                        missingRequired ++;
                        System.err.println(id + " has a PDF but no TXT file!");
                    }
                } else {
                    missingRequired ++;
                    System.err.println(line.trim() + " must be included but isn't cataloged!");
                }
            }
        }
        if (missingRequired > 0) {
            throw new RuntimeException("Cannot proceed while a required record is missing!");
        }
    }

    /**
     * Looks at a row in the master spreadsheet to determine if descriptive
     * metadata has been entered.  The current implementation assumes "yes"
     * in cases where a PBCore title exists.
     */
    private boolean isCatalogedByLeigh(PBCoreSpreadsheetRow r) {
        return (r.getTitle() != null && r.getTitle().trim().length() > 0);
    }

    public void ingestRecords(int max) throws Exception {
        try {
            int i = 0;
            // for every eligible entry
            for (PBCoreSpreadsheetRow r : master) {
                String id = r.getId();
                if ("ingested".equals(r.getNamedField("ingest status")) && !changed.contains(id) && !redo.contains(id)) {
                    System.out.println(id);
                    System.out.println("  " + r.getNamedField("ingest status") + " (previous run)");
                } else if (idToSkipReasonMap.containsKey(id) && !incompleteIdsToAllow.contains(id)) {
                    r.markAsNotIngested(idToSkipReasonMap.get(id));
                } else if (idsToInclude.contains(id)) {
                    if (i ++ >= max) {
                        break;
                    }
                    System.out.println(id);
                    // build the PBCore record
                    PBCoreSpreadsheetRow gm = m.getRowForId(id);
                    PBCoreDocument doc = (gm == null ? new PBCoreDocument(r) : new PBCoreDocument(gm, r));
                    doc.setKalturaUrl(videos.getKalturaUrl(id));
                    System.out.println("  " + doc.getAssetDate());

                    // ingest the Video/Metadata object
                    String pid = fedora.ingestWSLSVideoObject(doc);
                    System.out.println("  " + pid);

                    // ensure that the PDF is present
                    File pdf = pdfs.getPDFFile(id);
                    if (pdf== null || !pdf.exists()) {
                        if (!incompleteIdsToAllow.contains(id)) {
                            throw new IllegalStateException("PDF is not present!");
                        } else {
                            // fall through and ingest the record without 
                            // an anchor script
                        }
                    } else {
                        // ensure that the TXT is present 
                        File txt = txts.getTXTFile(id);
                        if (txt == null || !txt.exists()) {
                            throw new IllegalStateException("TXT is not present!");
                        }
    
                        // generate the Thumbnail
                        File thumbnailFile = File.createTempFile("thumbnail-", ".png");
                        thumbnailFile.deleteOnExit();
                        t.generateThubmnail(pdf, thumbnailFile);
    
                        // ingest the Anchor script object
                        System.out.println("  " + fedora.ingestWSLSAnchorScriptObject(id, pdf, thumbnailFile, txt));
                    }

                    r.markAsIngested(pid);
                }
            }

            // wait for the resource index
            Thread.sleep(10000);
            fedora.fixRelationships();
        } finally {
            System.out.println("Saving workbook...");
            master.writeOutWorkbook(new SimpleDateFormat("yyyy-MMM-dd").format(new Date()) + " ingest report");
            System.out.println("DONE");
        }
    }

}
