package ae.teletronics.solr.plugin;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ThreadRenamingComponentTest extends SolrTestBase {
	private String originalName;


	@Before
	public void init() {
		ForwardingSearchHandler.reset();
		originalName = Thread.currentThread().getName();
	}

	@After
	public void reset() {
		Thread.currentThread().setName(originalName);
	}

	/**
	 * Sunshine - renaming is done properly
	 */
	@Test
	public void testThreadIsRenamedDuringSearch() throws IOException, SolrServerException {
		AtomicReference<String> threadName = new AtomicReference<>(null);

		ForwardingSearchHandler.setDelegate((req, res) -> {
			threadName.set(Thread.currentThread().getName());
		});

		SolrQuery query = new SolrQuery("*:*");
		QueryRequest request = new QueryRequest(query);
		request.setPath("/threadnametest");
		NamedList<Object> response = server.request(request);

		assertNotNull(threadName.get());
		assertTrue(threadName.get().contains("*:*"));
	}

	/**
	 * Handle exception properly
	 */
	@Test
	public void testThreadIsRenamedToOriginalNameAfterFailure() throws IOException, SolrServerException {
		ForwardingSearchHandler.setDelegate((req, res) -> {
			throw new RuntimeException("Meltdown!");
		});

		try {
			SolrQuery query = new SolrQuery("*:*");
			QueryRequest request = new QueryRequest(query);
			request.setPath("/threadnametest");
			NamedList<Object> response = server.request(request);
			fail();
		} catch (Throwable e) {
			// Expected
		}

		assertEquals(Thread.currentThread().getName(), originalName);
	}

	/**
	 * Sunshine - verify statistics works
	 */
	@Test
	public void testStatisticsReturnRunningThreads() throws IOException, SolrServerException {
		AtomicReference<List<String>> runningRequests = new AtomicReference<>(null);

		ForwardingSearchHandler.setDelegate((req, res) -> {
			SolrCore core = h.getCoreContainer().getCore("collection1");
			ThreadRenamingRequestHandler requestHandler = (ThreadRenamingRequestHandler) core.getRequestHandler("/threadnametest");
			core.close();
			runningRequests.set(requestHandler.getRunningRequests());
		});

		SolrQuery query = new SolrQuery("*:*");
		QueryRequest request = new QueryRequest(query);
		request.setPath("/threadnametest");
		server.request(request);

		assertNotNull(runningRequests.get());
		assertEquals(1, runningRequests.get().size());
	}

}