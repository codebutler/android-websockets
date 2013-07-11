package com.koushikdutta.http;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.BasicNameValuePair;

import com.koushikdutta.http.HybiParser.HappyDataInputStream;

import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

public class WebSocketClient {
    private static final String TAG = "WebSocketClient";

    private URI mURI;
    private Listener mListener;
    private Socket mSocket;
    private Thread mThread;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private List<BasicNameValuePair> mExtraHeaders;
    private HybiParser mParser;
    private boolean mConnected;

    private DataCallback mDataCallback;
    private StringCallback mStringCallback;
    private ClosedCallback mClosedCallback;

    private HappyDataInputStream stream;

    private final Object mSendLock = new Object();

    private static TrustManager[] sTrustManagers;

    public static void setTrustManagers(TrustManager[] tm) {
        sTrustManagers = tm;
    }

    public WebSocketClient(URI uri, Listener listener,
            List<BasicNameValuePair> extraHeaders) {
        mURI = uri;
        mListener = listener;
        mExtraHeaders = extraHeaders;
        mConnected = false;
        mParser = new HybiParser(this);

        mHandlerThread = new HandlerThread("websocket-thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    public Listener getListener() {
        return mListener;
    }

    public void connect() {
        if (mThread != null && mThread.isAlive()) {
            return;
        }

        mThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    int port = (mURI.getPort() != -1) ? mURI.getPort()
                            : ((mURI.getScheme().equals("wss") || mURI.getScheme().equals("https")) ? 443
                                    : 80);

                    String path = TextUtils.isEmpty(mURI.getPath()) ? "/"
                            : mURI.getPath();
                    if (!TextUtils.isEmpty(mURI.getQuery())) {
                        path += "?" + mURI.getQuery();
                    }

                    String originScheme = mURI.getScheme().equals("wss") ? "https"
                            : "http";
                    URI origin = new URI(originScheme, "//" + mURI.getHost(), null);

                    SocketFactory factory = (mURI.getScheme().equals("wss") || mURI.getScheme().equals("https")) ? getSSLSocketFactory()
                            : SocketFactory.getDefault();
                    mSocket = factory.createSocket(mURI.getHost(), port);

                    PrintWriter out = new PrintWriter(mSocket.getOutputStream());
                    out.print("GET " + path + " HTTP/1.1\r\n");
                    out.print("Upgrade: websocket\r\n");
                    out.print("Connection: Upgrade\r\n");
                    out.print("Host: " + mURI.getHost() + "\r\n");
                    out.print("Origin: " + origin.toString() + "\r\n");
                    out.print("Sec-WebSocket-Key: " + createSecret() + "\r\n");
                    out.print("Sec-WebSocket-Version: 13\r\n");
                    if (mExtraHeaders != null) {
                        for (NameValuePair pair : mExtraHeaders) {
                            out.print(String.format("%s: %s\r\n", pair.getName(), pair.getValue()));
                        }
                    }
                    out.print("\r\n");
                    out.flush();

                    stream = new HybiParser.HappyDataInputStream(mSocket.getInputStream());

                    // Read HTTP response status line.
                    StatusLine statusLine = parseStatusLine(readLine(stream));
                    if (statusLine == null) {
                        throw new HttpException("Received no reply from server.");
                    } else if (statusLine.getStatusCode() != HttpStatus.SC_SWITCHING_PROTOCOLS) {
                        throw new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase());
                    }

                    // Read HTTP response headers.
                    String line;
                    while (!TextUtils.isEmpty(line = readLine(stream))) {
                        Header header = parseHeader(line);
                        if (header.getName().equals("Sec-WebSocket-Accept")) {
                            // FIXME: Verify the response...
                        }
                    }

                    mListener.onConnect(WebSocketClient.this);

                    mConnected = true;

                } catch (EOFException ex) {
                    Log.d(TAG, "WebSocket EOF!", ex);
                    mListener.onError(ex);
                    mConnected = false;

                } catch (SSLException ex) {
                    // Connection reset by peer
                    Log.d(TAG, "Websocket SSL error!", ex);
                    mListener.onError(ex);
                    mConnected = false;

                } catch (Exception ex) {
                    mListener.onError(ex);
                }
            }
        });
        mThread.start();
    }

    public void startParsing() {
        
        // Now decode websocket frames.
        try {
            mParser.start(stream);
        } catch (IOException e) {
            mClosedCallback.onCompleted(e);
        }
    }

    public void disconnect() {
        if (mSocket != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mSocket != null) {
                        try {
                            mSocket.close();
                        } catch (IOException ex) {
                            Log.d(TAG, "Error while disconnecting", ex);
                            mListener.onError(ex);
                        }
                        mSocket = null;
                    }
                    mConnected = false;
                }
            });
        }
    }

    public void send(String data) {
        sendFrame(mParser.frame(data));
    }

    public void send(byte[] data) {
        sendFrame(mParser.frame(data));
    }

    public boolean isOpen() {
        return mConnected;
    }

    private StatusLine parseStatusLine(String line) {
        if (TextUtils.isEmpty(line)) {
            return null;
        }
        return BasicLineParser.parseStatusLine(line, new BasicLineParser());
    }

    private Header parseHeader(String line) {
        return BasicLineParser.parseHeader(line, new BasicLineParser());
    }

    // Can't use BufferedReader because it buffers past the HTTP data.
    private String readLine(HybiParser.HappyDataInputStream reader) throws IOException {
        int readChar = reader.read();
        if (readChar == -1) {
            return null;
        }
        StringBuilder string = new StringBuilder("");
        while (readChar != '\n') {
            if (readChar != '\r') {
                string.append((char) readChar);
            }

            readChar = reader.read();
            if (readChar == -1) {
                return null;
            }
        }
        return string.toString();
    }

    private String createSecret() {
        byte[] nonce = new byte[16];
        for (int i = 0; i < 16; i++) {
            nonce[i] = (byte) (Math.random() * 256);
        }
        return Base64.encodeToString(nonce, Base64.DEFAULT).trim();
    }

    void sendFrame(final byte[] frame) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (mSendLock) {
                        OutputStream outputStream = mSocket.getOutputStream();
                        outputStream.write(frame);
                        outputStream.flush();
                    }
                } catch (IOException e) {
                    mListener.onError(e);
                }
            }
        });
    }

    public static interface Listener {
        public void onConnect(WebSocketClient webSocket);

        public void onError(Exception ex);

    }

    public static interface DataCallback {
        public void onDataAvailable(byte[] data);
    }

    public static interface StringCallback {
        public void onStringAvailable(String message);
    }

    public static interface ClosedCallback {
        public void onCompleted(Exception ex);
    }

    public DataCallback getDataCallback() {
        return mDataCallback;
    }

    public void setDataCallback(DataCallback dataCallback) {
        this.mDataCallback = dataCallback;
    }

    public StringCallback getStringCallback() {
        return mStringCallback;
    }

    public void setStringCallback(StringCallback stringCallback) {
        this.mStringCallback = stringCallback;
    }

    public ClosedCallback getClosedCallback() {
        return mClosedCallback;
    }

    public void setClosedCallback(ClosedCallback closedCallback) {
        this.mClosedCallback = closedCallback;
    }

    private SSLSocketFactory getSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, sTrustManagers, null);
        return context.getSocketFactory();
    }

    public static void create(URI uri, Listener listener) {
        WebSocketClient webSocket = new WebSocketClient(uri, listener, null);
        webSocket.connect();
    }
}
