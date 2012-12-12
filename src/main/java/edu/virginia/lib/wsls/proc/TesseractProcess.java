package edu.virginia.lib.wsls.proc;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

public class TesseractProcess {

    private String tesseractCommandPath; 
    
    public TesseractProcess() throws IOException {
        if (TesseractProcess.class.getClassLoader().getResource("conf/tesseract.properties") != null) {
            Properties p = new Properties();
            p.load(ImageMagickProcess.class.getClassLoader().getResourceAsStream("conf/tesseract.properties"));
            tesseractCommandPath = p.getProperty("convert-command");
        } else {
            tesseractCommandPath = "tesseract";
        }
    }
    
    public TesseractProcess(String path) {
        tesseractCommandPath = path;
    }
    
    public void generateOCR(File inputTif, File outputTxt) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(tesseractCommandPath, inputTif.getPath(), outputTxt.getPath().replace(".txt", "")).start();
        new Thread(new OutputDrainerThread(p.getInputStream())).start();
        new Thread(new OutputDrainerThread(p.getErrorStream())).start();
        int returnCode = p.waitFor();
        if (returnCode != 0) {
            throw new RuntimeException("Invalid return code for process!");
        }
    }
    
}
