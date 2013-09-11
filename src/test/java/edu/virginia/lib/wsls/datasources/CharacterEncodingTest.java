package edu.virginia.lib.wsls.datasources;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;

import junit.framework.Assert;

import org.apache.pdfbox.io.IOUtils;
import org.junit.Test;

public class CharacterEncodingTest {

    @Test
    public void testReadingGoodUTF8() throws URISyntaxException, IOException {
        CharsetDecoder d = Charset.forName("UTF-8").newDecoder();

        File f = new File(getClass().getClassLoader().getResource("7127_1-fixed.txt").toURI());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(new FileInputStream(f), baos);
        CharBuffer chars = d.decode(ByteBuffer.wrap(baos.toByteArray()));
    }

    @Test
    public void testReadingBadUTF8() throws URISyntaxException, IOException {
        CharsetDecoder d = Charset.forName("UTF-8").newDecoder();
        d.onMalformedInput(CodingErrorAction.REPORT);
        d.onUnmappableCharacter(CodingErrorAction.REPORT);

        File f = new File(getClass().getClassLoader().getResource("7127_1.txt").toURI());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), d));
        try {
            String line = null;
            while ((line = r.readLine()) != null) {
                // read all the content, the decoder will throw an exception if
                // there's an encoding problem.
            }
            CharBuffer chars = d.decode(ByteBuffer.wrap(baos.toByteArray()));
            Assert.fail("An encoding error should be encountered!");
        } catch (MalformedInputException ex) {
        }
    }

}
