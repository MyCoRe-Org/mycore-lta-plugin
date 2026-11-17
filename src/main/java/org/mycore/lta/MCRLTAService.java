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

package org.mycore.lta;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.access.MCRAccessException;
import org.mycore.common.MCRException;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.config.annotation.MCRInstance;
import org.mycore.common.config.annotation.MCRPostConstruction;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.lta.persistence.MCRLTASubmissionStore;
import org.mycore.lta.report.MCRLTAReportImporter;
import org.mycore.lta.report.MCRLTAReportRow;
import org.mycore.lta.selector.MCRLTASelector;

/**
 * A long term archiving service, e.g. the Rosetta instance of the TIB. A service is configured with the prefix
 * {@value #PROPERTY_PREFIX} followed by its ID. All building blocks are configurable instances:
 * <ul>
 *   <li><code>Selector</code>: selects the candidates, see {@link MCRLTASelector}</li>
 *   <li><code>PackageGenerator</code>: builds the package, see {@link MCRLTAPackageGenerator}</li>
 *   <li><code>Transport</code>: uploads the package, see {@link MCRLTATransport}</li>
 *   <li><code>Client</code>: creates deposits and queries their status, see {@link MCRLTAClient}</li>
 *   <li><code>ReportImporter</code> (optional): reads the reports of the archive, see
 *   {@link MCRLTAReportImporter}</li>
 * </ul>
 * Further properties:
 * <ul>
 *   <li><code>Reason</code>: the default reason of a submission, defaults to
 *   <code>MCR.LTA.Default.Reason</code></li>
 *   <li><code>PauseSeconds</code>: the pause between two submissions of a bulk run, defaults to
 *   <code>MCR.LTA.Default.PauseSeconds</code></li>
 * </ul>
 * All methods operate in the current session and transaction. Changes are written to the service flags of the
 * object; the database is updated by {@link MCRLTAEventHandler}.
 */
public class MCRLTAService {

    /**
     * The prefix of the properties of all services.
     */
    public static final String PROPERTY_PREFIX = "MCR.LTA.Service.";

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The selector of the candidates.
     */
    @MCRInstance(name = "Selector", valueClass = MCRLTASelector.class)
    public MCRLTASelector selector;

    /**
     * The generator of the packages.
     */
    @MCRInstance(name = "PackageGenerator", valueClass = MCRLTAPackageGenerator.class)
    public MCRLTAPackageGenerator packageGenerator;

    /**
     * The transport of the packages.
     */
    @MCRInstance(name = "Transport", valueClass = MCRLTATransport.class)
    public MCRLTATransport transport;

    /**
     * The client of the archive.
     */
    @MCRInstance(name = "Client", valueClass = MCRLTAClient.class)
    public MCRLTAClient client;

    /**
     * The importer of reports, may be null.
     */
    @MCRInstance(name = "ReportImporter", valueClass = MCRLTAReportImporter.class, required = false)
    public MCRLTAReportImporter reportImporter;

    /**
     * The default reason of a submission.
     */
    @MCRProperty(name = "Reason", defaultName = "MCR.LTA.Default.Reason")
    public String reason;

    private long pauseMillis;

    private String id;

    /**
     * Returns the configured service with the given ID. Services are singletons.
     *
     * @param id the ID of the service
     * @return the service
     */
    public static MCRLTAService obtainInstance(String id) {
        return MCRConfiguration2.getSingleInstanceOfOrThrow(MCRLTAService.class, PROPERTY_PREFIX + id);
    }

    /**
     * Sets the pause between two submissions of a bulk run.
     *
     * @param seconds the pause in seconds, may be fractional
     */
    @MCRProperty(name = "PauseSeconds", defaultName = "MCR.LTA.Default.PauseSeconds")
    public void setPauseSeconds(String seconds) {
        double value = Double.parseDouble(seconds.trim());
        if (value < 0) {
            throw new IllegalArgumentException("PauseSeconds must not be negative: " + seconds);
        }
        this.pauseMillis = Math.round(value * 1000);
    }

