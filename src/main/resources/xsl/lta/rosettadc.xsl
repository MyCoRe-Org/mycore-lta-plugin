<?xml version="1.0" encoding="UTF-8"?>
<!--
  Dublin Core description of a MyCoRe object for the dmdSec of a Rosetta SIP.
  Uses mods2oai_dc.xsl of MIR, so this stylesheet requires MIR or an application with the same stylesheet.
  Parameters:
    License: text of dcterms:license, e.g. the agreement between the institution and the archive (optional)
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/"
  xmlns:dc="http://purl.org/dc/elements/1.1/"
  xmlns:dcterms="http://purl.org/dc/terms/"
  xmlns:exslt="http://exslt.org/common"
  xmlns:mods="http://www.loc.gov/mods/v3"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  exclude-result-prefixes="oai_dc mods xlink exslt">

  <xsl:include href="../mods2oai_dc.xsl" />

  <xsl:param name="License" select="''" />

  <xsl:template match="/">
    <dc:record>
      <xsl:variable name="mods" select="mycoreobject/metadata//mods:mods" />
      <xsl:variable name="oai_dc">
        <xsl:apply-templates select="mycoreobject" mode="metadata" />
      </xsl:variable>

      <dc:identifier>
        <xsl:value-of select="mycoreobject/@ID" />
      </dc:identifier>

      <xsl:copy-of select="exslt:node-set($oai_dc)/oai_dc:dc/*[not(local-name()='rights' or local-name()='identifier')]" />

      <xsl:for-each select="$mods/mods:identifier[not(starts-with(text(),'info:eu-repo/grantAgreement/'))]">
        <dc:identifier>
          <xsl:value-of select="text()" />
        </dc:identifier>
      </xsl:for-each>

      <xsl:for-each select="$mods/mods:relatedItem[@type='original' or @type='otherVersion']">
        <xsl:choose>
          <xsl:when test="mods:identifier">
            <xsl:for-each select="mods:identifier">
              <dcterms:isVersionOf>
                <xsl:value-of select="text()" />
              </dcterms:isVersionOf>
            </xsl:for-each>
          </xsl:when>
          <xsl:when test="@xlink:href">
            <dcterms:isVersionOf>
              <xsl:value-of select="@xlink:href" />
            </dcterms:isVersionOf>
          </xsl:when>
        </xsl:choose>
      </xsl:for-each>

      <xsl:choose>
        <xsl:when test="$mods/mods:accessCondition[@type='embargo']">
          <dcterms:accessRights>info:eu-repo/semantics/embargoedAccess</dcterms:accessRights>
        </xsl:when>
        <xsl:otherwise>
          <dcterms:accessRights>info:eu-repo/semantics/openAccess</dcterms:accessRights>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:for-each select="$mods/mods:accessCondition[@type='use and reproduction' and @xlink:href]">
        <xsl:variable name="licenseId" select="substring-after(@xlink:href,'#')" />
        <xsl:if test="string-length($licenseId) &gt; 0">
          <xsl:variable name="license"
            select="document(concat('classification:metadata:0:children:mir_licenses:',$licenseId))" />
          <xsl:for-each select="$license//url[@xlink:href and @xlink:type='locator']">
            <dcterms:accessRights>
              <xsl:value-of select="@xlink:href" />
            </dcterms:accessRights>
          </xsl:for-each>
        </xsl:if>
      </xsl:for-each>

      <xsl:if test="string-length($License) &gt; 0">
        <dcterms:license>
          <xsl:value-of select="$License" />
        </dcterms:license>
      </xsl:if>
    </dc:record>
  </xsl:template>

</xsl:stylesheet>
