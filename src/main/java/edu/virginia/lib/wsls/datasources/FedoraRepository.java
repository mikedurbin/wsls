package edu.virginia.lib.wsls.datasources;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraClientException;

import edu.virginia.lib.wsls.fedora.FedoraHelper;
import edu.virginia.lib.wsls.fedora.RelationshipValidator;
import edu.virginia.lib.wsls.spreadsheet.PBCoreDocument;
import edu.virginia.lib.wsls.spreadsheet.PBCoreDocument.VariablePrecisionDate;

public class FedoraRepository {

    public static final String FEDORA_MODEL = "info:fedora/fedora-system:def/model#";
    public static final String FEDORA_RELS = "info:fedora/fedora-system:def/relations-external#";
    public static final String UVA_RELS = "http://fedora.lib.virginia.edu/relationships#";
    public static final String WSLS_RELS = "http://fedora.lib.virginia.edu/wsls/relationships#";

    private FedoraClient fc;

    private PIDRegistry pids;

    public FedoraRepository(FedoraClient client, File baseLuceneDir) throws Exception {
        fc = client;
        pids = new PIDRegistry(new File(baseLuceneDir, new java.net.URL(FedoraClient.describeRepository().execute(fc).getRepositoryInfo().getRepositoryBaseURL()).getHost()));
        //System.out.println("Lucene Index: ");
        //pids.dumpIndex(System.out);
    }

    public PIDRegistry getPIDRegistry() {
        return this.pids;
    }
    
