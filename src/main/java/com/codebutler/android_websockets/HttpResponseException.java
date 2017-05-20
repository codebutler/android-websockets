package com.codebutler.android_websockets;

public class HttpResponseException  extends Exception{
    private int statusCode;
    public HttpResponseException(int statusCode, String s) {
        this.statusCode = statusCode;
        throw new RuntimeException("status code : " + statusCode + ", " + s);
    }

    public int getStatusCode() {
        return statusCode;
    }
}
