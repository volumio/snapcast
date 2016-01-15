package de.badaix.snapcast.control;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.badaix.snapcast.ClientInfoItem;
import de.badaix.snapcast.TcpClient;

/**
 * Created by johannes on 13.01.16.
 */
public class RemoteControl implements TcpClient.TcpClientListener {

    private static final String TAG = "RC";

    private TcpClient tcpClient;
    private long msgId;
    private RemoteControlListener listener;
    private ServerInfo serverInfo;

    public interface RemoteControlListener {
        void onConnected(RemoteControl remoteControl);
        void onDisconnected(RemoteControl remoteControl);

        void onClientConnected(RemoteControl remoteControl, ClientInfo clientInfo);
        void onClientDisconnected(RemoteControl remoteControl, ClientInfo clientInfo);
        void onClientUpdated(RemoteControl remoteControl, ClientInfo clientInfo);

        void onServerInfo(RemoteControl remoteControl, ServerInfo serverInfo);
    }


    public RemoteControl(RemoteControlListener listener) {
        this.listener = listener;
        serverInfo = new ServerInfo();
        msgId = 0;
    }

    public void connect(final String host, final int port) {
        if ((tcpClient != null) && tcpClient.isConnected())
            return;

        tcpClient = new TcpClient(this);
        tcpClient.start(host, port);
    }

    public void disconnect() {
        if ((tcpClient != null) && (tcpClient.isConnected()))
            tcpClient.stop();
        tcpClient = null;
    }

    public boolean isConnected() {
        return ((tcpClient != null) && tcpClient.isConnected());
    }

    @Override
    public void onMessageReceived(TcpClient tcpClient, String message) {
        Log.d(TAG, "Msg received: " + message);
        try {
            JSONObject json = new JSONObject(message);
            if (json.has("id")) {
                Log.d(TAG, "ID: " + json.getString("id"));
                if ((json.get("result") instanceof JSONObject) && json.getJSONObject("result").has("clients")) {
                    serverInfo.clear();
                    JSONArray clients = json.getJSONObject("result").getJSONArray("clients");
                    for (int i = 0; i < clients.length(); i++) {
                        final ClientInfo clientInfo = new ClientInfo(clients.getJSONObject(i));
                        serverInfo.addClient(clientInfo);
                    }
                    if (listener != null)
                        listener.onServerInfo(this, serverInfo);
                }
            } else {
                String method = json.getString("method");
                Log.d(TAG, "Notification: " + method);
                if (method.contains("Client.On")) {
                    final ClientInfo clientInfo = new ClientInfo(json.getJSONObject("params").getJSONObject("data"));
//                    serverInfo.addClient(clientInfo);
                    if (listener != null) {
                        if (method.equals("Client.OnUpdate"))
                            listener.onClientUpdated(this, clientInfo);
                        else if (method.equals("Client.OnConnect"))
                            listener.onClientConnected(this, clientInfo);
                        else if (method.equals("Client.OnDisconnect"))
                            listener.onClientDisconnected(this, clientInfo);
                    }
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnected(TcpClient tcpClient) {
        Log.d(TAG, "onConnected");
        if (listener != null)
            listener.onConnected(this);
    }

    @Override
    public void onDisconnected(TcpClient tcpClient) {
        Log.d(TAG, "onDisconnected");
        if (listener != null)
            listener.onDisconnected(this);
    }

    private JSONObject jsonRequest(String method, JSONObject params) {
        JSONObject request = new JSONObject();
        try {
            request.put("jsonrpc", "2.0");
            request.put("method", method);
            request.put("id", msgId);
            if (params != null)
                request.put("params", params);
            msgId++;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return request;
    }

    public void getServerStatus() {
        JSONObject request = jsonRequest("System.GetStatus", null);
        tcpClient.sendMessage(request.toString());
    }

    public void setName(ClientInfo clientInfo, String name) {
        try {
            JSONObject request = jsonRequest("Client.SetName", new JSONObject("{\"client\": \"" + clientInfo.getMac() + "\", \"name\": " + name + "}"));
            tcpClient.sendMessage(request.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setVolume(ClientInfo clientInfo, int percent) {
        try {
            JSONObject request = jsonRequest("Client.SetVolume", new JSONObject("{\"client\": \"" + clientInfo.getMac() + "\", \"volume\": " + percent + "}"));
            tcpClient.sendMessage(request.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setMute(ClientInfo clientInfo, boolean mute) {
        try {
            JSONObject request = jsonRequest("Client.SetMute", new JSONObject("{\"client\": \"" + clientInfo.getMac() + "\", \"mute\": " + mute + "}"));
            tcpClient.sendMessage(request.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}