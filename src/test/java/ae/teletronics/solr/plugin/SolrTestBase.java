package ae.teletronics.solr.plugin;

import org.apache.commons.io.IOUtils;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.junit.After;
import org.junit.Before;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public abstract class SolrTestBase extends SolrTestCaseJ4 {
	protected SolrClient server;
	protected CoreContainer coreContainer;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		coreContainer = createCoreContainer(Paths.get("target/test-classes/solr"), IOUtils.toString(SolrTestBase.class.getResourceAsStream("/solr/solr.xml"), StandardCharsets.UTF_8));
		server = new EmbeddedSolrServer(h.getCoreContainer(), h.getCoreContainer().getAllCoreNames().iterator().next());
	}

	@After
	@Override
	public void tearDown() throws Exception {
		server.close();
		super.tearDown();
	}
}
