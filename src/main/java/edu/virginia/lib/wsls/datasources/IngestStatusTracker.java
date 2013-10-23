package edu.virginia.lib.wsls.datasources;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class IngestStatusTracker {

    private File ingestLog;

    private Map<String, String> idToPidMap;

    private FileOutputStream out;

    public IngestStatusTracker(File log) throws IOException {
        idToPidMap = new HashMap<String, String>();
        ingestLog = log;
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(log)));
        String line = null;
        while ((line = r.readLine()) != null) {
            String[] split = line.split(",");
            idToPidMap.put(split[0], split[1]);
        }
        r.close();
    }

    public boolean hasBeenIngested(String id) {
        return idToPidMap.containsKey(id);
    }

    public int getAlreadyIngestedCount() {
        return idToPidMap.size();
    }

    public void notifyIngest(String id, String pid) throws IOException {
        if (idToPidMap.containsKey(id)) {
            return;
        }
        idToPidMap.put(id, pid);
        if (out == null) {
            out = new FileOutputStream(ingestLog, true);
        }
        out.write((id + "," + pid + "\n").getBytes("UTF-8"));
        out.flush();
    }

}
