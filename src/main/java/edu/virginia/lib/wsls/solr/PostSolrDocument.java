package edu.virginia.lib.wsls.solr;

import static edu.virginia.lib.wsls.fedora.FedoraHelper.getSubjects;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Properties;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

public class PostSolrDocument {
    
    public static void main(String [] args) throws Exception {
        Properties p = new Properties();
        p.load(PostSolrDocument.class.getClassLoader().getResourceAsStream("conf/fedora.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        p.load(PostSolrDocument.class.getClassLoader().getResourceAsStream("conf/solr.properties"));
        
        for (String pid : getSubjects(fc, "info:fedora/fedora-system:def/model#hasModel", "uva-lib:pbcore2CModel")) {
            try {
                indexPid(fc, pid, p.getProperty("solr.update"), "uva-lib:indexableSDef", "getIndexingMetadata");
            } catch (Throwable t) {
                System.out.println("Unable to index " + pid);
            }
        }
    }
    
    public static void indexPid(FedoraClient fc, String pid, String updateUrl, String servicePid, String serviceMethod) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeStreamToStream(FedoraClient.getDissemination(pid, servicePid, serviceMethod).execute(fc).getEntityInputStream(), baos);
        
        HttpClient client = new HttpClient();

        PostMethod post = new PostMethod(updateUrl);
        Part[] parts = {
                new FilePart("add.xml", new ByteArrayPartSource("add.xml", baos.toByteArray()))
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
    
    public static void writeStreamToStream(InputStream is, OutputStream os) throws IOException {
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
