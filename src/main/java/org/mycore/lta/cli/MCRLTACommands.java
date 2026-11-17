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

package org.mycore.lta.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.datamodel.common.MCRXMLMetadataManager;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.frontend.cli.annotation.MCRCommand;
import org.mycore.frontend.cli.annotation.MCRCommandGroup;
import org.mycore.lta.MCRLTAException;
import org.mycore.lta.MCRLTAFlags;
import org.mycore.lta.MCRLTAService;
import org.mycore.lta.MCRLTAState;
import org.mycore.lta.MCRLTASubmission;
import org.mycore.lta.persistence.MCRLTASubmissionEntity;
import org.mycore.lta.persistence.MCRLTASubmissionStore;
import org.mycore.lta.report.MCRLTAReport;
import org.mycore.lta.report.MCRLTAReportImporter;
import org.mycore.lta.report.MCRLTAReportRow;
import org.mycore.lta.selector.MCRLTASelection;

/**
 * Commands of the long term archive module. Commands that work on many objects return one sub command per
 * object, so every object is processed in its own transaction and an interrupted run can be resumed.
 */
@MCRCommandGroup(name = "Long Term Archive Commands")
public final class MCRLTACommands {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String UNKNOWN_DATE = "unknown";

    private static final int SELECTION_PAGE_SIZE = 500;

    private MCRLTACommands() {
    }

    /**
     * Submits an object with a reason, even if it is not selected by the selector of the service.
     *
     * @param objectId the ID of the object
     * @param serviceId the ID of the service
     * @param reason the reason of the submission
     * @throws IOException if upload or deposit fail
     */
    @MCRCommand(syntax = "lta submit object {0} to service {1} with reason {2}",
        help = "Submits object {0} to long term archive service {1} with reason {2}.",
        order = 10)
    public static void submitObjectWithReason(String objectId, String serviceId, String reason)
        throws IOException {
        submit(MCRObjectID.getInstance(objectId), MCRLTAService.obtainInstance(serviceId), reason, false);
    }

    /**
     * Submits an object with the default reason, even if it is not selected by the selector of the service.
     *
     * @param objectId the ID of the object
     * @param serviceId the ID of the service
     * @throws IOException if upload or deposit fail
     */
    @MCRCommand(syntax = "lta submit object {0} to service {1}",
        help = "Submits object {0} to long term archive service {1} with the default reason.",
        order = 20)
    public static void submitObject(String objectId, String serviceId) throws IOException {
        submit(MCRObjectID.getInstance(objectId), MCRLTAService.obtainInstance(serviceId), null, false);
    }

    /**
     * Returns sub commands that submit all candidates selected by the selector of the service.
     *
     * @param serviceId the ID of the service
     * @return the sub commands
     */
    @MCRCommand(syntax = "lta submit all candidates to service {0}",
        help = "Submits all candidates of long term archive service {0}, one command per object.",
        order = 30)
    public static List<String> submitAllCandidates(String serviceId) {
        return submitCandidates(Integer.MAX_VALUE, serviceId);
    }

    /**
     * Returns sub commands that submit the next candidates selected by the selector of the service.
     *
     * @param count the maximum number of objects
     * @param serviceId the ID of the service
     * @return the sub commands
     */
    @MCRCommand(syntax = "lta submit {0} candidates to service {1}",
        help = "Submits up to {0} candidates of long term archive service {1}, one command per object.",
        order = 40)
    public static List<String> submitCandidates(String count, String serviceId) {
        return submitCandidates(Integer.parseInt(count.trim()), serviceId);
    }

    private static List<String> submitCandidates(int count, String serviceId) {
        MCRLTAService service = MCRLTAService.obtainInstance(serviceId);
        List<String> commands = new ArrayList<>();
        try (MCRLTASelection selection = service.getSelector().open()) {
            while (commands.size() < count) {
                List<MCRObjectID> page = selection.next(Math.min(SELECTION_PAGE_SIZE, count - commands.size()));
                if (page.isEmpty()) {
                    break;
                }
                page.forEach(id -> commands.add("lta submit candidate " + id + " to service " + serviceId));
            }
        }
        LOGGER.info("Selected {} candidates for {}", commands.size(), serviceId);
        return commands;
    }

    /**
     * Submits a candidate and pauses afterwards. Objects that are no longer candidates are skipped.
     *
     * @param objectId the ID of the object
     * @param serviceId the ID of the service
     * @throws IOException if upload or deposit fail, the remaining commands are not executed then
     */
    @MCRCommand(syntax = "lta submit candidate {0} to service {1}",
        help = "Submits candidate {0} to long term archive service {1}, skips it if it is no longer a candidate.",
        order = 50)
    public static void submitCandidate(String objectId, String serviceId) throws IOException {
        MCRLTAService service = MCRLTAService.obtainInstance(serviceId);
        if (submit(MCRObjectID.getInstance(objectId), service, null, true)) {
            service.pause();
        }
    }

