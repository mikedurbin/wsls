package edu.virginia.lib.wsls.util;

import com.yourmediashelf.fedora.client.FedoraClient;
import com.yourmediashelf.fedora.client.FedoraCredentials;
import edu.virginia.lib.wsls.datasources.FedoraRepository;
import edu.virginia.lib.wsls.datasources.PIDRegistry;
import edu.virginia.lib.wsls.datasources.WSLSMasterSpreadsheetArray;
import edu.virginia.lib.wsls.googledrive.DriveHelper;
import edu.virginia.lib.wsls.spreadsheet.PBCoreDocument;
import edu.virginia.lib.wsls.spreadsheet.PBCoreSpreadsheetRow;

import java.io.File;
import java.util.Properties;

public class PidRegistryRebuilder {

    public static void main(String [] args) throws Exception {
        // initialize connection to fedora
        Properties p = new Properties();
        p.load(PidRegistryRebuilder.class.getClassLoader().getResourceAsStream("conf/fedora.properties"));
        FedoraClient fc = new FedoraClient(new FedoraCredentials(p.getProperty("fedora-url"), p.getProperty("fedora-username"), p.getProperty("fedora-password")));

        p.load(PidRegistryRebuilder.class.getClassLoader().getResourceAsStream("conf/ingest.properties"));

        File snapshotDir = new File(p.getProperty("snapshot-dir"));

        Iterable<PBCoreSpreadsheetRow> master = new WSLSMasterSpreadsheetArray(new DriveHelper(snapshotDir));

        new PidRegistryRebuilder().rebuildItemDates(new FedoraRepository(fc, new File(p.getProperty("pid-registry-root"))).getPIDRegistry(), master, false);
    }

    public void rebuildItemDates(PIDRegistry pids, Iterable<PBCoreSpreadsheetRow> rows, boolean testRun) throws Exception {
        /**
        String collectionPid = "uva-lib:2214294";
        getPidDate(collectionPid, fc);
        for (String yearPid : getSubjects(fc, "info:fedora/fedora-system:def/relations-external#isPartOf", collectionPid)) {
            getPidDate(yearPid, fc);
            for (String monthPid : getSubjects(fc, "info:fedora/fedora-system:def/relations-external#isPartOf", yearPid)) {
                getPidDate(monthPid, fc);
            }
        }

        for (String pid : getSubjects(fc, "info:fedora/fedora-system:def/model#hasModel", "uva-lib:pbcore2CModel")) {
            getPidDate(pid, fc);
        }
         */
        StringBuffer report = new StringBuffer();
        int i = 0;
        for (PBCoreSpreadsheetRow r : rows) {
            String pid = pids.getPIDForWSLSID(r.getId());
            if (pid != null && r.getProcessingCode() != 4) {
                PBCoreDocument d = new PBCoreDocument(r);
                PBCoreDocument.VariablePrecisionDate d1 = d.getAssetVariablePrecisionDate();
                PBCoreDocument.VariablePrecisionDate d2 = pids.getDateForPid(pid);
                if (((d1 == null || d2 == null) && d1 != d2) || !d1.equals(d2)) {
                    if (!testRun) {
                        System.out.print("O");
                        pids.setPIDforWSLSID(r.getId(), pid, d);
                        report.append(r.getId() + " (" + pid + "): " + d2 + " => " + d1 + "\n");
                    } else {
                        System.out.print("X");
                        report.append(r.getId() + " (" + pid + "): " + d1 + " != " + d2 + "\n");
                    }
                } else {
                    System.out.print(".");
                }
                if (++ i % 80 == 0) {
                    System.out.println();
                }
                System.out.flush();
            }
        }
        System.out.println("\n\n" + report.toString());
    }
}
