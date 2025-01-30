/**
 *
 */
package org.opentoutatice.elasticsearch.api;

import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.opentoutatice.elasticsearch.config.exception.AliasConfigurationException;

import java.util.Map;

/**
 * @author dchevrier <chevrier.david.pro@gmail.com>
 *
 */
public interface OttcElasticSearchAdmin extends ElasticSearchAdmin {

    Map<String, String> getIndexNames();

    Map<String, String> getRepoNames();

    String getConfiguredIndexOrAliasNameForRepository(String repositoryName);

    boolean isZeroDownTimeReIndexingInProgress(String repository) throws InterruptedException;
    
    boolean aliasConfigured(String repositoryName);
    void initIndexesOrAlias(boolean dropIfExists) throws AliasConfigurationException;
    
    void dropAndInitIndexOrAlias(String indexName) throws AliasConfigurationException;
    
    void dropAndInitRepositoryIndexOrAlias(String repositoryName) throws AliasConfigurationException;

}
