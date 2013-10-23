package edu.virginia.lib.wsls.googledrive;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import org.apache.poi.util.IOUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

public class DriveHelper {

    public static void main(String [] args) throws Exception {
        DriveHelper d = new DriveHelper();
        for (java.io.File f : d.getSpreadsheets()) {
            System.out.println(f.getName());
        }

        System.out.println(d.getCatalogerSpreadsheet().getName());
    }

    private Drive d;

    private String folderId;

    private String catalogerSpreadsheetId;

    private java.io.File cacheDir;

    private List<java.io.File> spreadsheetFiles;

    private java.io.File catalogerSpreadsheet;

    private java.io.File snapshotDir;

    public DriveHelper(java.io.File snapshotDir) throws IOException, GeneralSecurityException {
        this();
        this.snapshotDir = snapshotDir;
    }

    public DriveHelper() throws IOException, GeneralSecurityException {
        Properties p = new Properties();
        p.load(getClass().getClassLoader()
                .getResourceAsStream("conf/gdrive.properties"));
        HttpTransport httpTransport = new NetHttpTransport();
        JsonFactory jsonFactory = new JacksonFactory();
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setServiceAccountId(p.getProperty("service-account"))
                .setServiceAccountScopes(Collections.singleton(DriveScopes.DRIVE))
                .setServiceAccountPrivateKeyFromP12File(
                        new java.io.File(p.getProperty("private-key-file")))
                .build();

        d = new Drive.Builder(httpTransport, jsonFactory, null)
                .setApplicationName("WSLS Ingester")
                .setHttpRequestInitializer(credential).build();

        folderId = p.getProperty("folder-id");
        catalogerSpreadsheetId = p.getProperty("cataloging-spreadsheet-id");

        if (!p.containsKey("cache-dir")) {
            throw new IllegalArgumentException("Required property \"cache-dir\" not present!");
        }
        cacheDir = new java.io.File(p.getProperty("cache-dir"));
    }

    public java.io.File getCatalogerSpreadsheet() throws IOException {
        if (catalogerSpreadsheet != null) {
            return catalogerSpreadsheet;
        }
        if (!cacheDir.exists()) {
            System.out.println("Initializing cache directory \"" + cacheDir.getAbsolutePath() + "\".");
            cacheDir.mkdirs();
        }
        catalogerSpreadsheet = getCurrentFile(d.files().get(catalogerSpreadsheetId).execute());
        return catalogerSpreadsheet;
    }

    public List<java.io.File> getSpreadsheets() throws IOException {
        if (spreadsheetFiles != null) {
            return spreadsheetFiles;
        }
        List<File> files = new ArrayList<File>();
        for (ChildReference c : d.children().list(folderId).execute().getItems()) {
            File f = d.files().get(c.getId()).execute();
            files.add(f);
        }
        Collections.sort(files, new Comparator<File>() {
            public int compare(File o1, File o2) {
                return o1.getTitle().compareTo(o2.getTitle());
            }
        });
        List<java.io.File> result = new ArrayList<java.io.File>();
        if (!cacheDir.exists()) {
            System.out.println("Initializing cache directory \"" + cacheDir.getAbsolutePath() + "\".");
            cacheDir.mkdirs();
        }
        for (File f : files) {
            result.add(getCurrentFile(f));
            //System.out.println(f.getTitle());
        }
        spreadsheetFiles = result;
        return spreadsheetFiles;
    }

    private java.io.File getCurrentFile(File f) throws IOException {
        if (snapshotDir != null) {
            return new java.io.File(snapshotDir, f.getId() + ".xlsx");
        }
        java.io.File cacheFile = new java.io.File(cacheDir, f.getId() + ".xlsx");
        java.io.File infoFile = new java.io.File(cacheDir, f.getId() + "-info.properties");
        if (!infoFile.exists()) {
            downloadFile(f, cacheFile, infoFile);
        } else {
            Properties info = new Properties();
            FileInputStream fis = new FileInputStream(infoFile);
            try {
                info.load(fis);
            } finally {
                fis.close();
            }
            if (!f.getModifiedDate().toString().equals(info.getProperty("last-modified-date"))) {
                downloadFile(f, cacheFile, infoFile);
            } else {
                System.out.println("Using cached file for spreadsheet \"" + f.getTitle() + "\".");
            }
        }
        return cacheFile;
    }

    private void downloadFile(File file, java.io.File cacheFile, java.io.File infoFile) throws IOException {
        HttpResponse resp = d.getRequestFactory().buildGetRequest(new GenericUrl(file.getExportLinks().get("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))).execute();
        FileOutputStream fos = new FileOutputStream(cacheFile);
        try {
            IOUtils.copy(resp.getContent(), fos);
        } finally {
            fos.close();
        }
        fos = new FileOutputStream(infoFile);
        Properties p = new Properties();
        if (file.getMd5Checksum() != null) {
            p.setProperty("checksum", file.getMd5Checksum());
        }
        p.setProperty("title", file.getTitle());
        p.setProperty("last-modified-by", file.getLastModifyingUserName());
        p.setProperty("last-modified-date", file.getModifiedDate().toString());
        try {
            p.store(fos, "Properties fetched from google on ingest at " + new Date().toString());
        } finally {
            fos.close();
        }
        System.out.println("Downloaded new version of \"" + file.getTitle() + "\".");

    }

    private static String displayList(List<String> list) {
        StringBuffer sb = new StringBuffer();
        for (String item : list) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(item);
        }
        return sb.toString();
    }
}
