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
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.MCRException;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.lta.MCRLTAService;
import org.mycore.lta.MCRLTAState;
import org.mycore.lta.persistence.MCRLTASubmissionEntity;
import org.mycore.lta.persistence.MCRLTASubmissionStore;
import org.mycore.mcr.cronjob.MCRCronjob;

/**
 * Queries the status of open deposits of a service. Deposits that were never checked come first, then the ones
 * checked longest ago. Each deposit is checked in its own session and transaction.
 * <p>
 * Properties (below <code>MCR.Cronjob.Jobs.{id}</code>):
 * <ul>
 *   <li><code>Service</code>: the ID of the service</li>
 *   <li><code>CheckIntervalHours</code>: deposits checked within this interval are skipped, defaults to
 *   <code>MCR.LTA.Default.Check.IntervalHours</code></li>
 *   <li><code>Limit</code>: the maximum number of deposits per run, defaults to
 *   <code>MCR.LTA.Default.Check.Limit</code></li>
 * </ul>
 */
public class MCRLTACheckCronjob extends MCRCronjob {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The ID of the service.
     */
    @MCRProperty(name = "Service")
    public String serviceId;

    private Duration interval;

    private int limit;

    /**
     * Sets the minimal interval between two checks of the same deposit.
     *
     * @param hours the interval in hours
     */
    @MCRProperty(name = "CheckIntervalHours", defaultName = "MCR.LTA.Default.Check.IntervalHours")
    public void setCheckIntervalHours(String hours) {
        this.interval = Duration.ofHours(Long.parseLong(hours.trim()));
    }

    /**
     * Sets the maximum number of deposits per run.
     *
     * @param limit the limit
     */
    @MCRProperty(name = "Limit", defaultName = "MCR.LTA.Default.Check.Limit")
    public void setLimit(String limit) {
        this.limit = Integer.parseInt(limit.trim());
    }

    @Override
    public void runJob() {
        MCRLTAService service = MCRLTAService.obtainInstance(serviceId);
        List<MCRLTASubmissionEntity> open = MCRLTACronjobSupport.callUnchecked(
            () -> MCRLTASubmissionStore.listOpen(serviceId, Instant.now().minus(interval), limit));
        LOGGER.info("Checking {} open deposits of {}", open.size(), serviceId);
        int changed = 0;
        for (int i = 0; i < open.size(); i++) {
            MCRLTASubmissionEntity entity = open.get(i);
            MCRObjectID objectId = MCRObjectID.getInstance(entity.getObjectId());
            getProcessable().setProgress(100 * i / open.size());
            getProcessable().setProgressText("Checking " + objectId + " (" + (i + 1) + "/" + open.size() + ")");
            try {
                MCRLTAState state = MCRLTACronjobSupport.call(() -> service.check(objectId,
                    entity.getDepositId()));
                if (state != MCRLTAState.SUBMITTED) {
                    changed++;
                }
            } catch (IOException e) {
                throw new MCRException("Aborting check of " + serviceId + " after error with " + objectId, e);
            } catch (Exception e) {
                LOGGER.error("Unable to check deposit {} of {}", entity.getDepositId(), objectId, e);
            }
        }
        getProcessable().setProgress(100);
        LOGGER.info("Checked {} deposits of {}, {} finished processing", open.size(), serviceId, changed);
    }

    @Override
    public String getDescription() {
        return "Checks up to " + limit + " open deposits of long term archive service " + serviceId;
    }

}
