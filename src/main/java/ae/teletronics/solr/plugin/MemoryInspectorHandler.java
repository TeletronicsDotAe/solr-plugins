package ae.teletronics.solr.plugin;

import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.Accountables;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.metrics.SolrMetricManager;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.RefCounted;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This plugin inspects selected parts of a running solr's memory usage. All {@link SolrCore}s in the {@link CoreContainer}
 * are inspected, trying to round up {@link Accountable}s. For now, each core gathers the {@link SolrIndexSearcher}s memory usage
 * by inspecting the underlying {@link IndexReader}s.
 */
public class MemoryInspectorHandler extends RequestHandlerBase implements SolrCoreAware {
	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private SolrCore core;
	private CoreContainer coreContainer;

	@Override
	public void handleRequestBody(SolrQueryRequest request, SolrQueryResponse response) {
		Accountable memory;
		if (coreContainer != null) {
			memory = inspectCoreContainer(coreContainer);
			if (request.getParams().getBool("dumpToStdOut", false)) {
				log.info(Accountables.toString(memory));
			}
		} else {
			memory = () -> 0;
		}
		Map<String, ?> map = toMap(memory);
		response.add("Memory dump", map);
	}

	private static Map<String, ?> toMap(Accountable accountable) {
		LinkedHashMap<String, Object> result = new LinkedHashMap<>();
		result.put(accountable.toString(), RamUsageEstimator.humanReadableUnits(accountable.ramBytesUsed(), new DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(Locale.ROOT))));
		Collection children = accountable.getChildResources().stream().map(a -> toMap(a)).collect(Collectors.toList());
		if (!children.isEmpty()) {
			result.put("children", children);
		}
		return result;
	}

	private Accountable inspectCoreContainer(CoreContainer coreContainer) {
		Collection<SolrCore> cores = (coreContainer.getCores() != null) ? coreContainer.getCores() : Collections.emptyList();
		Collection<Accountable> children = cores.stream().map(c -> inspectCore(c)).collect(Collectors.toList());
		String name = String.format("Solr cores (count: %s)", children.size());
		return Accountables.namedAccountable(name, children, children.stream().mapToLong(Accountable::ramBytesUsed).sum());
	}

	private Accountable inspectCore(SolrCore core) {
		Collection<Accountable> children = new ArrayList<>();
		RefCounted<SolrIndexSearcher> searcher = core.getSearcher();
		try {
			children.add(inspectSearcher(searcher.get()));
		} finally {
			searcher.decref();
		}
		String name = String.format("Core '%s' (codec: '%s', lucene match version: '%s')", core.getName(), core.getCodec().getName(), core.getSolrConfig().luceneMatchVersion);
		return Accountables.namedAccountable(name, children, children.stream().mapToLong(Accountable::ramBytesUsed).sum());
	}

	private Accountable inspectSearcher(SolrIndexSearcher searcher) {
		Collection<Accountable> children = new ArrayList<>();
		children.add(inspectIndexReader(searcher.getIndexReader()));
		String name = String.format("Searcher '%s' (numDocs: %s, maxDocs: %s)", searcher.getName(), searcher.numDocs(), searcher.maxDoc());
		return Accountables.namedAccountable(name, children, children.stream().mapToLong(Accountable::ramBytesUsed).sum());
	}

	private Accountable inspectIndexReader(IndexReader indexReader) {
		if (indexReader instanceof FilterLeafReader) {
			indexReader = FilterLeafReader.unwrap((LeafReader) indexReader);
		}

		final IndexReader unwrappedReader = indexReader;
		Accountable result;
		if (indexReader instanceof Accountable) {
			result = (Accountable) indexReader;
		} else {
			if (indexReader.leaves().size() == 0) {
				result = Accountables.namedAccountable(unwrappedReader.toString(), 0);
			} else {
				Collection<Accountable> children = indexReader.leaves().stream().map(lrc -> {
					LeafReader reader = lrc.reader();
					if (reader == unwrappedReader) {
						return Accountables.namedAccountable(unwrappedReader.toString(), 0);
					} else {
						return inspectIndexReader(reader);
					}
				}).collect(Collectors.toList());
				String name = String.format("IndexReader '%s', segments: %s", indexReader.getClass().getName(), indexReader.leaves().size());
				result = Accountables.namedAccountable(name, children, children.stream().mapToLong(Accountable::ramBytesUsed).sum());
			}
		}
		return result;
	}

	public String getName() {
		return "MemoryInspectorHandler";
	}

	public String getVersion() {
		return "1.0";
	}

	public String getDescription() {
		return "Inspects solr memory usage";
	}

	public Category getCategory() {
		return Category.ADMIN;
	}

	@Override
	public void initializeMetrics(SolrMetricManager manager, String registryName, String scope) {
		super.initializeMetrics(manager, registryName, scope);
		manager.registerGauge(this, registryName, () -> {
			return (core != null) ? inspectCore(core).ramBytesUsed() : 0;
		}, true, "memoryUsed", new String[]{this.getCategory().toString(), scope});
	}

	@Override
	public void inform(SolrCore solrCore) {
		this.core = solrCore;
		this.coreContainer = solrCore.getCoreContainer();
	}
}
