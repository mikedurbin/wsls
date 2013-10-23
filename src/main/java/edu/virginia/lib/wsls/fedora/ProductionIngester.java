package edu.virginia.lib.wsls.fedora;

import java.io.*;
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

import edu.virginia.lib.wsls.datasources.*;
import edu.virginia.lib.wsls.googledrive.DriveHelper;
import edu.virginia.lib.wsls.proc.ImageMagickProcess;
import edu.virginia.lib.wsls.solr.PostSolrDocument;
import edu.virginia.lib.wsls.spreadsheet.PBCoreDocument;
import edu.virginia.lib.wsls.spreadsheet.PBCoreSpreadsheetRow;
import edu.virginia.lib.wsls.util.PBCoreSpreadsheetComparison;
import edu.virginia.lib.wsls.util.SpreadsheetAnalyzer;
import org.apache.commons.io.FileUtils;

public class ProductionIngester {

    public static void main(String [] args) throws Exception {
        ProductionIngester p = new ProductionIngester();
        p.ingestRecords(14000);

        // TODO: add support for copyrighted materials

        PostSolrDocument solr = new PostSolrDocument();
        solr.reindexWSLSCollection(true);
    }

    private List<String> idsToInclude;
    private Set<String> changed;
    private Set<String> changedScripts;
    private List<String> incompleteIdsToAllow;
    private  Set<String> skip;

    private List<String> redo;

    private GoogleMetadata m;

    private GoogleMetadata lastM;

    private Iterable<PBCoreSpreadsheetRow> lastMaster;
    private Iterable<PBCoreSpreadsheetRow> master;

    private PDFSource pdfs;
    private TXTSource txts;
    private KalturaVideoSource videos;

    private FedoraRepository fedora;

    private ImageMagickProcess t;

    private IngestStatusTracker ingestStatusTracker;

    private IngestReport report;

    private DriveHelper d;

    private File snapshotDir;