    private static boolean submit(MCRObjectID objectId, MCRLTAService service, String reason,
        boolean skipNonCandidates) throws IOException {
        if (skipNonCandidates && !service.isCandidate(objectId)) {
            LOGGER.info("Skipping {}, it is no longer a candidate of {}", objectId, service.getId());
            return false;
        }
        MCRLTASubmission submission = service.submit(objectId, reason);
        if (submission.getState() == MCRLTAState.FAILED) {
            LOGGER.error("Package of {} could not be built, marked as failed", objectId);
        }
        return true;
    }

    /**
     * Returns sub commands that check all open deposits of a service.
     *
     * @param serviceId the ID of the service
     * @return the sub commands
     */
    @MCRCommand(syntax = "lta check service {0}",
        help = "Checks all open deposits of long term archive service {0}, one command per deposit.",
        order = 60)
    public static List<String> checkService(String serviceId) {
        MCRLTAService.obtainInstance(serviceId);
        return MCRLTASubmissionStore.listOpen(serviceId, Instant.now(), Integer.MAX_VALUE).stream()
            .map(e -> "lta check deposit " + e.getDepositId() + " of object " + e.getObjectId() + " for service "
                + serviceId)
            .toList();
    }

    /**
     * Checks all open deposits of an object.
     *
     * @param objectId the ID of the object
     * @param serviceId the ID of the service
     * @throws IOException if the status can not be queried
     */
    @MCRCommand(syntax = "lta check object {0} for service {1}",
        help = "Checks the open deposits of object {0} at long term archive service {1}.",
        order = 70)
    public static void checkObject(String objectId, String serviceId) throws IOException {
        MCRObjectID id = MCRObjectID.getInstance(objectId);
        MCRLTAService service = MCRLTAService.obtainInstance(serviceId);
        List<MCRLTASubmission> open = MCRLTAFlags.list(MCRMetadataManager.retrieveMCRObject(id), serviceId)
            .stream()
            .filter(s -> s.getState() == MCRLTAState.SUBMITTED)
            .toList();
        if (open.isEmpty()) {
            LOGGER.info("{} has no open deposit at {}", id, serviceId);
        }
        for (MCRLTASubmission submission : open) {
            LOGGER.info("Deposit {} of {} is {}", submission.getDepositId(), id,
                service.check(id, submission.getDepositId()));
        }
    }

    /**
     * Checks a single deposit.
     *
     * @param depositId the ID of the deposit
     * @param objectId the ID of the object
     * @param serviceId the ID of the service
     * @throws IOException if the status can not be queried
     */
    @MCRCommand(syntax = "lta check deposit {0} of object {1} for service {2}",
        help = "Checks deposit {0} of object {1} at long term archive service {2}.",
        order = 80)
    public static void checkDeposit(String depositId, String objectId, String serviceId) throws IOException {
        MCRObjectID id = MCRObjectID.getInstance(objectId);
        MCRLTAState state = MCRLTAService.obtainInstance(serviceId).check(id, depositId);
        LOGGER.info("Deposit {} of {} is {}", depositId, id, state);
    }

