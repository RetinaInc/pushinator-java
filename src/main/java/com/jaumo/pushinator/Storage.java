package com.jaumo.pushinator;

import com.corundumstudio.socketio.SocketIOClient;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class Storage {

    HashMap<Integer, User> users;
    HashMap<SocketIOClient, Integer> clients;

    private static Storage instance;

    public static Storage getInstance() {
        if (instance == null) {
            instance = new Storage();
        }

        return instance;
    }

    public Storage() {
        users = new HashMap<Integer, User>();
        clients = new HashMap<SocketIOClient, Integer>();
    }

    public User getUser(Integer userId) {
        if (isUserRegistered(userId)) {
            return users.get(userId);
        }

        return null;
    }

    public void addUser(Integer userId, User user) {
        if (!isUserRegistered(userId)) {
            users.put(userId, user);
        }
    }

    public void addClient(Integer userId, SocketIOClient client) {
        if (!clients.containsKey(client)) {
            clients.put(client, userId);
        }
    }

    public void removeClient(SocketIOClient client) {
        if (!clients.containsKey(client)) {
            return;
        }
        final Integer userId = clients.get(client);
        clients.remove(client);
        User user = getUser(userId);
        if (user != null) {
            user.removeClient(client);
            // Allow 10s grace time for reconnect before user is removed
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    removeUserIfEmpty(userId);
                }
            }, 10000);
        }
    }

    public void removeUserIfEmpty(Integer userId) {
        User user = getUser(userId);
        if (user != null) {
            if (!user.hasClients()) {
                users.remove(userId);
            }
        }
    }

    public boolean isUserRegistered(Integer userId) {
        return users.containsKey(userId);
    }
}
