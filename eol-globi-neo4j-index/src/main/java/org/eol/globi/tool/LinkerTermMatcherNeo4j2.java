package org.eol.globi.tool;

import org.apache.commons.lang.time.StopWatch;
import org.eol.globi.db.GraphServiceFactory;
import org.eol.globi.domain.NameType;
import org.eol.globi.domain.RelTypes;
import org.eol.globi.domain.TaxonNode;
import org.eol.globi.domain.Term;
import org.eol.globi.service.PropertyEnricherException;
import org.eol.globi.service.TaxonUtil;
import org.eol.globi.taxon.TermMatcher;
import org.eol.globi.util.NodeUtil;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LinkerTermMatcherNeo4j2 implements IndexerNeo4j {

    private static final int BATCH_SIZE = 100;

    private static final Logger LOG = LoggerFactory.getLogger(LinkerTermMatcherNeo4j2.class);
    private final TermMatcher termMatcher;
    private final GraphServiceFactory factory;

    public LinkerTermMatcherNeo4j2(TermMatcher termMatcher, GraphServiceFactory factory) {
        this.termMatcher = termMatcher;
        this.factory = factory;
    }


    @Override
    public void index() {
        final GraphDatabaseService graphDb = factory.getGraphService();
        Index<Node> taxons = graphDb.index().forNodes("taxons");
        IndexHits<Node> hits = taxons.query("*:*");

        final Map<Long, TaxonNode> nodeMap = new HashMap<>();
        int counter = 1;
        for (Node hit : hits) {
            if (counter % BATCH_SIZE == 0) {
                handleBatch(graphDb, termMatcher, nodeMap, counter);
            }
            TaxonNode node = new TaxonNode(hit);
            nodeMap.put(node.getNodeID(), node);
            counter++;
        }
        handleBatch(graphDb, termMatcher, nodeMap, counter);
    }

    private void handleBatch(final GraphDatabaseService graphDb, TermMatcher termMatcher, final Map<Long, TaxonNode> nodeMap, int counter) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String msgPrefix = "batch #" + counter / BATCH_SIZE;
        LOG.info(msgPrefix + " preparing...");
        List<Term> termRequests = new ArrayList<>();
        for (Map.Entry<Long, TaxonNode> entry : nodeMap.entrySet()) {
            Term e = new TermRequestImpl(entry.getValue().getId(), entry.getValue().getName(), entry.getKey());
            termRequests.add(e);
        }
        try {
            if (termRequests.size() > 0) {
                termMatcher.match(termRequests, (nodeId, name, relType, taxon) -> {
                    TaxonNode taxonNode = nodeMap.get(nodeId);
                    if (taxonNode != null
                            && NameType.NONE != relType
                            && !TaxonUtil.likelyHomonym(taxon, taxonNode)) {
                        NodeUtil.connectTaxa(taxon, taxonNode, graphDb, RelTypes.forType(relType));
                    }
                });
            }

        } catch (PropertyEnricherException ex) {
            LOG.error(msgPrefix + " problem matching terms", ex);
        }
        stopWatch.stop();
        LOG.info(msgPrefix + " completed in [" + stopWatch.getTime() + "] ms (" + (1.0 * stopWatch.getTime() / BATCH_SIZE) + " ms/name )");

        nodeMap.clear();
    }


}
