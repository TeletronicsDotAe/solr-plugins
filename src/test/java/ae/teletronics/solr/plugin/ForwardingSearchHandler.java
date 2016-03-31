package ae.teletronics.solr.plugin;

import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

import java.util.function.BiConsumer;

public class ForwardingSearchHandler extends SearchHandler {
	private static BiConsumer<SolrQueryRequest, SolrQueryResponse> delegate = getNOPConsumer();

	@Override
	public void handleRequest(SolrQueryRequest req, SolrQueryResponse rsp) {
		delegate.accept(req, rsp);
	}

	public static void reset() {
		delegate = getNOPConsumer();
	}

	public static void setDelegate(BiConsumer<SolrQueryRequest, SolrQueryResponse> delegate) {
		ForwardingSearchHandler.delegate = delegate;
	}

	private static BiConsumer<SolrQueryRequest, SolrQueryResponse> getNOPConsumer() {
		return delegate = (req, res) -> {};
	}
}
