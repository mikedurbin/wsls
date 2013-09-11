package edu.virginia.lib.wsls.solr;

import static edu.virginia.lib.wsls.fedora.FedoraHelper.getSubjects;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.FilePartSource;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.io.IOUtils;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraClientException;
import com.yourmediashelf.fedora.client.FedoraCredentials;

public class PostSolrDocument {
    public static void main(String [] args) throws Exception {
        PostSolrDocument solr = new PostSolrDocument();
        solr.reindexWSLSCollection(false);
    }

    private FedoraClient fc;

    private String updateUrl;
    private String servicePid;
    private String serviceMethod;

    public PostSolrDocument() throws IOException {
        Properties p = new Properties();
        p.load(PostSolrDocument.class.getClassLoader().getResourceAsStream("conf/fedora.properties"));
        fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        p.load(PostSolrDocument.class.getClassLoader().getResourceAsStream("conf/solr.properties"));
        updateUrl = p.getProperty("solr.update");
        servicePid = "uva-lib:indexableSDef";
        serviceMethod = "getIndexingMetadata";

        System.out.println("Created Solr Indexer to index content from " + p.getProperty("fedora-url") + " into the SOLR index at " + updateUrl + ".");
    }

    private void regenerateCollectionSummary(String collectionPid) throws IOException, FedoraClientException {
        long start = System.currentTimeMillis();
        System.out.println("Regenerating cached hierarchy...");
        try {
            FedoraClient.purgeDatastream(collectionPid, "hierarchy-brief-cached").execute(fc);
        } catch (FedoraClientException ex) {
            // unable to delete datastream
            System.out.println(ex.getMessage());
        }
        File temp = File.createTempFile("hierarchy-brief-cached", ".xml");
        temp.deleteOnExit();
        FileOutputStream o = new FileOutputStream(temp);
        writeStreamToStream(FedoraClient.getDissemination(collectionPid, "uva-lib:hierarchicalMetadataSDef", "getSummary").execute(fc).getEntityInputStream(), o);
        o.close();
        FedoraClient.addDatastream(collectionPid, "hierarchy-brief-cached").content(temp).mimeType("text/xml").controlGroup("M").dsLabel("cached result of uva-lib:hierarchicalMetadataSDef/getSummary").execute(fc);
        long end = System.currentTimeMillis();
        System.out.println("Completed in " + (end - start) + "ms.");
    }
    
    public void reindexWSLSCollection(boolean regenerate) throws Exception {
        String collectionPid = "uva-lib:2214294";
        try {
            List<String> failedPids = new ArrayList<String>();
            indexPid(collectionPid, fc, regenerate);
            for (String yearPid : getSubjects(fc, "info:fedora/fedora-system:def/relations-external#isPartOf", collectionPid)) {
                try {
                    indexPid(yearPid, fc, regenerate);
                } catch (Throwable t) {
                    System.out.println("failed to index year " + yearPid);
                    failedPids.add(yearPid);
                }
                for (String monthPid : getSubjects(fc, "info:fedora/fedora-system:def/relations-external#isPartOf", yearPid)) {
                    try {
                        indexPid(monthPid, fc, regenerate);
                    } catch (Throwable t) {
                        System.out.println(" failed to index month " + monthPid);
                        failedPids.add(monthPid);
                        t.printStackTrace();
                    }
                }
            }
    
            for (String pid : getSubjects(fc, "info:fedora/fedora-system:def/model#hasModel", "uva-lib:pbcore2CModel")) {
                try {
                    indexPid(pid, fc, regenerate);
                } catch (Throwable t) {
                    failedPids.add(pid);
                    t.printStackTrace();
                    System.out.println("Unable to index " + pid);
                }
            }

            //reindex any failures
            for (String pid : failedPids) {
                try {
                    indexPid(pid, fc, regenerate);
                } catch (Throwable t) {
                    t.printStackTrace();
                    System.out.println("Retrying after 30 second...");
                    Thread.sleep(30000);
                    try {
                        indexPid(pid, fc, regenerate);
                    } catch (Throwable t2) {
                        System.out.println("Retrying after 5 minutes...");
                        Thread.sleep(300000);
                        indexPid(pid, fc, regenerate);
                    }
                }
            }

            commit();
            optimize();
        } catch (Throwable t) {
            System.err.println("Error, rolling back index updates!");
            rollback();
        }
    }

