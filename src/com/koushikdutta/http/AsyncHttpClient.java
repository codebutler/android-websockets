package com.koushikdutta.http;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;

import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;

import com.codebutler.android_websockets.WebSocketClient;

/**
 * 
 * Created by Vinay S Shenoy on 07/09/2013
 */
public class AsyncHttpClient {

    public AsyncHttpClient() {

    }

    public static class SocketIORequest {

        private String mUri;
        private String mEndpoint;

        public SocketIORequest(String uri) {
            this(uri, null);
        }

        public SocketIORequest(String uri, String endpoint) {

            mUri = Uri.parse(uri).buildUpon().encodedPath("/socket.io/1/").build().toString();
            mEndpoint = endpoint;
        }

        public String getUri() {

            return mUri;
        }

        public String getEndpoint() {

            return mEndpoint;
        }
    }

    public static interface StringCallback {
        public void onCompleted(final Exception e, String result);
    }

    public static interface WebSocketConnectCallback {
        public void onCompleted(Exception ex, WebSocketClient webSocket);
    }

    public void executeString(final SocketIORequest socketIORequest, final StringCallback stringCallback) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                AndroidHttpClient httpClient = AndroidHttpClient.newInstance("android-websockets-2.0");
                HttpPost post = new HttpPost(socketIORequest.getUri());

                try {
                    HttpResponse res = httpClient.execute(post);
                    String responseString = readToEnd(res.getEntity().getContent());

                    if (stringCallback != null) {
                        stringCallback.onCompleted(null, responseString);
                    }

                } catch (IOException e) {

                    if (stringCallback != null) {
                        stringCallback.onCompleted(e, null);
                    }
                } finally {
                    httpClient.close();
                    httpClient = null;
                }
                return null;
            }
        }.execute();
    }

    private byte[] readToEndAsArray(InputStream input) throws IOException {
        DataInputStream dis = new DataInputStream(input);
        byte[] stuff = new byte[1024];
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        int read = 0;
        while ((read = dis.read(stuff)) != -1) {
            buff.write(stuff, 0, read);
        }

        return buff.toByteArray();
    }

    private String readToEnd(InputStream input) throws IOException {
        return new String(readToEndAsArray(input));
    }

}
