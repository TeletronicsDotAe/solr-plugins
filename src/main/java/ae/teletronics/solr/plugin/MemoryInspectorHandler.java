package ae.teletronics.solr.plugin;

import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.Accountables;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.RefCounted;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URL;
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
	private CoreContainer coreContainer;

	@Override
	public void handleRequestBody(SolrQueryRequest request, SolrQueryResponse response) {
		Accountable memory = inspectCoreContainer(coreContainer);
		if (request.getParams().getBool("dumpToStdOut", false)) {
			log.info(Accountables.toString(memory));
		}
		response.add("Memory dump", toMap(memory));
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
		Collection<Accountable> children = coreContainer.getCores().stream().map(c -> inspectCore(c)).collect(Collectors.toList());
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
		String name = String.format("Searcher (maxDocs: %s, debugInfo: '%s')", searcher.maxDoc(), searcher.toString());
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
				result = Accountables.namedAccountable(indexReader.toString(), children, children.stream().mapToLong(Accountable::ramBytesUsed).sum());
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
		return Category.QUERYHANDLER;
	}

	public String getSource() {
		return null;
	}

	public URL[] getDocs() {
		return new URL[0];
	}

	public NamedList getStatistics() {
		NamedList result = new SimpleOrderedMap<String>();
		result.add("Memory usage", Accountables.toString(inspectCoreContainer(coreContainer)));
		return result;
	}

	@Override
	public void inform(SolrCore solrCore) {
		this.coreContainer = solrCore.getCoreDescriptor().getCoreContainer();
	}
}
