import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.parser.Packet;
import com.jaumo.pushinator.Storage;
import com.jaumo.pushinator.User;
import org.junit.Test;

import java.net.SocketAddress;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

public class TestStorage {

    @Test
    public void testAddRemoveUser() {
        Storage s = new Storage();
        User u1 = new User("asd");
        User u2 = new User("sdf");
        s.addUser(1, u1);
        s.addUser(2, u2);
        assertTrue(s.isUserRegistered(2));
        assertFalse(s.isUserRegistered(3));
        assertNull(s.getUser(3));
        assertEquals(u1, s.getUser(1));
    }

    private SocketIOClient getClient() {
        return new SocketIOClient() {
            @Override
            public HandshakeData getHandshakeData() {
                return null;
            }

            @Override
            public Transport getTransport() {
                return null;
            }

            @Override
            public void sendEvent(String s, AckCallback<?> ackCallback, Object... objects) {

            }

            @Override
            public void send(Packet packet, AckCallback<?> ackCallback) {

            }

            @Override
            public void sendJsonObject(Object o, AckCallback<?> ackCallback) {

            }

            @Override
            public void sendMessage(String s, AckCallback<?> ackCallback) {

            }

            @Override
            public SocketIONamespace getNamespace() {
                return null;
            }

            @Override
            public UUID getSessionId() {
                return null;
            }

            @Override
            public SocketAddress getRemoteAddress() {
                return null;
            }

            @Override
            public boolean isChannelOpen() {
                return false;
            }

            @Override
            public void joinRoom(String s) {

            }

            @Override
            public void leaveRoom(String s) {

            }

            @Override
            public Set<String> getAllRooms() {
                return null;
            }

            @Override
            public void sendMessage(String s) {

            }

            @Override
            public void sendJsonObject(Object o) {

            }

            @Override
            public void send(Packet packet) {

            }

            @Override
            public void disconnect() {

            }

            @Override
            public void sendEvent(String s, Object... objects) {

            }

            @Override
            public void set(String s, String s2) {

            }

            @Override
            public String get(String s) {
                return null;
            }

            @Override
            public boolean has(String s) {
                return false;
            }

            @Override
            public void del(String s) {

            }
        };
    }

    @Test
    public void testConnectAndDisconnect() {
        Storage s = new Storage();
        User u1 = new User("asd");
        User u2 = new User("sdf");
        s.addUser(1, u1);
        s.addUser(2, u2);

        // Create user, no connection yet
        assertEquals(u1.getIdleSince().getTime(), new Date().getTime(), 10);
        assertNull(u1.getLastCallbackSent());
        assertTrue(u1.minimumIdleSince(new Date().getTime()));
        assertTrue(s.isStale(1));
        assertTrue(s.isStale(2));

        // Connect client
        SocketIOClient client1 = getClient();
        assertTrue(s.authClient(1, "asd", client1));

        assertTrue(u1.hasClients());
        assertNull(u1.getIdleSince());
        assertFalse(u1.minimumIdleSince(new Date().getTime()));
        assertEquals(1, u1.getClients().size());
        assertFalse(s.isStale(1));

        // Test callback
        assertNull(u1.getLastCallbackSent());
        long callbackTime = new Date().getTime();
        u1.setCallbackSent();
        assertEquals(u1.getLastCallbackSent().getTime(), callbackTime, 1);

        // Disconnect client
        long disconnectTime = new Date().getTime();
        s.removeClient(client1);
        assertEquals(u1.getLastCallbackSent().getTime(), callbackTime, 1);
        assertEquals(u1.getIdleSince().getTime(), disconnectTime, 10);
        assertTrue(u1.minimumIdleSince(new Date().getTime()));

        assertEquals(0, u1.getClients().size());
        assertFalse(u1.hasClients());
        assertTrue(s.isStale(1));

        // Auth fail
        assertFalse(s.authClient(1, "asdff", client1));
        assertFalse(s.authClient(3, "", client1));
        assertFalse(u2.hasClients());
    }

    @Test
    public void testRemoveStaleUsers() throws InterruptedException {
        Storage s = new Storage();
        User u1 = new User("asd");
        User u2 = new User("asd");
        User u3 = new User("asd");
        User u4 = new User("asd");
        s.addUser(1, u1);
        s.addUser(2, u2);
        s.addUser(3, u3);
        s.addUser(4, u4);

        // Connect client
        long connectTime = new Date().getTime();
        SocketIOClient client1 = getClient();
        assertTrue(s.authClient(1, "asd", client1));
        SocketIOClient client2 = getClient();
        assertTrue(s.authClient(2, "asd", client2));
        SocketIOClient client3 = getClient();
        assertTrue(s.authClient(3, "asd", client3));
        SocketIOClient client4 = getClient();
        assertTrue(s.authClient(4, "asd", client4));

        // Disconnect client
        long disconnectTime1 = new Date().getTime();
        s.removeClient(client1);
        s.removeClient(client3);

        Thread.sleep(500);
        long disconnectTime2 = new Date().getTime();
        s.removeClient(client2);
        s.removeClient(client4);

        // Add extra 10ms to be sure
        s.removeStaleUsers(disconnectTime1 + 10);

        assertFalse(s.isUserRegistered(1));
        assertTrue(s.isUserRegistered(2));
        assertFalse(s.isUserRegistered(3));
        assertTrue(s.isUserRegistered(4));

        assertFalse(s.isStale(1));
        assertTrue(s.isStale(2));
        assertFalse(s.isStale(3));
        assertTrue(s.isStale(4));

        // Add extra 10ms to be sure
        s.removeStaleUsers(disconnectTime2 + 10);

        assertFalse(s.isUserRegistered(1));
        assertFalse(s.isUserRegistered(2));
        assertFalse(s.isUserRegistered(3));
        assertFalse(s.isUserRegistered(4));

        assertFalse(s.isStale(1));
        assertFalse(s.isStale(2));
        assertFalse(s.isStale(3));
        assertFalse(s.isStale(4));
    }

}
