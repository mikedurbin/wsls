package edu.virginia.lib.wsls.thumbnails;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A thin wrapper around the ImageMagick's "convert" utility.
 * For this class to work, the "convert" utility must be in the
 * path (ie, executable with the simple command "convert") or 
 * the path must be specified in the conf/image-magick.properties
 * file.
 */
public class ImageMagickThumbnailer {
    
    private String convertCommandPath; 
    
    public ImageMagickThumbnailer() throws IOException {
        if (ImageMagickThumbnailer.class.getClassLoader().getResource("conf/image-magick.properties") != null) {
            Properties p = new Properties();
            p.load(ImageMagickThumbnailer.class.getClassLoader().getResourceAsStream("conf/image-magick.properties"));
            convertCommandPath = p.getProperty("convert-command");
        } else {
            convertCommandPath = "convert";
        }
    }
    
    public ImageMagickThumbnailer(String path) {
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
    
    private static class OutputDrainerThread implements Runnable {

        private InputStream is;
        
        public OutputDrainerThread(InputStream stream) {
            is = stream;
        }
        
        public void run() {
            byte[] buffer = new byte[1024];
            while (true) {
                try {
                    int read = is.read(buffer);
                    if (read == -1) {
                        return;
                    } else if (read == 0) {
                        Thread.sleep(300);
                    } else {
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                } catch (InterruptedException ex) {
                    // out sleep was interrupted, no big deal
                }
            } 
            
        }
        
    }
}
