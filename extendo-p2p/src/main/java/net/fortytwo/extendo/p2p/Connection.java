package net.fortytwo.extendo.p2p;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class Connection {
    protected static final Logger LOGGER = Logger.getLogger(Connection.class.getName());

    private Socket socket;

    private final Map<String, JSONHandler> handlers;

    private final List<BufferedMessage> buffer;

    private boolean stopped = false;

    public Connection() {
        handlers = new HashMap<String, JSONHandler>();
        buffer = new LinkedList<BufferedMessage>();
    }

    /**
     * @return this connection's socket, or null if the connection is not active
     */
    public Socket getSocket() {
        return socket;
    }

    public boolean isActive() {
        return null != socket;
    }

    public synchronized void start(final Socket socket) {
        if (isActive()) {
            throw new IllegalStateException("connection is already active");
        }

        this.socket = socket;

        for (BufferedMessage bm : buffer) {
            try {
                sendInternal(bm.tag, bm.body);
            } catch (JSONException e) {
                LOGGER.warning("error sending buffered message: " + e.getMessage());
                e.printStackTrace(System.err);
            } catch (IOException e) {
                LOGGER.warning("error sending buffered message: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
        buffer.clear();

        stopped = false;

        new Thread(new Runnable() {
            public void run() {
                try {
                    // TODO: recover from IO errors (e.g. due to temporary network issues)
                    handleIncomingMessages();
                } catch (Throwable e) {
                    LOGGER.severe("message handler thread failed with error: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        }).start();
    }

    public synchronized void stop() throws IOException {
        if (!isActive()) {
            throw new IllegalStateException("connection is not active");
        }

        stopped = true;
    }

    public void registerHandler(final String tag,
                                final JSONHandler handler) {
        handlers.put(tag, handler);
    }

    public synchronized void sendNow(final String tag,
                                     final JSONObject body) throws JSONException, IOException {
        if (!isActive()) {
            LOGGER.fine("can't send; connection is closed");
            return;
        }

        sendInternal(tag, body);
    }

    public synchronized void sendBuffered(final String tag,
                                          final JSONObject body) throws IOException, JSONException {
        if (isActive()) {
            sendInternal(tag, body);
        } else {
            BufferedMessage bm = new BufferedMessage();
            bm.tag = tag;
            bm.body = body;
            buffer.add(bm);
        }
    }

    private void sendInternal(final String tag,
                              final JSONObject body) throws JSONException, IOException {
        JSONObject message = new JSONObject();
        message.put(ExtendoAgent.PROP_TAG, tag);
        message.put(ExtendoAgent.PROP_BODY, body);

        String s = message.toString();

        System.out.println("sending message to " + socket.getRemoteSocketAddress() + ": " + s);

        socket.getOutputStream().write(s.getBytes());
    }

    private void close() throws IOException {
        socket.close();

        socket = null;
    }

    private void handleIncomingMessages() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        while (!stopped) {
            String line = br.readLine();

            System.out.println("received message from " + socket.getRemoteSocketAddress() + ": " + line);

            JSONObject message;
            try {
                message = new JSONObject(line);
            } catch (JSONException e) {
                LOGGER.warning("could not parse message as JSON: " + e.getMessage());
                continue;
            }

            String tag;
            try {
                tag = message.getString(ExtendoAgent.PROP_TAG);
            } catch (JSONException e) {
                LOGGER.warning("missing '" + ExtendoAgent.PROP_TAG + "' in JSON message. Discarding");
                continue;
            }

            JSONObject body;
            try {
                body = message.getJSONObject(ExtendoAgent.PROP_BODY);
            } catch (JSONException e) {
                LOGGER.warning("missing '" + ExtendoAgent.PROP_BODY + "' in JSON message. Discarding");
                continue;
            }

            JSONHandler handler = handlers.get(tag);

            if (null == handler) {
                LOGGER.warning("no handler for message with tag '" + tag + "'");
            } else {
                try {
                    handler.handle(body);
                } catch (Throwable t) {
                    // don't allow handler errors to kill this loop/thread
                    LOGGER.severe("JSON message handler failed with error: " + t.getMessage());
                    t.printStackTrace(System.err);
                }
            }
        }

        close();
    }

    private class BufferedMessage {
        public String tag;
        public JSONObject body;
    }

    public interface JSONHandler {
        void handle(JSONObject j);
    }
}