    /**
     * Derives the ID of the service from the name of its property.
     *
     * @param property the name of the property, e.g. <code>MCR.LTA.Service.TIB</code>
     */
    @MCRPostConstruction
    public void setProperty(String property) {
        this.id = property.substring(property.lastIndexOf('.') + 1);
    }

    /**
     * @return the ID of the service
     */
    public String getId() {
        return id;
    }

    /**
     * @return the selector of the candidates
     */
    public MCRLTASelector getSelector() {
        return selector;
    }

    /**
     * @return the importer of reports, if configured
     */
    public Optional<MCRLTAReportImporter> getReportImporter() {
        return Optional.ofNullable(reportImporter);
    }

    /**
     * @return the default reason of a submission
     */
    public String getDefaultReason() {
        return reason;
    }

    /**
     * Returns whether an object may be submitted: it exists and has no open or successful submission to this
     * service, neither in its service flags nor in the database.
     *
     * @param objectId the ID of the object
     * @return true, if the object may be submitted
     */
    public boolean isCandidate(MCRObjectID objectId) {
        if (!MCRMetadataManager.exists(objectId) || MCRLTASubmissionStore.hasBlocking(objectId, id)) {
            return false;
        }
        return !MCRLTAFlags.hasBlocking(MCRMetadataManager.retrieveMCRObject(objectId), id);
    }

    /**
     * Submits an object: builds the package, uploads it, creates the deposit and stores the submission in the
     * service flags of the object.
     * <p>
     * If the package can not be built, a failed submission without deposit is stored and returned, so the
     * object is not selected again automatically. No exception is thrown in this case, so the stored flag is not
     * lost by a rollback of the surrounding transaction. If upload or deposit fail, nothing is stored and an
     * {@link IOException} is thrown, because these errors usually affect all objects.
     *
     * @param objectId the ID of the object
     * @param submissionReason the reason of the submission, the default reason if null or blank
     * @return the stored submission, in state {@link MCRLTAState#FAILED} if the package could not be built
     * @throws IOException if upload or deposit fail
     * @throws MCRLTAException if the object does not exist or is already submitted
     */
    public MCRLTASubmission submit(MCRObjectID objectId, String submissionReason) throws IOException {
        String effectiveReason = submissionReason == null || submissionReason.isBlank() ? reason
            : submissionReason;
        if (!isCandidate(objectId)) {
            throw new MCRLTAException(objectId + " does not exist or has an open or successful submission to "
                + id);
        }
        MCRObject object = MCRMetadataManager.retrieveMCRObject(objectId);
        MCRLTAPackage pkg;
        try {
            pkg = packageGenerator.generate(object, effectiveReason);
        } catch (IOException | RuntimeException e) {
            MCRLTASubmission failed = new MCRLTASubmission.Builder(id, MCRLTAState.FAILED)
                .created(Instant.now())
                .reason(effectiveReason)
                .build();
            MCRLTAFlags.add(object, failed);
            save(object);
            LOGGER.error("Unable to build package of {} for {}, marked as failed", objectId, id, e);
            return failed;
        }
        LOGGER.info("Uploading {} with {} files to {}", objectId, pkg.manifest().getFileCount(), id);
        String subdirectory = transport.upload(pkg);
        MCRLTASubmitResult result = client.submit(subdirectory);
        MCRLTASubmission submission = MCRLTASubmission.ofSubmitted(id, result, effectiveReason);
        MCRLTAFlags.add(object, submission);
        save(object);
        LOGGER.info("Submitted {} to {}: deposit {}, submission {}", objectId, id, result.depositId(),
            result.submissionId());
        return submission;
    }

