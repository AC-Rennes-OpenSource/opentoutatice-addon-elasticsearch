/**
 *
 */
package org.opentoutatice.elasticsearch.reindexing.docs.bad.initial.state;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.client.RemoteException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.spi.JsonMarshalling.RemoteThrowable;
import org.nuxeo.ecm.automation.client.model.FileBlob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.api.ElasticSearchIndexing;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.test.RepositoryElasticSearchFeature;
import org.nuxeo.runtime.test.runner.BlacklistComponent;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.Jetty;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.opentoutatice.elasticsearch.core.reindexing.docs.automation.ReIndexZeroDownTimeES;
import org.opentoutatice.elasticsearch.core.reindexing.docs.es.state.exception.ReIndexingStateException;
import org.opentoutatice.elasticsearch.core.reindexing.docs.es.state.exception.ReIndexingStatusException;
import org.opentoutatice.elasticsearch.reindexing.docs.bad.initial.state.config.ZeroDownTimeBadInitialAliasConfigFeature;
import org.opentoutatice.elasticsearch.reindexing.docs.feature.EmbeddedAutomationServerFeatureWithOsvClient;

import com.google.inject.Inject;

/**
 * @author david
 *
 */
@RunWith(FeaturesRunner.class)
@Features({ZeroDownTimeBadInitialAliasConfigFeature.class, RepositoryElasticSearchFeature.class, EmbeddedAutomationServerFeatureWithOsvClient.class})
@BlacklistComponent("org.nuxeo.elasticsearch.ElasticSearchComponent")
@Deploy({"org.nuxeo.ecm.automation.test", "org.nuxeo.elasticsearch.core.test",})
@LocalDeploy({"fr.toutatice.ecm.platform.elasticsearch", "fr.toutatice.ecm.platform.elasticsearch:elasticsearch-config-test.xml",
        "fr.toutatice.ecm.platform.elasticsearch:usermanger-test.xml", "fr.toutatice.ecm.platform.elasticsearch:log4j.xml"})
@Jetty(port = 18080)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class ZeroDownTimeReIndexingBadInitialAliasTest {

    private static final Log log = LogFactory.getLog(ZeroDownTimeReIndexingBadInitialAliasTest.class);

    @Inject
    protected ElasticSearchAdmin esAdmin;

    @Inject
    protected ElasticSearchIndexing esIndexing;

    @Inject
    protected ElasticSearchService esService;

    @Inject
    protected CoreSession session;

    @Inject
    protected Session automationSession;

    static final String[][] users = {{"VirtualAdministrator", "secret"}, {"Administrator", "Administrator"}};

    @Test
    public void testZeroDownTimeReIndexingYetRunningFromAutomation() throws Exception {
        // Launch zero down time re-indexing
        this.launchReIndexingFromAutomation();
        // Do not wait & launch concurrent call
        RemoteException exc = null;
        try {
            this.launchReIndexingFromAutomation();
        } catch (RemoteException e) {
            exc = e;
        }

        Assert.assertNotNull(exc);
        JsonNode excAsJsonNode = ((org.nuxeo.ecm.automation.client.jaxrs.spi.JsonMarshalling.RemoteThrowable) exc.getRemoteCause()).getOtherNodes()
                .get("className");

        Assert.assertEquals(ReIndexingStatusException.class.getCanonicalName(), excAsJsonNode.getTextValue());
    }

    @Test
    public void testZeroDownTimeReIndexingAliasWithManyIndicesFromAutomation() throws Exception {
        // Launch zero down time re-indexing
        RemoteException exc = null;
        try {
            this.launchReIndexingFromAutomation();
        } catch (RemoteException e) {
            exc = e;
        }

        Assert.assertNotNull(exc);

        RemoteThrowable remoteCause = (org.nuxeo.ecm.automation.client.jaxrs.spi.JsonMarshalling.RemoteThrowable) exc.getRemoteCause();

        JsonNode excAsJsonNode = remoteCause.getOtherNodes().get("className");
        Assert.assertEquals(ReIndexingStateException.class.getCanonicalName(), excAsJsonNode.getTextValue());

        Assert.assertEquals(Boolean.TRUE,
                StringUtils.contains(((RemoteThrowable) remoteCause.getCause()).getCause().getMessage(), "[2] indices defined for alias [nxutest-alias]"));
    }

    @Test
    public void testZeroDownTimeReIndexingBadFormerAliasFromAutomation() throws Exception {
        // Launch zero down time re-indexing
        RemoteException exc = null;
        try {
            this.launchReIndexingFromAutomation();
        } catch (RemoteException e) {
            exc = e;
        }

        Assert.assertNotNull(exc);

        RemoteThrowable remoteCause = (org.nuxeo.ecm.automation.client.jaxrs.spi.JsonMarshalling.RemoteThrowable) exc.getRemoteCause();

        JsonNode excAsJsonNode = remoteCause.getOtherNodes().get("className");
        Assert.assertEquals(ReIndexingStateException.class.getCanonicalName(), excAsJsonNode.getTextValue());

        Assert.assertEquals(Boolean.TRUE,
                StringUtils.contains(((RemoteThrowable) remoteCause.getCause()).getCause().getMessage(), "One or both of transient aliases"));
    }


    public String launchReIndexingFromAutomation() throws Exception {
        StringBuilder launchedStatus = new StringBuilder();

        Session session_ = null;
        try {
            session_ = this.automationSession.getClient().getSession(users[0][0], users[0][1]);
            FileBlob launchedStatusFile = (FileBlob) session_.newRequest(ReIndexZeroDownTimeES.ID).set("repository", "test").execute();

            try (BufferedReader br = Files.newBufferedReader(Paths.get(launchedStatusFile.getFile().getPath()))) {
                launchedStatus.append(br.readLine());
            }

            log.info(String.format("[========= Launched status: [%s] =========]", launchedStatus.toString()));
        } finally {
            if (session_ != null) {
                session_.close();
            }
        }

        return launchedStatus.toString();
    }

}