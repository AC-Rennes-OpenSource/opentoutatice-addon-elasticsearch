package org.opentoutatice.elasticsearch.commands;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.commands.IndexingCommand;
import org.nuxeo.elasticsearch.commands.IndexingCommands;
import org.nuxeo.runtime.api.Framework;
import org.opentoutatice.elasticsearch.OttcElasticSearchComponent;
import org.opentoutatice.elasticsearch.core.reindexing.docs.manager.ReIndexingRunnerManager;
import org.opentoutatice.elasticsearch.core.reindexing.docs.transitory.TransitoryIndexUse;

public class OttcIndexingCommands extends IndexingCommands {

    private static final Log log = LogFactory.getLog(OttcIndexingCommands.class);

    public OttcIndexingCommands(DocumentModel doc) {
        super(doc);
    }

    @Override
    public void add(IndexingCommand.Type type, boolean sync, boolean recurse) {
        OttcElasticSearchComponent ea = (OttcElasticSearchComponent) Framework.getService(ElasticSearchAdmin.class);
        try {
            OttcIndexingCommand cmd = new OttcIndexingCommand(targetDocument, type, sync, recurse);
            cmd.setIndexName(ea.getConfiguredIndexOrAliasNameForRepository(cmd.getRepositoryName()));
            add_(cmd);
            if(log.isInfoEnabled()){
                log.info(String.format("Adding command on %s: " + cmd, ea.getConfiguredIndexOrAliasNameForRepository(cmd.getRepositoryName())));
            }
            if(ReIndexingRunnerManager.get().isReIndexingInProgress(cmd.getRepositoryName())){
                // Command on second index
                OttcIndexingCommand cmd_ = new OttcIndexingCommand(targetDocument, type, sync, recurse);
                cmd_.setIndexName(TransitoryIndexUse.WriteNew.getAlias());
                add_(cmd_);

                if(log.isInfoEnabled()){
                    log.info(String.format("Adding second command on %s: " + cmd_, TransitoryIndexUse.WriteNew.getAlias()));
                }
            }
        } catch (InterruptedException e) {
            throw new ClientException(e);
        }
    }

    protected void add_(OttcIndexingCommand command) {
        if (command == null) {
            return;
        }
        if (commandTypes.contains(command.getType())) {
            OttcIndexingCommand existing = (OttcIndexingCommand) find(command.getType());
            if(command.getIndexName().equals(existing.getIndexName())) {
                if (existing.merge(command)) {
                    return;
                }
            }
        } else if (commandTypes.contains(IndexingCommand.Type.INSERT)) {
            if (command.getType() == IndexingCommand.Type.DELETE) {
                // index and delete in the same tx
                clear(command.getIndexName());
            } else if (command.isSync()) {
                // switch to sync if possible
                OttcIndexingCommand existing = (OttcIndexingCommand) find(IndexingCommand.Type.INSERT);
                if(command.getIndexName().equals(existing.getIndexName())) {
                    existing.makeSync();
                }
            }
            // we already have an index command, don't care about the new command
            return;
        }
        if (command.getType() == IndexingCommand.Type.DELETE) {
            // no need to keep event before delete.
            clear(command.getIndexName());
        }
        commands.add(command);
        commandTypes.add(command.getType());
    }

    protected void clear(String indexName) {
        for(IndexingCommand command : commands) {
            if(((OttcIndexingCommand) command).getIndexName().equals(indexName)) {
              commands.remove(command);
              commandTypes.remove(command.getType());
            }
        }
    }

}
