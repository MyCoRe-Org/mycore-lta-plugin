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

package org.mycore.lta.persistence;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.backend.jpa.MCREntityManagerProvider;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.lta.MCRLTAState;
import org.mycore.lta.MCRLTASubmission;

import jakarta.persistence.EntityManager;

/**
 * Access to the database image of the service flags. All methods operate in the current transaction.
 */
public final class MCRLTASubmissionStore {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final List<MCRLTAState> BLOCKING_STATES = List.of(MCRLTAState.values()).stream()
        .filter(MCRLTAState::isBlocking)
        .toList();

    private MCRLTASubmissionStore() {
    }

    private static EntityManager em() {
        return MCREntityManagerProvider.getCurrentEntityManager();
    }

    /**
     * Replaces the rows of an object with the given submissions. The time of the last check is kept for
     * submissions with the same service and deposit. Violations of the rule that at most one submission per
     * service is open or successful are logged, but the rows are stored anyway.
     *
     * @param objectId the ID of the object
     * @param submissions the submissions from the service flags
     */
    public static void sync(MCRObjectID objectId, List<MCRLTASubmission> submissions) {
        EntityManager em = em();
        List<MCRLTASubmissionEntity> existing = list(objectId);
        Map<String, Instant> checked = new HashMap<>();
        for (MCRLTASubmissionEntity entity : existing) {
            if (entity.getDepositId() != null && entity.getChecked() != null) {
                checked.put(key(entity.getService(), entity.getDepositId()), entity.getChecked());
            }
            em.remove(entity);
        }
        em.flush();
        for (MCRLTASubmission submission : submissions) {
            MCRLTASubmissionEntity entity = new MCRLTASubmissionEntity(objectId.toString(), submission);
            if (submission.getDepositId() != null) {
                entity.setChecked(checked.get(key(submission.getService(), submission.getDepositId())));
            }
            em.persist(entity);
        }
        submissions.stream()
            .filter(s -> s.getState().isBlocking())
            .collect(Collectors.groupingBy(MCRLTASubmission::getService, Collectors.counting()))
            .forEach((service, count) -> {
                if (count > 1) {
                    LOGGER.warn("{} has {} open or successful submissions to {}", objectId, count, service);
                }
            });
    }

    private static String key(String service, String depositId) {
        return service + '\u0000' + depositId;
    }

    /**
     * Deletes all rows of an object.
     *
     * @param objectId the ID of the object
     */
    public static void delete(MCRObjectID objectId) {
        List<MCRLTASubmissionEntity> existing = list(objectId);
        existing.stream()
            .filter(e -> e.getState() == MCRLTAState.VERIFIED)
            .forEach(e -> LOGGER.warn("Deleted object {} is archived in {} as {}", objectId, e.getService(),
                e.getEntityId()));
        existing.forEach(em()::remove);
    }

    /**
     * Returns all rows of an object.
     *
     * @param objectId the ID of the object
     * @return the rows ordered by ID
     */
    public static List<MCRLTASubmissionEntity> list(MCRObjectID objectId) {
        return em()
            .createQuery("SELECT s FROM MCRLTASubmissionEntity s WHERE s.objectId = :objectId ORDER BY s.id",
                MCRLTASubmissionEntity.class)
            .setParameter("objectId", objectId.toString())
            .getResultList();
    }

    /**
     * Returns whether the object has an open or successful submission to the service.
     *
     * @param objectId the ID of the object
     * @param service the ID of the service
     * @return true, if another submission is not allowed
     */
    public static boolean hasBlocking(MCRObjectID objectId, String service) {
        Long count = em()
            .createQuery("SELECT COUNT(s) FROM MCRLTASubmissionEntity s WHERE s.objectId = :objectId"
                + " AND s.service = :service AND s.state IN :states", Long.class)
            .setParameter("objectId", objectId.toString())
            .setParameter("service", service)
            .setParameter("states", BLOCKING_STATES)
            .getSingleResult();
        return count > 0;
    }

    /**
     * Returns open submissions that were not checked since the given time, oldest check first.
     *
     * @param service the ID of the service
     * @param checkedBefore submissions checked at or after this time are skipped
     * @param limit the maximum number of rows
     * @return the rows
     */
    public static List<MCRLTASubmissionEntity> listOpen(String service, Instant checkedBefore, int limit) {
        return em()
            .createQuery("SELECT s FROM MCRLTASubmissionEntity s WHERE s.service = :service"
                + " AND s.state = :state AND s.depositId IS NOT NULL"
                + " AND (s.checked IS NULL OR s.checked < :checkedBefore)"
                + " ORDER BY s.checked ASC NULLS FIRST, s.id ASC", MCRLTASubmissionEntity.class)
            .setParameter("service", service)
            .setParameter("state", MCRLTAState.SUBMITTED)
            .setParameter("checkedBefore", checkedBefore)
            .setMaxResults(limit)
            .getResultList();
    }

    /**
     * Stores the time of the last status check of a deposit.
     *
     * @param service the ID of the service
     * @param depositId the ID of the deposit
     * @param checked the time of the check
     */
    public static void markChecked(String service, String depositId, Instant checked) {
        em()
            .createQuery("SELECT s FROM MCRLTASubmissionEntity s WHERE s.service = :service"
                + " AND s.depositId = :depositId", MCRLTASubmissionEntity.class)
            .setParameter("service", service)
            .setParameter("depositId", depositId)
            .getResultList()
            .forEach(s -> s.setChecked(checked));
    }

    /**
     * Returns the object of a submission information package.
     *
     * @param service the ID of the service
     * @param submissionId the ID of the submission information package
     * @return the ID of the object, if exactly one object matches
     */
    public static Optional<MCRObjectID> findObjectBySubmissionId(String service, String submissionId) {
        List<String> objectIds = em()
            .createQuery("SELECT DISTINCT s.objectId FROM MCRLTASubmissionEntity s WHERE s.service = :service"
                + " AND s.submissionId = :submissionId", String.class)
            .setParameter("service", service)
            .setParameter("submissionId", submissionId)
            .getResultList();
        if (objectIds.size() > 1) {
            LOGGER.warn("Submission {} of {} is assigned to several objects: {}", submissionId, service, objectIds);
            return Optional.empty();
        }
        return objectIds.stream().filter(Objects::nonNull).findFirst().map(MCRObjectID::getInstance);
    }

    /**
     * Counts the submissions of a service by state.
     *
     * @param service the ID of the service
     * @return the number of submissions per state
     */
    public static Map<MCRLTAState, Long> countByState(String service) {
        Map<MCRLTAState, Long> counts = new HashMap<>();
        em()
            .createQuery("SELECT s.state, COUNT(s) FROM MCRLTASubmissionEntity s WHERE s.service = :service"
                + " GROUP BY s.state", Object[].class)
            .setParameter("service", service)
            .getResultList()
            .forEach(row -> counts.put((MCRLTAState) row[0], (Long) row[1]));
        return counts;
    }

}
