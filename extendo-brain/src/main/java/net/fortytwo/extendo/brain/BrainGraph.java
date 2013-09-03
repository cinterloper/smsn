package net.fortytwo.extendo.brain;

import com.tinkerpop.blueprints.Index;
import com.tinkerpop.blueprints.KeyIndexableGraph;
import com.tinkerpop.blueprints.Parameter;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.wrappers.id.IdGraph;
import com.tinkerpop.frames.FramedGraph;
import net.fortytwo.extendo.Extendo;
import org.neo4j.index.impl.lucene.LowerCaseKeywordAnalyzer;

import java.io.File;
import java.io.FileWriter;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class BrainGraph {
    private static final Logger LOGGER = Extendo.getLogger(BrainGraph.class);

    private final IdGraph<KeyIndexableGraph> graph;

    private final FramedGraph<KeyIndexableGraph> framedGraph;

    private final ActivityLog activityLog;

    private Index<Vertex> searchIndex;

    private final Priorities priorities;

    private static final Map<KeyIndexableGraph, BrainGraph> graphs = new HashMap<KeyIndexableGraph, BrainGraph>();

    public static BrainGraph getInstance(final KeyIndexableGraph baseGraph) throws Exception {
        BrainGraph g = graphs.get(baseGraph);

        if (null == g) {
            g = new BrainGraph(baseGraph);
            graphs.put(baseGraph, g);
        }

        return g;
    }

    private BrainGraph(final KeyIndexableGraph baseGraph) throws Exception {
        IdGraph.IdFactory f = new MOBIdFactory();
        graph = new IdGraph<KeyIndexableGraph>(baseGraph);
        graph.setVertexIdFactory(f);
        graph.setEdgeIdFactory(f);

        framedGraph = new FramedGraph<KeyIndexableGraph>(graph);

        searchIndex = graph.getIndex("search", Vertex.class);
        if (null == searchIndex) {
            LOGGER.info("creating fulltext search index");
            searchIndex = graph.createIndex("search", Vertex.class, new Parameter("analyzer", LowerCaseKeywordAnalyzer.class.getName()));
        }

        if (!graph.getIndexedKeys(Vertex.class).contains(Extendo.ALIAS)) {
            LOGGER.info("creating key index for 'alias' property");
            graph.createKeyIndex(Extendo.ALIAS, Vertex.class);
        }

        File logFile = Extendo.getConfiguration().getFile(Extendo.ACTIVITY_LOG, null);

        if (null == logFile) {
            LOGGER.warning("no activity log specified");
            activityLog = null;
        } else {
            LOGGER.info("will use activity log at " + logFile.getPath());
            activityLog = new ActivityLog(new FileWriter(logFile, true));
        }

        priorities = new Priorities();
        priorities.refreshQueue(this);
    }

    public ActivityLog getActivityLog() {
        return activityLog;
    }

    public KeyIndexableGraph getGraph() {
        return graph;
    }

    public FramedGraph<KeyIndexableGraph> getFramedGraph() {
        return framedGraph;
    }

    public Priorities getPriorities() {
        return priorities;
    }

    public static String getId(final Atom a) {
        return (String) a.asVertex().getId();
    }

    private static class MOBIdFactory implements IdGraph.IdFactory {
        public String createId() {
            return Extendo.createRandomKey();
        }
    }

    public Atom getAtom(final String key) {
        Vertex v = this.getGraph().getVertex(key);

        return null == v ? null : getAtom(v);
    }

    public Atom getAtom(final Vertex v) {
        if (null == v) {
            throw new IllegalArgumentException("null vertex");
        }

        return framedGraph.frame(v, Atom.class);
    }

    public Atom createAtom(final Filter filter,
                           final String id) {
        Atom a = framedGraph.frame(this.getGraph().addVertex(id), Atom.class);
        a.setCreated(new Date().getTime());

        a.setSharability(filter.getDefaultSharability());
        a.setWeight(filter.getDefaultWeight());

        return a;
    }

    public AtomList createAtomList() {
        return framedGraph.frame(this.getGraph().addVertex(null), AtomList.class);
    }

    public AtomList createAtomList(Atom... elements) {
        if (0 == elements.length) {
            throw new IllegalArgumentException("empty list");
        }

        AtomList last = null;
        AtomList head = null;
        for (Atom a : elements) {
            AtomList cur = createAtomList();
            if (null == head) {
                head = cur;
            }
            if (last != null) {
                last.setRest(cur);
            }
            cur.setFirst(a);
            last = cur;
        }

        return head;
    }

    public void remove(final AtomList l) {
        this.getGraph().removeVertex(l.asVertex());
    }

    public Collection<Atom> getAtomsWithValue(final String value) {
        Collection<Atom> results = new LinkedList<Atom>();

        for (Vertex v : graph.getVertices(Extendo.VALUE, value)) {
            results.add(getAtom(v));
        }

        return results;
    }

    public void indexForSearch(final Atom a,
                               final String value) {
        searchIndex.put(Extendo.VALUE, value, a.asVertex());
    }

    /**
     * @return an Iterable of all atoms in the knowledge base, as opposed to all vertices
     * (many of which are list nodes rather than atoms)
     */
    public Iterable<Atom> getAtoms() {
        return new Iterable<Atom>() {
            public Iterator<Atom> iterator() {
                return new Iterator<Atom>() {
                    private Iterator<Vertex> iter = graph.getVertices().iterator();
                    private Atom next = null;

                    public boolean hasNext() {
                        if (null == next) {
                            while (iter.hasNext()) {
                                Vertex v = iter.next();

                                // Here, a vertex is considered an atom if it has a creation timestamp
                                if (null != v.getProperty(Extendo.CREATED)) {
                                    next = getAtom(v);
                                    break;
                                }
                            }

                            return null != next;
                        } else {
                            return true;
                        }
                    }

                    public Atom next() {
                        hasNext();
                        Atom tmp = next;
                        next = null;
                        return tmp;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public Collection<Atom> getAtomsByFulltextQuery(final String query,
                                                    final Filter filter) {
        Collection<Atom> results = new LinkedList<Atom>();

        for (Vertex v : searchIndex.query(Extendo.VALUE, query)) {
            Atom a = getAtom(v);

            if (null == a) {
                throw new IllegalStateException("vertex with id " + v.getId() + " is not an atom");
            }

            if (filter.isVisible(a)) {
                results.add(a);
            }
        }

        return results;
    }
}