package edu.virginia.lib.wsls.util;

import static edu.virginia.lib.wsls.fedora.FedoraHelper.getSubjects;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

import edu.virginia.lib.wsls.datasources.FedoraRepository;
import edu.virginia.lib.wsls.solr.PostSolrDocument;

import static edu.virginia.lib.wsls.fedora.FedoraHelper.getObjects;

public class ItemRemover {

    public static final String IS_PART_OF = "info:fedora/fedora-system:def/relations-external#isPartOf";
    
    /**
     * Two items were determined to have been ingested that were copyrighted.
     * This program removes those two items from fedora, removes them from the
     * index and reindexes their ancestors.
     */
    public static void main(String [] args) throws Exception {
        Properties p = new Properties();
        p.load(PostSolrDocument.class.getClassLoader().getResourceAsStream("conf/fedora.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        ItemRemover i = new ItemRemover(fc);

        i.removeItems(args);
    }

    private FedoraClient fc;

    private PostSolrDocument solr;

    public ItemRemover(FedoraClient fc) throws IOException {
        this.fc = fc;
        this.solr = new PostSolrDocument();
    }


    public void removeItems(String[] pids) throws Exception {
        Set<String> ancestorPids = new HashSet<String>();
        for (String pid : pids) {
            findAncestors(pid, ancestorPids);
        }
        System.out.println(ancestorPids.size() + " ancestors found");

        for (String pid : pids) {
            FedoraClient.purgeRelationship(pid).object("info:fedora/" + getObjects(fc, pid, IS_PART_OF).get(0)).predicate(IS_PART_OF).execute(fc);
            FedoraClient.purgeRelationship(pid).object("info:fedora/uva-lib:pbcore2CModel").predicate("info:fedora/fedora-system:def/model#hasModel").execute(fc); // this will prevent it from being indexed later
            System.out.println("Deleting " + pid + " from index.");
            solr.purgeRecord(pid);
        }

        System.out.println("Waiting for RI...");
        Thread.sleep(30000);
        FedoraRepository fedora = new FedoraRepository(fc, new File("/home/md5wz/Documents/projects/WSLS/Analysis/ProductionPidRegistries"));
        fedora.fixRelationships();

        System.out.println("Waiting for RI...");
        Thread.sleep(30000);
        for (String pid : ancestorPids) {
            System.out.println("Reindexing " + pid);
            solr.indexPid(pid, fc, true);
        }
        solr.commit();
    }

    private String findAnchorScriptPid(String pid) throws Exception {
        return getSubjects(fc, "http://fedora.lib.virginia.edu/wsls/relationships#isAnchorScriptFor", pid).get(0);
    }
    private void findAncestors(String pid, Set<String> ancestors) throws Exception {
        List<String> parents = getObjects(fc, pid, "info:fedora/fedora-system:def/relations-external#isPartOf");
        if (!parents.isEmpty()) {
            for (String parentPid : parents) {
                if (ancestors.contains(parentPid)) {
                    // already found
                } else {
                    findAncestors(parentPid, ancestors);
                    ancestors.add(parentPid);
                }
            }
        }
    }
}
