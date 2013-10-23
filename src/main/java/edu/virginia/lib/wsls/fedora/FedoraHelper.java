package edu.virginia.lib.wsls.fedora;

/**
 * Static methods that provide useful operations in Fedora.
 * These may either be referenced directly or via static
 * imports to other classes.
 */
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.yourmediashelf.fedora.client.FedoraClient;

public class FedoraHelper {

    public static final String FEDORA_RELS = "info:fedora/fedora-system:def/relations-external#";
    public static final String UVA_RELS = "http://fedora.lib.virginia.edu/relationships#";

    
    /**
     * Gets the objects of the given predicate for which the subject is give given subject.
     * For example, a relationship like "[subject] hasMarc [object]" this method would always
     * return marc record objects for the given subject.  
     * @param fc the fedora client that mediates access to fedora
     * @param subject the pid of the subject that will have the given predicate relationship 
     * to all objects returned.
     * @param predicate the predicate to query
     * @return the URIs of the objects that are related to the given subject by the given
     * predicate
     */
    public static List<String> getObjects(FedoraClient fc, String subject, String predicate) throws Exception {
        if (predicate == null) {
            throw new NullPointerException("predicate must not be null!");
        }
        String itqlQuery = "select $object from <#ri> where <info:fedora/" + subject + "> <" + predicate + "> $object";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("simple").execute(fc).getEntityInputStream()));
        List<String> pids = new ArrayList<String>();
        String line = null;
        Pattern p = Pattern.compile("\\Qobject : <info:fedora/\\E([^\\>]+)\\Q>\\E");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                pids.add(m.group(1));
            }
        }
        return pids;
    }
    
    /**
     * Gets the subjects of the given predicate for which the object is give given object.
     * For example, a relationship like "[subject] follows [object]" this method would always
     * return the subject that comes before the given object.  
     * @param fc the fedora client that mediates access to fedora
     * @param object the pid of the object that will have the given predicate relationship
     * to all subjects returned.
     * @param predicate the predicate to query
     * @return the URIs of the subjects that are related to the given object by the given
     * predicate
     */

    public static List<String> getSubjects(FedoraClient fc, String predicate, String object) throws Exception {
        if (predicate == null) {
            throw new NullPointerException("predicate must not be null!");
        }
        String itqlQuery = "select $subject from <#ri> where $subject <" + predicate + "> <info:fedora/" + object + ">";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("simple").execute(fc).getEntityInputStream()));
        List<String> pids = new ArrayList<String>();
        String line = null;
        Pattern p = Pattern.compile("\\Qsubject : <info:fedora/\\E([^\\>]+)\\Q>\\E");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                pids.add(m.group(1));
            }
        }
        return pids;
    }
    
    /**
     * Gets the subjects of the given predicate for which the object is give given object.
     * For example, a relationship like "[subject] follows [object]" this method would always
     * return the subject that comes before the given object.  
     * @param fc the fedora client that mediates access to fedora
     * @param object the pid of the object that will have the given predicate relationship
     * to all subjects returned.
     * @param predicate the predicate to query
     * @return the URIs of the subjects that are related to the given object by the given
     * predicate
     */

    public static List<String> getSubjectsWithLiteral(FedoraClient fc, String predicate, String literal) throws Exception {
        if (predicate == null) {
            throw new NullPointerException("predicate must not be null!");
        }
        String itqlQuery = "select $subject from <#ri> where $subject <" + predicate + "> '" + literal + "'";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("simple").execute(fc).getEntityInputStream()));
        List<String> pids = new ArrayList<String>();
        String line = null;
        Pattern p = Pattern.compile("\\Qsubject : <info:fedora/\\E([^\\>]+)\\Q>\\E");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                pids.add(m.group(1));
            }
        }
        return pids;
    }

    public static String getFirstPart(FedoraClient fc, String parent, String isPartOfPredicate, String followsPredicate) throws Exception {
        String itqlQuery = "select $object from <#ri> where $object <" + isPartOfPredicate + "> <info:fedora/" + parent + "> minus $object <" + followsPredicate + "> $other";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("simple").execute(fc).getEntityInputStream()));
        List<String> pids = new ArrayList<String>();
        String line = null;
        Pattern p = Pattern.compile("\\Qobject : <info:fedora/\\E([^\\>]+)\\Q>\\E");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                pids.add(m.group(1));
            }
        }
        if (pids.isEmpty()) {
            return null;
        } else if (pids.size() == 1) {
            return pids.get(0);
        } else {
            throw new RuntimeException(parent + ": Multiple items are \"first\"! " + pids.get(0) + ", " + pids.get(1) + ")");
        }
    }

    public static List<String> getOrderedParts(FedoraClient fc, String parent, String isPartOfPredicate, String followsPredicate) throws Exception {
        String itqlQuery = "select $object $previous from <#ri> where $object <" + isPartOfPredicate + "> <info:fedora/" + parent + "> and $object <" + followsPredicate + "> $previous";
        BufferedReader reader = new BufferedReader(new InputStreamReader(FedoraClient.riSearch(itqlQuery).lang("itql").format("csv").execute(fc).getEntityInputStream()));
        Map<String, String> prevToNextMap = new HashMap<String, String>();
        String line = reader.readLine(); // read the csv labels
        Pattern p = Pattern.compile("\\Qinfo:fedora/\\E([^,]*),\\Qinfo:fedora/\\E([^,]*)");
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                prevToNextMap.put(m.group(2), m.group(1));
            } else {
                throw new RuntimeException(line + " does not match pattern!");
            }
        }
        
        List<String> pids = new ArrayList<String>();
        String pid = getFirstPart(fc, parent, isPartOfPredicate, followsPredicate);
        if (pid == null && !prevToNextMap.isEmpty()) {
            throw new RuntimeException("There is no first child of " + parent + " (among " + prevToNextMap.size() + ")");
            // this is to handle some broke objects... in effect it treats
            // objects whose "previous" is not a sibling as if they had
            // no "previous"
            /*
            for (String prev : prevToNextMap.keySet()) {
                if (!prevToNextMap.values().contains(prev)) {
                    if (pid == null) {
                        pid = prev;
                    } else {
                        throw new RuntimeException("Two \"first\" children!");
                    }
                }
            }
            */
        }
        while (pid != null) {
            pids.add(pid);
            String nextPid = prevToNextMap.get(pid);
            prevToNextMap.remove(pid);
            pid = nextPid;
            
        }
        if (!prevToNextMap.isEmpty()) {
            for (Map.Entry<String, String> entry : prevToNextMap.entrySet()) {
                //System.err.println(entry.getKey() + " --> " + entry.getValue());
            }
            throw new RuntimeException("Broken relationship chain in children of " + parent);
        }
        return pids;
    }
    

    /**
     * A helper method that updates the relationships of the objects described
     * by the provided pids to insert the object with "pid" between the object
     * specified as previous and following.
     * @throws Exception 
     */
    public static void insertBetween(FedoraClient fc, String pid, String previousPid, String followingPid) throws Exception {
        /*
        //System.out.println("Inserting: " + previousPid + " --next--> " + pid + " --next--> " + followingPid);
        if (previousPid != null) {
            FedoraClient.addRelationship(pid).predicate(UVA_RELS + "follows").object("info:fedora/" + previousPid).execute(fc);
            //System.out.println("  " + pid + " follows " + previousPid);
        }
        if (followingPid != null) {
            // delete any existing previous relationships
            // This is complicated... on subsequent runs of this application
            // the previous pid for that object may *not* point to this object
            // because of the evolving and half-complete nature of the cache
            for (String uri : getPreviousObjectURIsFromRelsExt(fc, followingPid)) {
                FedoraClient.purgeRelationship(followingPid).predicate(UVA_RELS + "follows").object(uri).execute(fc);
            }

            FedoraClient.addRelationship(followingPid).predicate(UVA_RELS + "follows").object("info:fedora/" + pid).execute(fc);
            //System.out.println("  " + followingPid + " follows " + pid);
        }
        */
    }

    public static void setParent(FedoraClient fc, String pid, String parent) throws Exception {
        // clear any existing
        for (String uri : getParentObjectURIsFromRelsExt(fc, pid)) {
            System.out.println("PURGING " + pid + " -isPartOf-> " + uri.substring("info:fedora/".length()));
            FedoraClient.purgeRelationship(pid).predicate(FEDORA_RELS + "isPartOf").object(uri).execute(fc);
        }
        if (parent != null) {
            System.out.println("ADDING " + pid + " -isPartOf-> " + parent);
            FedoraClient.addRelationship(pid).predicate(FEDORA_RELS + "isPartOf").object("info:fedora/" + parent).execute(fc);
        }
    }

    public static void setFollows(FedoraClient fc, String pid, String prev) throws Exception {
        List<String> currentlyFollowing = getPreviousObjectURIsFromRelsExt(fc, pid);
        if (prev == null) {
            // clear any existing 
            for (String uri : currentlyFollowing) {
                System.out.println("PURGING " + uri.substring("info:fedora/".length()) + " --> " + pid);
                FedoraClient.purgeRelationship(pid).predicate(UVA_RELS + "follows").object(uri).execute(fc);
            }
        } else {
            boolean hasExistingFollowRelationship = false;
            for (String uri : currentlyFollowing) {
                if (uri.equals("info:fedora/" + prev)) {
                    hasExistingFollowRelationship = true;
                } else {
                    System.out.println("PURGING " + uri.substring("info:fedora/".length()) + " --> " + pid);
                    FedoraClient.purgeRelationship(pid).predicate(UVA_RELS + "follows").object(uri).execute(fc);
                }
            }
            if (!hasExistingFollowRelationship) {
                System.out.println("ADDING " + prev + " --> " + pid);
                FedoraClient.addRelationship(pid).predicate(UVA_RELS + "follows").object("info:fedora/" + prev).execute(fc);
            }
        }
    }

    public static List<String> getPreviousObjectURIsFromRelsExt(FedoraClient fc, String pid) throws Exception {
        List<String> pids = new ArrayList<String>();
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        DocumentBuilder builder = f.newDocumentBuilder();
        Document relsExtDoc = builder.parse(FedoraClient.getDatastreamDissemination(pid, "RELS-EXT").execute(fc).getEntityInputStream());
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {

            public String getNamespaceURI(String prefix) {
                if (prefix.equals("rdf")) {
                    return "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
                } else if (prefix.equals("uva")) {
                    return "http://fedora.lib.virginia.edu/relationships#";
                } else {
                    return null;
                }
            }

            public String getPrefix(String uri) {
                if (uri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#")) {
                    return "rdf";
                } else if (uri.equals("http://fedora.lib.virginia.edu/relationships#")) {
                    return "uva";
                } else {
                    return null;
                }
            }

            public Iterator getPrefixes(String uri) {
                if (uri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#")) {
                    return Arrays.asList(new String[] { "rdf" }).iterator();
                } else if (uri.equals("http://fedora.lib.virginia.edu/relationships#")) {
                    return Arrays.asList(new String[] { "uva" }).iterator();

                } else {
                    return null;
                }
            }});
        
        NodeList nl = ((NodeList) xpath.evaluate("rdf:RDF/rdf:Description/uva:follows", relsExtDoc, XPathConstants.NODESET));
        for (int i = 0; i < nl.getLength(); i ++) {
            pids.add((String) xpath.evaluate("@rdf:resource", nl.item(i), XPathConstants.STRING));
        }
        return pids;
    }

    public static List<String> getParentObjectURIsFromRelsExt(FedoraClient fc, String pid) throws Exception {
        List<String> pids = new ArrayList<String>();
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        DocumentBuilder builder = f.newDocumentBuilder();
        Document relsExtDoc = builder.parse(FedoraClient.getDatastreamDissemination(pid, "RELS-EXT").execute(fc).getEntityInputStream());
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {

            public String getNamespaceURI(String prefix) {
                if (prefix.equals("rdf")) {
                    return "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
                } else if (prefix.equals("rels")) {
                    return "info:fedora/fedora-system:def/relations-external#";
                } else {
                    return null;
                }
            }

            public String getPrefix(String uri) {
                if (uri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#")) {
                    return "rdf";
                } else if (uri.equals("info:fedora/fedora-system:def/relations-external#")) {
                    return "rels";
                } else {
                    return null;
                }
            }

            public Iterator getPrefixes(String uri) {
                if (uri.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#")) {
                    return Arrays.asList(new String[] { "rdf" }).iterator();
                } else if (uri.equals("info:fedora/fedora-system:def/relations-external#")) {
                    return Arrays.asList(new String[] { "rels" }).iterator();

                } else {
                    return null;
                }
            }});

        NodeList nl = ((NodeList) xpath.evaluate("rdf:RDF/rdf:Description/rels:isPartOf", relsExtDoc, XPathConstants.NODESET));
        for (int i = 0; i < nl.getLength(); i ++) {
            pids.add((String) xpath.evaluate("@rdf:resource", nl.item(i), XPathConstants.STRING));
        }
        return pids;
    }
}
