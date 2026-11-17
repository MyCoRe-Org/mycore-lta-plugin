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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.mycore.common.MCRException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A single attempt to submit a MyCoRe object to a long term archiving service.
 * <p>
 * Submissions are stored as JSON in service flags of type {@link MCRLTAFlags#FLAG_TYPE} of the object. The JSON
 * format is compatible with the format used by the former MACAU implementation. The key <code>checked</code> of that
 * format is ignored, the time of the last check is only kept in the database.
 * <p>
 * Instances are immutable, state changes create new instances.
 */
public final class MCRLTASubmission {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)
        .withZone(ZoneOffset.UTC);

    private final String service;

    private final MCRLTAState state;

    private final Instant created;

    private final String reason;

    private final String depositId;

    private final String submissionId;

    private final String depositState;

    private final Instant verified;

    private final String reportFileName;

    private final Instant processed;

    private final String entityId;

    private MCRLTASubmission(Builder builder) {
        this.service = Objects.requireNonNull(builder.service, "service must not be null");
        this.state = Objects.requireNonNull(builder.state, "state must not be null");
        this.created = builder.created;
        this.reason = builder.reason;
        this.depositId = builder.depositId;
        this.submissionId = builder.submissionId;
        this.depositState = builder.depositState;
        this.verified = builder.verified;
        this.reportFileName = builder.reportFileName;
        this.processed = builder.processed;
        this.entityId = builder.entityId;
    }

    /**
     * Creates a new submission in state {@link MCRLTAState#SUBMITTED}.
     *
     * @param service the ID of the service
     * @param result the result of the deposit
     * @param reason the reason of the submission
     * @return the new submission
     */
    public static MCRLTASubmission ofSubmitted(String service, MCRLTASubmitResult result, String reason) {
        Builder builder = new Builder(service, MCRLTAState.SUBMITTED);
        builder.created = result.created();
        builder.reason = reason;
        builder.depositId = result.depositId();
        builder.submissionId = result.submissionId();
        return builder.build();
    }

    /**
     * Parses a submission from the JSON value of a service flag.
     *
     * @param json the JSON string
     * @return the parsed submission
     * @throws MCRException if the JSON is invalid
     */
    public static MCRLTASubmission fromJson(String json) {
        Map<String, String> map;
        try {
            map = MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new MCRException("Invalid LTA submission: " + json, e);
        }
        String stateValue = map.get("state");
        Builder builder = new Builder(map.get("service"),
            stateValue == null ? MCRLTAState.SUBMITTED : MCRLTAState.valueOf(stateValue));
        builder.created = parseDate(map.get("created"));
        builder.reason = map.get("reason");
        builder.depositId = map.get("depositId");
        builder.submissionId = map.get("submissionId");
        builder.depositState = map.get("depositState");
        builder.verified = parseDate(map.get("verified"));
        builder.reportFileName = map.get("reportFileName");
        builder.processed = parseDate(map.get("processed"));
        builder.entityId = map.get("entityId");
        return builder.build();
    }

    /**
     * Formats a date the way it is stored in the JSON of a service flag.
     *
     * @param date the date, may be null
     * @return the formatted date or null
     */
    public static String formatDate(Instant date) {
        return date == null ? null : DATE_FORMAT.format(date);
    }

    private static Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, DATE_FORMAT).toInstant();
        } catch (DateTimeParseException e) {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        }
    }

    /**
     * Serializes this submission as JSON for a service flag. Absent values are omitted.
     *
     * @return the JSON string
     */
    public String toJson() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("service", service);
        map.put("state", state.name());
        putIfPresent(map, "created", formatDate(created));
        putIfPresent(map, "reason", reason);
        putIfPresent(map, "depositId", depositId);
        putIfPresent(map, "submissionId", submissionId);
        putIfPresent(map, "depositState", depositState);
        putIfPresent(map, "verified", formatDate(verified));
        putIfPresent(map, "reportFileName", reportFileName);
        putIfPresent(map, "processed", formatDate(processed));
        putIfPresent(map, "entityId", entityId);
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new MCRException("Unable to serialize LTA submission " + depositId, e);
        }
    }

    private static void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * Creates a copy with the result of a status check. A verified submission keeps its state.
     *
     * @param newState the state derived from the deposit state
     * @param newDepositState the state reported by the archive
     * @return the checked submission
     */
    public MCRLTASubmission withCheckResult(MCRLTAState newState, String newDepositState) {
        Builder builder = toBuilder();
        builder.state = state == MCRLTAState.VERIFIED ? MCRLTAState.VERIFIED : newState;
        builder.depositState = newDepositState;
        return builder.build();
    }

    /**
     * Creates a verified copy with the values of a report of the archive.
     *
     * @param newEntityId the ID of the archived entity
     * @param entityCreated the creation date of the archived entity, may be null
     * @param newReportFileName the file name of the report
     * @param now the time of the verification
     * @return the verified submission
     */
    public MCRLTASubmission withVerification(String newEntityId, Instant entityCreated, String newReportFileName,
        Instant now) {
        Builder builder = toBuilder();
        builder.state = MCRLTAState.VERIFIED;
        builder.entityId = newEntityId;
        builder.processed = entityCreated;
        builder.reportFileName = newReportFileName;
        builder.verified = now;
        return builder.build();
    }

    /**
     * Creates a failed copy, e.g. if a deposit has to be given up manually.
     *
     * @return the failed submission
     */
    public MCRLTASubmission withFailure() {
        Builder builder = toBuilder();
        builder.state = MCRLTAState.FAILED;
        return builder.build();
    }

    private Builder toBuilder() {
        Builder builder = new Builder(service, state);
        builder.created = created;
        builder.reason = reason;
        builder.depositId = depositId;
        builder.submissionId = submissionId;
        builder.depositState = depositState;
        builder.verified = verified;
        builder.reportFileName = reportFileName;
        builder.processed = processed;
        builder.entityId = entityId;
        return builder;
    }

    /**
     * Returns whether this submission and the given one describe the same deposit of the same service.
     *
     * @param other the other submission
     * @return true, if service and deposit ID are equal
     */
    public boolean isSameDeposit(MCRLTASubmission other) {
        return service.equals(other.service) && Objects.equals(depositId, other.depositId);
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
     * @return the ID of the deposit in the archive
     */
    public String getDepositId() {
        return depositId;
    }

    /**
     * @return the ID of the submission information package in the archive
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
     * @return the creation date of the archived entity according to the report
     */
    public Instant getProcessed() {
        return processed;
    }

    /**
     * @return the ID of the archived entity, e.g. the IE PID in Rosetta
     */
    public String getEntityId() {
        return entityId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MCRLTASubmission other && toJson().equals(other.toJson());
    }

    @Override
    public int hashCode() {
        return toJson().hashCode();
    }

    @Override
    public String toString() {
        return toJson();
    }

    /**
     * Builder for submissions, used when restoring submissions from other sources.
     */
    public static final class Builder {

        private final String service;

        private MCRLTAState state;

        private Instant created;

        private String reason;

        private String depositId;

        private String submissionId;

        private String depositState;

        private Instant verified;

        private String reportFileName;

        private Instant processed;

        private String entityId;

        /**
         * Creates a new builder.
         *
         * @param service the ID of the service
         * @param state the state of the submission
         */
        public Builder(String service, MCRLTAState state) {
            this.service = service;
            this.state = state;
        }

        /**
         * Sets the creation date.
         *
         * @param value the date
         * @return this builder
         */
        public Builder created(Instant value) {
            this.created = value;
            return this;
        }

        /**
         * Sets the reason.
         *
         * @param value the reason
         * @return this builder
         */
        public Builder reason(String value) {
            this.reason = value;
            return this;
        }

        /**
         * Sets the deposit ID.
         *
         * @param value the deposit ID
         * @return this builder
         */
        public Builder depositId(String value) {
            this.depositId = value;
            return this;
        }

        /**
         * Sets the submission ID.
         *
         * @param value the submission ID
         * @return this builder
         */
        public Builder submissionId(String value) {
            this.submissionId = value;
            return this;
        }

        /**
         * Sets the deposit state.
         *
         * @param value the deposit state
         * @return this builder
         */
        public Builder depositState(String value) {
            this.depositState = value;
            return this;
        }

        /**
         * Sets the verification date.
         *
         * @param value the date
         * @return this builder
         */
        public Builder verified(Instant value) {
            this.verified = value;
            return this;
        }

        /**
         * Sets the report file name.
         *
         * @param value the file name
         * @return this builder
         */
        public Builder reportFileName(String value) {
            this.reportFileName = value;
            return this;
        }

        /**
         * Sets the creation date of the archived entity.
         *
         * @param value the date
         * @return this builder
         */
        public Builder processed(Instant value) {
            this.processed = value;
            return this;
        }

        /**
         * Sets the entity ID.
         *
         * @param value the entity ID
         * @return this builder
         */
        public Builder entityId(String value) {
            this.entityId = value;
            return this;
        }

        /**
         * @return the new submission
         */
        public MCRLTASubmission build() {
            return new MCRLTASubmission(this);
        }

    }

}
