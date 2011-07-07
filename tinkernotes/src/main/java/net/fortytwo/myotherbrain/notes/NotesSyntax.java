package net.fortytwo.myotherbrain.notes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class NotesSyntax {
    // Regex of valid id prefixes, including parentheses, colon and trailing space
    public static final Pattern KEY_PREFIX = Pattern.compile("[a-zA-Z0-9@&]+:[a-zA-Z0-9@&]+: ");

    private static final int MAX_TYPE_LENGTH = 5;

    // Tabs count as four spaces each.
    private static final String TAB_REPLACEMENT = "    ";

    public void writeContexts(final List<NoteContext> notes,
                              final OutputStream out) throws JSONException {
        PrintStream p = new PrintStream(out);
        for (NoteContext c : notes) {
            printContext(c, p);
            p.println("");
        }
    }

    public void writeNotes(final List<Note> notes,
                           final OutputStream out) throws JSONException {
        PrintStream p = new PrintStream(out);
        for (Note n : notes) {
            printNote(n, 0, p);
        }
    }

    public JSONObject toJSON(final Note n) throws JSONException {
        JSONObject json = new JSONObject();

        //if (null != n.getLinkKey()) {
        JSONObject link = new JSONObject();
        json.put("link", link);

        link.put("key", n.getLinkKey());
        link.put("weight", n.getLinkWeight());
        link.put("sharability", n.getLinkSharability());
        link.put("value", n.getLinkValue());
        //}

        //if (null != n.getTargetKey()) {
        JSONObject target = new JSONObject();
        json.put("target", target);

        target.put("key", n.getTargetKey());
        target.put("weight", n.getTargetWeight());
        target.put("sharability", n.getTargetSharability());
        target.put("value", n.getTargetValue());
        //}

        if (0 < n.getChildren().size()) {
            JSONArray c = new JSONArray();
            json.put("children", c);
            int i = 0;
            for (Note child : n.getChildren()) {
                c.put(i, toJSON(child));
                i++;
            }
        }

        return json;
    }

    public List<Note> readNotes(final InputStream in) throws IOException, NoteParsingException {
        List<Note> notes = new LinkedList<Note>();
        parsePrivate(in, null, notes);
        return notes;
    }

    public List<NoteContext> readContexts(final InputStream in) throws IOException, NoteParsingException {
        List<NoteContext> contexts = new LinkedList<NoteContext>();
        parsePrivate(in, contexts, null);
        return contexts;
    }

    private void parsePrivate(final InputStream in,
                              final Collection<NoteContext> contexts,
                              final Collection<Note> notes) throws IOException, NoteParsingException {
        LinkedList<Note> hierarchy = new LinkedList<Note>();
        NoteContext context = null;
        boolean flat = null == contexts;

        InputStreamReader r = new InputStreamReader(in, "UTF-8");
        BufferedReader br = new BufferedReader(r);
        String line;
        int lineNumber = 0;
        while ((line = br.readLine()) != null) {
            lineNumber++;
            //System.out.println("" + lineNumber + ") " + line);

            String l = line;

            // Tabs count as four spaces.
            l = l.replaceAll("[\\t]", TAB_REPLACEMENT);

            if (0 == l.trim().length()) {
                // In the "flat" format, empty lines are simply ignored
                if (!flat) {
                    context = null;
                    hierarchy.clear();
                }
            } else if (l.startsWith("[")) {
                if (flat) {
                    throw new NoteParsingException(lineNumber, "contexts are not allowed in the 'flat' format");
                } else {
                    int m = l.lastIndexOf("]");
                    if (m < 0) {
                        throw new NoteParsingException(lineNumber, "non-terminated note context");
                    }
                    String text = l.substring(1, m).trim();

                    hierarchy.clear();
                    context = new NoteContext(text);
                    contexts.add(context);
                }
            } else {
                String targetKey = null;
                String linkKey = null;

                // Extract keys
                int k = l.indexOf(" ");
                if (k > 0 && KEY_PREFIX.matcher(l.substring(0, k + 1)).matches()) {
                    int i = l.indexOf(":");
                    int j = l.indexOf(":", i+1);
                    linkKey = l.substring(0, i);
                    targetKey = l.substring(i + 1, j);

                    l = l.substring(k + 1);
                }

                // Find indent level
                int indent = 0;
                if (l.startsWith(" ")) {
                    int i = 0;
                    while (l.charAt(i) == ' ') {
                        i++;
                    }

                    if (0 != i % 4) {
                        throw new NoteParsingException(lineNumber, "notes must be indented by multiples of 4 spaces");
                    }

                    indent = i / 4;

                    if (indent > hierarchy.size()) {
                        throw new NoteParsingException(lineNumber, "note is too deeply indented");
                    }

                    l = l.substring(i).trim();
                }

                while (hierarchy.size() > indent) {
                    hierarchy.removeLast();
                }

                if (0 == indent && null == context && !flat) {
                    context = new NoteContext("");
                    contexts.add(context);
                }

                int j = l.indexOf(" ");
                if (j < 0) {
                    j = l.length();
                }

                String linkValue = l.substring(0, j);
                if (linkValue.length() > MAX_TYPE_LENGTH) {
                    throw new NoteParsingException(lineNumber, "apparent note type is too long: " + linkValue);
                }
                l = l.substring(j);

                String description = "";
                String qualifier = null;
                if (0 < l.length()) {
                    if (l.startsWith(" [")) {
                        int m = l.indexOf("]");
                        if (m < 0) {
                            throw new NoteParsingException(lineNumber, "non-terminated note qualifier");
                        }

                        qualifier = l.substring(2, m - 1);
                        l = l.substring(m + 1);
                    }

                    if (!l.startsWith("  ")) {
                        throw new NoteParsingException(lineNumber, "double space after note type is missing");
                    }
                    // Note: a gap of *more* than two spaces is tolerated, for now.
                    l = l.trim();

                    if (l.contains("{{{")) {
                        int start = lineNumber;
                        boolean inside = false;
                        int index = 0;
                        while (true) {
                            // Check for the closing symbol before the opening symbol
                            int b2 = l.indexOf("}}}", index);
                            if (b2 >= 0) {
                                if (!inside) {
                                    throw new NoteParsingException(start, "unmatched verbatim block terminator" +
                                            " (on line " + lineNumber + ")");
                                }

                                inside = false;
                                index = b2 + 3;
                                continue;
                            }

                            int b1 = l.indexOf("{{{", index);
                            if (b1 >= 0) {
                                if (inside) {
                                    throw new NoteParsingException(start, "nested verbatim blocks (detected on line " +
                                            lineNumber + ") are not allowed");
                                }
                                inside = true;
                                index = b1 + 3;
                                continue;
                            }

                            description += l;

                            if (inside) {
                                description += "\n";
                                l = br.readLine();
                                if (null == l) {
                                    throw new NoteParsingException(start, "non-terminated verbatim block");
                                }
                                lineNumber++;
                                index = 0;
                            } else {
                                break;
                            }
                        }
                    } else {
                        description = l;
                    }
                }

                Note n = new Note(description);
                n.setLinkValue(linkValue);

                if (null != qualifier) {
                    n.setQualifier(qualifier);
                }

                if (null != targetKey) {
                    n.setTargetKey(targetKey);
                }

                if (null != linkKey) {
                    n.setLinkKey(linkKey);
                }

                if (0 < indent) {
                    hierarchy.get(hierarchy.size() - 1).addChild(n);
                } else {
                    if (flat) {
                        notes.add(n);
                    } else {
                        context.addNote(n);
                    }
                }

                hierarchy.add(n);
            }
        }
    }

    public List<Note> flatten(final List<NoteContext> contexts) {
        List<Note> notes = new LinkedList<Note>();
        for (NoteContext c : contexts) {
            Note n = new Note(c.getTargetValue());
            n.setLinkValue(".");
            notes.add(n);

            if (c.getChildren().size() > 0) {
                n.getChildren().addAll(flatten(c.getChildren()));
            }

            n.getChildren().addAll(c.getNotes());
        }

        return notes;
    }

    public class NoteParsingException extends Exception {
        public NoteParsingException(final int lineNumber,
                                    final String message) {
            super("line " + lineNumber + ": " + message);
        }
    }

    private static void printContext(final NoteContext c,
                                     final PrintStream p) throws JSONException {
        if (0 < c.getTargetValue().length()) {
            p.print("[");
            p.print(c.getTargetValue());
            p.print("]");
            p.print("\n");
        }

        for (Note n : c.getNotes()) {
            printNote(n, 0, p);
        }
    }

    private static void printNote(final Note n,
                                  final int indent,
                                  final PrintStream p) throws JSONException {

        if (null != n.getTargetKey() || null != n.getLinkKey()) {
            if (null != n.getLinkKey()) {
                p.print(padKey(n.getLinkKey()));
            }
            p.print(":");
            if (null != n.getTargetKey()) {
                p.print(padKey(n.getTargetKey()));
            }
            p.print(": ");
        }

        for (int i = 0; i < indent; i++) {
            p.print("    ");
        }

        p.print(null == n.getLinkValue() || 0 == n.getLinkValue().length() ? "_" : n.getLinkValue());
        p.print("  ");

        p.print(n.getTargetValue());

        p.print("\n");

        for (Note child : n.getChildren()) {
            printNote(child, indent + 1, p);
        }
    }

    private static String padKey(String id) {
        while (id.length() < 5) {
            id = "0" + id;
        }

        return id;
    }

    public static void main(final String[] args) throws Exception {
        NotesSyntax p = new NotesSyntax();
        List<NoteContext> contexts;

        InputStream in = new FileInputStream("/Users/josh/notes/notes.txt");
        try {
            contexts = p.readContexts(in);
        } finally {
            in.close();
        }

        p.writeContexts(contexts, System.out);
        //p.writeNotes(p.flatten(contexts), Format.JSON, System.out);
    }
}