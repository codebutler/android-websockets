package com.koushikdutta.async.http.socketio;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import android.os.Handler;
import android.text.TextUtils;

import com.koushikdutta.http.AsyncHttpClient;
import com.koushikdutta.http.AsyncHttpClient.SocketIORequest;
import com.koushikdutta.http.WebSocketClient;

public class SocketIOClient extends EventEmitter {
    boolean connected;
    boolean disconnected;

    private SocketIOCallbacks mSocketIOCallbacks;

    private void emitRaw(int type, String message, Acknowledge acknowledge) {
        connection.emitRaw(type, this, message, acknowledge);
    }

    public void emit(String name, JSONArray args) {
        emit(name, args, null);
    }

    public void emit(final String message) {
        emit(message, (Acknowledge) null);
    }

    public void emit(final JSONObject jsonMessage) {
        emit(jsonMessage, null);
    }

    public void emit(String name, JSONArray args, Acknowledge acknowledge) {
        final JSONObject event = new JSONObject();
        try {
            event.put("name", name);
            event.put("args", args);
            emitRaw(5, event.toString(), acknowledge);
        } catch (Exception e) {
        }
    }

    public void emit(final String message, Acknowledge acknowledge) {
        emitRaw(3, message, acknowledge);
    }

    public void emit(final JSONObject jsonMessage, Acknowledge acknowledge) {
        emitRaw(4, jsonMessage.toString(), acknowledge);
    }

    public static void connect(String uri, final ConnectCallback callback, final Handler handler) {
        connect(new SocketIORequest(uri), callback, handler);
    }

    ConnectCallback connectCallback;

    public static void connect(final SocketIORequest request, final ConnectCallback callback, final Handler handler) {

        final SocketIOConnection connection = new SocketIOConnection(handler, new AsyncHttpClient(), request);

        final ConnectCallback wrappedCallback = new ConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, SocketIOClient client) {
                if (ex != null || TextUtils.isEmpty(request.getEndpoint())) {
                    if (callback != null) {
                        client.setupCallbacks();
                        callback.onConnectCompleted(ex, client);
                    }

                    return;
                }

                // remove the root client since that's not actually being used.
                connection.clients.remove(client);

                // connect to the endpoint we want
                client.of(request.getEndpoint(), new ConnectCallback() {
                    @Override
                    public void onConnectCompleted(Exception ex, SocketIOClient client) {
                        if (callback != null) {
                            client.setupCallbacks();
                            callback.onConnectCompleted(ex, client);
                        }
                    }
                });
            }
        };

        connection.clients.add(new SocketIOClient(connection, "", wrappedCallback));
        connection.reconnect();

    }
    
    private void setupCallbacks() {
        setStringCallback(new StringCallback() {

            @Override
            public void onString(String string, Acknowledge acknowledge) {

                if (mSocketIOCallbacks != null) {
                    mSocketIOCallbacks.onMessage(string);
                }
            }
        });

        setJSONCallback(new JSONCallback() {

            @Override
            public void onJSON(JSONObject json, Acknowledge acknowledge) {

                if (mSocketIOCallbacks != null) {
                    mSocketIOCallbacks.onJSON(json);
                }

            }
        });

        setReconnectCallback(new ReconnectCallback() {

            @Override
            public void onReconnect() {

                if (mSocketIOCallbacks != null) {
                    mSocketIOCallbacks.onReconnect();
                }

            }
        });

        setDisconnectCallback(new DisconnectCallback() {

            @Override
            public void onDisconnect(Exception e) {

                if (mSocketIOCallbacks != null) {
                    mSocketIOCallbacks.onDisconnect(0, e.getMessage());
                }

            }
        });

        setErrorCallback(new ErrorCallback() {

            @Override
            public void onError(String error) {
                if (mSocketIOCallbacks != null) {
                    mSocketIOCallbacks.onError(new Exception(error));
                }

            }
        });
    }

    ErrorCallback errorCallback;

    private void setErrorCallback(ErrorCallback callback) {
        errorCallback = callback;
    }

    DisconnectCallback disconnectCallback;

    private void setDisconnectCallback(DisconnectCallback callback) {
        disconnectCallback = callback;
    }

    public DisconnectCallback getDisconnectCallback() {
        return disconnectCallback;
    }

    ReconnectCallback reconnectCallback;

    private void setReconnectCallback(ReconnectCallback callback) {
        reconnectCallback = callback;
    }

    JSONCallback jsonCallback;

    private void setJSONCallback(JSONCallback callback) {
        jsonCallback = callback;
    }

    StringCallback stringCallback;

    private void setStringCallback(StringCallback callback) {
        stringCallback = callback;
    }

    public void setSocketIOCallbacks(SocketIOCallbacks callbacks) {
        mSocketIOCallbacks = callbacks;
    }
    
    public SocketIOCallbacks getSocketIOCallbacks() {
        return mSocketIOCallbacks;
    }
    
    public void listenForEvents(List<String> events) {
        
        if(events == null) {
            return;
        }
        
        EventCallback callback = new EventCallback() {
            
            @Override
            public void onEvent(String event, JSONArray argument, Acknowledge acknowledge) {
                
                if(mSocketIOCallbacks != null) {
                    mSocketIOCallbacks.on(event, argument);
                }
            }
        };
        
        for(String event : events) {
            on(event, callback);
        }
    }

    SocketIOConnection connection;
    String endpoint;

    private SocketIOClient(SocketIOConnection connection, String endpoint,
            ConnectCallback callback) {
        this.endpoint = endpoint;
        this.connection = connection;
        this.connectCallback = callback;
    }

    public boolean isConnected() {
        return connected && !disconnected && connection.isConnected();
    }

    public void disconnect() {
        connection.disconnect(this);
        DisconnectCallback disconnectCallback = this.disconnectCallback;
        if (disconnectCallback != null) {
            disconnectCallback.onDisconnect(null);
        }
    }
    
    public void of(String endpoint, ConnectCallback connectCallback) {
        connection.connect(new SocketIOClient(connection, endpoint, connectCallback));
    }

    public WebSocketClient getWebSocket() {
        return connection.webSocketClient;
    }

    public static interface SocketIOCallbacks {

        public void on(String event, JSONArray arguments);

        public void onDisconnect(int code, String reason);

        public void onReconnect();

        public void onJSON(JSONObject json);

        public void onMessage(String message);

        public void onError(Exception error);
    }
}
