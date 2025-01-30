package org.opentoutatice.elasticsearch.listener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.elasticsearch.commands.IndexingCommands;
import org.nuxeo.elasticsearch.listener.ElasticSearchInlineListener;
import org.opentoutatice.elasticsearch.commands.OttcIndexingCommands;

public class OttcElasticSearchInlineListener extends ElasticSearchInlineListener {

    private static final Log log = LogFactory.getLog(OttcElasticSearchInlineListener.class);

    @Override
    protected IndexingCommands getOrCreateCommands(DocumentModel doc) {
        IndexingCommands cmds = getCommands(doc);
        if (cmds == null) {
            cmds = new OttcIndexingCommands(doc);
            getAllCommands().put(getDocKey(doc), cmds);
        }
        return cmds;
    }

}
