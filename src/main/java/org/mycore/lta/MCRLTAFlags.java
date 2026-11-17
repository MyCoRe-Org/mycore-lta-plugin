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

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.MCRException;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectService;

/**
 * Reads and writes the submissions stored in the service flags of a MyCoRe object.
 */
public final class MCRLTAFlags {

    /**
     * The type of the service flags holding the submissions.
     */
    public static final String FLAG_TYPE = "MyCoRe-LTA";

    private static final Logger LOGGER = LogManager.getLogger();

    private MCRLTAFlags() {
    }

    /**
     * Returns all submissions of an object. Flags that can not be parsed are logged and skipped.
     *
     * @param object the object
     * @return the submissions in the order of the flags
     */
    public static List<MCRLTASubmission> list(MCRObject object) {
        MCRObjectService service = object.getService();
        List<MCRLTASubmission> submissions = new ArrayList<>();
        for (int i = 0; i < service.getFlagSize(); i++) {
            if (FLAG_TYPE.equals(service.getFlagType(i))) {
                try {
                    submissions.add(MCRLTASubmission.fromJson(service.getFlag(i)));
                } catch (MCRException | IllegalArgumentException e) {
                    LOGGER.error("Skipping invalid LTA flag #{} of {}: {}", i, object.getId(), e.getMessage());
                }
            }
        }
        return submissions;
    }

    /**
     * Returns all submissions of an object to the given service.
     *
     * @param object the object
     * @param serviceId the ID of the service
     * @return the submissions to the service
     */
    public static List<MCRLTASubmission> list(MCRObject object, String serviceId) {
        return list(object).stream().filter(s -> s.getService().equals(serviceId)).toList();
    }

    /**
     * Returns whether the object has an open or successful submission to the given service.
     *
     * @param object the object
     * @param serviceId the ID of the service
     * @return true, if another submission is not allowed
     */
    public static boolean hasBlocking(MCRObject object, String serviceId) {
        return list(object, serviceId).stream().anyMatch(s -> s.getState().isBlocking());
    }

    /**
     * Adds a submission to the object. The object is not saved.
     *
     * @param object the object
     * @param submission the submission
     */
    public static void add(MCRObject object, MCRLTASubmission submission) {
        object.getService().addFlag(FLAG_TYPE, submission.toJson());
    }

    /**
     * Replaces the flag of the same deposit with the given submission. The object is not saved.
     *
     * @param object the object
     * @param submission the changed submission
     * @return true, if a flag was replaced
     */
    public static boolean replace(MCRObject object, MCRLTASubmission submission) {
        MCRObjectService service = object.getService();
        for (int i = 0; i < service.getFlagSize(); i++) {
            if (FLAG_TYPE.equals(service.getFlagType(i))) {
                MCRLTASubmission existing;
                try {
                    existing = MCRLTASubmission.fromJson(service.getFlag(i));
                } catch (MCRException | IllegalArgumentException e) {
                    continue;
                }
                if (existing.isSameDeposit(submission)) {
                    service.replaceFlag(i, submission.toJson());
                    return true;
                }
            }
        }
        return false;
    }

}
