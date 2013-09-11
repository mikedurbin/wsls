package edu.virginia.lib.wsls.util;

import java.io.File;
import java.util.Properties;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;

import edu.virginia.lib.wsls.datasources.FedoraRepository;

public class FindBrokenObject {

    public static void main(String args[]) throws Exception {
        Properties p = new Properties();
        p.load(FindBrokenObject.class.getClassLoader().getResourceAsStream("conf/fedora.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        p.load(FindBrokenObject.class.getClassLoader().getResourceAsStream("conf/ingest.properties"));

        FedoraRepository fedora = new FedoraRepository(fc, new File(p.getProperty("pid-registry-root")));
        fedora.testAllPids();
    }

}
