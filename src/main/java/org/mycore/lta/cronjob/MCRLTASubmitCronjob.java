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

package org.mycore.lta.cronjob;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.MCRException;
import org.mycore.common.MCRMailer;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.lta.MCRLTAService;
import org.mycore.lta.MCRLTAState;
import org.mycore.lta.MCRLTASubmission;
import org.mycore.lta.selector.MCRLTASelection;
import org.mycore.mcr.cronjob.MCRCronjob;

/**
 * Submits candidates of a service. The selection is read page by page until the limit of submissions is reached
 * or no candidates are left, so objects that turn out to be no candidates do not reduce the number of submissions.
 * Each object is submitted in its own session and transaction, between two objects the service pauses as
 * configured. Objects whose package can not be built are marked as failed and
 * the run continues. If upload or deposit fail, the run is aborted, because these errors usually affect all
 * objects.
 * <p>
 * Properties (below <code>MCR.Cronjob.Jobs.{id}</code>):
 * <ul>
 *   <li><code>Service</code>: the ID of the service</li>
 *   <li><code>Limit</code>: the maximum number of submissions per run, defaults to
 *   <code>MCR.LTA.Default.Submit.Limit</code></li>
 *   <li><code>MailFrom</code>, <code>MailTo</code> (optional): sender and recipient of a mail on errors</li>
 * </ul>
 */
public class MCRLTASubmitCronjob extends MCRCronjob {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The ID of the service.
     */
    @MCRProperty(name = "Service")
    public String serviceId;

    /**
     * The sender of error mails, may be null.
     */
    @MCRProperty(name = "MailFrom", required = false)
    public String mailFrom;

    /**
     * The recipient of error mails, may be null.
     */
    @MCRProperty(name = "MailTo", required = false)
    public String mailTo;

    private int limit;

    /**
     * Sets the maximum number of submissions per run.
     *
     * @param limit the limit
     */
    @MCRProperty(name = "Limit", defaultName = "MCR.LTA.Default.Submit.Limit")
    public void setLimit(String limit) {
        this.limit = Integer.parseInt(limit.trim());
    }

    @Override
    public void runJob() {
        MCRLTAService service = MCRLTAService.obtainInstance(serviceId);
        List<String> errors = new ArrayList<>();
        int submitted = 0;
        boolean pauseBeforeNext = false;
        try (MCRLTASelection selection = service.getSelector().open()) {
            List<MCRObjectID> page;
            while (submitted < limit
                && !(page = MCRLTACronjobSupport.callUnchecked(() -> selection.next(limit))).isEmpty()) {
                for (MCRObjectID objectId : page) {
                    if (submitted >= limit) {
                        break;
                    }
                    getProcessable().setProgress(100 * submitted / limit);
                    getProcessable().setProgressText("Submitting " + objectId + " (" + (submitted + 1) + "/"
                        + limit + ")");
                    if (pauseBeforeNext) {
                        service.pause();
                    }
                    try {
                        MCRLTASubmission submission = MCRLTACronjobSupport.call(
                            () -> service.isCandidate(objectId) ? service.submit(objectId, null) : null);
                        pauseBeforeNext |= submission != null;
                        if (submission == null) {
                            LOGGER.info("Skipping {}, it is no longer a candidate", objectId);
                        } else if (submission.getState() == MCRLTAState.FAILED) {
                            errors.add(objectId + ": package could not be built");
                        } else {
                            submitted++;
                        }
                    } catch (IOException e) {
                        errors.add(objectId + ": " + e.getMessage());
                        notify(submitted, errors, true);
                        throw new MCRException("Aborting submission to " + serviceId + " after error with "
                            + objectId, e);
                    } catch (Exception e) {
                        LOGGER.error("Unable to submit {} to {}", objectId, serviceId, e);
                        errors.add(objectId + ": " + e.getMessage());
                    }
                }
            }
        }
        getProcessable().setProgress(100);
        LOGGER.info("Submitted {} objects to {}, {} errors", submitted, serviceId, errors.size());
        notify(submitted, errors, false);
    }

    private void notify(int submitted, List<String> errors, boolean aborted) {
        if (errors.isEmpty() || mailFrom == null || mailTo == null || mailFrom.isBlank() || mailTo.isBlank()) {
            return;
        }
        String subject = "LTA submission to " + serviceId + (aborted ? " aborted" : " finished with errors");
        String body = "Submitted objects: " + submitted + "\nErrors:\n" + String.join("\n", errors);
        try {
            MCRMailer.send(mailFrom, mailTo, subject, body);
        } catch (RuntimeException e) {
            LOGGER.error("Unable to send mail about errors of LTA submission to {}", mailTo, e);
        }
    }

    @Override
    public String getDescription() {
        return "Submits up to " + limit + " objects to long term archive service " + serviceId;
    }

}
