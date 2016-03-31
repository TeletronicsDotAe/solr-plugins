# Solr plugins

A set of solr plugins that adds additional insight into a running solr.

### Memory inspector

A plugin that dumps detailed memory usage statistics of index readers as well as segment details, for all cores. Statistics are also accessible though queryhandler statictics in the solr admin gui.

#### Installation

Add this to your solrconfig.xml:

```xml
<requestHandler name="/memory" class="ae.teletronics.solr.plugin.MemoryInspectorHandler"/>
```

#### Usage

Do an http request to a collection, that has the memory inspector mapped. E.g.:

```
http://<ip>:<port>/solr/<collection>/memory?wt=json&indent=true
```

return the memory usage as json. If you add `dumpToStdOut=true` as a parameter, the memory usage is also logged on `INFO` level. The output looks something like this (depending on your configuration):


```
Solr cores (count: 2): 20.7 KB
|-- Core 'collection1' (codec: 'HighCompressionCompressingStoredFields', lucene match version: '5.4.0'): 20.7 KB
    |-- Searcher (maxDocs: 20, debugInfo: 'Searcher@34544949[collection1] main{ExitableDirectoryReader(UninvertingDirectoryReader(Uninverting(_a(5.4.0):c10) Uninverting(_b(5.4.0):c1) Uninverting(_c(5.4.0):c1) Uninverting(_d(5.4.0):c1) Uninverting(_e(5.4.0):c1) Uninverting(_f(5.4.0):c1) Uninverting(_g(5.4.0):c1) Uninverting(_h(5.4.0):c1) Uninverting(_i(5.4.0):c1) Uninverting(_j(5.4.0):c1) Uninverting(_l(5.4.0):c1)))}'): 20.7 KB
        |-- ExitableDirectoryReader(UninvertingDirectoryReader(Uninverting(_a(5.4.0):c10) Uninverting(_b(5.4.0):c1) Uninverting(_c(5.4.0):c1) Uninverting(_d(5.4.0):c1) Uninverting(_e(5.4.0):c1) Uninverting(_f(5.4.0):c1) Uninverting(_g(5.4.0):c1) Uninverting(_h(5.4.0):c1) Uninverting(_i(5.4.0):c1) Uninverting(_j(5.4.0):c1) Uninverting(_l(5.4.0):c1))): 20.7 KB
            |-- _a(5.4.0):c10: 2 KB
                |-- postings [PerFieldPostings(segment=_a formats=1)]: 1.5 KB
                    |-- format 'Lucene50_0' [BlockTreeTermsReader(fields=6,delegate=Lucene50PostingsReader(positions=false,payloads=false))]: 1.4 KB
                        |-- field '_version_' [BlockTreeTerms(terms=10,postings=10,positions=-1,docs=10)]: 233 bytes
                            |-- term index [FST(input=BYTE1,output=ByteSequenceOutputs,packed=false]: 73 bytes
                            ...
                        |-- delegate [Lucene50PostingsReader(positions=false,payloads=false)]: 32 bytes
                |-- docvalues [PerFieldDocValues(formats=1)]: 236 bytes
                    |-- format 'Lucene54_0' [Lucene54DocValuesProducer(fields=4)]: 216 bytes
                        |-- direct addresses meta field 's_s1' [org.apache.lucene.util.packed.DirectMonotonicReader$Meta@ccb70fe]: 144 bytes
                |-- stored fields [CompressingStoredFieldsReader(mode=HIGH_COMPRESSION,chunksize=5567)]: 312 bytes
                    |-- stored field index [CompressingStoredFieldsIndexReader(blocks=1)]: 312 bytes
                        |-- doc base deltas: 88 bytes
                        |-- start pointer deltas: 88 bytes
            |-- _b(5.4.0):c1: 1.9 KB
                ...
            |-- _c(5.4.0):c1: 1.9 KB
            ...
|-- Core 'collection2' (codec: 'HighCompressionCompressingStoredFields', lucene match version: '5.4.0'): 0 bytes
    |-- Searcher (maxDocs: 0, debugInfo: 'Searcher@6b7df682[collection2] main{ExitableDirectoryReader(UninvertingDirectoryReader())}'): 0 bytes
        |-- ExitableDirectoryReader(UninvertingDirectoryReader()): 0 bytes

```

### Thread renaming search handler
A search handler that renames the searching thread to the search handler class being executed along with a start timestamp and all query parameters. The actual search is handled by a configurable delegate. A list of running searches is accessible though queryhandler statictics in the solr admin gui.

#### Installation

Add this to your solrconfig.xml:

```xml
    <requestHandler name="/threadrenamingselect" class="ae.teletronics.solr.plugin.ThreadRenamingRequestHandler">
        <str name="delegate">/select</str>
    </requestHandler>
```

To make thread renaming the default select handler, change the mapping of the default select handler to something else than `/select` and map `ThreadRenamingRequestHandler`to `/select` instead.

#### Usage
An example thead name for a simple query could look like this:

```
org.apache.solr.handler.component.SearchHandler (Start: 03:38:06.599): {q=*:*}
```

This information will be part of all thread dumps as well as anything else printing thread names - e.g. logging, if configured to print the thread name. A list of all running searches, formatted like above, is available through the solr admin gui as well.