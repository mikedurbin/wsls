package edu.virginia.lib.wsls.fedora;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ibm.icu.text.DecimalFormat;
import com.yourmediashelf.fedora.client.FedoraClient;

import edu.virginia.lib.wsls.datasources.PIDRegistry;
import edu.virginia.lib.wsls.spreadsheet.PBCoreDocument.VariablePrecisionDate;

public class RelationshipValidator {

    public static final String IS_PART_OF = "info:fedora/fedora-system:def/relations-external#isPartOf";
    public static final String FOLLOWS = "http://fedora.lib.virginia.edu/relationships#follows";

    private FedoraClient fc;

    private PIDRegistry pids;

    public RelationshipValidator(FedoraClient client, PIDRegistry pr) {
        fc = client;
        pids = pr;
    }

    public void diagnoseParents() throws Exception {
        processParents(false);
    }

    public void fixParents() throws Exception {
        processParents(true);
    }

    private void processParents(boolean fix) throws Exception {
        if (fix) {
            System.out.println("Fixing parent relationships:\n");
        } else {
            System.out.println("Diagnosing parent relationships:\n");
        }
        StringBuffer report = new StringBuffer();
        int i = 0;
        for (String pid : FedoraHelper.getSubjects(fc, "info:fedora/fedora-system:def/model#hasModel", "uva-lib:pbcore2CModel")) {
            boolean fail = false;
            VariablePrecisionDate date = pids.getDateForPid(pid);
            String expectedParentPid = null;
            List<String> parents = FedoraHelper.getObjects(fc, pid, "info:fedora/fedora-system:def/relations-external#isPartOf");
            if (date == null || date.getMonth() == 0) {
                expectedParentPid = pids.getUnknownPid();
            } else {
                expectedParentPid = pids.getMonthPid(date.getYear(), date.getMonth());
            }
            if (expectedParentPid == null) {
                expectedParentPid = pids.getUnknownPid();
            }
            if (!parents.contains(expectedParentPid) || parents.size() != 1) {
                fail = true;
                if (fix) {
                    FedoraHelper.setParent(fc, pid, expectedParentPid);
                } else {
                    report.append(pid + " should be in " + expectedParentPid + "!\n");
                    System.out.print("X");
                    break;
                }
            } else {
                if (!fix) {
                    System.out.print(".");
                }
            }

            if (!fix) {
                System.out.flush();
                if (++ i % 80 == 0) {
                    System.out.println();
                }
            }
        }
        System.out.println("\n\n" + report.toString());
    }

    public void correctTree(String rootPid) throws Exception {
        //System.out.println(rootPid);
        correctChildSet(rootPid, IS_PART_OF, RelationshipValidator.Child.Precision.YEAR);
        for (String yearPid : getValidChildSet(rootPid, IS_PART_OF)) {
            if (yearPid.equals("uva-lib:2215692")) {
                // unknown date
                correctChildSet(yearPid, IS_PART_OF, RelationshipValidator.Child.Precision.PID);
            } else {
                correctChildSet(yearPid, IS_PART_OF, RelationshipValidator.Child.Precision.MONTH);
              //System.out.println("  " + yearPid);
                for (String monthPid : getValidChildSet(yearPid, IS_PART_OF)) {
                    correctChildSet(monthPid, IS_PART_OF, RelationshipValidator.Child.Precision.DAY);
                    //System.out.println("    " + monthPid);
                    for (String itemPid : getValidChildSet(monthPid, IS_PART_OF)) {
                        //System.out.println("      " + itemPid);
                    }
                }
            }
        }
    }

    private List<String> getValidChildSet(String object, String predicate) throws Exception {
        List<String> unordered = FedoraHelper.getSubjects(fc, IS_PART_OF, object);
        try {
            List<String> ordered = FedoraHelper.getOrderedParts(fc, object, IS_PART_OF, FOLLOWS);
            if (ordered.size() == unordered.size()) {
                unordered.removeAll(ordered);
                if (unordered.isEmpty()) {
                    return ordered;
                }
            }
            throw new RuntimeException("Children of " + object + " are invalid!");
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            //throw ex;
            return unordered;
        }
    }

    public void correctChildSet(String object, String predicate, Child.Precision p) throws IOException, Exception {
        List<Child> children = new ArrayList<Child>();
        for (String pid : getValidChildSet(object, predicate)) {
            children.add(new Child(pid, pids.getDateForPid(pid), p));
        }
        System.out.println("Current order:");
        for (Child c : children) {
            System.out.println(c.pid + " (" + c.d + ")");
        }
        System.out.println("\nCorrected order:");
        Collections.sort(children);
        for (Child c : children) {
            System.out.println(c.pid + " (" + c.d + ")");
        }
        String prev = null;
        for (Child c : children) {
            FedoraHelper.setFollows(fc, c.pid, prev);
            prev = c.pid;
        }
    }

    private static final class Child implements Comparable<Child> {

        public static enum Precision {
            YEAR,
            MONTH,
            DAY,
            PID;
        }

        private String pid;

        private VariablePrecisionDate d;

        private Precision precision;

        public Child(String pid, VariablePrecisionDate d, Precision p) {
            this.pid = pid;
            this.d = d;
            precision = p;
        }

        public int compareTo(Child c) {
            DecimalFormat YF = new DecimalFormat("0000");
            DecimalFormat DF = new DecimalFormat("00");
            if (!precision.equals(c.precision)) {
                throw new RuntimeException("Precision mismatch!");
            } else if (precision.equals(Precision.PID)) {
                return pid.compareTo(c.pid);
            } else if (precision.equals(Precision.YEAR)) {
                return (YF.format(d.getYear()) + pid).compareTo(YF.format(c.d.getYear()) + c.pid);
            } else if (precision.equals(Precision.MONTH)) {
                return (YF.format(d.getYear()) + DF.format(d.getMonth()) + pid).compareTo(YF.format(c.d.getYear()) + DF.format(c.d.getMonth()) + c.pid);
            } else {
                return (YF.format(d.getYear()) + DF.format(d.getMonth()) + DF.format(d.getDay()) + pid).compareTo(YF.format(c.d.getYear()) + DF.format(c.d.getMonth()) + DF.format(c.d.getDay()) + c.pid);
            }
        }
    }
}
