/**
 * 
 */
package org.opentoutatice.elasticsearch.core.service;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.CHILDREN_FIELD;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.DOC_TYPE;
import static org.nuxeo.elasticsearch.ElasticSearchConstants.PATH_FIELD;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequestBuilder;
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.deletebyquery.IndexDeleteByQueryResponse;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.nuxeo.ecm.automation.jaxrs.io.documents.JsonESDocumentWriter;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.model.NoSuchDocumentException;
import org.nuxeo.elasticsearch.commands.IndexingCommand;
import org.nuxeo.elasticsearch.commands.IndexingCommand.Type;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.MetricsService;
import org.opentoutatice.elasticsearch.api.OttcElasticSearchIndexing;
import org.opentoutatice.elasticsearch.core.reindexing.docs.es.state.exception.EsStateCheckException;
import org.opentoutatice.elasticsearch.core.reindexing.docs.manager.ReIndexingRunnerManager;
import org.opentoutatice.elasticsearch.core.reindexing.docs.manager.exception.ReIndexingException;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;

/**
 * @author dchevrier <chevrier.david.pro@gmail.com>
 *
 */
//Extends ElasticSearchAdminImpl to respect other services signatures (ElasticSearchIndexing, AlasticSearchService).
//Eg, cf ElasticSearchCompoent#applicationStarted()
public class OttcElasticSearchIndexingImpl /*extends ElasticSearchIndexingImpl*/ implements OttcElasticSearchIndexing {

	private static final Log log = LogFactory.getLog(OttcElasticSearchIndexingImpl.class);

	private final OttcElasticSearchAdminImpl esa;

	private final Timer deleteTimer;

	private final Timer indexTimer;

	private final Timer bulkIndexTimer;

	private final boolean useExternalVersion;

	private JsonESDocumentWriter jsonESDocumentWriter;
	
