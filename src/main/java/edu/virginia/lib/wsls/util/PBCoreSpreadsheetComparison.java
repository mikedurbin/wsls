package edu.virginia.lib.wsls.util;

import edu.virginia.lib.wsls.datasources.WSLSMasterSpreadsheet;
import edu.virginia.lib.wsls.datasources.WSLSMasterSpreadsheetArray;
import edu.virginia.lib.wsls.googledrive.DriveHelper;
import edu.virginia.lib.wsls.spreadsheet.PBCoreSpreadsheetRow;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class PBCoreSpreadsheetComparison implements Iterable<String> {

    public static void main(String args[]) throws Exception {
        WSLSMasterSpreadsheet master = new WSLSMasterSpreadsheet(new File("src/main/resources/master2.xlsx"));

        for (String id : new PBCoreSpreadsheetComparison(master, new WSLSMasterSpreadsheetArray(new DriveHelper()))) {
            System.out.println(id + " changed.");
        }


    }

    private Iterable<PBCoreSpreadsheetRow> left;
    private Iterable<PBCoreSpreadsheetRow> right;

    public PBCoreSpreadsheetComparison(Iterable<PBCoreSpreadsheetRow> left, Iterable<PBCoreSpreadsheetRow> right) {
        this.left = left;
        this.right = right;
    }


    @Override
    public Iterator<String> iterator() {
        return new PBCoreSpreadsheetComparisonDiffIterator(left.iterator(), right.iterator());
    }

    private static class PBCoreSpreadsheetComparisonDiffIterator
            implements Iterator<String> {

        private Iterator<PBCoreSpreadsheetRow> left;

        private Iterator<PBCoreSpreadsheetRow> right;

        private LinkedList<PBCoreSpreadsheetRow> leftBuffer;

        private LinkedList<PBCoreSpreadsheetRow> rightBuffer;

        private int maxBufferSize;

        private Queue<String> diffIdBuffer;

        public PBCoreSpreadsheetComparisonDiffIterator(
                Iterator<PBCoreSpreadsheetRow> left,
                Iterator<PBCoreSpreadsheetRow> right) {
            this.left = left;
            leftBuffer = new LinkedList<PBCoreSpreadsheetRow>();
            this.right = right;
            rightBuffer = new LinkedList<PBCoreSpreadsheetRow>();
            diffIdBuffer = new LinkedList<String>();
            maxBufferSize = 100;
        }

        public boolean hasNext() {
            if (!diffIdBuffer.isEmpty()) {
                return true;
            } else if (!left.hasNext() && !right.hasNext()) {
                return false;
            } else {
                computeNextDiff();
            }
            return (!diffIdBuffer.isEmpty());
        }

        /**
         * Returns the diffIdBuffer previously loaded differing elements from the
         * buffers or fills the buffers until the diffIdBuffer match is found and
         * then returns the first different row ID.
         */
        private void computeNextDiff() {
            while ((left.hasNext() || right.hasNext())) {
                if (left.hasNext()) {
                    fetchOne(left.next(), leftBuffer, rightBuffer);
                    if (!diffIdBuffer.isEmpty()) {
                        return;
                    }
                }
                if (right.hasNext()) {
                    fetchOne(right.next(), rightBuffer, leftBuffer);
                    if (!diffIdBuffer.isEmpty()) {
                        return;
                    }

                }
            }

            // We've reached the end of both iterators, anything left on the
            // buffers represent differences.
            PBCoreSpreadsheetRow row = null;
            while ((row = leftBuffer.poll()) != null) {
                diffIdBuffer.add(row.getId());
            }
            while ((row = rightBuffer.poll()) != null) {
                diffIdBuffer.add(row.getId());
            }

        }

        private boolean fetchOne(PBCoreSpreadsheetRow next, LinkedList<PBCoreSpreadsheetRow> bufferOne, LinkedList<PBCoreSpreadsheetRow> bufferTwo) {
            assert(Collections.disjoint(bufferOne, bufferTwo));

            if (bufferTwo.contains(next)) {
                // a match was found, flush everything before that
                // match onto the diff list.
                PBCoreSpreadsheetRow row = null;
                while ((row = bufferOne.poll()) != null) {
                    diffIdBuffer.add(row.getId());
                }

                while ((row = bufferTwo.poll()) != null) {
                    if (row.equals(next)) {
                        break;
                    } else {
                        diffIdBuffer.add(row.getId());
                    }
                }
                return true;
            } else {
                if (bufferOne.size() == maxBufferSize) {
                    throw new RuntimeException("Difference longer than " + maxBufferSize + " rows! (starting at id " + leftBuffer.peek().getId() + " and " + rightBuffer.peek().getId() + ")");
                }
                bufferOne.add(next);
                return false;
            }
        }


        public String next() {
            if (hasNext()) {
                return diffIdBuffer.poll();
            }
            throw new IllegalStateException();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

}
