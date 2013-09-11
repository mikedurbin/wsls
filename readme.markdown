# WSLS

This simple set of java classes support the workflow for the WSLS 
project at UVA's Libraries.  Code exists to transform spreadsheets
into PBCore records, and fedora object XML exists to support the 
validation and transformation of that PBCore into SOLR add documents
suitable for use in Virgo (a Blacklight-based discovery system).

## ProductionIngester.java
A sophisticated program that takes a suite of inputs and ingests
records to the configured repository and indexes them in the
configured solr index.

The required configurations are found and explained in the 
/src/main/resources/conf/ directory.

	mvn exec:java -Dexec.mainClass=edu.virginia.lib.wsls.fedora.ProductionIngester

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


