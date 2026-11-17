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

package org.mycore.lta.selector;

import java.io.IOException;
import java.util.List;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.mycore.common.MCRException;
import org.mycore.common.config.annotation.MCRProperty;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.solr.MCRSolrCoreManager;
import org.mycore.solr.auth.MCRSolrAuthenticationLevel;
import org.mycore.solr.auth.MCRSolrAuthenticationManager;

/**
 * Selects the candidates with a query in the main Solr core. The selection pages with a cursor mark, sorted by ID.
 * <p>
 * Properties:
 * <ul>
 *   <li><code>Query</code>: the Solr query, e.g. <code>state:published AND -lta.TIB.state:*</code></li>
 *   <li><code>FilterQuery</code> (optional): an additional filter query, e.g. <code>objectType:mods</code></li>
 * </ul>
 */
public class MCRLTASolrSelector implements MCRLTASelector {

    /**
     * The Solr query.
     */
    @MCRProperty(name = "Query")
    public String query;

    /**
     * An additional filter query, may be null.
     */
    @MCRProperty(name = "FilterQuery", required = false)
    public String filterQuery;

    @Override
    public MCRLTASelection open() {
        return new Selection();
    }

    private final class Selection implements MCRLTASelection {

        private String cursorMark = CursorMarkParams.CURSOR_MARK_START;

        private boolean done;

        @Override
        public List<MCRObjectID> next(int limit) {
            if (done) {
                return List.of();
            }
            ModifiableSolrParams params = new ModifiableSolrParams();
            params.set("q", query);
            if (filterQuery != null && !filterQuery.isBlank()) {
                params.set("fq", filterQuery);
            }
            params.set("fl", "id");
            params.set("sort", "id asc");
            params.set("rows", limit);
            params.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            QueryRequest request = new QueryRequest(params);
            MCRSolrAuthenticationManager.obtainInstance().applyAuthentication(request,
                MCRSolrAuthenticationLevel.SEARCH);
            QueryResponse response;
            try {
                response = request.process(MCRSolrCoreManager.getMainSolrClient());
            } catch (SolrServerException | IOException e) {
                throw new MCRException("Unable to query Solr for LTA candidates: " + query, e);
            }
            String nextCursorMark = response.getNextCursorMark();
            if (nextCursorMark == null || nextCursorMark.equals(cursorMark)) {
                done = true;
            }
            cursorMark = nextCursorMark;
            return response.getResults().stream()
                .map(doc -> getId(doc))
                .filter(MCRObjectID::isValid)
                .map(MCRObjectID::getInstance)
                .toList();
        }

        private static String getId(SolrDocument doc) {
            return String.valueOf(doc.getFirstValue("id"));
        }

        @Override
        public void close() {
            done = true;
        }

    }

}