    /**
     * Stores/Updates the video/metadata object in fedora for a WSLS video
     */
    public String ingestWSLSVideoObject(PBCoreDocument pbcore) throws Exception {
        // locate the existing object (if present)
        String videoPid = pids.getPIDForWSLSID(pbcore.getId());
        if (videoPid != null) {
            // purge old relationships
            FedoraClient.purgeDatastream(videoPid, "RELS-EXT").execute(fc);
        }

        // locate/create parent objects
        String parentPid = getOrCreateParentInHierarchy(pbcore);

        // update/create video object
        if (videoPid == null) {
            videoPid = FedoraClient.ingest().execute(fc).getPid();
            pids.setPIDforWSLSID(pbcore.getId(), videoPid, pbcore);
        }

        // add the relationship to the parent
        FedoraClient.addRelationship(videoPid).object("info:fedora/" + parentPid).predicate(FEDORA_RELS + "isPartOf").execute(fc);

        // insert in the sequence
        String[] insertionPoint = pids.getItemInsertionPoint(videoPid, pbcore.getAssetVariablePrecisionDate());
        String previousPid = insertionPoint[0];
        String followingPid = insertionPoint[1];
        FedoraHelper.insertBetween(fc, videoPid, previousPid, followingPid);

        // add the content models
        FedoraClient.addRelationship(videoPid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:pbcore2CModel").execute(fc);
        FedoraClient.addRelationship(videoPid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:eadItemCModel").execute(fc);

        // set the PBCore document
        FedoraClient.addDatastream(videoPid, "metadata").controlGroup("M").mimeType("text/xml").dsLabel("PBCore metadata").content(pbcore.getXMLAsString()).execute(fc);

        return videoPid;
    }

    public String ingestWSLSAnchorScriptObject(String id, File pdf, File thumbnail, File text, boolean update) throws Exception {
        // locate the existing object (if present)
        String videoPid = pids.getPIDForWSLSID(id);
        String scriptPid = pids.getAnchorPIDForWSLSID(id);

        if (scriptPid != null) {
            if (!update) {
                return scriptPid;
            } else {
                // purge old relationships
                FedoraClient.purgeDatastream(scriptPid, "RELS-EXT").execute(fc);
            }
        }

        if (videoPid == null) {
            throw new IllegalStateException("No video/metadata object found for id " + id + "!");
        }

        // update/create script object
        if (scriptPid == null) {
            scriptPid = FedoraClient.ingest().execute(fc).getPid();
            pids.setAnchorPIDForWSLSID(id, scriptPid);
        }

        // add the relationship to the video/metadata
        FedoraClient.addRelationship(scriptPid).predicate(WSLS_RELS + "isAnchorScriptFor").object("info:fedora/" + videoPid).execute(fc);

        // add the content model
        FedoraClient.addRelationship(scriptPid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:wslsScriptCModel").execute(fc);

        // add the scriptPDF datastream
        FedoraClient.addDatastream(scriptPid, "scriptPDF").controlGroup("M").mimeType("application/pdf").dsLabel("Anchor Script (PDF)").content(pdf).execute(fc);

        // add the scriptTXT datastream
        FedoraClient.addDatastream(scriptPid, "scriptTXT").controlGroup("M").mimeType("text/plain").dsLabel("Anchor Script (keyed text)").content(text).execute(fc);

        // add the thumbnail datastream
        FedoraClient.addDatastream(scriptPid, "thumbnail").controlGroup("M").mimeType("image/png").dsLabel("Thumbnail image of anchor script").content(thumbnail).execute(fc);

        return scriptPid;
    }

    public void purgeWSLSAnchorScriptObject(String id) throws IOException {
        String scriptPid = pids.getAnchorPIDForWSLSID(id);
        if (scriptPid != null) {
            FedoraClient.purgeObject(scriptPid);
        }
    }

    private String getOrCreateParentInHierarchy(PBCoreDocument pbcore) throws Exception {
        VariablePrecisionDate date = pbcore.getAssetVariablePrecisionDate();
        if (date == null) {
            return getOrCreateUnknownFolderPid();
        } else {
            return getOrCreateMonthPid(date);
        }
    }
    public String recreateUnknownFolderPid() throws Exception {
        String pid = pids.getUnknownPid();
        if (pid == null) {
            throw new IllegalStateException();
        } 
        FedoraClient.ingest(pid).execute(fc);

        String parentPid = getOrCreateCollectionPid();

        // add isPartOf relationship to collection
        FedoraClient.addRelationship(pid).object("info:fedora/" + parentPid).predicate(FEDORA_RELS + "isPartOf").execute(fc);

        // add follows relationship to last date
        String[] insertionPoint = pids.getUnknownFolderInsertionPoint(pid);
        String previousPid = insertionPoint[0];
        String followingPid = insertionPoint[1];
        FedoraHelper.insertBetween(fc, pid, previousPid, followingPid);

        // add UNDISCOVERABLE assertion
        FedoraClient.addRelationship(pid).isLiteral(true).predicate(UVA_RELS + "visibility").object("UNDISCOVERABLE").execute(fc);

        // add content models
        FedoraClient.addRelationship(pid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:mods3.4CModel").execute(fc);
        FedoraClient.addRelationship(pid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:eadComponentCModel").execute(fc);

        // add a MODS record
        FedoraClient.addDatastream(pid, "descMetadata").controlGroup("M").mimeType("text/xml").content(getMODSRecord("unknown", "Video clips and corresponding anchor scripts from an unknown date.", null)).execute(fc);

        return pid;
    }


    public String getOrCreateUnknownFolderPid() throws Exception {
        String pid = pids.getUnknownPid();
        if (pid == null) {
            pid = FedoraClient.ingest().execute(fc).getPid();
            pids.setUnknownPid(pid);
        } else {
            // no need to update, just return
            return pid;
        }

        String parentPid = getOrCreateCollectionPid();

        // add isPartOf relationship to collection
        FedoraClient.addRelationship(pid).object("info:fedora/" + parentPid).predicate(FEDORA_RELS + "isPartOf").execute(fc);

        // add follows relationship to last date
        String[] insertionPoint = pids.getUnknownFolderInsertionPoint(pid);
        String previousPid = insertionPoint[0];
        String followingPid = insertionPoint[1];
        FedoraHelper.insertBetween(fc, pid, previousPid, followingPid);

        // add UNDISCOVERABLE assertion
        FedoraClient.addRelationship(pid).isLiteral(true).predicate(UVA_RELS + "visibility").object("UNDISCOVERABLE").execute(fc);

        // add content models
        FedoraClient.addRelationship(pid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:mods3.4CModel").execute(fc);
        FedoraClient.addRelationship(pid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:eadComponentCModel").execute(fc);

        // add a MODS record
        FedoraClient.addDatastream(pid, "descMetadata").controlGroup("M").mimeType("text/xml").content(getMODSRecord("unknown", "Video clips and corresponding anchor scripts from an unknown date.", null)).execute(fc);

        return pid;
    }
/*
    private String getNextPid() throws IOException, FedoraClientException {
        InputStream is = FedoraClient.getNextPID().execute(fc).getEntityInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(is, baos);
        String response = new String(baos.toByteArray(), "UTF-8");
        Pattern p = Pattern.compile(".*\\Q<pid>\\E(.*)\\Q</pid>\\E.*");
        Matcher m = p.matcher(response);
        if (m.matches()) {
            return m.group(1);
        } else {
            throw new RuntimeException("Unable to parse pid from: " + response);
        }
    }
*/
    private String getOrCreateCollectionPid() throws FedoraClientException, IOException, URISyntaxException {
        String pid = pids.getWSLSCollectionPid();
        if (pid == null) {
            pid = FedoraClient.ingest().execute(fc).getPid();
            pids.setWSLSCollectionPid(pid);
            FedoraClient.addDatastream(pid, "descMetadata").content(new File(getClass().getClassLoader().getResource("collection-ead-fragment.xml").toURI())).execute(fc);
            FedoraClient.addRelationship(pid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:eadCollectionCModel").execute(fc);
            FedoraClient.addRelationship(pid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:eadMetadataFragmentCModel").execute(fc);
            FedoraClient.addRelationship(pid).isLiteral(true).predicate(UVA_RELS + "visibility").object("UNDISCOVERABLE").execute(fc);
            return pid;
        } else {
            return pid;
        }
    }

    private String getOrCreateMonthPid(VariablePrecisionDate date) throws Exception {
        String pid = pids.getMonthPid(date.getYear(), date.getMonth());
        if (pid == null) {
            pid = FedoraClient.ingest().execute(fc).getPid();
            pids.setMonthPid(date.getYear(), date.getMonth(), pid);
        } else {
            // no need to update, lets just return...
            return pid;
        }

        String parentPid = getOrCreateYearPid(date.getYear());

        // add isPartOf relationship to collection
        FedoraClient.addRelationship(pid).object("info:fedora/" + parentPid).predicate(FEDORA_RELS + "isPartOf").execute(fc);

        // update ordering
        String[] insertionPoint = pids.getMonthInsertionPoint(pid, date);
        FedoraHelper.insertBetween(fc, pid, insertionPoint[0], insertionPoint[1]);

        // add UNDISCOVERABLE assertion
        FedoraClient.addRelationship(pid).isLiteral(true).predicate(UVA_RELS + "visibility").object("UNDISCOVERABLE").execute(fc);

        // add content models
        FedoraClient.addRelationship(pid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:mods3.4CModel").execute(fc);
        FedoraClient.addRelationship(pid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:eadComponentCModel").execute(fc);

        // add a MODS record
        FedoraClient.addDatastream(pid, "descMetadata").controlGroup("M").mimeType("text/xml").content(getMODSRecord(date.getMonthString(), "Video clips and corresponding anchor scripts from " + date.getMonthString() + " of " + date.getYear() + ".", null)).execute(fc);

        return pid;
    }

    private String getOrCreateYearPid(int year) throws Exception {
        String pid = pids.getYearPid(year);
        if (pid == null) {
            pid = FedoraClient.ingest().execute(fc).getPid();
            pids.setYearPid(year, pid);
        } else {
            // no need to update, just return
            return pid;
        }
        String parentPid = getOrCreateCollectionPid();

        // add isPartOf relationship to collection
        FedoraClient.addRelationship(pid).object("info:fedora/" + parentPid).predicate(FEDORA_RELS + "isPartOf").execute(fc);

        // update ordering
        String[] insertionPoint = pids.getYearInsertionPoint(pid, year);
        FedoraHelper.insertBetween(fc, pid, insertionPoint[0], insertionPoint[1]);

        // add UNDISCOVERABLE assertion
        FedoraClient.addRelationship(pid).isLiteral(true).predicate(UVA_RELS + "visibility").object("UNDISCOVERABLE").execute(fc);

        // add content models
        FedoraClient.addRelationship(pid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:mods3.4CModel").execute(fc);
        FedoraClient.addRelationship(pid).predicate(FEDORA_MODEL + "hasModel").object("info:fedora/uva-lib:eadComponentCModel").execute(fc);

        // add a MODS record
        FedoraClient.addDatastream(pid, "descMetadata").controlGroup("M").mimeType("text/xml").content(getMODSRecord(String.valueOf(year), "Video clips and corresponding anchor scripts from " + year + ".", null)).execute(fc);

        return pid;
    }

    /**
     * Purges an object (year/month, year or collection) object once it is
     * determined to be childless.  This is a complicated operation in fedora
     * because due to no synchronous querying of the resource index, 
     * @param pid
     */
    private void purgeHierarchicalObjectIfChildless(String pid) {

    }

    public void diagnoseParents() throws Exception {
        RelationshipValidator validator = new RelationshipValidator(fc, pids);
        validator.diagnoseParents();
    }

    public void fixParents() throws Exception {
        RelationshipValidator validator = new RelationshipValidator(fc, pids);
        validator.fixParents();
    }

    public void fixRelationships() throws IOException, Exception {
        RelationshipValidator validator = new RelationshipValidator(fc, pids);
        validator.fixParents();
        validator.correctTree(pids.getWSLSCollectionPid());
    }

    private String getMODSRecord(String title, String description, String w3cdtfDate) throws UnsupportedEncodingException, XMLStreamException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(baos);
        w.writeStartDocument("UTF-8", "1.0");
        w.writeCharacters("\n");
        
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
        w.close();
        return new String(baos.toByteArray(), "UTF-8");
    }

    public void testAllPids() throws IOException, FedoraClientException {
        for (String pid : pids.listAllPids()) {
            try {
                FedoraClient.getRelationships(pid).execute(fc);
                System.out.println(pid + " is OK.");
            } catch (Throwable t) {
                System.out.println(pid + " is NOT OK!");
                t.printStackTrace();
            }
        }
    }
}