    public ProductionIngester() throws Exception {
        // initialize report
        report = new IngestReport();

        // initialize connection to fedora
        Properties p = new Properties();
        p.load(getClass().getClassLoader().getResourceAsStream("conf/fedora.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        p.load(getClass().getClassLoader().getResourceAsStream("conf/ingest.properties"));

        ingestStatusTracker = new IngestStatusTracker(new File(p.getProperty("ingested-items")));

        snapshotDir = new File(p.getProperty("snapshot-dir"));

        fedora = new FedoraRepository(fc, new File(p.getProperty("pid-registry-root")));

        // initialize connection to google drive
        d = snapshotDir.exists() ? new DriveHelper(snapshotDir) : new DriveHelper();

        // TODO: get this from snapshot
        lastMaster = new WSLSMasterSpreadsheet(new File(p.getProperty("last-master")));

        master = new WSLSMasterSpreadsheetArray(d);

        // TODO: get this from snapshot
        lastM = new GoogleMetadata(new File(p.getProperty("last-google")));

        m = new GoogleMetadata(d.getCatalogerSpreadsheet());

        pdfs = new PDFSource(new File(p.getProperty("pdf-dir")), new File(p.getProperty("pdf-corrected-dir")));
        txts = new TXTSource(new File(p.getProperty("txt-dir")), new File(p.getProperty("text-changes")));
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
        //if (snapshotDir.exists() && snapshotDir.list().length > 0) {
        //    throw new RuntimeException();
        //} else {
        //    snapshotDir.mkdirs();
        //}
        skip = new HashSet<String>();
        idsToInclude = new ArrayList<String>();
        changed = new HashSet<String>();
        changedScripts = new HashSet<String>();
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
                report.skip(r.getId(), IngestReport.UNRECOGNIZED_ID);
                skip.add(r.getId());
            } else {
                if (idsToInclude.contains(r.getId())) {
                    dupes.add(r.getId());
                    report.skip(r.getId(), IngestReport.DUPLICATED_ID);
                    skip.add(r.getId());
                } else {
                    if (r.getTitle() == null || r.getTitle().trim().length() == 0) {
                        report.skip(r.getId(), IngestReport.UNKNOWN_TITLE);
                        skip.add(r.getId());
                    } else {
                        PBCoreSpreadsheetRow lastIngest = lastM.getRowForId(r.getId());
                        if (lastIngest == null || !lastIngest.equals(r)) {
                            changed.add(r.getId());
                            if (lastIngest != null) {
                                report.modifiedInCatalogerSpreadsheet(r.getId(), lastIngest, r);
                            } else {
                                report.addedInCatalogerSpreadsheet(r.getId());
                            }
                        }
                        idsToInclude.add(r.getId());
                    }
                }
            }
        }
        //System.out.println(changed.size() + " records changed in form-input metadata");
        idsToInclude.removeAll(dupes);

        // Identify all the records in the master spreadsheet that are new or 
        // have changed since the last ingest
        for (String id : new PBCoreSpreadsheetComparison(lastMaster, master)) {
            changed.add(id);
            report.modifiedInMaster(id);
        }

        // Identify all the records that have had updated PDF files
        for (String id : pdfs.getIdsWithChangedPDFs()) {
            report.updatedPDF(id);
            changedScripts.add(id);
        }

        // Identify all the records that have had updated text files
        for (String id : txts.getUpdatedTXTIds()) {
            report.updatedTXT(id);
            changedScripts.add(id);
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
                    skip.add(r.getId());
                    report.skip(r.getId(), IngestReport.MARKED_COPYRIGHTED);
                }
            }
        }

        // Ensure that the records are truly ready for ingest
        // 1.  They have a video URL
        // 2.  They have a PDF if type 1
        // 3.  They have a TXT file if type 1
        List<String> incompleteIds = new ArrayList<String>();
        for (String id : idsToInclude) {
            boolean hasVideo = videos.getKalturaUrl(id) != null;
            boolean hasPDF = pdfs.getPDFFile(id) != null;
            boolean hasTXT = txts.getTXTFile(id) != null;
            boolean invalidTXT = (hasTXT ? txts.isFileValidUTF8(txts.getTXTFile(id)) : false);
            boolean suspectedCopyright = hasTXT ? txts.isTextProbablyCopyrighted(txts.getTXTFile(id)) : false;
            if (!hasVideo || !hasPDF || !hasTXT || suspectedCopyright) {
                incompleteIds.add(id);
                skip.add(id);
                report.skip(id, (hasVideo ? 0 : IngestReport.MISSING_VIDEO)
                        | (hasPDF ? 0 : IngestReport.MISSING_PDF)
                        | (hasTXT ? 0 : IngestReport.MISSING_TXT)
                        | (invalidTXT ? IngestReport.INVALID_TXT : 0)
                        | (suspectedCopyright ? IngestReport.SUSPECTED_COPYRIGHT : 0));
            }
            
        }
        idsToInclude.removeAll(incompleteIds);

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
        report.setStartingCount(ingestStatusTracker.getAlreadyIngestedCount());
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
        boolean success = false;
        try {
            int i = 0;
            // for every eligible entry
            for (PBCoreSpreadsheetRow r : master) {
                String id = r.getId();
                if (ingestStatusTracker.hasBeenIngested(id) && !changed.contains(id) && !redo.contains(id)) {
                    System.out.println(id);
                    System.out.println("  ingested (previous run)");
                } else if (skip.contains(id) && !incompleteIdsToAllow.contains(id)) {
                } else if (idsToInclude.contains(id)) {
                    if (i ++ >= max) {
                        break;
                    }
                    System.out.println(id);
                    // build the PBCore record
                    PBCoreSpreadsheetRow gm = m.getRowForId(id);
                    PBCoreDocument doc = (gm == null ? new PBCoreDocument(r) : new PBCoreDocument(gm, r));
                    doc.setKalturaUrl(videos.getKalturaUrl(id));
                    System.out.println("  " + (doc.getAssetDate() == null ? "no date" : new SimpleDateFormat("MM/dd/yyyy").format(doc.getAssetDate())));

                    // ingest the Video/Metadata object
                    String pid = fedora.ingestWSLSVideoObject(doc);
                    System.out.println("  " + pid);

                    // ensure that the PDF is present
                    File pdf = pdfs.getPDFFile(id);
                    if (pdf== null || !pdf.exists()) {
                        if (!incompleteIdsToAllow.contains(id) && 2 != r.getProcessingCode()) {
                            throw new IllegalStateException("PDF is not present!");
                        } else {
                            // fall through and ingest the record without 
                            // an anchor script (purging the anchor script if present)
                            fedora.purgeWSLSAnchorScriptObject(id);
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
                        System.out.println("  " + fedora.ingestWSLSAnchorScriptObject(id, pdf, thumbnailFile, txt, changedScripts.contains(id)));
                    }
                    ingestStatusTracker.notifyIngest(id, pid);
                    report.ingested(id, pid);
                }
            }

            // wait for the resource index
            Thread.sleep(10000);
            fedora.fixRelationships();
            success = true;
        } finally {
            snapshotRemoteResources();

            report.setEndingCount(ingestStatusTracker.getAlreadyIngestedCount());
            if (success) {
                report.sendSuccess();
            } else {
                report.sendFailure();
            }
        }
    }

    public void snapshotRemoteResources() throws IOException {
        for (File f : d.getSpreadsheets()) {
            File dest = new File(snapshotDir, f.getName());
            FileUtils.copyFile(f, dest);
        }
        FileUtils.copyFile(d.getCatalogerSpreadsheet(), new File(snapshotDir, d.getCatalogerSpreadsheet().getName()));
    }


}
