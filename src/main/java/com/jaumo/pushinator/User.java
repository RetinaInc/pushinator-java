package com.jaumo.pushinator;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.parser.Packet;

import java.util.ArrayList;

public class User {

    private String hash;
    private ArrayList<SocketIOClient> clients;

    public User(String hash) {
        clients = new ArrayList<SocketIOClient>();
        this.hash = hash;
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
    }

    public synchronized void removeClient(SocketIOClient client) {
        if (clients.contains(client)) {
            clients.remove(client);
        }
    }

    public synchronized void send(Packet packet) {
        for (SocketIOClient client: clients) {
            client.send(packet);
        }
    }

    public boolean hasClients() {
        return clients.size() > 0;
    }
}
