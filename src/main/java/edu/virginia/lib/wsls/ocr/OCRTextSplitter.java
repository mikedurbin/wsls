package edu.virginia.lib.wsls.ocr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import edu.virginia.lib.wsls.ocr.ProcessedDocument.OCRPage;


/**
 * A program that takes several PDF files (a combined PDF and a PDF for each
 * part that was split from it), a single text file that was keyed from the
 * combined PDF files and breaks the text file into parts that are believed to
 * be associated with the several PDF files.
 * 
 * There are several assumptions embedded in this code that make the
 * break determination easiser:
 * 1.  The break will occur at a newline character in the hand-keyed
 *     text file
 * 2.  The files for OCR represent the whole document
 * 3.  The images in the split PDF files are sufficiently similar to the 
 *     corresponding images in the original that they would result in 
 *     identical OCR output
 */
public class OCRTextSplitter {

    /**
     * If one argument is specified, it is assumed to be a directory in which
     * the PDFs and text file for a single record exist.  Otherwise the 
     * arguments are expected to be all of the broken up PDFs along with the
     * unbroken PDF and the text transcription of the unbroken PDF.
     */
    public static void main(String args[]) throws IOException, InterruptedException {
        String[] files = args;
        if (args.length == 1) {
            File dir = new File(args[0]);
            File [] f = dir.listFiles();
            files = new String[f.length];
            for (int i = 0; i < f.length; i ++) {
                files[i] = f[i].getAbsolutePath();
            }
        }
        List<ProcessedDocument> partPdfs = new ArrayList<ProcessedDocument>();
        ProcessedDocument completePdf = null;
        String text = null;
        File textFile = null;
        for (String arg : files) {
            if (arg.endsWith(".pdf")) {
                File pdf = new File(arg);
                if (!pdf.exists()) {
                    System.err.println("Specified PDF, " + pdf + ", does not exist!");
                    System.exit(-1);
                }
                if (pdf.getName().contains("_")) {
                    partPdfs.add(new ProcessedDocument(pdf));
                } else if (completePdf == null){
                    completePdf = new ProcessedDocument(pdf);
                } else {
                    System.err.println("Cannot determine whether " + pdf + " or " + completePdf.getFilename() + " is the complete PDF.");
                    System.exit(-1);
                }
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
                System.err.println("Arguments must either be PDF files (.pdf) or text files (.txt)! (" + arg + ")");
                System.exit(-1);
            }
        }
        // determine complete document
        int partsPages = 0;
        for (ProcessedDocument d : partPdfs) {
            partsPages += d.getPageCount();
        }
        if (partsPages != completePdf.getPageCount()) {
            System.err.println("There is a different number of pages in the parts (" + partsPages + ") than in \"" + completePdf.getFilename() + "\" (" + completePdf + ")!");
            System.exit(-1);
        }

        // determine page order (this algorithm is based on the assumption 
        // that the OCR of each page will be the same whether it's in the 
        // combined PDF or the split one.
        System.out.println("\nDetecting page order:");
        List<OCRPage> unorderedPages = new ArrayList<OCRPage>();
        for (ProcessedDocument d : partPdfs) {
            unorderedPages.addAll(d.getPages());
        }
        List<OCRPage> orderedPages = new ArrayList<OCRPage>();
        String unmatchedText = completePdf.getOCRText();
        while (!unorderedPages.isEmpty()) {
            boolean match = false;
            for (OCRPage p : unorderedPages) {
                if (unmatchedText.startsWith(p.getOCRText().trim())) {
                    match = true;
                    orderedPages.add(p);
                    unorderedPages.remove(p);
                    unmatchedText = unmatchedText.substring(p.getOCRText().trim().length()).trim();
                    System.out.println("  " + p.getDocument().getFilename() + " page " + (p.getPageIndex() + 1) + " is page " + orderedPages.size() + " of " + completePdf.getFilename());
                    break;
                } else {
                }
            }
            if (!match) {
                System.err.println("Unable to determine next match!");
                System.out.println("Unmatched text: \n" + unmatchedText);
                for (OCRPage p : unorderedPages) {
                    System.out.println("\n" + p.getDocument().getFilename() + " page " + p.getPageIndex() + ":\n" + p.getOCRText());
                }
                System.exit(-1);
            }
        }

        //List<String> guessedFragments = new ArrayList<String>();
        for (int i = 0; i < orderedPages.size(); i ++) {
            if (i == orderedPages.size() - 1) {
                // the remaining part is the match
                // by default.
                orderedPages.get(i).setKeyedText(text);
                //guessedFragments.add(text);
            } else {
                String fuzzyFirst = orderedPages.get(i).getOCRText();
                // we already ensured that all the pages are in order and complete
                StringBuffer fuzzyRest = new StringBuffer();
                for (int j = i + 1; j < orderedPages.size(); j ++) {
                    fuzzyRest.append(orderedPages.get(j).getOCRText());
                }
                String first = matchFirstPartLevenshtein(fuzzyFirst, fuzzyRest.toString(), text);
                orderedPages.get(i).setKeyedText(first);
                //guessedFragments.add(first);
                text = text.substring(first.length());
            }
        }
        //System.out.println("Matches: ");
        //for (String frag : guessedFragments) {
        //    System.out.println(summarizeString(frag, 40, 80) + "\n");
        //}
        
        System.out.println("Computed Matches:");
        for (ProcessedDocument d : partPdfs) {
            System.out.println(d.getFilename());
            for (OCRPage p : d.getPages()) {
                //System.out.println("  " + summarizeString(p.getKeyedText(), 40, 77));
                System.out.println("  " + p.getKeyedText());
            }
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
