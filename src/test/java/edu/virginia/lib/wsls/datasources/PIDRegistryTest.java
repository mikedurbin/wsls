package edu.virginia.lib.wsls.datasources;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.virginia.lib.wsls.spreadsheet.PBCoreDocument;
import edu.virginia.lib.wsls.spreadsheet.PBCoreDocument.VariablePrecisionDate;
import edu.virginia.lib.wsls.spreadsheet.PBCoreSpreadsheetRow;

public class PIDRegistryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    PIDRegistry pidReg;

    GoogleMetadata m;

    @Before
    public void setUpPIDRegistry() throws IOException, ParserConfigurationException, URISyntaxException {
        File luceneDir = tempFolder.newFolder("luceneDir");
        pidReg = new PIDRegistry(luceneDir);

        m = new GoogleMetadata(new File(getClass().getClassLoader().getResource("5record.xlsx").toURI()));
    }

    @Test
    public void testCollectionPID() throws IOException {
        Assert.assertTrue("Collection PID must be null before set.", pidReg.getWSLSCollectionPid() == null);
        pidReg.setWSLSCollectionPid("this is the collection pid");
        Assert.assertTrue("The collection pid must be stored accurately.", "this is the collection pid".equals(pidReg.getWSLSCollectionPid()));
    }

    @Test
    public void testYear() throws IOException {
        pidReg.setYearPid(1998, "year value");
        Assert.assertTrue("The year pid must be stored accurately.", "year value".equals(pidReg.getYearPid(1998)));
        Assert.assertTrue("Year PID must be null until set", pidReg.getYearPid(1999) == null);
    }

    @Test
    public void testMonth() throws IOException {
        pidReg.setMonthPid(1998, 6, "month value");
        Assert.assertTrue("The year/month pid must be stored accurately.", "month value".equals(pidReg.getMonthPid(1998, 6)));
        Assert.assertTrue("The year/month pid must be null until set.", pidReg.getMonthPid(1999, 1) == null);
    }

    @Test
    public void testVideo() throws IOException, ParserConfigurationException {
        String pid = "uva-lib:video0003_1";
        String id = "0003_1";
        Assert.assertTrue("The WSLS video item pid must be null until set.", pidReg.getPIDForWSLSID(id) == null);
        pidReg.setPIDforWSLSID(id, pid, new PBCoreDocument(m.iterator().next()));
        Assert.assertTrue("The WSLS video item pid must be stored accurately.", pid.equals(pidReg.getPIDForWSLSID(id)));
    }

    @Test
    public void testAnchorScript() throws IOException {
        String pid = "uva-lib:anchor0003_1";
        String id = "0003_1";
        Assert.assertTrue("The WSLS anchor script item pid must be null until set.", pidReg.getAnchorPIDForWSLSID(id) == null);
        pidReg.setAnchorPIDForWSLSID(id, pid);
        Assert.assertTrue("The WSLS anchor script item pid must be stored accurately.", pid.equals(pidReg.getAnchorPIDForWSLSID(id)));
    }

    @Test
    public void testAnchorScriptWithVideo() throws IOException, ParserConfigurationException {
        String videoPid = "uva-lib:video-0003_1";
        String anchorPid = "uva-lib:anchor-0003_1";
        String id = "0003_1";
        Assert.assertTrue("The WSLS video item pid must be null until set.", pidReg.getPIDForWSLSID(id) == null);
        pidReg.setPIDforWSLSID(id, videoPid, new PBCoreDocument(m.iterator().next()));
        Assert.assertTrue("The WSLS video item pid must be stored accurately.", videoPid.equals(pidReg.getPIDForWSLSID(id)));
        Assert.assertTrue("The WSLS anchor script item pid must be null until set.", pidReg.getAnchorPIDForWSLSID(id) == null);
        pidReg.setAnchorPIDForWSLSID(id, anchorPid);
        Assert.assertTrue("The WSLS video item pid must be stored accurately.", videoPid.equals(pidReg.getPIDForWSLSID(id)));
        Assert.assertTrue("The WSLS anchor pid must be stored accurately.", anchorPid.equals(pidReg.getAnchorPIDForWSLSID(id)));
    }

    @Test
    public void testOrdering() throws Exception {
        WSLSMasterSpreadsheet master = new WSLSMasterSpreadsheet(new File(getClass().getClassLoader().getResource("25-master.xlsx").toURI()));
        List<PBCoreDocument> docs = new ArrayList<PBCoreDocument>();
        for (PBCoreSpreadsheetRow r : master) {
            docs.add(new PBCoreDocument(r));
        }
        PBCoreDocument august3 = docs.get(12);
        PBCoreDocument august4 = docs.get(17);
        PBCoreDocument august15 = docs.get(13);
        Assert.assertEquals("Row x of test data needs to have date y", "8/3/1960", august3.getAssetDate());
        Assert.assertEquals("Row x of test data needs to have date y", "8/4/1960", august4.getAssetDate());
        Assert.assertEquals("Row x of test data needs to have date y", "8/15/1960", august15.getAssetDate());

        String[] insertionPoint = pidReg.getItemInsertionPoint("test:1", august3.getAssetVariablePrecisionDate()); 
        Assert.assertNull("Nothing should come before the first entry for a given year and month.",insertionPoint[0]);
        Assert.assertNull("Nothing should come after the first entry for a given year and month.",insertionPoint[1]);
        pidReg.setPIDforWSLSID(august3.getId(), "test:1", august3);
        insertionPoint = pidReg.getItemInsertionPoint("test:2", august15.getAssetVariablePrecisionDate());
        Assert.assertEquals("August 3rd should come before the August 15th entry for a given year.", "test:1", insertionPoint[0]);
        Assert.assertNull("Nothing should come after August 15th for a given year.",insertionPoint[1]);
        pidReg.setPIDforWSLSID(august15.getId(), "test:2", august15);
        insertionPoint = pidReg.getItemInsertionPoint("test:3", august4.getAssetVariablePrecisionDate());
        Assert.assertEquals("August 3th should come before the August 4th entry for a given year.", "test:1", insertionPoint[0]);
        Assert.assertEquals("August 15th should come after August 4th for a given year.","test:2", insertionPoint[1]);

        PBCoreDocument u0 = docs.get(0);
        Assert.assertNull("Test date must not be complete. (" + u0.getAssetDate() + ")", u0.getAssetVariablePrecisionDate());
        PBCoreDocument u1 = docs.get(1);
        Assert.assertNull("Test date must not be complete. (" + u1.getAssetDate() + ")", u1.getAssetVariablePrecisionDate());
        PBCoreDocument u2 = docs.get(2);
        Assert.assertNull("Test date must not be complete. (" + u2.getAssetDate() + ")", u2.getAssetVariablePrecisionDate());

        insertionPoint = pidReg.getItemInsertionPoint("u1", u1.getAssetVariablePrecisionDate()); 
        Assert.assertNull("Nothing should come before the first entry with an unknown year and month.",insertionPoint[0]);
        Assert.assertNull("Nothing should come after the first entry with an unknown year and month.",insertionPoint[1]);
        pidReg.setPIDforWSLSID(u1.getId(), "u1", u1);
        insertionPoint = pidReg.getItemInsertionPoint("u0", u0.getAssetVariablePrecisionDate()); 
        Assert.assertNull("u0 should be first",insertionPoint[0]);
        Assert.assertEquals("u0 should come before u1", "u1", insertionPoint[1]);
        pidReg.setPIDforWSLSID(u0.getId(), "u0", u0);
        insertionPoint = pidReg.getItemInsertionPoint("u2", u2.getAssetVariablePrecisionDate()); 
        Assert.assertEquals("u2 should come after u1","u1", insertionPoint[0]);
        Assert.assertNull("nothing should come after u2", insertionPoint[1]);
        pidReg.setPIDforWSLSID(u2.getId(), "u2", u2);
    }

    @Test
    public void testYearOrdering() throws Exception {
        pidReg.setYearPid(1900, "1900");
        pidReg.setYearPid(1950, "1950");
        Assert.assertNull("2000 should be last.", pidReg.getYearInsertionPoint("2000", 2000)[1]);
        Assert.assertEquals("1920 is after 1900.", "1900", pidReg.getYearInsertionPoint("1920", 1920)[0]);
        Assert.assertEquals("1920 is before 1950.", "1950", pidReg.getYearInsertionPoint("1920", 1920)[1]);
        pidReg.setYearPid(1951, "1951");
        pidReg.setUnknownPid("unknown");
        Assert.assertEquals("Subsequent values with same years should follow existing values.", "1951", pidReg.getYearInsertionPoint("1951b", 1951)[0]);
        Assert.assertEquals("1951 is the highest year before unknown.", "unknown", pidReg.getYearInsertionPoint("1951", 1951)[1]);
        Assert.assertNull("1800 should be the first value", pidReg.getYearInsertionPoint("1800", 1800)[0]);
    }
    
    @Test
    public void testMonthOrdering() throws Exception {
        pidReg.setMonthPid(1980, 2, "Feb-1980");
        pidReg.setMonthPid(1981, 3, "Mar-1981");
        Assert.assertEquals("Feb-1980 is the last month registered in 1980.", "Feb-1980", pidReg.getMonthInsertionPoint("Dec-1980", new VariablePrecisionDate(1980, 12))[0]);
        pidReg.setMonthPid(1980, 4, "Mar-1980");
        Assert.assertNull("January is the first month in 1980", pidReg.getMonthInsertionPoint("Jan-1980", new VariablePrecisionDate(1980, 1))[0]);
        Assert.assertEquals("January comes before February in 1980", "Feb-1980", pidReg.getMonthInsertionPoint("Jan-1980", new VariablePrecisionDate(1980, 1))[1]);
        Assert.assertEquals("If we update Mar-1980 to be in april, it is inserted after Feb, not March.", "Feb-1980", pidReg.getMonthInsertionPoint("Mar-1980", new VariablePrecisionDate(1980, 4))[0]);
        pidReg.setMonthPid(1980, 1, "Jan-1980");
        
    }
}
