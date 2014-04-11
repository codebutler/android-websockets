# THIS LIBRARY IS DEPRECATED IN FAVOR OF:

[AndroidAsync](https://github.com/koush/AndroidAsync)








# WebSocket and Socket.IO client for Android

## Credits

The hybi parser is based on code from the [faye project](https://github.com/faye/faye-websocket-node). Faye is Copyright (c) 2009-2012 James Coglan. Many thanks for the great open-source library!

The hybi parser was ported from JavaScript to Java by [Eric Butler](https://twitter.com/codebutler) <eric@codebutler.com>.

The WebSocket client was written by [Eric Butler](https://twitter.com/codebutler) <eric@codebutler.com>.

The Socket.IO client was written by [Koushik Dutta](https://twitter.com/koush).

The Socket.IO client component was ported from Koushik Dutta's AndroidAsync(https://github.com/koush/AndroidAsync) by [Vinay S Shenoy](https://twitter.com/vinaysshenoy)

## WebSocket Usage

```java
List<BasicNameValuePair> extraHeaders = Arrays.asList(
    new BasicNameValuePair("Cookie", "session=abcd")
);

WebSocketClient client = new WebSocketClient(URI.create("wss://irccloud.com"), new WebSocketClient.Listener() {
    @Override
    public void onConnect() {
        Log.d(TAG, "Connected!");
    }

    @Override
    public void onMessage(String message) {
        Log.d(TAG, String.format("Got string message! %s", message));
    }

    @Override
    public void onMessage(byte[] data) {
        Log.d(TAG, String.format("Got binary message! %s", toHexString(data)));
    }

    @Override
    public void onDisconnect(int code, String reason) {
        Log.d(TAG, String.format("Disconnected! Code: %d Reason: %s", code, reason));
    }

    @Override
    public void onError(Exception error) {
        Log.e(TAG, "Error!", error);
    }

}, extraHeaders);

client.connect();

// Later… 
client.send("hello!");
client.send(new byte[] { 0xDE, 0xAD, 0xBE, 0xEF });
client.disconnect();
```

## Socket.IO Usage

```java
SocketIOClient.connect("http://localhost:80", new ConnectCallback() {

    @Override
    public void onConnectCompleted(Exception ex, SocketIOClient client) {
        
        if (ex != null) {
            return;
        }

        //Save the returned SocketIOClient instance into a variable so you can disconnect it later
        client.setDisconnectCallback(MainActivity.this);
        client.setErrorCallback(MainActivity.this);
        client.setJSONCallback(MainActivity.this);
        client.setStringCallback(MainActivity.this);
        
        //You need to explicitly specify which events you are interested in receiving
        client.addListener("news", MainActivity.this);

        client.of("/chat", new ConnectCallback() {
        
            @Override
            public void onConnectCompleted(Exception ex, SocketIOClient client) {
                
                if (ex != null) {
                    ex.printStackTrace();
                    return;
                }

                //This client instance will be using the same websocket as the original client, 
                //but will point to the indicated endpoint
                client.setDisconnectCallback(MainActivity.this);
                client.setErrorCallback(MainActivity.this);
                client.setJSONCallback(MainActivity.this);
                client.setStringCallback(MainActivity.this);
                client.addListener("a message", MainActivity.this);

            }
        });

    }
}, new Handler());

        
@Override
public void onEvent(String event, JSONArray argument, Acknowledge acknowledge) {
    try {
        Log.d("MainActivity", "Event:" + event + "Arguments:"
                + argument.toString(2));
    } catch (JSONException e) {
        e.printStackTrace();
    }

}

@Override
public void onString(String string, Acknowledge acknowledge) {
    Log.d("MainActivity", string);

}

@Override
public void onJSON(JSONObject json, Acknowledge acknowledge) {
    try {
        Log.d("MainActivity", "json:" + json.toString(2));
    } catch (JSONException e) {
        e.printStackTrace();
    }

}

@Override
public void onError(String error) {
    Log.d("MainActivity", error);

}

@Override
public void onDisconnect(Exception e) {
    Log.d(mComponentTag, "Disconnected:" + e.getMessage());

}

```



## TODO

* Run [autobahn tests](http://autobahn.ws/testsuite)
* Investigate using [naga](http://code.google.com/p/naga/) instead of threads.

## License

(The MIT License)
	
	Copyright (c) 2009-2012 James Coglan
	Copyright (c) 2012 Eric Butler 
	Copyright (c) 2012 Koushik Dutta 
	
	Permission is hereby granted, free of charge, to any person obtaining a copy of
	this software and associated documentation files (the 'Software'), to deal in
	the Software without restriction, including without limitation the rights to use,
	copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
	Software, and to permit persons to whom the Software is furnished to do so,
	subject to the following conditions:
	
	The above copyright notice and this permission notice shall be included in all
	copies or substantial portions of the Software.
	
	THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
	FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
	COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
	IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
	CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
	 
