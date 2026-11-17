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

import org.mycore.lta.MCRLTAState;
import org.mycore.lta.MCRLTASubmission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A row of the database image of the service flags: one row per submission of an object to a service.
 * <p>
 * The service flags are the authoritative source. Only {@link #getChecked()} is kept exclusively in the database.
 */
@Entity
@Table(name = "MCRLTASubmission",
    indexes = {
        @Index(name = "MCRLTASubmissionObject", columnList = "objectId"),
        @Index(name = "MCRLTASubmissionState", columnList = "service, state"),
        @Index(name = "MCRLTASubmissionDeposit", columnList = "service, depositId"),
        @Index(name = "MCRLTASubmissionSIP", columnList = "service, submissionId")
    })
public class MCRLTASubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String objectId;

    @Column(nullable = false, length = 64)
    private String service;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private MCRLTAState state;

    private Instant created;

    @Column(length = 1024)
    private String reason;

    @Column(length = 128)
    private String depositId;

    @Column(length = 128)
    private String submissionId;

    @Column(length = 64)
    private String depositState;

    private Instant verified;

    @Column(length = 1024)
    private String reportFileName;

    private Instant processed;

    @Column(length = 128)
    private String entityId;

    private Instant checked;

    /**
     * Required by JPA.
     */
    public MCRLTASubmissionEntity() {
    }

    /**
     * Creates a row for a submission.
     *
     * @param objectId the ID of the object
     * @param submission the submission
     */
    public MCRLTASubmissionEntity(String objectId, MCRLTASubmission submission) {
        this.objectId = objectId;
        this.service = submission.getService();
        this.state = submission.getState();
        this.created = submission.getCreated();
        this.reason = submission.getReason();
        this.depositId = submission.getDepositId();
        this.submissionId = submission.getSubmissionId();
        this.depositState = submission.getDepositState();
        this.verified = submission.getVerified();
        this.reportFileName = submission.getReportFileName();
        this.processed = submission.getProcessed();
        this.entityId = submission.getEntityId();
    }

    /**
     * @return the row as submission, without the time of the last check
     */
    public MCRLTASubmission toSubmission() {
        return new MCRLTASubmission.Builder(service, state)
            .created(created)
            .reason(reason)
            .depositId(depositId)
            .submissionId(submissionId)
            .depositState(depositState)
            .verified(verified)
            .reportFileName(reportFileName)
            .processed(processed)
            .entityId(entityId)
            .build();
    }

    /**
     * @return the generated ID of the row
     */
    public Long getId() {
        return id;
    }

    /**
     * @return the ID of the object
     */
    public String getObjectId() {
        return objectId;
    }

    /**
     * @return the ID of the service
     */
    public String getService() {
        return service;
    }

    /**
     * @return the state of the submission
     */
    public MCRLTAState getState() {
        return state;
    }

    /**
     * @return the creation date of the deposit
     */
    public Instant getCreated() {
        return created;
    }

    /**
     * @return the reason of the submission
     */
    public String getReason() {
        return reason;
    }

    /**
     * @return the ID of the deposit
     */
    public String getDepositId() {
        return depositId;
    }

    /**
     * @return the ID of the submission information package
     */
    public String getSubmissionId() {
        return submissionId;
    }

    /**
     * @return the last deposit state reported by the archive
     */
    public String getDepositState() {
        return depositState;
    }

    /**
     * @return the date of the verification
     */
    public Instant getVerified() {
        return verified;
    }

    /**
     * @return the file name of the report used for the verification
     */
    public String getReportFileName() {
        return reportFileName;
    }

    /**
     * @return the creation date of the archived entity
     */
    public Instant getProcessed() {
        return processed;
    }

    /**
     * @return the ID of the archived entity
     */
    public String getEntityId() {
        return entityId;
    }

    /**
     * @return the time of the last status check, only stored in the database
     */
    public Instant getChecked() {
        return checked;
    }

    /**
     * @param checked the time of the last status check
     */
    public void setChecked(Instant checked) {
        this.checked = checked;
    }

}
