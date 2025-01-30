/*
 * (C) Copyright 2014 Académie de Rennes (http://www.ac-rennes.fr/), OSIVIA (http://www.osivia.com) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 *
 * Contributors:
 * mberhaut1
 *
 */
package fr.toutatice.ecm.elasticsearch.codec;

import fr.toutatice.ecm.elasticsearch.search.TTCSearchResponse;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.nuxeo.ecm.automation.io.services.codec.ObjectCodec;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

public class TTCEsCodec extends ObjectCodec<TTCSearchResponse> {

    private static final Log log = LogFactory.getLog(TTCEsCodec.class);

    private static final Pattern SYSTEM_PROPS_PATTERN = Pattern.compile("ecm:.+");
    public TTCEsCodec() {
        super(TTCSearchResponse.class);
    }

    @Override
    public String getType() {
        return "esresponse";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(JsonGenerator jg, TTCSearchResponse value) throws IOException {
        SearchHits upperhits = value.getSearchResponse().getHits();
        Pattern schemasRegex = Pattern.compile(value.getSchemasRegex());

        SearchHit[] searchhits = upperhits.getHits();

        jg.writeStartObject();
        jg.writeStringField("entity-type", "documents");
        if (value.isPaginable()) {
            jg.writeBooleanField("isPaginable", value.isPaginable());
            jg.writeNumberField("resultsCount", searchhits.length);
            jg.writeNumberField("totalSize", upperhits.getTotalHits());
            jg.writeNumberField("pageSize", value.getPageSize());
            // Take empty response into account
            long pageCount = 0;
            if (value.getPageSize() > 0) {
                pageCount = (upperhits.getTotalHits() / value.getPageSize()) + ((0 < (upperhits.getTotalHits() % value.getPageSize())) ? 1 : 0);
            }
            jg.writeNumberField("pageCount", pageCount);
            jg.writeNumberField("currentPageIndex", value.getCurrentPageIndex());
        }

        jg.writeArrayFieldStart("entries");

        this.writeEntries(jg, schemasRegex, searchhits);

        jg.writeEndArray();

        jg.writeEndObject();
        jg.flush();
    }

    /**
     * @param jg
     * @param schemasRegex
     * @param searchhits
     * @throws IOException
     * @throws JsonGenerationException
     * @throws JsonProcessingException
     */
    protected void writeEntries(JsonGenerator jg, Pattern schemasRegex, SearchHit[] searchhits)
            throws IOException, JsonGenerationException, JsonProcessingException {
        for (SearchHit hit : searchhits) {
            this.writeEntry(jg, schemasRegex, hit.getSource());
        }
    }

    /**
     * @param jg
     * @param schemasRegex
     * @throws IOException
     * @throws JsonGenerationException
     * @throws JsonProcessingException
     */
    private void writeEntry(JsonGenerator jg, Pattern schemasRegex, Map<String, Object> source)
            throws IOException, JsonGenerationException, JsonProcessingException {
        jg.writeStartObject();

        // convert ES JSON mapping into Nuxeo automation mapping
        jg.writeStringField("entity-type", "document");
        jg.writeStringField("repository", (String) source.get("ecm:repository"));
        jg.writeStringField("uid", (String) source.get("ecm:uuid"));
        jg.writeStringField("path", (String) source.get("ecm:path"));
        jg.writeStringField("type", (String) source.get("ecm:primaryType"));
        jg.writeStringField("state", (String) source.get("ecm:currentLifeCycleState"));
        jg.writeStringField("parentRef", (String) source.get("ecm:parentId"));
        jg.writeStringField("versionLabel", (String) source.get("ecm:versionLabel"));
        jg.writeStringField("isCheckedOut", StringUtils.EMPTY);
        jg.writeStringField("title", (String) source.get("dc:title"));
        jg.writeStringField("lastModified", (String) source.get("dc:modified"));
        jg.writeObjectField("facets", source.get("ecm:mixinType"));
        jg.writeStringField("changeToken", (String) source.get("ecm:changeToken"));
        // jg.writeStringField("ancestorId", (String) source.get("ecm:ancestorId"));

        jg.writeObjectFieldStart("properties");
        for (String key : source.keySet()) {
            if (!SYSTEM_PROPS_PATTERN.matcher(key).matches() && schemasRegex.matcher(key).matches()) {
                jg.writeObjectField(key, source.get(key));
            }
        }
        jg.writeEndObject();
        jg.writeEndObject();
        jg.flush();
    }

}
