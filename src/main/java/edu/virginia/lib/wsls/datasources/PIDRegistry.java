package edu.virginia.lib.wsls.datasources;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Lock;
import org.apache.lucene.util.Version;

import edu.virginia.lib.wsls.spreadsheet.PBCoreDocument;
import edu.virginia.lib.wsls.spreadsheet.PBCoreDocument.VariablePrecisionDate;


/**
 * We wouldn't need a PID registry if fedora had a proper way of locating 
 * objects, but so long as the resource index updates aren't synchronized
 * we must maintain a list of these PIDs somewhere else.
 */
public class PIDRegistry {

    private Analyzer analyzer;

    private Directory luceneDirectory;

    private IndexWriter writer;

    public PIDRegistry(File luceneDir) throws IOException {
        luceneDirectory = FSDirectory.open(luceneDir);
        analyzer = new StandardAnalyzer(Version.LUCENE_43);
        IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_43, analyzer);
        iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
        writer = new IndexWriter(luceneDirectory, iwc);
    }

    public String getPIDForWSLSID(String id) throws IOException {
        return getPIDForID(id);
    }

    public void setPIDforWSLSID(String id, String pid, PBCoreDocument pbcore) throws IOException {
        writeKeyPair(id, pid, pbcore.getAssetVariablePrecisionDate(), "item");
    }

    public String getAnchorPIDForWSLSID(String id) throws IOException {
        return getPIDForID("anchor-script-" + id);
    }

    public void setAnchorPIDForWSLSID(String id, String pid) throws IOException {
        writeKeyPair("anchor-script-" + id, pid, "script");
    }
    
    public String getWSLSCollectionPid() throws IOException {
        return getPIDForID("collection");
    }

    public void setWSLSCollectionPid(String pid) throws IOException {
        writeKeyPair("collection", pid, "collection");
    }

    private String getId(int year) {
        return "year-" + year;
    }

    public String getYearPid(int year) throws IOException {
        return getPIDForID(getId(year));
    }

    public void setYearPid(int year, String pid) throws IOException {
        writeKeyPair(getId(year), pid, year, "year");
    }

    private String getId(int year, int month) {
        return "year-" + year + "-month" + month;
    }

    public String getMonthPid(int year, int month) throws IOException {
        return getPIDForID(getId(year, month));
    }

    public void setMonthPid(int year, int month, String pid) throws IOException {
        writeKeyPair(getId(year, month), pid, year, month, null, "month");
    }

    public String getUnknownPid() throws IOException {
        return getPIDForID("unknown-date");
    }

    public void setUnknownPid(String pid) throws IOException {
        writeKeyPair("unknown-date", pid, Integer.MAX_VALUE, "year");
    }

    public void writeKeyPair(String id, String pid, String type) throws IOException {
        writeKeyPair(id, pid, null, null, null, type);
    }
    private void writeKeyPair(String id, String pid, Integer year, String type) throws IOException {
        writeKeyPair(id, pid, year, null, null, type);
    }
    private void writeKeyPair(String id, String pid, VariablePrecisionDate date, String type) throws IOException {
        if (date == null) {
            writeKeyPair(id, pid, null, null, null, type);
        } else {
            writeKeyPair(id, pid, date.getYear(), date.getMonth(), date.hasDay() ? date.getDay() : null, type);
        }
    }
    private void writeKeyPair(String id, String pid, Integer year, Integer month, Integer day, String type) throws IOException {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new StringField("pid", pid, Field.Store.YES));
        doc.add(new IntField("year", year == null ? 0 : year, Field.Store.YES));
        doc.add(new IntField("month", month == null ? 0 : month, Field.Store.YES));
        doc.add(new IntField("day", day == null ? 0 : day, Field.Store.YES));
        doc.add(new StringField("type", type, Field.Store.YES));
        writer.updateDocument(new Term("id", id), doc);
        writer.commit();
    }

    private String getPIDForID(String id) throws IOException {
        DirectoryReader reader = null;
        try {
            reader = DirectoryReader.open(luceneDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs results = searcher.search(new TermQuery(new Term("id", id)), 1);
            if (results.totalHits == 1) {
                return searcher.doc(results.scoreDocs[0].doc).getFields("pid")[0].stringValue();
            } else {
                return null;
            }
        } catch (IndexNotFoundException ex) {
            return null;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    public void close() throws IOException {
        luceneDirectory.close();
    }

    public VariablePrecisionDate getDateForPid(String pid) throws IOException {
        DirectoryReader reader = null;
        try {
            reader = DirectoryReader.open(luceneDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);
            BooleanQuery q = new BooleanQuery();
            q.add(new TermQuery(new Term("pid", pid)), BooleanClause.Occur.MUST);
            Lock lock = luceneDirectory.getLockFactory().makeLock("search-lock");
            lock.obtain();
            try {
                TotalHitCountCollector c = new TotalHitCountCollector();
                searcher.search(q, c);
                if (c.getTotalHits() == 0) {
                    throw new IllegalArgumentException("PID " + pid + " is unknown!");
                } else {
                    TopDocs results = searcher.search(q, c.getTotalHits());
                    if (results.totalHits > 1) {
                        throw new IllegalArgumentException("There are " + results.totalHits + " records for PID " + pid);
                    }
                    Document doc = searcher.doc(results.scoreDocs[0].doc);
                    return new VariablePrecisionDate(doc.getField("year").numericValue().intValue(), doc.getField("month").numericValue().intValue(), doc.getField("day").numericValue().intValue());
                }
            } finally {
                lock.release();
            }
        } catch (IndexNotFoundException ex) {
            throw new IllegalArgumentException("PID " + pid + " is unknown!");
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    public String[] getItemInsertionPoint(String pid, VariablePrecisionDate date) throws IOException {
        // items will either have complete dates or no date
        if (date != null) {
            return getInsertionPoint(pid, date.getYear(), date.getMonth(), date.getDay(), "item");
        } else {
            return getInsertionPoint(pid, "item");
        }
    }

    public String[] getMonthInsertionPoint(String pid, VariablePrecisionDate date) throws IOException {
        return getInsertionPoint(pid, date.getYear(), date.getMonth(), null, "month");
    }

    public String[] getYearInsertionPoint(String pid, int year) throws IOException {
        return getInsertionPoint(pid, year, null, null, "year");
    }

    public String[] getUnknownFolderInsertionPoint(String pid) throws IOException {
        return getInsertionPoint(pid, Integer.MAX_VALUE, null, null, "year");
    }

    /**
     * Sorts items in the "unknown" folder which are identified in the index
     * as "item" type with a year, month and day value of zero.
     */
    private String[] getInsertionPoint(String pid, String type) throws IOException {
        DirectoryReader reader = null;
        try {
            reader = DirectoryReader.open(luceneDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);
            BooleanQuery q = new BooleanQuery();
            q.add(new TermQuery(new Term("type", type)), BooleanClause.Occur.MUST);
            q.add(NumericRangeQuery.newIntRange("year", 0, 0, true, true), BooleanClause.Occur.MUST);
            q.add(NumericRangeQuery.newIntRange("month", 0, 0, true, true), BooleanClause.Occur.MUST);
            q.add(NumericRangeQuery.newIntRange("day", 0, 0, true, true), BooleanClause.Occur.MUST);
            q.add(new TermQuery(new Term("pid", pid)), BooleanClause.Occur.MUST_NOT);

            Sort s = new Sort(new SortField("pid", SortField.Type.STRING));
            Lock lock = luceneDirectory.getLockFactory().makeLock("search-lock");
            lock.obtain();
            try {
                TotalHitCountCollector c = new TotalHitCountCollector();
                searcher.search(q, c);
                if (c.getTotalHits() == 0) {
                    return new String[] { null, null};
                } else {
                    TopDocs results = searcher.search(q, c.getTotalHits(), s);
                    //System.out.println(pid + ": " + c.getTotalHits() + " hits amongst which to sort");
                    Document prev = null;
                    Document current = null;
                    for (ScoreDoc scoreDoc : results.scoreDocs) {
                        current = searcher.doc(scoreDoc.doc);
                        String currentVal = current.get("pid");
                        //System.out.println(pid + ": " + (prev == null ? null : prev.get("pid")) + " <? " + pid + " <=? " + currentVal);
                        if (prev != null) {
                            // see if our pid falls between the previous and the current
                            String prevVal = prev.get("pid");
                            if (pid.compareTo(prevVal) >= 0 && pid.compareTo(currentVal) < 0) {
                                //System.out.println(pid + " is between " + prevVal + " and " + current);
                                break;
                            }
                        } else {
                            if (pid.compareTo(currentVal) < 0) {
                                //System.out.println(pid + " is first");
                                break;
                            }
                        }
                        prev = current;
                        current = null;
                    }
                    if (current == null) {
                        //System.out.println(pid + " is last");
                    }
                    return new String[] { (prev != null ? prev.get("pid") : null), (current != null ? current.get("pid") : null) };
                }
            } finally {
                lock.release();
            }
        } catch (IndexNotFoundException ex) {
            return new String[] {null, null};
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private String[] getInsertionPoint(String pid, Integer year, Integer month, Integer day, String type) throws IOException {
        DirectoryReader reader = null;
        try {
            String sortLevel = day != null ? "day" : month != null ? "month" : year != null ? "year" : "pid";
            int targetVal = day != null ? day : month != null ? month : year != null ? year : Integer.MAX_VALUE;

            reader = DirectoryReader.open(luceneDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);
            BooleanQuery q = new BooleanQuery();
            Sort s = new Sort(new SortField(sortLevel, SortField.Type.INT));
            if (month != null) {
                q.add(NumericRangeQuery.newIntRange("year", year, year, true, true), BooleanClause.Occur.MUST);
            }
            if (day != null) {
                q.add(NumericRangeQuery.newIntRange("month", month, month, true, true), BooleanClause.Occur.MUST);
            }
            q.add(new TermQuery(new Term("type", type)), BooleanClause.Occur.MUST);
            q.add(new TermQuery(new Term("pid", pid)), BooleanClause.Occur.MUST_NOT);
            Lock lock = luceneDirectory.getLockFactory().makeLock("search-lock");
            lock.obtain();
            try {
                TotalHitCountCollector c = new TotalHitCountCollector();
                searcher.search(q, c);
                if (c.getTotalHits() == 0) {
                    return new String[] { null, null};
                } else {
                    TopDocs results = searcher.search(q, c.getTotalHits(), s);
                    Document prev = null;
                    Document current = null;
                    for (ScoreDoc scoreDoc : results.scoreDocs) {
                        current = searcher.doc(scoreDoc.doc);
                        if (prev != null) {
                            // see if our months falls between the previous and the current
                            int prevVal = prev.getField(sortLevel).numericValue().intValue();
                            int currentVal = current.getField(sortLevel).numericValue().intValue();
                            //System.out.println(prevVal + " <=? " + targetVal + " <? " + currentVal);
                            if (targetVal >= prevVal && targetVal < currentVal) {
                                break;
                            }
                        } else {
                            try {
                                if (targetVal < current.getField(sortLevel).numericValue().intValue()) {
                                    // the new value should be first
                                    break;
                                }
                            } catch (NullPointerException ex) {
                                // the item the cannot be sorted should be sorted by pid
                                if (current.get("pid").compareTo(pid) > 0) {
                                    break;
                                }
                                
                            }
                        }
                        prev = current;
                        current = null;
                    }
                    return new String[] { (prev != null ? prev.get("pid") : null), (current != null ? current.get("pid") : null) };
                }
            } finally {
                lock.release();
            }
        } catch (IndexNotFoundException ex) {
            return new String[] {null, null};
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    public List<String> listAllPids() throws IOException {
        List<String> pids = new ArrayList<String>();
        DirectoryReader reader = null;
        try {
            reader = DirectoryReader.open(luceneDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);
    
            Lock lock = luceneDirectory.getLockFactory().makeLock("search-lock");
            lock.obtain();
            try {
                TotalHitCountCollector c = new TotalHitCountCollector();
                searcher.search(new MatchAllDocsQuery(), c);
                TopDocs results = searcher.search(new MatchAllDocsQuery(), c.getTotalHits(), new Sort(new SortField("id", SortField.Type.STRING)));
                for (ScoreDoc scoreDoc : results.scoreDocs) {
                    Document current = searcher.doc(scoreDoc.doc);
                    pids.add(current.get("pid"));
                }
            } finally {
                lock.release();
            }
        } catch (IndexNotFoundException ex) {
            // no pids to include
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return pids;
    }

    public void dumpIndex(OutputStream os) throws Exception {
        PrintWriter out = new PrintWriter(os);
        DirectoryReader reader = null;
        try {
            reader = DirectoryReader.open(luceneDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);
    
            Lock lock = luceneDirectory.getLockFactory().makeLock("search-lock");
            lock.obtain();
            try {
                TotalHitCountCollector c = new TotalHitCountCollector();
                searcher.search(new MatchAllDocsQuery(), c);
                TopDocs results = searcher.search(new MatchAllDocsQuery(), c.getTotalHits(), new Sort(new SortField("id", SortField.Type.STRING)));
                for (ScoreDoc scoreDoc : results.scoreDocs) {
                    Document current = searcher.doc(scoreDoc.doc);
                    out.println(current.get("id") + ", " + current.get("pid") + ", " + current.get("type") + ", " + current.get("year") + "/" + current.get("month") + "/" + current.get("day"));
                }
            } finally {
                lock.release();
                out.flush();
            }
        } catch (IndexNotFoundException ex) {
            // nothing to print out
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