	public OttcElasticSearchIndexingImpl(OttcElasticSearchAdminImpl esa) {
		this.esa = (OttcElasticSearchAdminImpl) esa;
		MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());
		indexTimer = registry.timer(MetricRegistry.name("nuxeo", "elasticsearch", "service", "index"));
		deleteTimer = registry.timer(MetricRegistry.name("nuxeo", "elasticsearch", "service", "delete"));
		bulkIndexTimer = registry.timer(MetricRegistry.name("nuxeo", "elasticsearch", "service", "bulkIndex"));
		this.jsonESDocumentWriter = new JsonESDocumentWriter();// default writer
		this.useExternalVersion = esa.useExternalVersion();
	}

	/**
	 * @since 7.2
	 */
	public OttcElasticSearchIndexingImpl(OttcElasticSearchAdminImpl esa, JsonESDocumentWriter jsonESDocumentWriter) {
		this(esa);
		this.jsonESDocumentWriter = jsonESDocumentWriter;
	}

	/**
	 * {@inheritDoc}
	 * @throws EsStateCheckException 
	 */
	@Override
	public boolean reIndexAllDocumentsWithZeroDownTime(String repository) throws ReIndexingException, EsStateCheckException {
		// Delegation
		return ReIndexingRunnerManager.get().reIndexWithZeroDownTime(repository);
	}

	@Override
	public void runIndexingWorker(List<IndexingCommand> cmds) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void runReindexingWorker(String repositoryName, String nxql) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void indexNonRecursive(List<IndexingCommand> cmds) throws ClientException {
		int nbCommands = cmds.size();
		if (nbCommands == 1) {
			indexNonRecursive(cmds.get(0));
			return;
		}
		// simulate long indexing
		// try {Thread.sleep(1000);} catch (InterruptedException e) { }

		processBulkDeleteCommands(cmds);
		Context stopWatch = bulkIndexTimer.time();
		try {
			processBulkIndexCommands(cmds);
		} finally {
			stopWatch.stop();
		}
		esa.totalCommandProcessed.addAndGet(nbCommands);
		refreshIfNeeded(cmds);
	}

	void processBulkDeleteCommands(List<IndexingCommand> cmds) {
		// Can be optimized with a single delete by query
		for (IndexingCommand cmd : cmds) {
			if (cmd.getType() == Type.DELETE) {
				Context stopWatch = deleteTimer.time();
				try {
					processDeleteCommand(cmd);
				} finally {
					stopWatch.stop();
				}
			}
		}
	}

	void processBulkIndexCommands(List<IndexingCommand> cmds) throws ClientException {
		BulkRequestBuilder bulkRequest = esa.getClient().prepareBulk();
		Set<String> docIds = new HashSet<>(cmds.size());
		for (IndexingCommand cmd : cmds) {
			if (cmd.getType() == Type.DELETE || cmd.getType() == Type.UPDATE_DIRECT_CHILDREN) {
				continue;
			}
			if (!docIds.add(cmd.getTargetDocumentId())) {
				// do not submit the same doc 2 times
				continue;
			}
			try {
				IndexRequestBuilder idxRequest = buildEsIndexingRequest(cmd);
				if (idxRequest != null) {
					bulkRequest.add(idxRequest);
				}
			} catch (ClientException | IllegalArgumentException e) {
				if (e.getCause() instanceof NoSuchDocumentException) {
					log.info("Skip indexing command to bulk, doc does not exists anymore: " + cmd);
				} else {
					log.error("Skip indexing command to bulk, fail to create request: " + cmd, e);
				}
			}
		}
		if (bulkRequest.numberOfActions() > 0) {
			if (log.isDebugEnabled()) {
				log.debug(String.format(
						"Index %d docs in bulk request: curl -XPOST 'http://localhost:9200/_bulk' -d '%s'",
						bulkRequest.numberOfActions(), bulkRequest.request().requests().toString()));
			}
			BulkResponse response = bulkRequest.execute().actionGet();
			if (response.hasFailures()) {
				logBulkFailure(response);
			}
		}
	}

	protected void logBulkFailure(BulkResponse response) {
		boolean isError = false;
		StringBuilder sb = new StringBuilder();
		sb.append("Ignore indexing of some docs more recent versions has already been indexed");
		for (BulkItemResponse item : response.getItems()) {
			if (item.isFailed()) {
				if (item.getFailure().getStatus() == RestStatus.CONFLICT) {
					sb.append("\n  ").append(item.getFailureMessage());
				} else {
					isError = true;
				}
			}
		}
		if (isError) {
			log.error(response.buildFailureMessage());
		} else {
			log.info(sb);
		}
	}

	protected void refreshIfNeeded(List<IndexingCommand> cmds) {
		for (IndexingCommand cmd : cmds) {
			if (refreshIfNeeded(cmd))
				return;
		}
	}

	private boolean refreshIfNeeded(IndexingCommand cmd) {
		if (cmd.isSync()) {
			esa.refresh();
			return true;
		}
		return false;
	}

	@Override
	public void indexNonRecursive(IndexingCommand cmd) throws ClientException {
		Type type = cmd.getType();
		if (type == Type.UPDATE_DIRECT_CHILDREN) {
			// the parent don't need to be indexed
			return;
		}
		Context stopWatch = null;
		try {
			if (type == Type.DELETE) {
				stopWatch = deleteTimer.time();
				processDeleteCommand(cmd);
			} else {
				stopWatch = indexTimer.time();
				processIndexCommand(cmd);
			}
			refreshIfNeeded(cmd);
		} finally {
			if (stopWatch != null) {
				stopWatch.stop();
			}
			esa.totalCommandProcessed.incrementAndGet();
		}
	}

	void processIndexCommand(IndexingCommand cmd) {
		IndexRequestBuilder request;
		try {
			request = buildEsIndexingRequest(cmd);
		} catch (ClientException | IllegalStateException e) {
			if (e.getCause() instanceof NoSuchDocumentException) {
				request = null;
			} else {
				log.error("Fail to create request for indexing command: " + cmd, e);
				return;
			}
		}
		if (request == null) {
			log.info("Cancel indexing command because target document does not exists anymore: " + cmd);
			return;
		}
		if (log.isDebugEnabled()) {
			log.debug(String.format("Index request: curl -XPUT 'http://localhost:9200/%s/%s/%s' -d '%s'",
					esa.getIndexNameForRepository(cmd.getRepositoryName()), DOC_TYPE, cmd.getTargetDocumentId(),
					request.request().toString()));
		}
		try {
			request.execute().actionGet();
		} catch (VersionConflictEngineException e) {
			log.info("Ignore indexing of doc " + cmd.getTargetDocumentId()
					+ " a more recent version has already been indexed: " + e.getMessage());
		}
	}

	void processDeleteCommand(IndexingCommand cmd) {
		if (cmd.isRecurse()) {
			processDeleteCommandRecursive(cmd);
		} else {
			processDeleteCommandNonRecursive(cmd);
		}
	}

	void processDeleteCommandNonRecursive(IndexingCommand cmd) {
		String indexName = esa.getIndexNameForRepository(cmd.getRepositoryName());
		DeleteRequestBuilder request = esa.getClient().prepareDelete(indexName, DOC_TYPE, cmd.getTargetDocumentId());
		if (log.isDebugEnabled()) {
			log.debug(String.format("Delete request: curl -XDELETE 'http://localhost:9200/%s/%s/%s'", indexName,
					DOC_TYPE, cmd.getTargetDocumentId()));
		}
		request.execute().actionGet();
	}

	void processDeleteCommandRecursive(IndexingCommand cmd) {
		String indexName = esa.getIndexNameForRepository(cmd.getRepositoryName());
		// we don't want to rely on target document because the document can be
		// already removed
		String docPath = getPathOfDocFromEs(cmd.getRepositoryName(), cmd.getTargetDocumentId());
		if (docPath == null) {
			if (!Framework.isTestModeSet()) {
				log.warn("Trying to delete a non existing doc: " + cmd.toString());
			}
			return;
		}
		QueryBuilder query = QueryBuilders.constantScoreQuery(FilterBuilders.termFilter(CHILDREN_FIELD, docPath));
		DeleteByQueryRequestBuilder deleteRequest = esa.getClient().prepareDeleteByQuery(indexName).setTypes(DOC_TYPE)
				.setQuery(query);
		if (log.isDebugEnabled()) {
			log.debug(
					String.format("Delete byQuery request: curl -XDELETE 'http://localhost:9200/%s/%s/_query' -d '%s'",
							indexName, DOC_TYPE, query.toString()));
		}
		DeleteByQueryResponse responses = deleteRequest.execute().actionGet();
		for (IndexDeleteByQueryResponse response : responses) {
			// there is no way to trace how many docs are removed
			if (response.getFailedShards() > 0) {
				log.error(String.format("Delete byQuery fails on shard: %d out of %d", response.getFailedShards(),
						response.getTotalShards()));
			}
		}
	}

	/**
	 * Return the ecm:path of an ES document or null if not found.
	 */
	String getPathOfDocFromEs(String repository, String docId) {
		String indexName = esa.getIndexNameForRepository(repository);
		GetRequestBuilder getRequest = esa.getClient().prepareGet(indexName, DOC_TYPE, docId).setFields(PATH_FIELD);
		if (log.isDebugEnabled()) {
			log.debug(String.format("Get path of doc: curl -XGET 'http://localhost:9200/%s/%s/%s?fields=%s'", indexName,
					DOC_TYPE, docId, PATH_FIELD));
		}
		GetResponse ret = getRequest.execute().actionGet();
		if (!ret.isExists() || ret.getField(PATH_FIELD) == null) {
			// doc not found
			return null;
		}
		return ret.getField(PATH_FIELD).getValue().toString();
	}

	/**
	 * Return indexing request or null if the doc does not exists anymore.
	 *
	 * @throws ClientException in case of pb to get the document or generate json
	 * @throws                 java.lang.IllegalStateException if the command is not
	 *                         attached to a session
	 */
	IndexRequestBuilder buildEsIndexingRequest(IndexingCommand cmd) throws ClientException {
		DocumentModel doc = cmd.getTargetDocument();
		if (doc == null) {
			return null;
		}
		try {
			JsonFactory factory = new JsonFactory();
			XContentBuilder builder = jsonBuilder();
			JsonGenerator jsonGen = factory.createJsonGenerator(builder.stream());
			jsonESDocumentWriter.writeESDocument(jsonGen, doc, cmd.getSchemas(), null);
			IndexRequestBuilder ret = esa.getClient()
					.prepareIndex(esa.getIndexNameForRepository(cmd.getRepositoryName()), DOC_TYPE,
							cmd.getTargetDocumentId())
					.setSource(builder);
			if (useExternalVersion && cmd.getOrder() > 0) {
				ret.setVersionType(VersionType.EXTERNAL).setVersion(cmd.getOrder());
			}
			return ret;
		} catch (ClientException e) {
			throw e;
		} catch (Exception e) {
			throw new ClientException("Unable to create index request for Document " + cmd.getTargetDocumentId(), e);
		}
	}
}
