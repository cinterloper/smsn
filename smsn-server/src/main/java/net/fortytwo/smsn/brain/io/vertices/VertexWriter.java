package net.fortytwo.smsn.brain.io.vertices;

import net.fortytwo.smsn.SemanticSynchrony;
import net.fortytwo.smsn.brain.io.BrainWriter;
import net.fortytwo.smsn.brain.io.Format;
import net.fortytwo.smsn.brain.model.Atom;
import net.fortytwo.smsn.brain.model.AtomGraph;
import net.fortytwo.smsn.brain.rdf.KnowledgeBase;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class VertexWriter extends BrainWriter {
    private static final Logger logger = Logger.getLogger(VertexWriter.class.getName());

    @Override
    public List<Format> getFormats() {
        return Arrays.asList(VertexTSVFormat.getInstance());
    }

    @Override
    public void doExport(Context context) throws IOException {

        AtomGraph sourceGraph = context.getFilteredGraph();
        KnowledgeBase sourceKb = context.getKnowledgeBase();
        PrintStream p = new PrintStream(context.getDestStream());

        p.println("created\tid\tweight\tsharability\tclass\tout\tin\tvalue\talias");

        for (Atom a : sourceGraph.getAllAtoms()) {
            Long c = a.getCreated();
            if (null != c) {
                p.print(c);
                p.print('\t');
                p.print(a.getId());
                p.print('\t');
                p.print(a.getWeight());
                p.print('\t');
                p.print(a.getSharability());
                p.print('\t');

                List<KnowledgeBase.AtomClassEntry> entries = sourceKb.getClassInfo(a);
                if (null != entries && entries.size() > 0) {
                    KnowledgeBase.AtomClassEntry e = entries.get(0);
                    p.print(e.getInferredClassName());
                    p.print('\t');
                    p.print(e.getOutScore());
                    p.print('\t');
                    p.print(e.getInScore());
                    p.print('\t');
                } else {
                    p.print("\t0\t0\t");
                }

                String value = a.getValue();
                if (null == value) {
                    logger.warning("note has null @value: " + a.getId());
                } else {
                    p.print(escapeValue(a.getValue()));
                }
                p.print('\t');

                String alias = a.getAlias();
                if (null != alias) {
                    p.print(escapeValue(alias));
                }

                p.print('\n');
            }
        }
    }

    // Note: quote characters (") need to be replaced, e.g. with underscores (_), if this data is imported into R.
    // Otherwise, R becomes confused and skips rows.
    private String escapeValue(final String value) {
        return SemanticSynchrony.unicodeEscape(value);
    }
}
