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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mycore.lta.MCRLTAState;

public class MCRRosettaClientTest {

    private MCRRosettaClient client;

    @BeforeEach
    public void setUp() {
        client = new MCRRosettaClient();
        client.baseUrl = "https://rosetta.example.org";
        client.username = "user";
        client.password = "secret";
        client.institution = "INS";
        client.producerId = "P1";
        client.materialFlowId = "M1";
        client.connectTimeoutSeconds = "5";
        client.readTimeoutSeconds = "10";
        client.pendingStates = "INPROCESS";
        client.successStates = "FINISHED, APPROVED";
        client.creationDateFormat = "dd/MM/yyyy HH:mm:ss";
        client.creationDateZone = "Europe/Berlin";
        client.init();
    }

    @Test
    public void testMapState() {
        assertEquals(MCRLTAState.SUBMITTED, client.mapState("INPROCESS"));
        assertEquals(MCRLTAState.FINISHED, client.mapState("finished"));
        assertEquals(MCRLTAState.FINISHED, client.mapState("APPROVED"));
        assertEquals(MCRLTAState.FAILED, client.mapState("REJECTED"));
        assertEquals(MCRLTAState.FAILED, client.mapState("DECLINED"));
    }

    @Test
    public void testDepositBody() {
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<deposit link=\"string\">"
            + "<subdirectory>mir_mods_00000001</subdirectory><producer>P1</producer>"
            + "<material_flow>M1</material_flow></deposit>\r\n", client.createDepositBody("mir_mods_00000001"));
    }

}
