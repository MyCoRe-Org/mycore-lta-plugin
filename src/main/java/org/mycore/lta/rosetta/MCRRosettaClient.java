/*
 * This file is part of ***  M y C o R e  ***
 * See https://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mycore.lta.rosetta;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathFactory;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.config.annotation.MCRPostConstruction;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.lta.MCRLTAClient;
import org.mycore.lta.MCRLTADepositStatus;
import org.mycore.lta.MCRLTAState;
import org.mycore.lta.MCRLTASubmitResult;

/**
 * Client of the REST API of Ex Libris Rosetta (<code>/rest/v0/deposits</code>).
 * <p>
 * Properties:
 * <ul>
 *   <li><code>BaseUrl</code>: the base URL, e.g. <code>https://rosetta.develop.lza.tib.eu</code></li>
 *   <li><code>Username</code>, <code>Password</code>, <code>Institution</code>: the credentials, the user name is
 *   sent as <code>{Username}-institutionCode-{Institution}</code></li>
 *   <li><code>ProducerId</code>, <code>MaterialFlowId</code>: the producer and material flow of the deposits</li>
 *   <li><code>ConnectTimeoutSeconds</code>, <code>ReadTimeoutSeconds</code>: timeouts, defaults to
 *   <code>MCR.LTA.Default.Rosetta.ConnectTimeoutSeconds</code> and
 *   <code>MCR.LTA.Default.Rosetta.ReadTimeoutSeconds</code></li>
 *   <li><code>PendingStates</code>: comma separated deposit states that mean that processing is not finished,
 *   defaults to <code>MCR.LTA.Default.Rosetta.PendingStates</code></li>
 *   <li><code>SuccessStates</code>: comma separated deposit states that mean success, defaults to
 *   <code>MCR.LTA.Default.Rosetta.SuccessStates</code>. Every other state means failure.</li>
 *   <li><code>CreationDateFormat</code>, <code>CreationDateZone</code>: format and time zone of the creation
 *   date of a deposit, defaults to <code>MCR.LTA.Default.Rosetta.CreationDateFormat</code> and
 *   <code>MCR.LTA.Default.Rosetta.CreationDateZone</code></li>
 * </ul>
 * The password and the authorization header are never logged.
 */
public class MCRRosettaClient implements MCRLTAClient {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String DEPOSITS = "/rest/v0/deposits";

    /**
     * The base URL.
     */
    @MCRProperty(name = "BaseUrl")
    public String baseUrl;

    /**
     * The user name without institution.
     */
    @MCRProperty(name = "Username")
    public String username;

    /**
     * The password.
     */
    @MCRProperty(name = "Password")
    public String password;

    /**
     * The institution code.
     */
    @MCRProperty(name = "Institution")
    public String institution;

    /**
     * The ID of the producer.
     */
    @MCRProperty(name = "ProducerId")
    public String producerId;

    /**
     * The ID of the material flow.
     */
    @MCRProperty(name = "MaterialFlowId")
    public String materialFlowId;

    /**
     * The connect timeout in seconds.
     */
    @MCRProperty(name = "ConnectTimeoutSeconds", defaultName = "MCR.LTA.Default.Rosetta.ConnectTimeoutSeconds")
    public String connectTimeoutSeconds;

    /**
     * The read timeout in seconds.
     */
    @MCRProperty(name = "ReadTimeoutSeconds", defaultName = "MCR.LTA.Default.Rosetta.ReadTimeoutSeconds")
    public String readTimeoutSeconds;

    /**
     * The pending deposit states.
     */
    @MCRProperty(name = "PendingStates", defaultName = "MCR.LTA.Default.Rosetta.PendingStates")
    public String pendingStates;

    /**
     * The successful deposit states.
     */
    @MCRProperty(name = "SuccessStates", defaultName = "MCR.LTA.Default.Rosetta.SuccessStates")
    public String successStates;

    /**
     * The format of the creation date.
     */
    @MCRProperty(name = "CreationDateFormat", defaultName = "MCR.LTA.Default.Rosetta.CreationDateFormat")
    public String creationDateFormat;

    /**
     * The time zone of the creation date.
     */
    @MCRProperty(name = "CreationDateZone", defaultName = "MCR.LTA.Default.Rosetta.CreationDateZone")
    public String creationDateZone;

    private HttpClient httpClient;

    private Duration readTimeout;

    private Set<String> pending;

    private Set<String> success;

    private DateTimeFormatter dateFormatter;

