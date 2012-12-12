package edu.virginia.lib.wsls.ocr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import edu.virginia.lib.wsls.proc.ImageMagickProcess;
import edu.virginia.lib.wsls.proc.TesseractProcess;


/**
 * A program that takes several PDF files, a single text file that
 * was keyed from the several PDF files when together and breaks the
 * text file into parts that are believed to be associated with the 
 * several PDF files.
 * 
 * There are several assumptions embedded in this code that make the
 * break determination easiser:
 * 1.  The break will occur at a newline character in the hand-keyed
 *     text file
 * 2.  The files for OCR represent the whole document and are *in order*
 * 3.  Each PDF only has one page. (may be false)?!?! 
 */
public class OCRTextSplitter {

    public static void main(String args[]) throws IOException, InterruptedException {
        List<File> pdfs = new ArrayList<File>();
        String text = null;
        File textFile = null;
        for (String arg : args) {
            if (arg.endsWith(".pdf")) {
                File pdf = new File(arg);
                if (!pdf.exists()) {
                    System.err.println("Specified PDF, " + pdf + ", does not exist!");
                    System.exit(-1);
                }
                pdfs.add(pdf);
            } else if (arg.endsWith(".txt")) {
                if (textFile == null) {
                    textFile = new File(arg);
                    if (!textFile.exists()) {
                        System.err.println("Specified text file, " + text + ", does not exist!");
                        System.exit(-1);
                    }
                    text = FileUtils.readFileToString(textFile);
                }
            } else {
                System.err.println("Arguments must either be PDF files (.pdf) or text files (.txt)!");
                System.exit(-1);
            }
        }
        Collections.sort(pdfs);
        
        ImageMagickProcess convert = new ImageMagickProcess();
        TesseractProcess tesseract = new TesseractProcess();

        // ORC each image
        List<String> fuzzyParts = new ArrayList<String>();
        for (File pdfFile : pdfs) {
            System.out.println("Balancing image...");
            File cleantif = File.createTempFile("ocr-ready", ".tif");
            convert.generateOCRReadyTiff(pdfFile, cleantif);
            File ocrtext = File.createTempFile("generated-ocr", ".txt");
            System.out.println("Generating OCR...");
            // assumption 3: one page PDFs
            tesseract.generateOCR(cleantif, ocrtext);
            fuzzyParts.add(FileUtils.readFileToString(ocrtext));
            cleantif.delete();
            ocrtext.delete();
        }
        
        List<String> guessedFragments = new ArrayList<String>();
        for (int i = 0; i < fuzzyParts.size(); i ++) {
            if (i == fuzzyParts.size() - 1) {
                // the remaining part is the match
                // by default.
                guessedFragments.add(text);
            } else {
                String fuzzyFirst = fuzzyParts.get(i);
                // assumption 2: all the parts are in order and complete
                StringBuffer fuzzyRest = new StringBuffer();
                for (int j = i + 1; j < fuzzyParts.size(); j ++) {
                    fuzzyRest.append(fuzzyParts.get(j));
                }
                String first = matchFirstPartLevenshtein(fuzzyFirst, fuzzyRest.toString(), text);
                guessedFragments.add(first);
                text = text.substring(first.length());
            }
        }
        System.out.println("Matches: ");
        for (String frag : guessedFragments) {
            System.out.println(summarizeString(frag, 40, 80) + "\n");
        }
    }
    
    public static String matchFirstPartLevenshtein(String fuzzyFirst, String fuzzyRest, String full) {
        int delimiterOffset = 0;
        int bestFirstScore = -1;
        int bestRestScore = -1;
        int bestOffset = 0;
        // assumption 1: the breaks will occur at a newline character
        while ((delimiterOffset = full.indexOf('\n', delimiterOffset + 1)) != -1) {
            String firstPart = full.substring(0, delimiterOffset + 1);
            String rest = full.substring(delimiterOffset +1);
            int firstScore = StringUtils.getLevenshteinDistance(firstPart, fuzzyFirst);
            int restScore = StringUtils.getLevenshteinDistance(rest, fuzzyRest);
            if (bestFirstScore == -1 || ((firstScore + restScore) < (bestFirstScore + bestRestScore))) {
                bestOffset = delimiterOffset;
                bestFirstScore = firstScore;
                bestRestScore = restScore;
            }
        }
        return full.substring(0, bestOffset);
    }
    
    public static String matchFirstPartJaccard(String fuzzyFirst, String fuzzyRest, String full) {
        int delimiterOffset = 0;
        float bestFirstScore = 0;
        float bestRestScore = 0;
        int bestOffset = 0;
        // assumption 1: the breaks will occur at a newline character
        while ((delimiterOffset = full.indexOf('\n', delimiterOffset + 1)) != -1) {
            String firstPart = full.substring(0, delimiterOffset + 1);
            String rest = full.substring(delimiterOffset +1);
            float firstScore = computeJaccardIndexByWord(firstPart, fuzzyFirst, " ");
            float restScore = computeJaccardIndexByWord(rest, fuzzyRest, " ");
            if ((firstScore + restScore) > (bestFirstScore + bestRestScore)) {
                bestOffset = delimiterOffset;
                bestFirstScore = firstScore;
                bestRestScore = restScore;
            }
        }
        return full.substring(0, bestOffset);
    }
    
    private static float computeJaccardIndexByWord(String first, String second, String delimiter) {
        List<String> firstStrings = Arrays.asList(first.split(delimiter));
        List<String> secondStrings = Arrays.asList(second.split(delimiter));
        int intersectionCount = CollectionUtils.intersection(firstStrings, secondStrings).size();
        int unionCount = CollectionUtils.union(firstStrings,  secondStrings).size();
        if (intersectionCount == 0 || unionCount == 0) {
            return 0f;
        } else {
            return ((float) intersectionCount / (float) unionCount);
        }
    }
    
    public static String summarizeString(String s, int prefixLength, int suffixLength) {
        if (prefixLength + suffixLength >= s.length()) {
            return s.replaceAll("\\r|\\n", "");
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < prefixLength; i ++) {
            sb.append(s.charAt(i));
        }
        sb.append("...");
        for (int i = s.length() - suffixLength - 1; i < s.length(); i ++) {
            sb.append(s.charAt(i));
        }
        return sb.toString().replaceAll("\\r|\\n", "");
    }
    
    
    
    
}