    /**
     * Reads a report of the archive and returns one sub command per confirmed submission. Rows that can not be
     * assigned to an object are logged.
     *
     * @param serviceId the ID of the service
     * @param file the path of the report
     * @return the sub commands
     * @throws IOException if the report can not be read
     */
    @MCRCommand(syntax = "lta verify service {0} with report {1}",
        help = "Verifies the submissions to long term archive service {0} that are confirmed by report {1} "
            + "(CSV or XLSX).",
        order = 90)
    public static List<String> verifyService(String serviceId, String file) throws IOException {
        MCRLTAService service = MCRLTAService.obtainInstance(serviceId);
        MCRLTAReportImporter importer = service.getReportImporter()
            .orElseThrow(() -> new MCRLTAException("No ReportImporter configured for service " + serviceId));
        Path path = Path.of(file);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Report not found: " + path.toAbsolutePath());
        }
        MCRLTAReport report = importer.read(path);
        report.problems().forEach(p -> LOGGER.warn("Report {}: {}", report.fileName(), p));
        List<String> commands = new ArrayList<>();
        int unmatched = 0;
        for (MCRLTAReportRow row : report.rows()) {
            Optional<MCRObjectID> objectId = MCRLTASubmissionStore.findObjectBySubmissionId(serviceId,
                row.submissionId());
            if (objectId.isEmpty() && row.objectId() != null && MCRObjectID.isValid(row.objectId())) {
                objectId = Optional.of(MCRObjectID.getInstance(row.objectId()));
            }
            if (objectId.isEmpty()) {
                unmatched++;
                LOGGER.warn("Report {}, row {}: no object found for submission {} (entity {})", report.fileName(),
                    row.rowNumber(), row.submissionId(), row.entityId());
                continue;
            }
            if (row.objectId() != null && !row.objectId().equals(objectId.get().toString())) {
                LOGGER.warn("Report {}, row {}: submission {} belongs to {}, the report names {}",
                    report.fileName(), row.rowNumber(), row.submissionId(), objectId.get(), row.objectId());
            }
            commands.add("lta verify submission " + row.submissionId() + " of object " + objectId.get()
                + " for service " + serviceId + " as entity " + row.entityId() + " created "
                + (row.created() == null ? UNKNOWN_DATE : row.created().toString()) + " from report "
                + path.toAbsolutePath());
        }
        LOGGER.info("Report {}: {} rows, {} assigned, {} unmatched, {} problems", report.fileName(),
            report.rows().size(), commands.size(), unmatched, report.problems().size());
        return commands;
    }

    /**
     * Verifies a single submission confirmed by a report.
     *
     * @param submissionId the ID of the submission
     * @param objectId the ID of the object
     * @param serviceId the ID of the service
     * @param entityId the ID of the archived entity
     * @param created the creation date of the entity as ISO instant or <code>unknown</code>
     * @param file the path of the report
     */
    @MCRCommand(syntax = "lta verify submission {0} of object {1} for service {2} as entity {3} created {4} "
        + "from report {5}",
        help = "Verifies submission {0} of object {1} at long term archive service {2} as entity {3}.",
        order = 100)
    public static void verifySubmission(String submissionId, String objectId, String serviceId, String entityId,
        String created, String file) {
        Instant createdDate = UNKNOWN_DATE.equals(created) ? null : Instant.parse(created);
        MCRLTAReportRow row = new MCRLTAReportRow(0, entityId, submissionId, createdDate, objectId);
        String reportFileName = Path.of(file).getFileName().toString();
        MCRObjectID id = MCRObjectID.getInstance(objectId);
        switch (MCRLTAService.obtainInstance(serviceId).verify(id, row, reportFileName)) {
            case VERIFIED -> LOGGER.info("Verified submission {} of {}", submissionId, id);
            case ALREADY_VERIFIED -> LOGGER.info("Submission {} of {} is already verified", submissionId, id);
            case NOT_FOUND -> LOGGER.warn("Submission {} not found in {}", submissionId, id);
        }
    }

    /**
     * Rebuilds the database entries of an object from its service flags.
     *
     * @param objectId the ID of the object
     */
    @MCRCommand(syntax = "lta sync object {0}",
        help = "Rebuilds the long term archive database entries of object {0} from its service flags.",
        order = 110)
    public static void syncObject(String objectId) {
        MCRObjectID id = MCRObjectID.getInstance(objectId);
        if (!MCRMetadataManager.exists(id)) {
            MCRLTASubmissionStore.delete(id);
            return;
        }
        MCRObject object = MCRMetadataManager.retrieveMCRObject(id);
        MCRLTASubmissionStore.sync(id, MCRLTAFlags.list(object));
    }

    /**
     * Returns sub commands that rebuild the database entries of all objects.
     *
     * @return the sub commands
     */
    @MCRCommand(syntax = "lta sync all objects",
        help = "Rebuilds the long term archive database entries of all objects, one command per object.",
        order = 120)
    public static List<String> syncAllObjects() {
        return MCRXMLMetadataManager.getInstance().listIDs().stream()
            .filter(id -> !MCRObjectID.getInstance(id).getTypeId().equals("derivate"))
            .sorted()
            .map(id -> "lta sync object " + id)
            .toList();
    }

    /**
     * Lists the submissions of an object, from its service flags and from the database.
     *
     * @param objectId the ID of the object
     */
    @MCRCommand(syntax = "lta list submissions of object {0}",
        help = "Lists the long term archive submissions of object {0}.",
        order = 130)
    public static void listSubmissions(String objectId) {
        MCRObjectID id = MCRObjectID.getInstance(objectId);
        MCRObject object = MCRMetadataManager.retrieveMCRObject(id);
        List<MCRLTASubmission> flags = MCRLTAFlags.list(object);
        LOGGER.info("{} has {} submissions in its service flags", id, flags.size());
        flags.forEach(s -> LOGGER.info("  {}", s.toJson()));
        List<MCRLTASubmissionEntity> entities = MCRLTASubmissionStore.list(id);
        LOGGER.info("{} has {} submissions in the database", id, entities.size());
        entities.forEach(e -> LOGGER.info("  {} {} deposit {} submission {} checked {}", e.getService(),
            e.getState(), e.getDepositId(), e.getSubmissionId(), e.getChecked()));
    }

    /**
     * Shows the number of submissions per state of a service.
     *
     * @param serviceId the ID of the service
     */
    @MCRCommand(syntax = "lta show statistics of service {0}",
        help = "Shows the number of submissions per state of long term archive service {0}.",
        order = 140)
    public static void showStatistics(String serviceId) {
        Map<MCRLTAState, Long> counts = MCRLTASubmissionStore.countByState(serviceId);
        for (MCRLTAState state : MCRLTAState.values()) {
            LOGGER.info("{} {}: {}", serviceId, state.name().toLowerCase(Locale.ROOT),
                counts.getOrDefault(state, 0L));
        }
    }

}
