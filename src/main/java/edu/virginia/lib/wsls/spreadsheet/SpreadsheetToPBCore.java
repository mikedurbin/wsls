package edu.virginia.lib.wsls.spreadsheet;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class SpreadsheetToPBCore {

    public static final String PBCORE_NS = "http://www.pbcore.org/PBCore/PBCoreNamespace.html";
    public static final String PBCORE_XSD_LOC = "http://www.pbcore.org/xsd/pbcore-2.0.xsd";
    public static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
    

    
    public static void main(String[] args) throws Exception {
        // parse the spreadsheet
        Workbook wb = null;
        try {
            wb = new HSSFWorkbook(SpreadsheetToPBCore.class.getClassLoader().getResourceAsStream("example/7622.xlsx"));
        } catch (OfficeXmlFileException ex) {
            wb = new XSSFWorkbook(SpreadsheetToPBCore.class.getClassLoader().getResourceAsStream("example/7622.xlsx"));
        }

        Sheet sheet = wb.getSheetAt(0);
        
        FileOutputStream fos = new FileOutputStream("pbcore.zip");
        ZipOutputStream zos = new ZipOutputStream(fos);
        // output the pbCore document
        ColumnMapping m = new ColumnMapping(sheet.getRow(0));
        System.out.println("Processing " + sheet.getLastRowNum() + " records...");
        for (int i = 1; i <= sheet.getLastRowNum(); i ++) {
            PBCoreRow row = new ColumnNameBasedPBCoreRow(sheet.getRow(i), m);
            String id = row.getId();
            System.out.println(id);
            zos.putNextEntry(new ZipEntry(id + ".xml"));
            
            // create a document
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            Document pbcoreDoc = f.newDocumentBuilder().newDocument();
            
            // add the fields
            Element root = pbcoreDoc.createElementNS(PBCORE_NS, "pbcoreDescriptionDocument");
            root.setAttributeNS(XSI_NS, "xsi:schemaLocation", PBCORE_NS + " " + PBCORE_XSD_LOC);
            pbcoreDoc.appendChild(root);
            
            String type = "clip";
            addPBCoreElement(root, "pbcoreAssetType", type);
            
            String date = row.getAssetDate();
            addPBCoreElement(root, "pbcoreAssetDate", date, "content");
            
            addPBCoreElement(root, "pbcoreIdentifier", row.getId(), "source", "uva");
            
            addPBCoreElement(root, "pbcoreTitle", row.getTitle());
            
            for (String topic : row.getTopics()) {
                addPBCoreElement(root, "pbcoreSubject", topic, "subjectType", "Topic", "source", "LCSH");
            }
            
            addPBCoreElement(root, "pbcoreSubject", row.getPlace(), "subjectType", "Place", "source", "LCSH");

            for (String entity : row.getEntitiesLCSH()) {
                addPBCoreElement(root, "pbcoreSubject", entity, "subjectType", "Entity");
            }
            
            for (String entity : row.getEntities()) {
                addPBCoreElement(root, "pbcoreSubject", entity, "subjectType", "Entity");
            }
            
            addPBCoreElement(root, "pbcoreDescription", row.getAbstract(), "descriptionType", "abstract");
            
            //Element instanceEl = pbcoreDoc.createElementNS(PBCORE_NS, "pbcoreInstantiation");
            //addPBCoreElement(instanceEl, "instantiationIdentifier", row.getId(), "source", "uva");
            //addPBCoreElement(instanceEl, "instantiationLocation", "TBD");
            //addPBCoreElement(instanceEl, "instantiationDuration", row.getDuration());
            //addPBCoreElement(instanceEl, "instantiationColors", row.getColors());
            //addPBCoreElement(instanceEl, "instantiationLanguage", "eng");
            //root.appendChild(instanceEl);
            
            
            appendInstantiationIfAvailable(root, SpreadsheetToPBCore.class.getClassLoader().getResourceAsStream("example/" + row.getId() + "_PBCore_TECH.xml"));
            appendInstantiationIfAvailable(root, SpreadsheetToPBCore.class.getClassLoader().getResourceAsStream("example/" + row.getId() + "_H_PBCore_TECH.xml"));
            appendInstantiationIfAvailable(root, SpreadsheetToPBCore.class.getClassLoader().getResourceAsStream("example/" + row.getId() + "_L_PBCore_TECH.xml"));
            
            writeOutXMLDocument(pbcoreDoc, zos);
            zos.closeEntry();
            
        }
        
        zos.close();
         
         
    }
    
    private static void appendInstantiationIfAvailable(Element parent, InputStream is) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
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

        Element pbcoreInstantiationEl = parent.getOwnerDocument().createElementNS(PBCORE_NS, "pbcoreInstantiation");
        String id = ((String) xpath.evaluate("pbcore:instantiationLocation", doc.getDocumentElement(), XPathConstants.STRING)).replace(".mov", "");
        addPBCoreElement(pbcoreInstantiationEl, "instantiationIdentifier", id, "source", "uva");
        NodeList children = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i ++) {
            Node node = children.item(i);
            node = node.cloneNode(true);
            pbcoreInstantiationEl.getOwnerDocument().adoptNode(node);
            pbcoreInstantiationEl.appendChild(node);
        }
        parent.appendChild(pbcoreInstantiationEl);
        
    }
    
    private static Document readXMLStreamAsDocument(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        DocumentBuilder builder = f.newDocumentBuilder();
        return builder.parse(is);
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
     * Serializes a DOM to an XML String which is returned.
     */
    public static void writeOutXMLDocument(Document doc, OutputStream os) throws TransformerException, IOException {
        DOMSource source = new DOMSource(doc);
        StreamResult sResult = new StreamResult(os);
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer t = tFactory.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        t.setOutputProperty(OutputKeys.METHOD, "xml");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        t.transform(source, sResult);
    }
    
}
