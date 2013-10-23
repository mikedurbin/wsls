package edu.virginia.lib.wsls.spreadsheet;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class PBCoreDocument {

    public static final String PBCORE_NS = "http://www.pbcore.org/PBCore/PBCoreNamespace.html";
    public static final String PBCORE_XSD_LOC = "http://www.pbcore.org/xsd/pbcore-2.0.xsd";
    public static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    private List<PBCoreSpreadsheetRow> records;

    private Document doc; 

    /** A URL to override whatever the specified kaltura URL is */
    private String url;

    public PBCoreDocument(PBCoreSpreadsheetRow row) throws ParserConfigurationException {
        records = Collections.singletonList(row);
    }

    public PBCoreDocument(PBCoreSpreadsheetRow ... rows) throws ParserConfigurationException {
        records = new ArrayList<PBCoreSpreadsheetRow>();
        for (PBCoreSpreadsheetRow row : rows) {
            if (row != null) {
                records.add(row);
            }
        }
    }

    public void setKalturaUrl(String url) {
        if (doc != null) {
            throw new IllegalStateException("The document has already been committed!");
        }
        this.url = url;
    }

    public String getId() {
        for (PBCoreSpreadsheetRow r : records) {
            if (r.getId() != null) {
                return r.getId();
            }
        }
        return null;
    }

    public Date getAssetDate() {
        for (PBCoreSpreadsheetRow r : records) {
            if (r.getAssetDate() != null) {
                return r.getAssetDate();
            }
        }
        return null;
    }

    /**
     * Parses the AssetDate and if a valid month and year are specified, returns
     * an array with the year and month as the first and second element.  Ie,
     * for the date "12/19/2012" an array of [2012, 12] would be returned.  For
     * a date value of "1992" or "0/0/1992" null would be returned.
     * @return
     */
    public VariablePrecisionDate getAssetVariablePrecisionDate() {
        if (getAssetDate() == null) {
            return new VariablePrecisionDate(0, 0, 0);
        } else {
            String date = new SimpleDateFormat("MM/dd/yyyy").format(getAssetDate());
            String[] mdy = date.split("/");
            if (mdy.length != 3) {
                throw new IllegalArgumentException("Unrecognized date: \"" + date + "\"");
            } else {
                int day = Integer.parseInt(mdy[1]);
                if (day > 31 || day < 1) {
                    return null;
                }
                int month = Integer.parseInt(mdy[0]);
                if (month > 12 || month < 1) {
                    return null;
                }
                int year = Integer.parseInt(mdy[2]);
                if (year < 100) {
                    year += 1900;
                }
                if (year < 1000 || year > 9999) {
                    return null;
                } else {
                    return new VariablePrecisionDate(year, month, day);
                }
            }
        }
    }

    public String getTitle() {
        for (PBCoreSpreadsheetRow r : records) {
            if (r.getTitle() != null) {
                return r.getTitle();
            }
        }
        return null;
    }

    public String getAbstract() {
        for (PBCoreSpreadsheetRow r : records) {
            if (r.getAbstract() != null) {
                return r.getAbstract();
            }
        }
        return null;
    }

    public List<String> getPlaces() {
        for (PBCoreSpreadsheetRow r : records) {
            if (r.getPlaces() != null) {
                return r.getPlaces();
            }
        }
        return null;
    }

    public List<String> getTopics() {
        for (PBCoreSpreadsheetRow r : records) {
            if (r.getTopics() != null) {
                return r.getTopics();
            }
        }
        return null;
    }

    public List<String> getEntitiesLCSH() {
        for (PBCoreSpreadsheetRow r : records) {
            if (r.getEntitiesLCSH() != null) {
                return r.getEntitiesLCSH();
            }
        }
        return null;
    }

    public String getInstantiationLocation() {
        for (PBCoreSpreadsheetRow r : records) {
            if (r.getInstantiationLocation() != null) {
                return r.getInstantiationLocation();
            }
        }
        return null;
    }

    public String getInstantiationDuration() {
        for (PBCoreSpreadsheetRow r : records) {
            if (r.getInstantiationDuration() != null) {
                return r.getInstantiationDuration();
            }
        }
        return null;
    }

    public String getInstantiationColors() {
        for (PBCoreSpreadsheetRow r : records) {
            if (r.getInstantiationColors() != null) {
                return r.getInstantiationColors();
            }
        }
        return null;
    }

    public String getInstantiationAnnotation() {
        for (PBCoreSpreadsheetRow r : records) {
            if (r.getInstantiationAnnotation() != null) {
                return r.getInstantiationAnnotation();
            }
        }
        return null;
    }

    private void processPBCoreRows() throws ParserConfigurationException {
        // create a document
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        doc = f.newDocumentBuilder().newDocument();
        
        // add the fields
        Element root = doc.createElementNS(PBCORE_NS, "pbcoreDescriptionDocument");
        root.setAttributeNS(XSI_NS, "xsi:schemaLocation", PBCORE_NS + " " + PBCORE_XSD_LOC);
        doc.appendChild(root);
        
        String type = "clip";
        addPBCoreElement(root, "pbcoreAssetType", type);
        
        if (getAssetVariablePrecisionDate() != null) {
            String date = new SimpleDateFormat("MM/dd/yy").format(getAssetDate());
            addPBCoreElement(root, "pbcoreAssetDate", date, "content");
        }
        
        addPBCoreElement(root, "pbcoreIdentifier", getId(), "source", "uva");
        String id = getId();
        
        addPBCoreElement(root, "pbcoreTitle", getTitle());
        
        for (String topic : getTopics()) {
            addPBCoreElement(root, "pbcoreSubject", topic, "subjectType", "Topic", "source", "LCSH");
        }
        
        for (String place : getPlaces()) {
            addPBCoreElement(root, "pbcoreSubject", place, "subjectType", "Place", "source", "LCSH");
        }

        for (String entity : getEntitiesLCSH()) {
            addPBCoreElement(root, "pbcoreSubject", entity, "subjectType", "Entity", "source", "LCSH");
        }
        
        addPBCoreElement(root, "pbcoreDescription", getAbstract(), "descriptionType", "abstract");

        Element instanceEl = doc.createElementNS(PBCORE_NS, "pbcoreInstantiation");
        addPBCoreElement(instanceEl, "instantiationIdentifier", getId(), "source", "uva");
        addPBCoreElement(instanceEl, "instantiationLocation", (url != null ? url : getInstantiationLocation()));
        addPBCoreElement(instanceEl, "instantiationDuration", getInstantiationDuration());
        addPBCoreElement(instanceEl, "instantiationColors", getInstantiationColors());
        addPBCoreElement(instanceEl, "instantiationAnnotation", getInstantiationAnnotation());
        root.appendChild(instanceEl);
    }
    
    private static void addPBCoreElement(Element parent, String elementName, String elementValue, String ... params) {
        if (elementValue != null) {
            Element el = parent.getOwnerDocument().createElementNS(PBCORE_NS, elementName);
            el.appendChild(parent.getOwnerDocument().createTextNode(elementValue));
            parent.appendChild(el);
            if (params != null && params.length % 2 == 0) {
                for (int i = 0; i < params.length / 2; i ++) {
                    el.setAttribute(params[i * 2], params[i * 2 + 1]);
                }
            }
        }
        
    }

    /**
     * Appends the pbcore:instantiation element from the pbcore document 
     * that is parsed from the provided InputStream to this document.  This
     * method is useful for merging technical and descriptive metadata into
     * a single record.
     * @param is an InputStream that represents a PBCore XML record whose
     * instantiation element is to be appended to this record.
     */
    public void appendInstantiationIfAvailable(InputStream is) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
        if (doc == null) {
            processPBCoreRows();
        }
        if (is == null) {
            return;
        }
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {

            public String getNamespaceURI(String prefix) {
                if (prefix.equals("pbcore")) {
                    return PBCORE_NS;
                } else {
                    return null;
                }
            }

            public String getPrefix(String uri) {
                if (uri.equals(PBCORE_NS)) {
                    return "pbcore";
                } else {
                    return null;
                }
            }

            public Iterator getPrefixes(String uri) {
                if (uri.equals(PBCORE_NS)) {
                    return Arrays.asList(new String[] { "pbcore"}).iterator();
                } else {
                    return null;
                }
            }});
        
        Document doc = readXMLStreamAsDocument(is);

        Element pbcoreInstantiationEl = doc.createElementNS(PBCORE_NS, "pbcoreInstantiation");
        String id = ((String) xpath.evaluate("pbcore:instantiationLocation", doc.getDocumentElement(), XPathConstants.STRING)).replace(".mov", "");
        addPBCoreElement(pbcoreInstantiationEl, "instantiationIdentifier", id, "source", "uva");
        NodeList children = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i ++) {
            Node node = children.item(i);
            node = node.cloneNode(true);
            pbcoreInstantiationEl.getOwnerDocument().adoptNode(node);
            pbcoreInstantiationEl.appendChild(node);
        }
        doc.getDocumentElement().appendChild(pbcoreInstantiationEl);
        
    }
    
    private static Document readXMLStreamAsDocument(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        DocumentBuilder builder = f.newDocumentBuilder();
        return builder.parse(is);
    }
    
    public Document getDocument() throws ParserConfigurationException {
        if (doc == null) {
            processPBCoreRows();
        }
        return doc;
    }

    public void writeOutXML(OutputStream os) throws TransformerException, ParserConfigurationException {
        if (doc == null) {
            processPBCoreRows();
        }
        DOMSource source = new DOMSource(doc);
        StreamResult sResult = new StreamResult(os);
        TransformerFactory tFactory = TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl", null);
        Transformer t = tFactory.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        t.setOutputProperty(OutputKeys.METHOD, "xml");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        t.transform(source, sResult);
    }

    public String getXMLAsString() throws TransformerException, ParserConfigurationException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeOutXML(baos);
        baos.close();
        return new String(baos.toByteArray(), "UTF-8");
    }

    public static class VariablePrecisionDate {
        Integer year;
        Integer month;
        Integer day;

        public boolean equals(Object o) {
            if (o instanceof VariablePrecisionDate) {
                VariablePrecisionDate d = (VariablePrecisionDate) o;
                return toString().equals(d.toString());
            }
            return false;
        }

        public int hashCode() {
            return year + (month*10000) + (day * 1000000);
        }


        public VariablePrecisionDate(int y, int m) {
            year = y;
            month = m;
            day = null;
        }
        
        public VariablePrecisionDate(int y, int m, int d) {
            year = y;
            month = m;
            if (d > 0) {
                day = d;
            }
        }

        public int getYear() {
            return year;
        }

        public int getMonth() {
            return month;
        }

        public boolean hasDay() {
            return day != null;
        }

        public Integer getDay() {
            return day;
        }

        public String toWC3DTF() {
            return year + "-" + new DecimalFormat("00").format(month);
        }

        public String getMonthString() {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.MONTH, month - 1);
            return new SimpleDateFormat("MMMM").format(c.getTime());
        }

        public String toString() {
            return year + "-" + month + "-" + (day == null ? "00" : String.valueOf(day));
        }
    }
}
