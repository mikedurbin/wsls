package edu.virginia.lib.wsls.fedora;

import java.io.File;
import java.util.Properties;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

import edu.virginia.lib.wsls.datasources.GoogleMetadata;
import edu.virginia.lib.wsls.datasources.KalturaVideoSource;
import edu.virginia.lib.wsls.datasources.PDFSource;
import edu.virginia.lib.wsls.datasources.TXTSource;
import edu.virginia.lib.wsls.datasources.WSLSMasterSpreadsheet;

/**
 * A utlity to ingest just the PDFs and Text files for scripts into a 
 * fedora repository with methods to quickly get the pid of a particular
 * script from it's WSLS id.
 */
@Deprecated
public class PDFIngester {

    public static void main(String [] args) throws Exception {
        Properties p = new Properties();
        p.load(BatchIngest.class.getClassLoader().getResourceAsStream("conf/fedora.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        PDFIngester pdfIngester = new PDFIngester(fc, p.getProperty("fedora-url"));

        p.load(PDFIngester.class.getClassLoader().getResourceAsStream("conf/ingest.properties"));

        pdfIngester.setPDFs(new PDFSource(new File(p.getProperty("pdf-dir"))));
        pdfIngester.setTXTs(new TXTSource(new File(p.getProperty("txt-dir"))));
        pdfIngester.setMaster(new WSLSMasterSpreadsheet(new File(p.getProperty("current-master"))));
        pdfIngester.setVideos(new KalturaVideoSource(new File(p.getProperty("kaltura-id-log"))));
        pdfIngester.setGoogleMetadata(new GoogleMetadata(new File(p.getProperty("current-google"))));

        //pdfIngester.ingestRows(500, 0, true, false);
        //FileOutputStream fos = new FileOutputStream("example-100.html");
        //pdfIngester.writeOutHTML(100, 0, fos);
        //fos.close();
    }

    private FedoraClient fc;

    private String fedoraUrl;

    private GoogleMetadata m;
    private KalturaVideoSource videos;
    private PDFSource pdfs;
    private TXTSource txts;
    private WSLSMasterSpreadsheet master;

    public PDFIngester(FedoraClient fc, String fedoraUrl) {
        this.fc = fc;
        this.fedoraUrl = fedoraUrl;
    }

    public void setVideos(KalturaVideoSource videos) {
        this.videos = videos;
    }

    public void setGoogleMetadata(GoogleMetadata m) {
        this.m = m;
    }

    public void setPDFs(PDFSource pdfs) {
        this.pdfs = pdfs;
    }

    public void setTXTs(TXTSource txts) {
        this.txts = txts;
    }

    public void setMaster(WSLSMasterSpreadsheet master) {
        this.master = master;
    }

    /*
    public void ingestRows(int count, int offset, boolean onlyGoogleMetadata, boolean purge) throws Exception {
        //if (purge) {
        //    BatchIngest.createHierarchyObjects(master, count, fc, BatchIngest.Operation.PURGE);
        //} else {
        //    BatchIngest.createHierarchyObjects(master, count, fc, BatchIngest.Operation.PURGE);
        //    BatchIngest.createHierarchyObjects(master, count, fc, BatchIngest.Operation.ADD_MISSING);
        //}
        ImageMagickProcess t = new ImageMagickProcess();
        int index = 0;
        for (PBCoreSpreadsheetRow row : master)
            if (m == null) {
                m = new ColumnMapping(sheet.getRow(0));
            } else {
                if (index >= offset) {
                    ColumnNameBasedPBCoreRow row = new ColumnNameBasedPBCoreRow(r, m);
                    if (!onlyGoogleMetadata || (this.m.getMetadata(row.getId()) != null)) {
                        if (purge) {
                            try {
                                FedoraClient.purgeObject("wsls:" + row.getId()).execute(fc);
                                FedoraClient.purgeObject("wsls-script:" + row.getId()).execute(fc);
                                System.out.println("Purged records for " + row.getId());
                            } catch (FedoraClientException ex) {
                                if (ex.getMessage().contains("404")) {
                                    // do nothing
                                } else {
                                    throw ex;
                                }
                            }
                        } else {
                            BatchIngest.ingestRecord(fc, t, "wsls:" + row.getId(), "wsls-script:" + row.getId(), getPDFFile(row.getId()), getTXTFile(row.getId()), (this.m != null ? new CombinedPBCoreSpreadsheetRow(this.m.getMetadata(row.getId()), row) : row), getKalturaUrl(row.getId()));
                        }
                        index ++;
                        if (index - offset > count) {
                            break;
                        }
                    } else {
                        System.out.println("Skipping " + row.getId() + " because no google form metadata.");
                    }
                }
            }
        }
    }

    private void ingestPDFs(String id, String pid) throws IOException, XMLStreamException, FactoryConfigurationError, FedoraClientException {
        File pdf = getPDFFile(id);
        File txt = getTXTFile(id);
        if (pdf == null) {
            throw new RuntimeException("No PDF for " + id + "!");
        }
        if (txt == null) {
            throw new RuntimeException("No Text file for " + id + "!");
        }
        File temp = File.createTempFile("pbcore-document-", "-foxml.xml");
        FileOutputStream fos = new FileOutputStream(temp);
        try {
            BatchIngest.writeOutScriptFoxml(pid, fos, pdf, txt, null, id);
            FedoraClient.ingest(pid).content(temp).execute(fc);
        } finally {
            fos.close();
            temp.delete();
        }
    }

    private void ingestRow() {
        
    }
    
    private String getPDFUrl(String id) throws Exception {
        String pid = getPIDforID(id);
        if (pid == null) {
            return null;
        } else {
            return fedoraUrl + "/objects/" + pid + "/datastreams/scriptPDF/content";
        }
    }

    private String getTextUrl(String id) throws Exception {
        String pid = getPIDforID(id);
        if (pid == null) {
            return null;
        } else {
            return fedoraUrl + "/objects/" + pid + "/datastreams/scriptTXT/content";
        }
    }

    private String getPIDforID(String id) throws Exception {
        List<String> pids = getSubjectsWithLiteral(fc, "dc:identifier", id);
        if (pids.isEmpty()) {
            return null;
        } else if (pids.size() == 1) {
            return pids.get(0);
        } else {
            throw new RuntimeException("Multiple entries exist for id " + id + "!");
        }
    }
    
    */

}
