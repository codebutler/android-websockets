package com.codebutler.android_websockets;

public class HttpException extends Exception {
    public HttpException() {
        throw new RuntimeException("Runtime Exception");
    }

    public HttpException(String message) {
        throw new RuntimeException(message);
    }

    public HttpException(String message, Throwable cause) {
        throw new RuntimeException("message : " + message);
    }
}
