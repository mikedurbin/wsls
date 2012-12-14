# WSLS

This simple set of java classes support the workflow for the WSLS 
project at UVA's Libraries.  Code exists to transform spreadsheets
into PBCore records, and fedora object XML exists to support the 
validation and transformation of that PBCore into SOLR add documents
suitable for use in Virgo (a Blacklight-based discovery system).

## BatchIngest.java
A simple program that takes a provided spreadsheet, pdf directory and
text directory and ingests all items represented on the spreadsheet
for which a PDF and TXT file exist.

The fedora repository to which the files are ingested is configured in
the src/main/resources/conf/fedora.properties file.

This program uses ImageMagick to generate thumbnail images of the PDFs.
On linux systems, as long as "convert" is in the path for the user running
this program, no configuration change is necessary.  On other platforms 
in the file src/main/resources/conf/image-magick.properties the path should
be specified.

	mvn exec:java -Dexec.mainClass=edu.virginia.lib.wsls.fedora.BatchIngest -Dexec.args="spreadsheet.xlsx texts pdfs"

## PostSolrDocument.java
A simple program that indexes everything in the configured fedora 
repository that has the "uva-lib:pbcore2CModel" content model to the
SOLR server configured in src/main/resources/conf/solr.properties.

	mvn exec:java -Dexec.mainClass=edu.virginia.lib.wsls.solr.PostSolrDocument

## ContentAnalyzer.java
A program that takes a CSV file containing two columns (the uploaded video file name
and the system ID it was assigned upon upload) and a directory of PDF files and 
generates a report attempting to match the files based on the ids it can glean from
the filenames.

The output is a CSV file sorted by id containing a row for every id for which a script
or a clip has been matched.

	mvn exec:java -Dexec.mainClass=edu.virginia.lib.wsls.util.ContentAnalyzer -Dexec.args="uploaded.csv pdfs output.csv"

## foxml files
* __uva-lib:documentedMappingCModel__: a content model for objects 
  that contain XSLT that converts to SOLR and has documentation about
  which original fields map to which SOLR fields. 
* __uva-lib:documentedMappingSDef__: the service definition which 
  defines the method "getMappingDocumentation" which returns an
  HTML response that when rendered presents a table showing which 
  fields in the source document map to which fields in the result
  document.
* __uva-lib:documentedMappingSDep__: the service deployment that 
  implements the above method using XSLT.
* __uva-lib:indexableSDef__: a service definition which defines
  the method "getIndexingMetadata" which is meant to return a SOLR
  add doc that includes a record compatible iwth UVa's Virgo (a 
  Blacklight-based discovery system)
* __uva-lib:pbcore2CModel__: a content model for objects that contain
  valid PBCore 2.0 records in the "metadata" datastream.
* __uva-lib:wslsPBCore2IndexableSDep__: a service deployment that 
  implements uva-lib:indexableSDef to generate a SOLR index document
  from a PBCore 2.0 record that is tailored to the WSLS project
* __uva-lib:wslsScriptCModel__: a content model for WSLS anchor script
  objects.  These must contain the PDF of the anchor script, a text
  representation of the script and a PNG thumbnail image of the scanned
  script.


