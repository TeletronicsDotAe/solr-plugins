package ae.teletronics.solr.plugin;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.metrics.SolrMetricManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import uk.org.lidalia.slf4jext.Level;
import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.IntStream;

public class MemoryInspectorTest extends SolrTestBase {

	@Before
	public void init() {
		IntStream.range(0, 20).forEach(i -> {
			SolrInputDocument doc = new SolrInputDocument();
			doc.addField("id", Integer.valueOf(i));
			doc.addField("s_s1", String.valueOf(Math.random()));
			try {
				server.add(doc);
				server.commit(true, true);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	/**
	 * Sunshine
	 */
	@Test
	public void testMemoryInspection() throws IOException, SolrServerException {
		GenericSolrRequest request = new GenericSolrRequest(SolrRequest.METHOD.GET, "/admin/memory", null);
		NamedList<Object> response = server.request(request);
		Map map = response.asMap(2);
		Assert.assertEquals(2, map.size());
	}

	@Test
	public void testMetricsInMetricRegistry() {
		SolrMetricManager metricManager = coreContainer.getMetricManager();
		Long collection1MemoryUsage = (Long) metricManager.registry("solr.core.collection1").getGauges().get("ADMIN./admin/memory.memoryUsed").getValue();
		Assert.assertNotNull(collection1MemoryUsage);
		Assert.assertTrue(collection1MemoryUsage > 0l);
	}

	@Test
	public void testDumpingToLogger() throws IOException, SolrServerException {
		// Given
		TestLogger logger = TestLoggerFactory.getTestLogger(MemoryInspectorHandler.class);
		logger.setEnabledLevels(Level.INFO);

		// When
		SolrParams solrParams = new ModifiableSolrParams(Collections.singletonMap("dumpToStdOut", new String[] {"true"}));
		GenericSolrRequest request = new GenericSolrRequest(SolrRequest.METHOD.GET, "/admin/memory", solrParams);
		NamedList<Object> response = server.request(request);

		// Then
		Assert.assertEquals(1, logger.getLoggingEvents().stream().filter(event -> event.getMessage().contains("Solr core")).count());
	}


}