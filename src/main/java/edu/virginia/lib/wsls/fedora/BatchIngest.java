package edu.virginia.lib.wsls.fedora;

import static edu.virginia.lib.wsls.fedora.FedoraHelper.getSubjectsWithLiteral;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

import edu.virginia.lib.wsls.proc.ImageMagickProcess;
import edu.virginia.lib.wsls.spreadsheet.ColumnMapping;
import edu.virginia.lib.wsls.spreadsheet.ColumnNameBasedPBCoreRow;
import edu.virginia.lib.wsls.spreadsheet.PBCoreDocument;

/**
 * Ingests a batch of PBCore records (for video clips) and their
 * respective anchor scripts.  This class uses a single FOXML
 * ingest (as opposed to several REST API calls) to sidestep issues
 * of transaction.  If an object is in the repository, it can be
 * considered complete (though the script that points to it may
 * not be present).  This simplifies stopping and rerunning this
 * process.
 */
public class BatchIngest {

    public static final String FOXML_URI = "info:fedora/fedora-system:def/foxml#";
    public static final String RDF_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String FEDORA_MODEL = "info:fedora/fedora-system:def/model#";
    public static final String FEDORA_RELS = "info:fedora/fedora-system:def/relations-external#";
    public static final String FOXML_SCHEMA_LOC = "http://www.fedora.info/definitions/1/0/foxml1-1.xsd";
    public static final String WSLS_RELATIONSHIP_PREDICATE_PREFIX = "http://fedora.lib.virginia.edu/wsls/relationships#";
    public static final String UVA_RELATIONSHIP_PREDICATE_PREFIX = "http://fedora.lib.virginia.edu/relationships#";
    
    private File scriptTextDir;
    
    private File scriptPdfDir;
    
    private File spreadsheet;
    
