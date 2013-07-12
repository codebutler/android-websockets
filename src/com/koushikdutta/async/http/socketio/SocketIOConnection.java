package com.koushikdutta.async.http.socketio;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;

import org.json.JSONArray;
import org.json.JSONObject;

import android.os.Handler;
import android.text.TextUtils;

import com.codebutler.android_websockets.WebSocketClient;
import com.codebutler.android_websockets.WebSocketClient.Listener;
import com.koushikdutta.http.AsyncHttpClient;
import com.koushikdutta.http.AsyncHttpClient.SocketIORequest;

/**
 * Created by koush on 7/1/13.
 */
class SocketIOConnection {

    private Handler mHandler;
    AsyncHttpClient httpClient;
    int heartbeat;
    ArrayList<SocketIOClient> clients = new ArrayList<SocketIOClient>();
    WebSocketClient webSocketClient;
    SocketIORequest request;

    public SocketIOConnection(Handler handler, AsyncHttpClient httpClient,
            SocketIORequest request) {
        mHandler = handler;
        this.httpClient = httpClient;
        this.request = request;
    }

    public boolean isConnected() {
        return webSocketClient != null && webSocketClient.isConnected();
    }

    Hashtable<String, Acknowledge> acknowledges = new Hashtable<String, Acknowledge>();
    int ackCount;

    public void emitRaw(int type, SocketIOClient client, String message, Acknowledge acknowledge) {
        String ack = "";
        if (acknowledge != null) {
            String id = "" + ackCount++;
            ack = id + "+";
            acknowledges.put(id, acknowledge);
        }
        webSocketClient.send(String.format("%d:%s:%s:%s", type, ack, client.endpoint, message));
    }

    public void connect(SocketIOClient client) {
        clients.add(client);
        webSocketClient.send(String.format("1::%s", client.endpoint));
    }

    public void disconnect(SocketIOClient client) {
        clients.remove(client);

        // see if we can leave this endpoint completely
        boolean needsEndpointDisconnect = true;
        for (SocketIOClient other : clients) {
            // if this is the default endpoint (which disconnects everything),
            // or another client is using this endpoint,
            // we can't disconnect
            if (TextUtils.equals(other.endpoint, client.endpoint)
                    || TextUtils.isEmpty(client.endpoint)) {
                needsEndpointDisconnect = false;
                break;
            }
        }

        if (needsEndpointDisconnect)
            webSocketClient.send(String.format("0::%s", client.endpoint));

        // and see if we can disconnect the socket completely
        if (clients.size() > 0)
            return;

        webSocketClient.disconnect();
        webSocketClient = null;
    }

    void reconnect() {
        if (isConnected()) {
            return;
        }

        // initiate a session
        httpClient.executeString(request, new AsyncHttpClient.StringCallback() {
            @Override
            public void onCompleted(final Exception e, String result) {
                if (e != null) {
                    reportDisconnect(e);
                    return;
                }

                try {
                    String[] parts = result.split(":");
                    String session = parts[0];
                    if (!"".equals(parts[1]))
                        heartbeat = Integer.parseInt(parts[1]) / 2 * 1000;
                    else
                        heartbeat = 0;

                    String transportsLine = parts[3];
                    String[] transports = transportsLine.split(",");
                    HashSet<String> set = new HashSet<String>(Arrays.asList(transports));
                    if (!set.contains("websocket"))
                        throw new Exception("websocket not supported");

                    final String sessionUrl = request.getUri().toString()
                            + "websocket/" + session + "/";
                    
                    SocketIOConnection.this.webSocketClient =  new WebSocketClient(URI.create(sessionUrl), new Listener() {
                        
                        @Override
                        public void onMessage(byte[] data) {
                            //Do nothing
                            
                        }
                        
                        @Override
                        public void onMessage(String message) {
                            try {
                                // Log.d(TAG, "Message: " + message);
                                String[] parts = message.split(":", 4);
                                int code = Integer.parseInt(parts[0]);
                                switch (code) {
                                case 0:
                                    // disconnect
                                    webSocketClient.disconnect();
                                    reportDisconnect(null);
                                    break;
                                case 1:
                                    // connect
                                    reportConnect(parts[2]);
                                    break;
                                case 2:
                                    // heartbeat
                                    webSocketClient.send("2::");
                                    break;
                                case 3: {
                                    // message
                                    reportString(parts[2], parts[3], acknowledge(parts[1]));
                                    break;
                                }
                                case 4: {
                                    // json message
                                    final String dataString = parts[3];
                                    final JSONObject jsonMessage = new JSONObject(dataString);
                                    reportJson(parts[2], jsonMessage, acknowledge(parts[1]));
                                    break;
                                }
                                case 5: {
                                    final String dataString = parts[3];
                                    final JSONObject data = new JSONObject(dataString);
                                    final String event = data.getString("name");
                                    final JSONArray args = data.optJSONArray("args");
                                    reportEvent(parts[2], event, args, acknowledge(parts[1]));
                                    break;
                                }
                                case 6:
                                    // ACK
                                    final String[] ackParts = parts[3].split("\\+", 2);
                                    Acknowledge ack = acknowledges.remove(ackParts[0]);
                                    if (ack == null)
                                        return;
                                    JSONArray arguments = null;
                                    if (ackParts.length == 2)
                                        arguments = new JSONArray(ackParts[1]);
                                    ack.acknowledge(arguments);
                                    break;
                                case 7:
                                    // error
                                    reportError(parts[2], parts[3]);
                                    break;
                                case 8:
                                    // noop
                                    break;
                                default:
                                    throw new Exception("unknown code");
                                }
                            } catch (Exception ex) {
                                webSocketClient.disconnect();
                                webSocketClient = null;
                                reportDisconnect(ex);
                            }
                        
                            
                        }
                        
                        @Override
                        public void onError(Exception error) {
                            reportDisconnect(error);
                        }
                        
                        @Override
                        public void onDisconnect(int code, String reason) {
                            
                            reportDisconnect(new IOException(String.format("Disconnected code %d for reason %s", code, reason)));
                        }
                        
                        @Override
                        public void onConnect() {
                            reconnectDelay = 1000L;
                            setupHeartbeat();
                            
                        }
                    }, null);
                    SocketIOConnection.this.webSocketClient.connect();

                } catch (Exception ex) {
                    reportDisconnect(ex);
                }
            }
        });

    }

