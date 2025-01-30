/**
 *
 */
package org.opentoutatice.elasticsearch.core.reindexing.docs.runner.works;

import org.nuxeo.elasticsearch.work.BucketIndexingWorker;
import org.nuxeo.elasticsearch.work.ScrollingIndexingWorker;
import org.opentoutatice.elasticsearch.core.reindexing.docs.constant.ReIndexingConstants;

import java.util.List;

/**
 * @author david
 */
public class ScrollingReIndexingWorker extends ScrollingIndexingWorker {

    public ScrollingReIndexingWorker(String repositoryName, String nxql) {
        super(repositoryName, nxql);
    }

    private static final long serialVersionUID = -1871761129531962566L;

    @Override
    public String getCategory() {
        return ReIndexingConstants.REINDEXING_QUEUE_ID;
    }

    @Override
    protected void scheduleBucketWorker(List<String> bucket, boolean isLast) {
        if (bucket.isEmpty()) {
            return;
        }
        BucketIndexingWorker subWorker = new BucketReIndexingWorker(this.repositoryName, bucket, isLast);
//        try {
//            Thread.sleep(90000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
        this.getWorkManager().schedule(subWorker);
    }
}
