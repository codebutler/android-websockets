package com.codebutler.android_websockets;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.http.AndroidHttpClient;
import android.os.Looper;
import android.util.Log;

public class SocketIOClient {
    public static interface Handler {
        public void onConnect();

        public void on(String event, JSONArray arguments);

        public void onDisconnect(int code, String reason);

        public void onJSON(JSONObject json);

        public void onMessage(String message);

        public void onError(Exception error);
    }

    private static final String TAG = "SocketIOClient";
    
    String mURL;
    Handler mHandler;
    String mSession;
    int mHeartbeat;
    WebSocketClient mClient;

    public SocketIOClient(URI uri, Handler handler) {
        // remove trailing "/" from URI, in case user provided e.g. http://test.com/
        mURL = uri.toString().replaceAll("/$", "") + "/socket.io/1/";
        mHandler = handler;
    }

    private static String downloadUriAsString(final HttpUriRequest req) throws IOException {
        AndroidHttpClient client = AndroidHttpClient.newInstance("android-websockets");
        try {
            HttpResponse res = client.execute(req);
            return readToEnd(res.getEntity().getContent());
        }
        finally {
            client.close();
        }
    }

    private static byte[] readToEndAsArray(InputStream input) throws IOException {
        DataInputStream dis = new DataInputStream(input);
        byte[] stuff = new byte[1024];
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        int read = 0;
        while ((read = dis.read(stuff)) != -1) {
            buff.write(stuff, 0, read);
        }

        return buff.toByteArray();
    }

    private static String readToEnd(InputStream input) throws IOException {
        return new String(readToEndAsArray(input));
    }

    android.os.Handler mSendHandler;
    Looper mSendLooper;

    public void emit(String name, JSONArray args) throws JSONException {
        final JSONObject event = new JSONObject();
        event.put("name", name);
        event.put("args", args);
        Log.d(TAG, "Emitting event: " + event.toString());
        mSendHandler.post(new Runnable() {
            @Override
            public void run() {
                mClient.send(String.format("5:::%s", event.toString()));
            }
        });
    }
    
    public void emit(final String message) {
        mSendHandler.post(new Runnable() {
            
            @Override
            public void run() {
                mClient.send(String.format("3:::%s", message));
            }
        });
    }
    
    public void emit(final JSONObject jsonMessage) {
        
        mSendHandler.post(new Runnable() {
            
            @Override
            public void run() {
                mClient.send(String.format("4:::%s", jsonMessage.toString()));
            }
        });
    }

    private void connectSession() throws URISyntaxException {
        mClient = new WebSocketClient(new URI(mURL + "websocket/" + mSession), new WebSocketClient.Listener() {
            @Override
            public void onMessage(byte[] data) {
                cleanup();
                mHandler.onError(new Exception("Unexpected binary data"));
            }

            @Override
            public void onMessage(String message) {
                try {
                    Log.d(TAG, "Message: " + message);
                    String[] parts = message.split(":", 4);
                    int code = Integer.parseInt(parts[0]);
                    switch (code) {
                    case 1:
                        // connect
                        mHandler.onConnect();
                        break;
                    case 2:
                        // heartbeat
                        mClient.send("2::");
                        break;
                    case 3: {
                        // message
                        final String messageId = parts[1];
                        final String dataString = parts[3];
                        
                        if(!"".equals(messageId)) {
                            mSendHandler.post(new Runnable() {
                                
                                @Override
                                public void run() {
                                    mClient.send(String.format("6:::%s", messageId));
                                }
                            });
                        }
                        mHandler.onMessage(dataString);
                        break;
                    }
                    case 4: {
                        //json message
                        final String messageId = parts[1];
                        final String dataString = parts[3];
                        
                        JSONObject jsonMessage = null;
                        
                        try {
                            jsonMessage = new JSONObject(dataString);
                        } catch(JSONException e) {
                            jsonMessage = new JSONObject();
                        }
                        if(!"".equals(messageId)) {
                            mSendHandler.post(new Runnable() {
                                
                                @Override
                                public void run() {
                                    mClient.send(String.format("6:::%s", messageId));
                                }
                            });
                        }
                        mHandler.onJSON(jsonMessage);
                        break;
                    }
                    case 5: {
                        final String messageId = parts[1];
                        final String dataString = parts[3];
                        JSONObject data = new JSONObject(dataString);
                        String event = data.getString("name");
                        JSONArray args;
                        try {
                            args = data.getJSONArray("args");
                        } catch (JSONException e) {
                            args = new JSONArray();
                        }
                        if (!"".equals(messageId)) {
                            mSendHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mClient.send(String.format("6:::%s", messageId));
                                }
                            });
                        }
                        mHandler.on(event, args);
                        break;
                    }
                    case 6:
                        // ACK
                        break;
                    case 7:
                        // error
                        throw new Exception(message);
                    case 8:
                        // noop
                        break;
                    default:
                        throw new Exception("unknown code");
                    }
                }
                catch (Exception ex) {
                    cleanup();
                    onError(ex);
                }
            }

            @Override
            public void onError(Exception error) {
                cleanup();
                mHandler.onError(error);
            }

            @Override
            public void onDisconnect(int code, String reason) {
                cleanup();
                // attempt reconnect with same session?
                mHandler.onDisconnect(code, reason);
            }

            @Override
            public void onConnect() {
                mSendHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mSendHandler.postDelayed(this, mHeartbeat);
                        mClient.send("2:::");
                    }
                }, mHeartbeat);
            }
        }, null);
        mClient.connect();
    }

    public void disconnect() throws IOException {
        cleanup();
    }

    private void cleanup() {
        mClient.disconnect();
        mClient = null;
       
        mSendLooper.quit();
        mSendLooper = null;
        mSendHandler = null;
    }

    public void connect() {
        if (mClient != null)
            return;
        new Thread() {
            public void run() {
                HttpPost post = new HttpPost(mURL);
                try {
                    String line = downloadUriAsString(post);
                    String[] parts = line.split(":");
                    mSession = parts[0];
                    String heartbeat = parts[1];
                    if (!"".equals(heartbeat))
                        mHeartbeat = Integer.parseInt(heartbeat) / 2 * 1000;
                    String transportsLine = parts[3];
                    String[] transports = transportsLine.split(",");
                    HashSet<String> set = new HashSet<String>(Arrays.asList(transports));
                    if (!set.contains("websocket"))
                        throw new Exception("websocket not supported");

                    Looper.prepare();
                    mSendLooper = Looper.myLooper();
                    mSendHandler = new android.os.Handler();

                    connectSession();

                    Looper.loop();
                }
                catch (Exception e) {
                    mHandler.onError(e);
                }
            };
        }.start();
    }
}

