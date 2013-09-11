<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs"
    version="2.0">
    
    <xsl:param name="pid" required="yes" />
    <xsl:param name="id" required="yes" />
    <xsl:param name="parentPid" required="no" />
    <xsl:param name="previousPid" required="no" />
    <xsl:param name="visibility" required="no" />
    <xsl:param name="label" />
    <xsl:param name="metadataCModel">uva-lib:pbcore2CModel</xsl:param>
    <xsl:param name="structuralCModel">uva-lib:eadItemCModel</xsl:param>

    <xsl:output encoding="UTF-8" version="1.0" />
    
    <xsl:template match="/">
        <foxml:digitalObject VERSION="1.1"
            xmlns:foxml="info:fedora/fedora-system:def/foxml#">
            <xsl:attribute name="PID" select="$pid" />
            <foxml:objectProperties>
                <foxml:property NAME="info:fedora/fedora-system:def/model#state" VALUE="A"/>
                <foxml:property NAME="info:fedora/fedora-system:def/model#label">
                    <xsl:attribute name="VALUE"><xsl:value-of select="$label" /></xsl:attribute>
                </foxml:property>
            </foxml:objectProperties>
            <foxml:datastream ID="DC" STATE="A" CONTROL_GROUP="X" VERSIONABLE="true">
                <foxml:datastreamVersion ID="DC1.0" LABEL="Dublin Core Record for this object" MIMETYPE="text/xml" FORMAT_URI="http://www.openarchives.org/OAI/2.0/oai_dc/">
                    <foxml:xmlContent>
                        <oai_dc:dc xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.openarchives.org/OAI/2.0/oai_dc/ http://www.openarchives.org/OAI/2.0/oai_dc.xsd">
                            <xsl:if test="$label">
                                <dc:title><xsl:value-of select="$label" /></dc:title>
                            </xsl:if>
                            <dc:identifier><xsl:value-of select="$pid" /></dc:identifier>
                            <dc:identifier><xsl:value-of select="$id" /></dc:identifier>
                        </oai_dc:dc>
                    </foxml:xmlContent>
                </foxml:datastreamVersion>
            </foxml:datastream>
            <foxml:datastream ID="RELS-EXT" CONTROL_GROUP="X" VERSIONABLE="true">
                <foxml:datastreamVersion ID="RELS-EXT.0" FORMAT_URI="info:fedora/fedora-system:FedoraRELSExt-1.0" MIMETYPE="application/rdf+xml">
                    <foxml:xmlContent>
                      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" 
                                xmlns:fedora="info:fedora/fedora-system:def/relations-external#" 
                                xmlns:uva-lib="http://fedora.lib.virginia.edu/relationships#">
                            <rdf:Description>
                                <xsl:attribute name="rdf:about" select="concat('info:fedora/', $pid)" />
                                <hasModel xmlns="info:fedora/fedora-system:def/model#">
                                    <xsl:attribute name="rdf:resource" select="concat('info:fedora/', $metadataCModel)" />
                                </hasModel>
                              <hasModel xmlns="info:fedora/fedora-system:def/model#">
                                <xsl:attribute name="rdf:resource" select="concat('info:fedora/', $structuralCModel)" />
                              </hasModel>
                              <xsl:if test="$parentPid">
                                <fedora:isPartOf>
                                  <xsl:attribute name="rdf:resource" select="concat('info:fedora/', $parentPid)" />
                                </fedora:isPartOf>
                              </xsl:if>
                              <xsl:if test="$previousPid">
                                <uva-lib:follows>
                                  <xsl:attribute name="rdf:resource" select="concat('info:fedora/', $previousPid)" />
                                </uva-lib:follows>
                              </xsl:if>
                              <xsl:if test="$visibility">
                                <uva-lib:visibility><xsl:value-of select="$visibility" /></uva-lib:visibility>
                              </xsl:if>
                            </rdf:Description>
                        </rdf:RDF>
                    </foxml:xmlContent>
                </foxml:datastreamVersion>
            </foxml:datastream>
            <foxml:datastream ID="metadata" STATE="A" CONTROL_GROUP="M" VERSIONABLE="true">
                <foxml:datastreamVersion ID="metadata1.0" LABEL="PBCore 2.0 metadata" MIMETYPE="text/xml">
                    <foxml:xmlContent><xsl:copy-of select="current()" /></foxml:xmlContent>
                </foxml:datastreamVersion>
            </foxml:datastream>
        </foxml:digitalObject>
    </xsl:template>
    
</xsl:stylesheet>