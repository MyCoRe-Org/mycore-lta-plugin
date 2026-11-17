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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

public class MCRLTASubmissionTest {

    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05.678Z");

    @Test
    public void testJsonRoundTrip() {
        MCRLTASubmission submission = new MCRLTASubmission.Builder("TIB", MCRLTAState.VERIFIED)
            .created(CREATED)
            .reason("Initial submission")
            .depositId("123")
            .submissionId("456")
            .depositState("FINISHED")
            .verified(CREATED.plusSeconds(60))
            .reportFileName("report.xlsx")
            .processed(CREATED.plusSeconds(30))
            .entityId("IE789")
            .build();
        String json = submission.toJson();
        assertTrue(json.contains("\"created\":\"2026-01-02T03:04:05.678Z\""), json);
        assertEquals(submission, MCRLTASubmission.fromJson(json));
    }

    @Test
    public void testJsonOmitsMissingValues() {
        MCRLTASubmission failed = new MCRLTASubmission.Builder("TIB", MCRLTAState.FAILED).created(CREATED).build();
        String json = failed.toJson();
        assertFalse(json.contains("depositId"), json);
        MCRLTASubmission parsed = MCRLTASubmission.fromJson(json);
        assertEquals(MCRLTAState.FAILED, parsed.getState());
        assertNull(parsed.getDepositId());
    }

    @Test
    public void testCheckResultKeepsVerified() {
        MCRLTASubmission submitted = MCRLTASubmission.ofSubmitted("TIB",
            new MCRLTASubmitResult("1", "2", CREATED), "reason");
        assertEquals(MCRLTAState.SUBMITTED, submitted.getState());
        MCRLTASubmission finished = submitted.withCheckResult(MCRLTAState.FINISHED, "FINISHED");
        assertEquals(MCRLTAState.FINISHED, finished.getState());
        assertTrue(finished.isSameDeposit(submitted));
        MCRLTASubmission verified = finished.withVerification("IE1", CREATED, "report.csv", CREATED);
        assertEquals(MCRLTAState.VERIFIED, verified.getState());
        assertEquals(MCRLTAState.VERIFIED, verified.withCheckResult(MCRLTAState.FAILED, "REJECTED").getState());
    }

    @Test
    public void testBlockingStates() {
        assertTrue(MCRLTAState.SUBMITTED.isBlocking());
        assertTrue(MCRLTAState.FINISHED.isBlocking());
        assertTrue(MCRLTAState.VERIFIED.isBlocking());
        assertFalse(MCRLTAState.FAILED.isBlocking());
    }

}
