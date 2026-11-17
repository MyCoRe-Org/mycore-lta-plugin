<?xml version="1.0" encoding="UTF-8"?>
<!--
  Adds the long term archive state of an object to its Solr document:
    lta.{service}.state: the states of the submissions to the service (multi valued)
    lta.{service}.created: the creation dates of the submissions (multi valued)
    lta.{service}.depositId, lta.{service}.submissionId, lta.{service}.entityId (multi valued)
  The data is read with the lta: URI resolver from the service flags of the object.
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:import href="xslImport:solr-document:lta/solrlta.xsl" />

  <xsl:template match="mycoreobject">
    <xsl:apply-imports />
    <xsl:if test="service/servflags/servflag[@type='MyCoRe-LTA']">
      <xsl:for-each select="document(concat('lta:',@ID))/lta/submission">
        <field name="{concat('lta.',@service,'.state')}">
          <xsl:value-of select="@state" />
        </field>
        <xsl:if test="@created">
          <field name="{concat('lta.',@service,'.created')}">
            <xsl:value-of select="@created" />
          </field>
        </xsl:if>
        <xsl:if test="@depositId">
          <field name="{concat('lta.',@service,'.depositId')}">
            <xsl:value-of select="@depositId" />
          </field>
        </xsl:if>
        <xsl:if test="@submissionId">
          <field name="{concat('lta.',@service,'.submissionId')}">
            <xsl:value-of select="@submissionId" />
          </field>
        </xsl:if>
        <xsl:if test="@entityId">
          <field name="{concat('lta.',@service,'.entityId')}">
            <xsl:value-of select="@entityId" />
          </field>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>

</xsl:stylesheet>
