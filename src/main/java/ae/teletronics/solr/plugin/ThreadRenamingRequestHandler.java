package ae.teletronics.solr.plugin;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.metrics.SolrMetricManager;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.util.plugin.SolrCoreAware;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * This plugin decorates another {@link SolrRequestHandler} and renames the searching thread to
 * show the query and the start timestamp. The list of searching threads is exposed as queryhandler
 * statistics in the solr admin gui.
 */
public class ThreadRenamingRequestHandler extends RequestHandlerBase implements SolrCoreAware {
	private ConcurrentMap<Thread, String> executingThreads = new ConcurrentHashMap<>();
	private String delegateName;
	private SolrRequestHandler delegate;

	public void init(NamedList initArgs) {
		delegateName = String.valueOf(initArgs.get("delegate"));
	}

	public void inform(SolrCore core) {
		delegate = core.getRequestHandler(delegateName);
		if (delegate == null) {
			throw new IllegalArgumentException("Solr request handler delegate not found! Please check your <str name=\"delegate\">...</str> init argument");
		}
	}

	@Override
	public void handleRequestBody(SolrQueryRequest solrQueryRequest, SolrQueryResponse solrQueryResponse) {
		Thread thread = Thread.currentThread();
		String originalThreadName = thread.getName();
		try {
			try {
				executingThreads.put(thread, originalThreadName);
				thread.setName(String.format("%s (Start: %s): %s",
						delegate.getName(),
						DateTimeFormatter.ISO_LOCAL_TIME.format(LocalTime.now()),
						SolrParams.toMap(solrQueryRequest.getParams().toNamedList()).toString())
				);
			} catch (SecurityException | NullPointerException e) {
				// Just don't do anything. If we are not allowed to change thread names (or I missed that getParams could be null), we still want to execute the request
			}
			delegate.handleRequest(solrQueryRequest, solrQueryResponse);
		} finally {
			executingThreads.remove(thread);
			try {
				thread.setName(originalThreadName);
			} catch (SecurityException e) {
				// Yeah, still an issue
			}
		}
	}

	public int getRunningRequestCount() {
		return executingThreads.size();
	}

	public List<String> getRunningRequests() {
		return executingThreads.keySet().stream().map(Thread::getName).collect(Collectors.toList());
	}

	public String getName() {
		return "ThreadRenamingRequestHandlerDelegate";
	}

	public String getDescription() {
		return "Thread renaming request handler delegate";
	}

	public Category getCategory() {
		return Category.ADMIN;
	}

	@Override
	public void initializeMetrics(SolrMetricManager manager, String registryName, String scope) {
		super.initializeMetrics(manager, registryName, scope);
		manager.registerGauge(this, registryName, () -> executingThreads.size(), true, "runningRequests", new String[]{this.getCategory().toString(), scope});
	}
}
