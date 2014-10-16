package com.jaumo.pushinator;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.parser.Packet;

import java.util.ArrayList;
import java.util.Date;

public class User {

    private String hash;
    private ArrayList<SocketIOClient> clients;
    protected Date idleSince;
    protected Date lastCallbackSent;

    public User(String hash) {
        clients = new ArrayList<SocketIOClient>();
        this.hash = hash;
        idleSince = new Date();
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public boolean isValid(String hash) {
        return hash != null && this.hash != null && hash.equals(this.hash);
    }

    public synchronized void addClient(SocketIOClient client) {
        if (!clients.contains(client)) {
            clients.add(client);
        }
        idleSince = null;
    }

    public synchronized void removeClient(SocketIOClient client) {
        if (clients.contains(client)) {
            clients.remove(client);
        }
        if (!hasClients()) {
            idleSince = new Date();
        }
    }

    public synchronized void send(Packet packet) {
        for (SocketIOClient client: clients) {
            client.send(packet);
        }
    }

    public boolean minimumIdleSince(long timestamp) {
        return idleSince != null && idleSince.getTime() <= timestamp;
    }

    public boolean didSendCallbackAfter(long timestamp) {
        return lastCallbackSent != null && lastCallbackSent.getTime() > timestamp;
    }

    public boolean hasClients() {
        return clients.size() > 0;
    }

    public ArrayList<SocketIOClient> getClients() {
        return clients;
    }

    public Date getIdleSince() {
        return idleSince;
    }

    public Date getLastCallbackSent() {
        return lastCallbackSent;
    }

    public void setCallbackSent() {
        lastCallbackSent = new Date();
    }
}
