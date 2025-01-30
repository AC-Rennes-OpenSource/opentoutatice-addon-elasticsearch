package org.opentoutatice.elasticsearch.commands;

import org.codehaus.jackson.JsonGenerator;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.elasticsearch.commands.IndexingCommand;

import java.io.IOException;

public class OttcIndexingCommand extends IndexingCommand {

    private String indexName;

    public OttcIndexingCommand(DocumentModel document, Type commandType, boolean sync, boolean recurse) throws InterruptedException {
        super(document, commandType, sync, recurse);
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    @Override
    public void toJSON(JsonGenerator jsonGen) throws IOException {
        jsonGen.writeStartObject();
        jsonGen.writeStringField("id", id);
        jsonGen.writeStringField("type", String.format("%s", type));
        jsonGen.writeStringField("docId", getTargetDocumentId());
        jsonGen.writeStringField("path", path);
        jsonGen.writeStringField("repo", getRepositoryName());
        jsonGen.writeBooleanField("recurse", recurse);
        jsonGen.writeBooleanField("sync", sync);
        jsonGen.writeNumberField("order", getOrder());
        jsonGen.writeStringField("index", getIndexName());
        jsonGen.writeEndObject();
    }
}
