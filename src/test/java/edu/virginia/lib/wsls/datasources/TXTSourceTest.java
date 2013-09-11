package edu.virginia.lib.wsls.datasources;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import junit.framework.Assert;

import org.junit.Test;

public class TXTSourceTest {

    @Test
    public void testReadingBadUTF8() throws URISyntaxException, IOException {
        Assert.assertFalse("Example file should have encoding problems.", TXTSource.isFileValidUTF8(new File(getClass().getClassLoader().getResource("7127_1.txt").toURI())));
    }

    @Test
    public void testReadingGoodUTF8() throws URISyntaxException, IOException {
        Assert.assertTrue("Example file should have no encoding problems.", TXTSource.isFileValidUTF8(new File(getClass().getClassLoader().getResource("7127_1-fixed.txt").toURI())));
    }

}