    /**
     * Initializes the client after all properties are set.
     */
    @MCRPostConstruction
    public void init() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(Long.parseLong(connectTimeoutSeconds.trim())))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        readTimeout = Duration.ofSeconds(Long.parseLong(readTimeoutSeconds.trim()));
        pending = toSet(pendingStates);
        success = toSet(successStates);
        dateFormatter = DateTimeFormatter.ofPattern(creationDateFormat, Locale.ROOT)
            .withZone(ZoneId.of(creationDateZone));
    }

    private static Set<String> toSet(String value) {
        return MCRConfiguration2.splitValue(value)
            .map(s -> s.toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public MCRLTASubmitResult submit(String subdirectory) throws IOException {
        String body = createDepositBody(subdirectory);
        HttpRequest request = newRequest(DEPOSITS)
            .header("Content-Type", "text/xml; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        LOGGER.info("Creating deposit for {} at {} as {}", subdirectory, request.uri(), getUser());
        Document response = send(request);
        String depositId = getText(response, "id");
        String submissionId = getText(response, "sip_id");
        Instant created = parseCreationDate(getOptionalText(response, "creation_date"));
        return new MCRLTASubmitResult(depositId, submissionId, created);
    }

    @Override
    public MCRLTADepositStatus getStatus(String depositId) throws IOException {
        HttpRequest request = newRequest(DEPOSITS + "/" + depositId).GET().build();
        LOGGER.debug("Querying deposit {} at {} as {}", depositId, request.uri(), getUser());
        String depositState = getText(send(request), "status");
        return new MCRLTADepositStatus(depositState, mapState(depositState));
    }

    /**
     * Maps a deposit state reported by Rosetta to the state of the submission.
     *
     * @param depositState the deposit state
     * @return {@link MCRLTAState#SUBMITTED} if pending, {@link MCRLTAState#FINISHED} if successful,
     * {@link MCRLTAState#FAILED} otherwise
     */
    public MCRLTAState mapState(String depositState) {
        String normalized = depositState.trim().toUpperCase(Locale.ROOT);
        if (pending.contains(normalized)) {
            return MCRLTAState.SUBMITTED;
        }
        if (success.contains(normalized)) {
            return MCRLTAState.FINISHED;
        }
        return MCRLTAState.FAILED;
    }

    /**
     * Creates the body of a deposit request.
     *
     * @param subdirectory the subdirectory of the package
     * @return the XML body
     */
    public String createDepositBody(String subdirectory) {
        Element deposit = new Element("deposit");
        deposit.setAttribute("link", "string");
        deposit.addContent(new Element("subdirectory").setText(subdirectory));
        deposit.addContent(new Element("producer").setText(producerId));
        deposit.addContent(new Element("material_flow").setText(materialFlowId));
        return new XMLOutputter(Format.getCompactFormat().setEncoding("UTF-8")).outputString(new Document(deposit));
    }

    private HttpRequest.Builder newRequest(String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String credentials = getUser() + ":" + password;
        String token = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(URI.create(base + path))
            .timeout(readTimeout)
            .header("Accept", "application/xml")
            .header("Authorization", "Basic " + token);
    }

    private String getUser() {
        return username + "-institutionCode-" + institution;
    }

    private Document send(HttpRequest request) throws IOException {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling " + request.uri(), e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Rosetta returned HTTP " + response.statusCode() + " for " + request.method() + " "
                + request.uri() + ": " + abbreviate(response.body()));
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.contains("xml")) {
            throw new IOException("Rosetta returned unexpected content type '" + contentType + "' for "
                + request.uri() + ": " + abbreviate(response.body()));
        }
        return parse(response.body());
    }

    private static Document parse(String xml) throws IOException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        builder.setExpandEntities(false);
        try {
            return builder.build(new StringReader(xml));
        } catch (JDOMException e) {
            throw new IOException("Invalid XML response from Rosetta: " + abbreviate(xml), e);
        }
    }

    private static String getText(Document document, String name) throws IOException {
        List<Element> elements = find(document, name);
        if (elements.size() != 1) {
            throw new IOException("Expected one element " + name + " in Rosetta response, found "
                + elements.size());
        }
        return elements.getFirst().getTextTrim();
    }

    private static String getOptionalText(Document document, String name) {
        List<Element> elements = find(document, name);
        return elements.isEmpty() ? null : elements.getFirst().getTextTrim();
    }

    private static List<Element> find(Document document, String name) {
        return XPathFactory.instance().compile("//*[local-name()='" + name + "']", Filters.element())
            .evaluate(document);
    }

    private Instant parseCreationDate(String value) {
        if (value == null || value.isEmpty()) {
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(value, dateFormatter).atZone(ZoneId.of(creationDateZone)).toInstant();
        } catch (DateTimeException e) {
            LOGGER.warn("Unable to parse creation date '{}' of deposit, using current time", value);
            return Instant.now();
        }
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) + "..." : value;
    }

}
