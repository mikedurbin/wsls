package edu.virginia.lib.wsls.proc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * A thin wrapper around the ImageMagick's "convert" utility.
 * For this class to work, the "convert" utility must be in the
 * path (ie, executable with the simple command "convert") or 
 * the path must be specified in the conf/image-magick.properties
 * file.
 */
public class ImageMagickProcess {
    
    private String convertCommandPath; 
    
    public ImageMagickProcess() throws IOException {
        if (ImageMagickProcess.class.getClassLoader().getResource("conf/image-magick.properties") != null) {
            Properties p = new Properties();
            p.load(ImageMagickProcess.class.getClassLoader().getResourceAsStream("conf/image-magick.properties"));
            convertCommandPath = p.getProperty("convert-command");
        } else {
            convertCommandPath = "convert";
        }
    }
    
    public ImageMagickProcess(String path) {
        convertCommandPath = path;
    }
    
    public void generateThubmnail(File inputPdf, File outputPng) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(convertCommandPath, "-thumbnail", "120x120", inputPdf.getPath() + "[0]", outputPng.getPath()).start();
        new Thread(new OutputDrainerThread(p.getInputStream())).start();
        new Thread(new OutputDrainerThread(p.getErrorStream())).start();
        int returnCode = p.waitFor();
        if (returnCode != 0) {
            throw new RuntimeException("Invalid return code for process!");
        }
    }
    
    public void generateOCRReadyTiff(File inputPdf, File outputTif) throws IOException, InterruptedException  {
        Process p = new ProcessBuilder(convertCommandPath, "-colorspace", "gray", "-density", "300", "-sigmoidal-contrast", "3,0%", inputPdf.getPath() + "[0]", outputTif.getPath()).start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new Thread(new OutputDrainerThread(p.getInputStream(), baos)).start();
        new Thread(new OutputDrainerThread(p.getErrorStream(), baos)).start();
        int returnCode = p.waitFor();
        if (returnCode != 0) {
            System.err.println(baos.toString("UTF-8"));
            throw new RuntimeException("Invalid return code for process!");
            
        }
    }

}
