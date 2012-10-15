package edu.virginia.lib.wsls.spreadsheet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;

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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class PBCoreDocument {

    public static final String PBCORE_NS = "http://www.pbcore.org/PBCore/PBCoreNamespace.html";
    public static final String PBCORE_XSD_LOC = "http://www.pbcore.org/xsd/pbcore-2.0.xsd";
    public static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
    
    private Document doc; 
    
    private String id;
    
    public PBCoreDocument(PBCoreSpreadsheetRow row) throws ParserConfigurationException {
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
        
        String date = row.getAssetDate();
        addPBCoreElement(root, "pbcoreAssetDate", date, "content");
        
        addPBCoreElement(root, "pbcoreIdentifier", row.getId(), "source", "uva");
        id = row.getId();
        
        addPBCoreElement(root, "pbcoreTitle", row.getTitle());
        
        for (String topic : row.getTopics()) {
            addPBCoreElement(root, "pbcoreSubject", topic, "subjectType", "Topic", "source", "LCSH");
        }
        
        for (String place : row.getPlaces()) {
            addPBCoreElement(root, "pbcoreSubject", place, "subjectType", "Place", "source", "LCSH");
        }

        for (String entity : row.getEntitiesLCSH()) {
            addPBCoreElement(root, "pbcoreSubject", entity, "subjectType", "Entity", "source", "LCSH");
        }
        
        addPBCoreElement(root, "pbcoreDescription", row.getAbstract(), "descriptionType", "abstract");

        Element instanceEl = doc.createElementNS(PBCORE_NS, "pbcoreInstantiation");
        addPBCoreElement(instanceEl, "instantiationIdentifier", row.getId(), "source", "uva");
        addPBCoreElement(instanceEl, "instantiationLocation", row.getInstantiationLocation());
        addPBCoreElement(instanceEl, "instantiationDuration", row.getInstantiationDuration());
        addPBCoreElement(instanceEl, "instantiationColors", row.getInstantiationColors());
        addPBCoreElement(instanceEl, "instantiationAnnotation", row.getInstantiationAnnotation());
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
    
    public String getId() {
        return id;
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
    
    public Document getDocument() {
        return doc;
    }
    
    public void writeOutXML(OutputStream os) throws TransformerException {
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
    
}
