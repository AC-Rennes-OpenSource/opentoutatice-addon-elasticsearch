package fr.toutatice.ecm.elasticsearch.automation;

import org.elasticsearch.action.search.SearchResponse;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.jaxrs.DefaultJsonAdapter;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;

import fr.toutatice.ecm.elasticsearch.query.TTCNxQueryBuilder;
import fr.toutatice.ecm.elasticsearch.search.TTCSearchResponse;

@Operation(id = QueryES.ID, category = Constants.CAT_FETCH, label = "Query  via ElasticSerach", description = "Perform a query on ElasticSerach instead of Repository")
public class QueryES {

//	private static final Log log = LogFactory.getLog(QueryES.class);
    public static final String ID = "Document.QueryES";
    private static final int DEFAULT_MAX_RESULT_SIZE = 10000;
    
    @Context
    CoreSession session;
    
    @Context
    ElasticSearchService elasticSearchService;
    
    @Param(name = "query", required = false)
    protected String query;

    @Param(name = "currentPageIndex", required = false)
    protected Integer currentPageIndex;

    @Param(name = "pageSize", required = false)
    protected Integer pageSize;

    @OperationMethod
    public DefaultJsonAdapter run() throws OperationException {
    	
    	NxQueryBuilder builder = new TTCNxQueryBuilder(session).nxql(query);
    	if (null != currentPageIndex && null != pageSize) {
    		builder.offset(((0 < currentPageIndex ? currentPageIndex : 1) - 1) * pageSize);
    		builder.limit(pageSize);
    	} else {
    		builder.limit(DEFAULT_MAX_RESULT_SIZE);
    	}
    	
    	elasticSearchService.query(builder);
    	SearchResponse esResponse = ((TTCNxQueryBuilder) builder).getSearchResponse();
    	return new DefaultJsonAdapter(new TTCSearchResponse(esResponse, pageSize, currentPageIndex));
    }
    
}