    public static void main(String [] args) throws Exception {
        if (args.length != 3 && args.length != 4) {
            System.err.println("Usage: BatchIngest [spreadsheet] [txtDir] [pdfDir] --replace");
            return;
        }
        File spreadsheet = new File(args[0]);
        File txtDir = new File(args[1]);
        File pdfDir = new File(args[2]);
         
        BatchIngest b = new BatchIngest(spreadsheet, txtDir, pdfDir);
        
        Properties p = new Properties();
        p.load(BatchIngest.class.getClassLoader().getResourceAsStream("conf/fedora.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        Operation replace = Operation.ADD_MISSING;
        if (args.length == 4 && "--replace".equals(args[3])) {
            replace = Operation.REPLACE;
        } else if (args.length == 4 && "--purge".equals(args[3])) {
            replace = Operation.PURGE;
        }
        b.ingest(fc, replace);
    }
    
    public BatchIngest(File xls, File texts, File pdfs) {
        scriptTextDir = texts;
        scriptPdfDir = pdfs;
        spreadsheet = xls;
    }
    
    public void validateSourceMaterials() throws Exception {
        process(null, Operation.ADD_MISSING);
    }
    
    public void ingest(FedoraClient fc, Operation replace) throws Exception {
        createHierarchyObjects(fc, replace);
        process(fc, replace);
    }
    
    private static enum Operation {
        ADD_MISSING,
        REPLACE,
        PURGE;
    }
    
    private void process(FedoraClient fc, Operation replace) throws FactoryConfigurationError, Exception {
        Workbook wb = null;
        try {
            wb = new HSSFWorkbook(new FileInputStream(spreadsheet));
        } catch (OfficeXmlFileException ex) {
            wb = new XSSFWorkbook(new FileInputStream(spreadsheet));
        }
        
        ImageMagickProcess t = new ImageMagickProcess();
        
        Sheet sheet = wb.getSheetAt(0);
        ColumnMapping m = new ColumnMapping(sheet.getRow(0));
        String previousParentPid = null;
        String previousPid = null;
        for (int i = 1; i <= sheet.getLastRowNum(); i ++) {
            ColumnNameBasedPBCoreRow row = new ColumnNameBasedPBCoreRow(sheet.getRow(i), m);
            PBCoreDocument pbcore = new PBCoreDocument(row);
            System.out.println(row.getId() + " parsed");
            File pdf = new File(scriptPdfDir, row.getId() + ".pdf");
            File text = new File(scriptTextDir, row.getId() + ".txt");
            if (!pdf.exists() || !text.exists()) {
                if (!pdf.exists()) {
                    System.err.println("Missing pdf file: " + pdf.getPath());
                }
                if (!text.exists()) {
                    System.err.println("Missing text file: " + text.getPath());
                }
            } else if (fc != null) {
                // ingest the objects
                List<String> oldPids = getSubjectsWithLiteral(fc, "dc:identifier", pbcore.getId());
                Collections.sort(oldPids);
                if (replace.equals(Operation.PURGE) || replace.equals(Operation.REPLACE)) {
                    for (String pid : oldPids) {
                        FedoraClient.purgeObject(pid).execute(fc);
                        System.out.println("Purging " + pid);
                    }
                }
                if (!(replace.equals(Operation.PURGE) || (replace.equals(Operation.ADD_MISSING) && !oldPids.isEmpty()))) {
                    File thumbnailFile = File.createTempFile("thumbnail-", ".png");
                    thumbnailFile.deleteOnExit();
                    t.generateThubmnail(pdf, thumbnailFile);

                    File mainFoxml = File.createTempFile("main-object-", "-foxml.xml");
                    mainFoxml.deleteOnExit();
                    String mainPid = oldPids.isEmpty() ? FedoraClient.getNextPID().execute(fc).getPid() : oldPids.get(0);
                    String parentPid = getMonthPidForDate(fc, row.getAssetDate());

                    writeOutPBCoreFoxml(mainPid, parentPid, (parentPid.equals(previousParentPid) ? previousPid : null), pbcore, new FileOutputStream(mainFoxml));
                    previousParentPid = parentPid;
                    previousPid = mainPid;

                    mainPid = FedoraClient.ingest(mainPid).content(mainFoxml).execute(fc).getPid();
                    System.out.println("Ingested " + mainPid);
                    
                    File scriptFoxml = File.createTempFile("script-object", "-foxml.xml");
                    scriptFoxml.deleteOnExit();
                    String scriptPid = oldPids.size() != 2 ? FedoraClient.getNextPID().execute(fc).getPid() : oldPids.get(1);
                    writeOutScriptFoxml(scriptPid, new FileOutputStream(scriptFoxml), pdf, text, mainPid, pbcore.getId());
                    scriptPid = FedoraClient.ingest(scriptPid).content(scriptFoxml).execute(fc).getPid();
                    System.out.println("Ingested " + scriptPid + " (script)");
                    
                    if (thumbnailFile.exists() && thumbnailFile.length() > 0) {
                        FedoraClient.addDatastream(scriptPid, "thumbnail").controlGroup("M").content(thumbnailFile).mimeType("image/png").execute(fc);
                    } else {
                        System.err.println("Error generating thumbnail for " + pdf.getPath() + ".");
                    }
                } else {
                    System.out.println("Skipping ingest of " + pbcore.getId() + " because it's already in the repository.");
                }
            }
        }
    }

    public void createHierarchyObjects(FedoraClient fc, Operation replace) throws Exception {
        Workbook wb = null;
        try {
            wb = new HSSFWorkbook(new FileInputStream(spreadsheet));
        } catch (OfficeXmlFileException ex) {
            wb = new XSSFWorkbook(new FileInputStream(spreadsheet));
        }

        Set<String> representedYears = new HashSet<String>();
        Map<String, Set<String>> representedMonths = new HashMap<String, Set<String>>();
        
        Sheet sheet = wb.getSheetAt(0);
        SimpleDateFormat format = new SimpleDateFormat("MM/dd/yy");
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
        SimpleDateFormat monthYear = new SimpleDateFormat("yyyy-MM");
        ColumnMapping m = new ColumnMapping(sheet.getRow(0));
        for (int i = 1; i <= sheet.getLastRowNum(); i ++) {
            ColumnNameBasedPBCoreRow row = new ColumnNameBasedPBCoreRow(sheet.getRow(i), m);
            String date = row.getAssetDate();
            Date d = format.parse(date);
            Calendar c = new GregorianCalendar();
            c.setTime(d);
            String year = yearFormat.format(d);
            representedYears.add(year);
            Set<String> months = representedMonths.get(year);
            if (months == null) {
                months = new HashSet<String>();
                representedMonths.put(year, months);
            }
            months.add(monthYear.format(d));
        }

        List<String> years = new ArrayList<String>(representedYears);
        Collections.sort(years);
        if (replace.equals(Operation.PURGE)) {
            purgeCollectionObject(fc);
            for (String year : years) {
                purgeYearObject(fc, year);
                List<String> months = new ArrayList<String>(representedMonths.get(year));
                for (String month : months) {
                    System.out.println("  " + month);
                    purgeMonthObject(fc, month);
                }
            }
        } else {
            String collectionPid = createCollectionObject(fc);
            String previousYearPid = null;
            for (String year : years) {
                System.out.println(year);
                String yearPid = createYearObject(fc, year, collectionPid, previousYearPid);
                List<String> months = new ArrayList<String>(representedMonths.get(year));
                Collections.sort(months);
                String previousMonthPid = null;
                for (String month : months) {
                    System.out.println("  " + month);
                    previousMonthPid = createMonthObject(fc, month, yearPid, previousMonthPid);
                }
                previousYearPid = yearPid;
            }
        }
    }

    private String createMonthObject(FedoraClient fc, String date, String yearPid, String previousPid) throws Exception {
        String id = "wsls-" + date;
        List<String> oldPids = getSubjectsWithLiteral(fc, "dc:identifier", id);
        if (oldPids.isEmpty()) {
            String pid = FedoraClient.getNextPID().execute(fc).getPid();
            File foxml = File.createTempFile("temporary", "-foxml.xml");
            foxml.deleteOnExit();
            String title = new SimpleDateFormat("MMMM").format(new SimpleDateFormat("yyyy-MM").parse(date));
            writeOutHierarchyFoxml(Arrays.asList(new String[] { "uva-lib:mods3.4CModel", "uva-lib:eadComponentCModel"}), pid,
                    yearPid, previousPid, id, date, title, "Video clips and corresponding anchor scripts from " + title + " of " + date.substring(0, 4) + ".", new FileOutputStream(foxml));
            pid = FedoraClient.ingest().content(foxml).execute(fc).getPid();
            System.out.println("Created month (" + date + ") object " + pid);
            return pid;
        } else {
            System.out.println("Found month (" + date + ") object " + oldPids.get(0));
            return oldPids.get(0);
        }
    }

    private void purgeMonthObject(FedoraClient fc, String date) throws Exception {
        String id = "wsls-" + date;
        List<String> oldPids = getSubjectsWithLiteral(fc, "dc:identifier", id);
        if (!oldPids.isEmpty()) {
            System.out.println("Purging month (" + date + ") object " + oldPids.get(0));
           FedoraClient.purgeObject(oldPids.get(0)).execute(fc);
        }
    }

    private String createYearObject(FedoraClient fc, String date, String collectionPid, String previousPid) throws Exception {
        String id = "wsls-" + date;
        List<String> oldPids = getSubjectsWithLiteral(fc, "dc:identifier", id);
        if (oldPids.isEmpty()) {
            String pid = FedoraClient.getNextPID().execute(fc).getPid();
            File foxml = File.createTempFile("temporary", "-foxml.xml");
            foxml.deleteOnExit();
            String title = date;
            writeOutHierarchyFoxml(Arrays.asList(new String[] { "uva-lib:mods3.4CModel", "uva-lib:eadComponentCModel"}), pid,
                    collectionPid, previousPid, "wsls-" + date, date, title, "Video clips and corresponding anchor scripts from " + title + ".", new FileOutputStream(foxml));
            pid = FedoraClient.ingest().content(foxml).execute(fc).getPid();
            System.out.println("Created year (" + date + ") object " + pid);
            return pid;
        } else {
            System.out.println("Found year (" + date + ") object " + oldPids.get(0));
            return oldPids.get(0);
        }
    }

    private void purgeYearObject(FedoraClient fc, String date) throws Exception {
        String id = "wsls-" + date;
        List<String> oldPids = getSubjectsWithLiteral(fc, "dc:identifier", id);
        if (!oldPids.isEmpty()) {
            System.out.println("Purging year (" + date + ") object " + oldPids.get(0));
            FedoraClient.purgeObject(oldPids.get(0)).execute(fc);
        }
    }

    private String getMonthPidForDate(FedoraClient fc, String date) throws Exception {
        String id = "wsls-" + new SimpleDateFormat("yyyy-MM").format(new SimpleDateFormat("MM/dd/yy").parse(date));
        List<String> oldPids = getSubjectsWithLiteral(fc, "dc:identifier", id);
        return oldPids.get(0);
    }

    private String createCollectionObject(FedoraClient fc) throws Exception {
        List<String> oldPids = getSubjectsWithLiteral(fc, "dc:identifier", "wsls-collection");
        if (oldPids.isEmpty()) {
            String pid = FedoraClient.getNextPID().execute(fc).getPid();
            FedoraClient.ingest(pid).execute(fc);
            FedoraClient.addDatastream(pid, "DC").controlGroup("X").mimeType("text/xml").content(new File(getClass().getClassLoader().getResource("collection-dc.xml").toURI())).execute(fc);
            FedoraClient.addDatastream(pid, "descMetadata").content(new File(getClass().getClassLoader().getResource("collection-ead-fragment.xml").toURI())).execute(fc);
            FedoraClient.addRelationship(pid).object("info:fedora/uva-lib:eadCollectionCModel").predicate("info:fedora/fedora-system:def/model#hasModel").execute(fc);
            FedoraClient.addRelationship(pid).object("info:fedora/uva-lib:eadMetadataFragmentCModel").predicate("info:fedora/fedora-system:def/model#hasModel").execute(fc);
            FedoraClient.addRelationship(pid).isLiteral(true).object("UNDISCOVERABLE").predicate("http://fedora.lib.virginia.edu/relationships#visibility").execute(fc);
            System.out.println("Created collection object at " + pid + ".");
            return pid;
        } else {
            System.out.println("Located collection object at " + oldPids.get(0) + ".");
            return oldPids.get(0);
        }
    }
    
    private void purgeCollectionObject(FedoraClient fc) throws Exception {
        List<String> oldPids = getSubjectsWithLiteral(fc, "dc:identifier", "wsls-collection");
        if (!oldPids.isEmpty()) {
            System.out.println("Purging collection (" + oldPids.get(0) + ").");
            FedoraClient.purgeObject(oldPids.get(0));
        }
    }

    /**
     * Builds a Document (DOM) representing the FOXML for a PBCore2
     * foxml object and serializes it to XML.
     * @param pid the pid of the newly created object
     * @throws IOException 
     */
    public void writeOutPBCoreFoxml(String pid, String parentPid, String previousPid, PBCoreDocument pbcore, OutputStream os) throws ParserConfigurationException, XMLStreamException, FactoryConfigurationError, TransformerException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        pbcore.writeOutXML(baos);
        TransformerFactory tFactory = TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl", null);
        Templates template = tFactory.newTemplates(new StreamSource(getClass().getClassLoader().getResourceAsStream("pbcore2-to-foxml.xsl")));
        Transformer t = template.newTransformer();
        t.setParameter("pid", pid);
        t.setParameter("id", pbcore.getId());
        if (parentPid != null ) {
            t.setParameter("parentPid", parentPid);
        }
        if (previousPid != null) {
            t.setParameter("previousPid", previousPid);
        }
        t.transform(new StreamSource(new ByteArrayInputStream(baos.toByteArray())), new StreamResult(os));
        os.close();
    }
    
    /**
     * Writes out FOXML for the object that contains thescript and text file.
     */
    public void writeOutScriptFoxml(String pid, OutputStream os, File pdf, File text, String pbcoreObjectPid, String id) throws XMLStreamException, FactoryConfigurationError, FileNotFoundException, IOException {
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(os);
        w.writeStartDocument("UTF-8", "1.0");
        w.writeCharacters("\n");
        
        w.writeStartElement("foxml", "digitalObject", FOXML_URI);
        w.writeAttribute("VERSION", "1.1");
        w.writeAttribute("PID", pid);
        w.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        w.writeAttribute("xmlns:foxml", FOXML_URI);
        w.writeAttribute("xsi", "http://www.w3.org/2001/XMLSchema-instance", "schemaLocation", FOXML_URI + " " + FOXML_SCHEMA_LOC);
        
        w.writeStartElement("foxml", "objectProperties", FOXML_URI);
        w.writeStartElement("foxml", "property", FOXML_URI);
        w.writeAttribute("NAME", "info:fedora/fedora-system:def/model#state");
        w.writeAttribute("VALUE", "Active");
        w.writeEndElement(); // foxml:property
        w.writeEndElement(); // foxml:objectProperties
        
        // DC Datastream
        w.writeStartElement("foxml", "datastream", FOXML_URI);
        w.writeAttribute("ID", "DC");
        w.writeAttribute("STATE", "A");
        w.writeAttribute("CONTROL_GROUP", "X");
        w.writeAttribute("VERSIONABLE", "true");
        w.writeStartElement("foxml", "datastreamVersion", FOXML_URI);
        w.writeAttribute("ID", "DC1.0");
        w.writeAttribute("MIMETYPE", "text/xml");
        w.writeAttribute("FORMAT_URI", "http://www.openarchives.org/OAI/2.0/oai_dc/");
        w.writeStartElement("foxml", "xmlContent", FOXML_URI);
        w.writeStartElement("oai_dc", "dc", "http://www.openarchives.org/OAI/2.0/oai_dc/");
        w.writeAttribute("xmlns:oai_dc", "http://www.openarchives.org/OAI/2.0/oai_dc/");
        w.writeAttribute("xmlns:dc", "http://purl.org/dc/elements/1.1/");
        w.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        w.writeAttribute("xsi", "http://www.w3.org/2001/XMLSchema-instance", "schemaLocation", "http://www.openarchives.org/OAI/2.0/oai_dc/ http://www.openarchives.org/OAI/2.0/oai_dc.xsd");
        w.writeStartElement("dc", "identifier", "http://purl.org/dc/elements/1.1/");
        w.writeCharacters(pid);
        w.writeEndElement(); // dc:identifier
        w.writeStartElement("dc", "identifier", "http://purl.org/dc/elements/1.1/");
        w.writeCharacters(id);
        w.writeEndElement(); // dc:identifier
        w.writeEndElement(); // oai_dc:dc
        w.writeEndElement(); // foxml:xmlContent
        w.writeEndElement(); // foxml:datastreamVersion
        w.writeEndElement(); // foxml:datastream (DC)
        
        // RELS-EXT Datastream
        w.writeStartElement("foxml", "datastream", FOXML_URI);
        w.writeAttribute("ID", "RELS-EXT");
        w.writeAttribute("STATE", "A");
        w.writeAttribute("CONTROL_GROUP", "X");
        w.writeAttribute("VERSIONABLE", "true");
        w.writeStartElement("foxml", "datastreamVersion", FOXML_URI);
        w.writeAttribute("ID", "RELS-EXT1.0");
        w.writeAttribute("MIMETYPE", "application/rdf+xml");
        w.writeAttribute("FORMAT_URI", "info:fedora/fedora-system:FedoraRELSExt-1.0");
        w.writeStartElement("foxml", "xmlContent", FOXML_URI);
        w.writeStartElement("rdf", "RDF", RDF_URI);
        w.writeAttribute("xmlns:fedora-model", FEDORA_MODEL);
        w.writeAttribute("xmlns:rdf", RDF_URI);
        w.writeAttribute("xmlns:wsls", WSLS_RELATIONSHIP_PREDICATE_PREFIX);
        w.writeStartElement("rdf", "Description", RDF_URI);
        w.writeAttribute("rdf", RDF_URI, "about", "info:fedora/" + pid);
        w.writeStartElement("fedora-model", "hasModel", FEDORA_MODEL);
        w.writeAttribute("rdf", RDF_URI, "resource", "info:fedora/uva-lib:wslsScriptCModel");
        w.writeEndElement(); // fedora-model:hasModel
        w.writeStartElement("wsls", "isAnchorScriptFor", WSLS_RELATIONSHIP_PREDICATE_PREFIX);
        w.writeAttribute("rdf", RDF_URI, "resource", "info:fedora/" + pbcoreObjectPid);
        w.writeEndElement(); // wsls:isAnchorScriptFor
        w.writeEndElement(); // rdf:Description
        w.writeEndElement(); // rdf:RDF
        w.writeEndElement(); // foxml:xmlConent
        w.writeEndElement(); // foxml:datastreamVersion
        w.writeEndElement(); // foxml:datastream (RELS-EXT)
        
        // scriptPDF
        embedFile(os, w, "scriptPDF", "application/pdf", null, pdf);
        
        // scriptTXT
        embedFile(os, w, "scriptTXT", "text/plain", null, text);
        
        w.writeEndElement(); // foxml:digitalObject
        w.writeEndDocument();
        w.flush();
        w.close();
    }
    
    /**
     * Writes out FOXML for the object that represents a component (year, month
     * or whatever).
     */
    public void writeOutHierarchyFoxml(List<String> contentModelPids, String pid, String parentPid, String previousPid, String id, String w3cdtfDate, String title, String description, OutputStream os) throws XMLStreamException, FactoryConfigurationError, FileNotFoundException, IOException {
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(os);
        w.writeStartDocument("UTF-8", "1.0");
        w.writeCharacters("\n");
        
        w.writeStartElement("foxml", "digitalObject", FOXML_URI);
        w.writeAttribute("VERSION", "1.1");
        if (pid != null) {
            w.writeAttribute("PID", pid);
        }
        w.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        w.writeAttribute("xmlns:foxml", FOXML_URI);
        w.writeAttribute("xsi", "http://www.w3.org/2001/XMLSchema-instance", "schemaLocation", FOXML_URI + " " + FOXML_SCHEMA_LOC);
        
        w.writeStartElement("foxml", "objectProperties", FOXML_URI);
        w.writeStartElement("foxml", "property", FOXML_URI);
        w.writeAttribute("NAME", "info:fedora/fedora-system:def/model#state");
        w.writeAttribute("VALUE", "Active");
        w.writeEndElement(); // foxml:property
        w.writeEndElement(); // foxml:objectProperties
        
        // DC Datastream
        w.writeStartElement("foxml", "datastream", FOXML_URI);
        w.writeAttribute("ID", "DC");
        w.writeAttribute("STATE", "A");
        w.writeAttribute("CONTROL_GROUP", "X");
        w.writeAttribute("VERSIONABLE", "true");
        w.writeStartElement("foxml", "datastreamVersion", FOXML_URI);
        w.writeAttribute("ID", "DC1.0");
        w.writeAttribute("MIMETYPE", "text/xml");
        w.writeAttribute("FORMAT_URI", "http://www.openarchives.org/OAI/2.0/oai_dc/");
        w.writeStartElement("foxml", "xmlContent", FOXML_URI);
        w.writeStartElement("oai_dc", "dc", "http://www.openarchives.org/OAI/2.0/oai_dc/");
        w.writeAttribute("xmlns:oai_dc", "http://www.openarchives.org/OAI/2.0/oai_dc/");
        w.writeAttribute("xmlns:dc", "http://purl.org/dc/elements/1.1/");
        w.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        w.writeAttribute("xsi", "http://www.w3.org/2001/XMLSchema-instance", "schemaLocation", "http://www.openarchives.org/OAI/2.0/oai_dc/ http://www.openarchives.org/OAI/2.0/oai_dc.xsd");
        if (pid != null) {
            w.writeStartElement("dc", "identifier", "http://purl.org/dc/elements/1.1/");
            w.writeCharacters(pid);
            w.writeEndElement(); // dc:identifier
        }
        w.writeStartElement("dc", "identifier", "http://purl.org/dc/elements/1.1/");
        w.writeCharacters(id);
        w.writeEndElement(); // dc:identifier
        w.writeEndElement(); // oai_dc:dc
        w.writeEndElement(); // foxml:xmlContent
        w.writeEndElement(); // foxml:datastreamVersion
        w.writeEndElement(); // foxml:datastream (DC)
        
        // RELS-EXT Datastream
        w.writeStartElement("foxml", "datastream", FOXML_URI);
        w.writeAttribute("ID", "RELS-EXT");
        w.writeAttribute("STATE", "A");
        w.writeAttribute("CONTROL_GROUP", "X");
        w.writeAttribute("VERSIONABLE", "true");
        w.writeStartElement("foxml", "datastreamVersion", FOXML_URI);
        w.writeAttribute("ID", "RELS-EXT1.0");
        w.writeAttribute("MIMETYPE", "application/rdf+xml");
        w.writeAttribute("FORMAT_URI", "info:fedora/fedora-system:FedoraRELSExt-1.0");
        w.writeStartElement("foxml", "xmlContent", FOXML_URI);
        w.writeStartElement("rdf", "RDF", RDF_URI);
        w.writeAttribute("xmlns:fedora-model", FEDORA_MODEL);
        w.writeAttribute("xmlns:fedora", FEDORA_RELS);
        w.writeAttribute("xmlns:rdf", RDF_URI);
        w.writeAttribute("xmlns:wsls", WSLS_RELATIONSHIP_PREDICATE_PREFIX);
        w.writeAttribute("xmlns:uva", UVA_RELATIONSHIP_PREDICATE_PREFIX);
        w.writeStartElement("rdf", "Description", RDF_URI);
        w.writeAttribute("rdf", RDF_URI, "about", "info:fedora/" + pid);
        w.writeStartElement("uva", "visibility", UVA_RELATIONSHIP_PREDICATE_PREFIX);
        w.writeCharacters("UNDISCOVERABLE");
        w.writeEndElement();
        for (String contentModel : contentModelPids) {
            w.writeStartElement("fedora-model", "hasModel", FEDORA_MODEL);
            w.writeAttribute("rdf", RDF_URI, "resource", "info:fedora/" + contentModel);
            w.writeEndElement(); // fedora-model:hasModel
        }
        if (parentPid != null) {
            w.writeStartElement("fedora", "isPartOf", FEDORA_RELS);
            w.writeAttribute("rdf", RDF_URI, "resource", "info:fedora/" + parentPid);
            w.writeEndElement();
        }
        if (previousPid != null) {
            w.writeStartElement("uva", "follows", UVA_RELATIONSHIP_PREDICATE_PREFIX);
            w.writeAttribute("rdf", RDF_URI, "resource", "info:fedora/" + previousPid);
            w.writeEndElement();
        }
        w.writeEndElement(); // rdf:Description
        w.writeEndElement(); // rdf:RDF
        w.writeEndElement(); // foxml:xmlConent
        w.writeEndElement(); // foxml:datastreamVersion
        w.writeEndElement(); // foxml:datastream (RELS-EXT)
        
        // MODS datastream
        w.writeStartElement("foxml", "datastream", FOXML_URI);
        w.writeAttribute("ID", "descMetadata");
        w.writeAttribute("STATE", "A");
        w.writeAttribute("CONTROL_GROUP", "M");
        w.writeAttribute("VERSIONABLE", "true");
        w.writeStartElement("foxml", "datastreamVersion", FOXML_URI);
        w.writeAttribute("ID", "descMetadata1.0");
        w.writeAttribute("MIMETYPE", "text/xml");
        w.writeStartElement("foxml", "xmlContent", FOXML_URI);
        /*
        <mods:mods xmlns:mods="http://www.loc.gov/mods/v3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-3.xsd">
        <mods:titleInfo>
          <mods:title>Daily Progress Issues from June 1892</mods:title>
        </mods:titleInfo>
        <mods:originInfo>
          <mods:dateCreated keydate="yes" encoding="w3cdtf">1892-06</mods:dateCreated>
        </mods:originInfo>
        </mods:mods>
        */
        w.writeStartElement("mods", "mods", "http://www.loc.gov/mods/v3");
        w.writeAttribute("xmlns:mods", "http://www.loc.gov/mods/v3");
        w.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        w.writeAttribute("xsi", "http://www.w3.org/2001/XMLSchema-instance", "schemaLocation", "http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-3.xsd");
        
        w.writeStartElement("mods", "titleInfo", "http://www.loc.gov/mods/v3");
        w.writeStartElement("mods", "title", "http://www.loc.gov/mods/v3");
        w.writeCharacters(title);
        w.writeEndElement(); // mods:title
        w.writeEndElement(); // mods:titleInfo
        
        if (description != null) {
            w.writeStartElement("mods", "abstract", "http://www.loc.gov/mods/v3");
            w.writeCharacters(description);
            w.writeEndElement(); // mods:abstract
        }
        
        if (w3cdtfDate != null) {
            w.writeStartElement("mods", "originInfo", "http://www.loc.gov/mods/v3");
            w.writeStartElement("mods", "dateCreated", "http://www.loc.gov/mods/v3");
            w.writeAttribute("keydate", "yes");
            w.writeAttribute("encoding", "w3cdtf");
            w.writeCharacters(w3cdtfDate);
            w.writeEndElement(); // mods:dateCreated
            w.writeEndElement(); // mods:originInfo
        }

        w.writeEndElement(); // mods:mods
        
        w.writeEndElement(); // foxml:xmlContent
        w.writeEndElement(); // foxml:datastreamVersion
        w.writeEndElement(); // foxml:datastream (DC)
        
        w.writeEndElement(); // foxml:digitalObject
        w.writeEndDocument();
        w.flush();
        w.close();
    }
    
    private void embedFile(OutputStream underlyingOutputStream, XMLStreamWriter w, String dsId, String mimeType, String checksum, File file) throws XMLStreamException, FileNotFoundException, IOException {
        w.writeStartElement("foxml", "datastream", FOXML_URI);
        w.writeAttribute("ID", dsId);
        w.writeAttribute("STATE", "A");
        w.writeAttribute("CONTROL_GROUP", "M");
        w.writeAttribute("VERSIONABLE", "true");
        w.writeStartElement("foxml", "datastreamVersion", FOXML_URI);
        w.writeAttribute("ID", dsId + "1.0");
        w.writeAttribute("MIMETYPE", mimeType);
        if (checksum != null) {
            w.writeStartElement("foxml", "contentDigest", FOXML_URI);
            w.writeAttribute("TYPE", "MD5");
            w.writeAttribute("DIGEST", checksum);
            w.writeEndElement(); // foxml:contentDigest
        }
        w.writeStartElement("foxml", "binaryContent", FOXML_URI);
        
        // flush all this content to the underlying
        // OutputStream since we'll be piping data 
        // from a different source into that stream next.
        w.writeCharacters(" "); // semantically meaningless, but forces the start element to be written
        w.flush();
        
        Base64OutputStream b64 = new Base64OutputStream(underlyingOutputStream);
        long bytes = IOUtils.copyLarge(new FileInputStream(file), b64);
        b64.flush();
        
        w.writeEndElement(); // foxml:binaryContent
        w.writeEndElement(); // foxml:datastreamVersion
        w.writeEndElement(); // foxml:datastream
    }
}
