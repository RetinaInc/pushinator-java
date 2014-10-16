package com.jaumo.pushinator;

import com.corundumstudio.socketio.SocketIOClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Storage {

    HashMap<Integer, User> users;
    HashMap<SocketIOClient, Integer> clients;
    Logger logger;
    LinkedList<Integer> staleUsers;
    int staleUserExpireTime;

    public Storage() {
        users = new HashMap<Integer, User>();
        clients = new HashMap<SocketIOClient, Integer>();
        staleUsers = new LinkedList<Integer>();
        logger = LoggerFactory.getLogger(Storage.class);
    }

    public synchronized User getUser(Integer userId) {
        if (isUserRegistered(userId)) {
            return users.get(userId);
        }

        return null;
    }

    public void setStaleUserExpireTime(int staleUserExpireTime) {
        this.staleUserExpireTime = staleUserExpireTime;
    }

    public synchronized void addUser(Integer userId, User user) {
        if (!isUserRegistered(userId)) {
            users.put(userId, user);
            staleUsers.add(userId);
        }
    }

    public synchronized Boolean authClient(Integer userId, String hash, SocketIOClient client) {
        User user = getUser(userId);
        if (user == null) {
            logger.debug("Auth user {} with hash {} failed. User not registered.", userId, hash);
            client.disconnect();
            return false;
        } else if (!user.isValid(hash)) {
            logger.debug("Auth user {} with hash {} failed. Hash mismatch.", userId, hash);
            client.disconnect();
            return false;
        } else {
            logger.debug("Auth user {} with hash {} success", userId, hash);
            addClient(userId, client);
            user.addClient(client);
            staleUsers.remove(userId);
        }

        return true;
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
            if (!user.hasClients()) {
                staleUsers.add(userId);
            }
        }
        else {
            logger.warn("User could not be resolved for disconnected client");
        }
    }

    public Boolean isStale(Integer userId) {
        return staleUsers.contains(userId);
    }

    public void removeStaleUsers() {
        removeStaleUsers(new Date().getTime() - (1000 * staleUserExpireTime));
    }

    public synchronized void removeStaleUsers(long minimumIdleSince) {
        Integer userId;
        int removed = 0;
        try {
            while (true) {
                userId = staleUsers.getFirst();
                if (userId == null) {
                    logger.debug("UserId null");
                    break;
                }

                User u = users.get(userId);
                if (u == null) {
                    logger.debug("User null");
                }
                if (u != null && !u.minimumIdleSince(minimumIdleSince)) {
                    logger.debug("removeStaleUsers: First non-idle user {}", userId);
                    break;
                }
                removed++;
                users.remove(userId);
                staleUsers.removeFirst();
            }
        }
        catch (NoSuchElementException e) {
            // End loop
        }
        logger.info("Removed {} stale users", removed);
    }

    public boolean isUserRegistered(Integer userId) {
        return users.containsKey(userId);
    }
}
