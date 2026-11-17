<?xml version="1.0" encoding="UTF-8"?>
<!--
  METS file (content/ie1.xml) of a Rosetta SIP, created from the manifest of the package:
  <manifest objectId reason><derivate id><file number path originalPath size md5/></derivate></manifest>
  Parameters:
    DcTransformerId: content transformer of the Dublin Core description
    Status, IeEntityType, UserDefinedA, UserDefinedB, UserDefinedC: generalIECharacteristics
    PreservationType, UsageType, RevisionNumber: generalRepCharacteristics
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:mets="http://www.loc.gov/METS/"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:mcrxsl="xalan://org.mycore.common.xml.MCRXMLFunctions"
  exclude-result-prefixes="mcrxsl">

  <xsl:param name="DcTransformerId" select="'lta-rosetta-dc'" />
  <xsl:param name="Status" select="''" />
  <xsl:param name="IeEntityType" select="''" />
  <xsl:param name="UserDefinedA" select="''" />
  <xsl:param name="UserDefinedB" select="''" />
  <xsl:param name="UserDefinedC" select="''" />
  <xsl:param name="PreservationType" select="''" />
  <xsl:param name="UsageType" select="''" />
  <xsl:param name="RevisionNumber" select="''" />

  <xsl:template match="/manifest">
    <mets:mets>

      <mets:dmdSec ID="ie-dmd">
        <mets:mdWrap MDTYPE="DC">
          <mets:xmlData>
            <xsl:copy-of select="document(concat('xslTransform:',$DcTransformerId,':mcrobject:',@objectId))" />
          </mets:xmlData>
        </mets:mdWrap>
      </mets:dmdSec>

      <mets:amdSec ID="ie-amd">
        <mets:techMD ID="ie-amd-tech">
          <mets:mdWrap MDTYPE="OTHER" OTHERMDTYPE="dnx">
            <mets:xmlData>
              <dnx xmlns="http://www.exlibrisgroup.com/dps/dnx">
                <section id="generalIECharacteristics">
                  <record>
                    <key id="submissionReason">
                      <xsl:value-of select="@reason" />
                    </key>
                    <key id="status">
                      <xsl:value-of select="$Status" />
                    </key>
                    <key id="IEEntityType">
                      <xsl:value-of select="$IeEntityType" />
                    </key>
                    <xsl:if test="$UserDefinedA != ''">
                      <key id="UserDefinedA">
                        <xsl:value-of select="$UserDefinedA" />
                      </key>
                    </xsl:if>
                    <xsl:if test="$UserDefinedB != ''">
                      <key id="UserDefinedB">
                        <xsl:value-of select="$UserDefinedB" />
                      </key>
                    </xsl:if>
                    <xsl:if test="$UserDefinedC != ''">
                      <key id="UserDefinedC">
                        <xsl:value-of select="$UserDefinedC" />
                      </key>
                    </xsl:if>
                  </record>
                </section>
              </dnx>
            </mets:xmlData>
          </mets:mdWrap>
        </mets:techMD>
        <mets:sourceMD ID="ie-amd-source-1">
          <mets:mdWrap MDTYPE="OTHER" OTHERMDTYPE="object">
            <mets:xmlData>
              <xsl:copy-of select="document(concat('mcrobject:',@objectId))" />
            </mets:xmlData>
          </mets:mdWrap>
        </mets:sourceMD>
        <xsl:for-each select="derivate">
          <mets:sourceMD ID="{concat('ie-amd-source-',position()+1)}">
            <mets:mdWrap MDTYPE="OTHER" OTHERMDTYPE="derivate">
              <mets:xmlData>
                <xsl:copy-of select="document(concat('mcrobject:',@id))" />
              </mets:xmlData>
            </mets:mdWrap>
          </mets:sourceMD>
        </xsl:for-each>
      </mets:amdSec>

      <mets:amdSec ID="rep1-amd">
        <mets:techMD ID="rep1-amd-tech">
          <mets:mdWrap MDTYPE="OTHER" OTHERMDTYPE="dnx">
            <mets:xmlData>
              <dnx xmlns="http://www.exlibrisgroup.com/dps/dnx">
                <section id="generalRepCharacteristics">
                  <record>
                    <key id="preservationType">
                      <xsl:value-of select="$PreservationType" />
                    </key>
                    <key id="usageType">
                      <xsl:value-of select="$UsageType" />
                    </key>
                    <key id="RevisionNumber">
                      <xsl:value-of select="$RevisionNumber" />
                    </key>
                  </record>
                </section>
              </dnx>
            </mets:xmlData>
          </mets:mdWrap>
        </mets:techMD>
      </mets:amdSec>

      <xsl:for-each select="derivate/file">
        <mets:amdSec ID="fid1-{@number}-amd">
          <mets:techMD ID="fid1-{@number}-amd-tech">
            <mets:mdWrap MDTYPE="OTHER" OTHERMDTYPE="dnx">
              <mets:xmlData>
                <dnx xmlns="http://www.exlibrisgroup.com/dps/dnx">
                  <section id="generalFileCharacteristics">
                    <record>
                      <key id="fileOriginalPath">
                        <xsl:value-of select="concat(/manifest/@objectId,'/',@path)" />
                      </key>
                      <key id="fileSizeBytes">
                        <xsl:value-of select="@size" />
                      </key>
                    </record>
                  </section>
                  <section id="fileFixity">
                    <record>
                      <key id="fixityType">MD5</key>
                      <key id="fixityValue">
                        <xsl:value-of select="@md5" />
                      </key>
                    </record>
                  </section>
                </dnx>
              </mets:xmlData>
            </mets:mdWrap>
          </mets:techMD>
        </mets:amdSec>
      </xsl:for-each>

      <mets:fileSec>
        <mets:fileGrp ADMID="rep1-amd" ID="rep1">
          <xsl:for-each select="derivate/file">
            <mets:file ADMID="fid1-{@number}-amd" ID="fid1-{@number}">
              <mets:FLocat LOCTYPE="URL" xlink:href="{concat(../@id,mcrxsl:encodeURIPath(@originalPath))}" />
            </mets:file>
          </xsl:for-each>
        </mets:fileGrp>
      </mets:fileSec>

      <mets:structMap ID="rep1-1" TYPE="PHYSICAL">
        <mets:div LABEL="Preservation Master">
          <xsl:for-each select="derivate">
            <mets:div LABEL="{@id}">
              <xsl:for-each select="file">
                <mets:div TYPE="FILE">
                  <mets:fptr FILEID="fid1-{@number}" />
                </mets:div>
              </xsl:for-each>
            </mets:div>
          </xsl:for-each>
        </mets:div>
      </mets:structMap>

    </mets:mets>
  </xsl:template>

</xsl:stylesheet>
