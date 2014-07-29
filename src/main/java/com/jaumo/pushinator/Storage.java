package com.jaumo.pushinator;

import com.corundumstudio.socketio.SocketIOClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class Storage {

    HashMap<Integer, User> users;
    HashMap<SocketIOClient, Integer> clients;
    Logger logger;

    private static Storage instance;

    public Storage() {
        users = new HashMap<Integer, User>();
        clients = new HashMap<SocketIOClient, Integer>();
        logger = LoggerFactory.getLogger(Storage.class);
    }

    public synchronized User getUser(Integer userId) {
        if (isUserRegistered(userId)) {
            return users.get(userId);
        }

        return null;
    }

    public synchronized void addUser(Integer userId, User user) {
        if (!isUserRegistered(userId)) {
            users.put(userId, user);
        }
    }

    public synchronized void addClient(Integer userId, SocketIOClient client) {
        if (!clients.containsKey(client)) {
            clients.put(client, userId);
        }
    }

    public synchronized void removeClient(SocketIOClient client) {
        if (!clients.containsKey(client)) {
            return;
        }
        final Integer userId = clients.get(client);
        clients.remove(client);
        User user = getUser(userId);
        if (user != null) {
            user.removeClient(client);
            logger.debug("Disconnected user {}", userId);
            // Allow 10s grace time for reconnect before user is removed
            if (!user.hasClients()) {
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        removeUserIfEmpty(userId);
                    }
                }, 10000);
            }
        }
        else {
            logger.warn("User could not be resolved for disconnected client");
        }
    }

    public synchronized void removeUserIfEmpty(Integer userId) {
        User user = getUser(userId);
        if (user != null) {
            if (!user.hasClients()) {
                users.remove(userId);
                logger.debug("Remove user {}, no sessions", userId);
            }
        }
    }

    public boolean isUserRegistered(Integer userId) {
        return users.containsKey(userId);
    }
}