    /**
     * Queries the status of an open deposit and stores the result, if the state changed.
     *
     * @param objectId the ID of the object
     * @param depositId the ID of the deposit
     * @return the state after the check
     * @throws IOException if the status can not be queried
     */
    public MCRLTAState check(MCRObjectID objectId, String depositId) throws IOException {
        MCRObject object = MCRMetadataManager.retrieveMCRObject(objectId);
        MCRLTASubmission submission = MCRLTAFlags.list(object, id).stream()
            .filter(s -> Objects.equals(s.getDepositId(), depositId))
            .findFirst()
            .orElseThrow(() -> new MCRLTAException("No submission with deposit " + depositId + " in " + objectId));
        if (submission.getState() != MCRLTAState.SUBMITTED) {
            MCRLTASubmissionStore.markChecked(id, depositId, Instant.now());
            return submission.getState();
        }
        MCRLTADepositStatus status = client.getStatus(depositId);
        MCRLTASubmissionStore.markChecked(id, depositId, Instant.now());
        if (status.isPending()) {
            LOGGER.debug("Deposit {} of {} is still pending: {}", depositId, objectId, status.depositState());
            return MCRLTAState.SUBMITTED;
        }
        MCRLTASubmission checked = submission.withCheckResult(status.state(), status.depositState());
        MCRLTAFlags.replace(object, checked);
        save(object);
        LOGGER.info("Deposit {} of {} is {} ({})", depositId, objectId, checked.getState(), status.depositState());
        return checked.getState();
    }

    /**
     * Applies a row of a report of the archive to the matching submission.
     *
     * @param objectId the ID of the object
     * @param row the row of the report
     * @param reportFileName the file name of the report
     * @return the outcome
     */
    public VerifyOutcome verify(MCRObjectID objectId, MCRLTAReportRow row, String reportFileName) {
        if (!MCRMetadataManager.exists(objectId)) {
            return VerifyOutcome.NOT_FOUND;
        }
        MCRObject object = MCRMetadataManager.retrieveMCRObject(objectId);
        List<MCRLTASubmission> submissions = MCRLTAFlags.list(object, id);
        Optional<MCRLTASubmission> match = submissions.stream()
            .filter(s -> Objects.equals(s.getSubmissionId(), row.submissionId()))
            .findFirst();
        if (match.isEmpty()) {
            return VerifyOutcome.NOT_FOUND;
        }
        MCRLTASubmission submission = match.get();
        if (submission.getState() == MCRLTAState.VERIFIED
            && Objects.equals(submission.getEntityId(), row.entityId())) {
            return VerifyOutcome.ALREADY_VERIFIED;
        }
        if (submission.getState() == MCRLTAState.FAILED) {
            LOGGER.warn("Report {} confirms failed submission {} of {}, verifying it anyway", reportFileName,
                row.submissionId(), objectId);
        }
        MCRLTASubmission verified = submission.withVerification(row.entityId(), row.created(), reportFileName,
            Instant.now());
        MCRLTAFlags.replace(object, verified);
        save(object);
        LOGGER.info("Verified submission {} of {}: entity {}", row.submissionId(), objectId, row.entityId());
        return VerifyOutcome.VERIFIED;
    }

    /**
     * Pauses between two submissions of a bulk run as configured by <code>PauseSeconds</code>.
     */
    public void pause() {
        if (pauseMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(pauseMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MCRException("Interrupted while pausing between submissions", e);
        }
    }

    private static void save(MCRObject object) {
        try {
            MCRMetadataManager.update(object);
        } catch (MCRAccessException e) {
            throw new MCRLTAException("Not allowed to update " + object.getId(), e);
        }
    }

    /**
     * The outcome of applying a row of a report.
     */
    public enum VerifyOutcome {

        /**
         * The submission was verified.
         */
        VERIFIED,

        /**
         * The submission was already verified with the same entity.
         */
        ALREADY_VERIFIED,

        /**
         * No matching submission was found.
         */
        NOT_FOUND

    }

}