    void setupHeartbeat() {
        final WebSocketClient ws = webSocketClient;
        Runnable heartbeatRunner = new Runnable() {
            @Override
            public void run() {
                if (heartbeat <= 0 || ws != webSocketClient || ws == null
                        || !ws.isConnected())
                    return;
                webSocketClient.send("2:::");

                mHandler.postDelayed(this, heartbeat);
            }
        };
        heartbeatRunner.run();
    }

    private interface SelectCallback {
        void onSelect(SocketIOClient client);
    }

    private void select(String endpoint, SelectCallback callback) {
        for (SocketIOClient client : clients) {
            if (endpoint == null || TextUtils.equals(client.endpoint, endpoint)) {
                callback.onSelect(client);
            }
        }
    }

    private void delayReconnect() {
        if (webSocketClient != null || clients.size() == 0)
            return;

        // see if any client has disconnected,
        // and that we need a reconnect
        boolean disconnected = false;
        for (SocketIOClient client : clients) {
            if (client.disconnected) {
                disconnected = true;
                break;
            }
        }

        if (!disconnected)
            return;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                reconnect();
            }
        }, reconnectDelay);
        reconnectDelay *= 2;
    }

    long reconnectDelay = 1000L;

    private void reportDisconnect(final Exception ex) {
        select(null, new SelectCallback() {
            @Override
            public void onSelect(final SocketIOClient client) {
                if (client.connected) {
                    client.disconnected = true;
                    final DisconnectCallback closed = client.getDisconnectCallback();
                    if (closed != null) {
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                closed.onDisconnect(ex);

                            }
                        });

                    }
                } else {
                    // client has never connected, this is a initial connect
                    // failure
                    final ConnectCallback callback = client.connectCallback;
                    if (callback != null) {
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                callback.onConnectCompleted(ex, client);

                            }
                        });

                    }
                }
            }
        });

        delayReconnect();
    }

    private void reportConnect(String endpoint) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                if (client.isConnected())
                    return;
                if (!client.connected) {
                    // normal connect
                    client.connected = true;
                    ConnectCallback callback = client.connectCallback;
                    if (callback != null)
                        callback.onConnectCompleted(null, client);
                } else if (client.disconnected) {
                    // reconnect
                    client.disconnected = false;
                    ReconnectCallback callback = client.reconnectCallback;
                    if (callback != null)
                        callback.onReconnect();
                } else {
                    // double connect?
                    // assert false;
                }
            }
        });
    }

    private void reportJson(String endpoint, final JSONObject jsonMessage, final Acknowledge acknowledge) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                final JSONCallback callback = client.jsonCallback;
                if (callback != null) {
                    mHandler.post(new Runnable() {

                        @Override
                        public void run() {
                            callback.onJSON(jsonMessage, acknowledge);

                        }
                    });
                }

            }
        });
    }

    private void reportString(String endpoint, final String string, final Acknowledge acknowledge) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                final StringCallback callback = client.stringCallback;
                if (callback != null) {
                    mHandler.post(new Runnable() {

                        @Override
                        public void run() {
                            callback.onString(string, acknowledge);

                        }
                    });

                }
            }
        });
    }

    private void reportEvent(String endpoint, final String event, final JSONArray arguments, final Acknowledge acknowledge) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(final SocketIOClient client) {
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        client.onEvent(event, arguments, acknowledge);

                    }
                });

            }
        });
    }

    private void reportError(String endpoint, final String error) {
        select(endpoint, new SelectCallback() {
            @Override
            public void onSelect(SocketIOClient client) {
                final ErrorCallback callback = client.errorCallback;
                if (callback != null) {

                    mHandler.post(new Runnable() {

                        @Override
                        public void run() {
                            callback.onError(error);

                        }
                    });

                }
            }
        });
    }

    private Acknowledge acknowledge(final String messageId) {
        if (TextUtils.isEmpty(messageId))
            return null;

        return new Acknowledge() {
            @Override
            public void acknowledge(JSONArray arguments) {
                String data = "";
                if (arguments != null)
                    data += "+" + arguments.toString();
                webSocketClient.send(String.format("6:::%s%s", messageId, data));
            }
        };
    }

    

}