    public void indexPid(String pid, FedoraClient fc, boolean regenerate) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!regenerate) {
            try {
                writeStreamToStream(FedoraClient.getDatastreamDissemination(pid, "solrArchive").execute(fc).getEntityInputStream(), baos);
            } catch (Exception ex) {
                //System.out.println("No solrArchive datastream for " + pid + ", regenerating...");
                regenerate = true;
            }
        }
        if (regenerate) {
            if (pid.equals("uva-lib:2214294")) {
                // because of a timeout issue, we cache the result of a dissemination in the collection object
                if (regenerate) {
                    regenerateCollectionSummary(pid);
                }
            }

            writeStreamToStream(FedoraClient.getDissemination(pid, servicePid, serviceMethod).execute(fc).getEntityInputStream(), baos);
            FedoraClient.addDatastream(pid, "solrArchive").content(new String(baos.toByteArray(), "UTF-8")).controlGroup("M").versionable(true).mimeType("text/xml").dsLabel("Index Data for Posting to Solr").execute(fc);
        }

        // validate encoding
        CharsetDecoder d = Charset.forName("UTF-8").newDecoder();
        d.onMalformedInput(CodingErrorAction.REPORT); // this may not be necessary as it may be the default
        d.onUnmappableCharacter(CodingErrorAction.REPORT); // this may not be necessary as it may be the default
        InputStreamReader r = new InputStreamReader(new ByteArrayInputStream(baos.toByteArray()), d);
        IOUtils.readLines(r);
        System.out.println("Encoding is valid!");

        HttpClient client = new HttpClient();

        PostMethod post = new PostMethod(updateUrl);
        Part[] parts = {
                new FilePart("add.xml", new ByteArrayPartSource("add.xml", baos.toByteArray()), "text/xml", "UTF-8")
        };
        post.setRequestEntity(
                new MultipartRequestEntity(parts, post.getParams())
            );
        try {
            client.executeMethod(post);
            int status = post.getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + updateUrl + "\" failed: " + post.getStatusLine() + " (" + pid + ")");
            } else {
                System.out.println("Indexed " + pid);
            }
        } finally {
            post.releaseConnection();
        }
    }

    public void purgeRecord(String pid) throws HttpException, IOException {
        String url = updateUrl + "?stream.body=" + URLEncoder.encode("<delete><query>id:\"" + pid + "\"</query></delete>", "UTF-8");
        GetMethod get = new GetMethod(url);
        try {
            HttpClient client = new HttpClient();
            client.executeMethod(get);
            int status = get.getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + url + "\" failed: " + get.getStatusLine());
            }
            System.out.println("Purged record for pid " + pid);
        } finally {
            get.releaseConnection();
        }
    }

    public void postFile(File f) throws HttpException, IOException {
        HttpClient client = new HttpClient();

        PostMethod post = new PostMethod(updateUrl);
        Part[] parts = {
                new FilePart("add.xml", new FilePartSource("add.xml", f), "text/xml", "UTF-8")
        };
        post.setRequestEntity(
                new MultipartRequestEntity(parts, post.getParams())
            );
        try {
            client.executeMethod(post);
            int status = post.getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + updateUrl + "\" failed: " + post.getStatusLine());
            } else {
                System.out.println("Posted " + f.getPath());
            }
        } finally {
            post.releaseConnection();
        }
    }

    public void commit() throws HttpException, IOException {
        String url = updateUrl + "?stream.body=%3Ccommit/%3E";
        GetMethod get = new GetMethod(url);
        try {
            HttpClient client = new HttpClient();
            client.executeMethod(get);
            int status = get.getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + url + "\" failed: " + get.getStatusLine());
            }
            System.out.println("Committed changes");
        } finally {
            get.releaseConnection();
        }
    }

    public void rollback() throws HttpException, IOException {
        String url = updateUrl + "?stream.body=%3Crollback/%3E";
        GetMethod get = new GetMethod(url);
        try {
            HttpClient client = new HttpClient();
            client.executeMethod(get);
            int status = get.getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + url + "\" failed: " + get.getStatusLine());
            }
        } finally {
            get.releaseConnection();
        }
    }

    public void optimize() throws HttpException, IOException {
        String url = updateUrl + "?stream.body=%3Coptimize/%3E";
        GetMethod get = new GetMethod(url);
        try {
            HttpClient client = new HttpClient();
            client.executeMethod(get);
            int status = get.getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new RuntimeException("REST action \"" + url + "\" failed: " + get.getStatusLine());
            }
            System.out.println("Optimized index");
        } finally {
            get.releaseConnection();
        }
    }
    
    public void writeStreamToStream(InputStream is, OutputStream os) throws IOException {
        ReadableByteChannel inputChannel = Channels.newChannel(is);  
        WritableByteChannel outputChannel = Channels.newChannel(os);  
        ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);  
        while (inputChannel.read(buffer) != -1) {  
            buffer.flip();  
            outputChannel.write(buffer);  
            buffer.compact();  
        }  
        buffer.flip();  
        while (buffer.hasRemaining()) {  
            outputChannel.write(buffer);  
        }  
       inputChannel.close();  
       outputChannel.close();
    }    

}
