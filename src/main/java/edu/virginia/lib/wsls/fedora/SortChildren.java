package edu.virginia.lib.wsls.fedora;

import static edu.virginia.lib.wsls.fedora.FedoraHelper.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

import edu.virginia.lib.wsls.solr.PostSolrDocument;

public class SortChildren {

    public static void main(String [] args) throws Exception {
        Properties p = new Properties();
        p.load(PostSolrDocument.class.getClassLoader().getResourceAsStream("conf/fedora.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));
    }
    
    private static void sortMonths(FedoraClient fc, String yearPid) throws Exception {
     // pull all of the children of a node
        List<Node> children = new ArrayList<Node>();
        for (String child : getSubjects(fc, "info:fedora/fedora-system:def/relations-external#isPartOf", yearPid)) {
            Node n = new Node();
            n.pid = child;
            n.previousPids = getObjects(fc, child, "http://fedora.lib.virginia.edu/relationships#follows");
            children.add(n);
            System.out.println(n.pid + " follows " + n.previousPids.size() + " other objects");
        }
        // test for dupes
        Set<String> prev = new HashSet<String>();
        boolean broken = false;
        for (Node n : children) {
            for (String previousPid : n.previousPids) {
                if (prev.contains(previousPid)) {
                    broken = true;
                    System.out.println("Two pids follow " + previousPid);
                }
                prev.add(previousPid);
            }
        }

        if (broken) {
            // sort them (by pid)
            Collections.sort(children);
            String follows = null;
            for (Node n : children) {
                for (String previousPid : n.previousPids) {
                    FedoraClient.purgeRelationship(n.pid).predicate("http://fedora.lib.virginia.edu/relationships#follows").object("info:fedora/" + previousPid).execute(fc);
                    System.out.println(n.pid + " follows " + previousPid);
                }
                if (follows != null) {
                    FedoraClient.addRelationship(n.pid).predicate("http://fedora.lib.virginia.edu/relationships#follows").object("info:fedora/" + follows).execute(fc);
                }
                follows = n.pid;
            }
        }

    }
    
    private static void sortEntries(FedoraClient fc, String nodePid) throws Exception {
        // pull all of the children of a node
        List<Node> children = new ArrayList<Node>();
        for (String child : getSubjects(fc, "info:fedora/fedora-system:def/relations-external#isPartOf", nodePid)) {
            Node n = new Node();
            n.pid = child;
            n.previousPids = getObjects(fc, child, "http://fedora.lib.virginia.edu/relationships#follows");
            children.add(n);
            //System.out.println(n.pid + " follows " + n.previousPids.size() + " other objects");
        }
        // test for dupes
        Set<String> prev = new HashSet<String>();
        boolean broken = false;
        for (Node n : children) {
            for (String previousPid : n.previousPids) {
                if (prev.contains(previousPid)) {
                    broken = true;
                    System.out.println("Two pids follow " + previousPid);
                }
                prev.add(previousPid);
            }
        }

        if (broken) {
            // sort them (by pid)
            Collections.sort(children);
            String follows = null;
            for (Node n : children) {
                for (String previousPid : n.previousPids) {
                    FedoraClient.purgeRelationship(n.pid).predicate("http://fedora.lib.virginia.edu/relationships#follows").object("info:fedora/" + previousPid).execute(fc);
                    System.out.println(n.pid + " follows " + previousPid);
                }
                if (follows != null) {
                    FedoraClient.addRelationship(n.pid).predicate("http://fedora.lib.virginia.edu/relationships#follows").object("info:fedora/" + follows).execute(fc);
                }
                follows = n.pid;
            }
        }
    }
    
    private static class Node implements Comparable<Node> {
        String pid;
        List<String> previousPids;

        public int compareTo(Node n) {
            return pid.compareTo(n.pid); 
        }
    }
}
