package com.jaumo.pushinator;

import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.corundumstudio.socketio.listener.MultiTypeEventListener;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class Server {

    class StartUpException extends Exception {

    }

    Logger logger;
    SocketIOServer server;
    NettyAdmin adminServer;
    Config config;

    Server() {
        config = new Config();
    }

    public void setCommandLine(String[] args) throws StartUpException {
        final CommandLineParser cmdLineGnuParser = new PosixParser();

        final Options options = Config.buildOptions();
        try {
            CommandLine commandLine = cmdLineGnuParser.parse(options, args);
            if (commandLine.hasOption("help")) {
                printUsage(options);
                throw new StartUpException();
            } else {
                config = new Config(commandLine);
            }
        } catch (ParseException parseException) {
            logger.error("Command line option: " + parseException.getMessage());
            throw new StartUpException();
        }
    }

    public void printUsage(Options options) {
        final PrintWriter writer = new PrintWriter(System.out);
        final HelpFormatter usageFormatter = new HelpFormatter();
        usageFormatter.printUsage(writer, 80, "pushinator", options);
        writer.close();
    }

    private SocketIOServer setUpSocketServer(final Storage storage) throws StartUpException {
        Configuration config = new Configuration();
        config.getSocketConfig().setReuseAddress(true);

        logger.info("Socket Option backlog: " + config.getSocketConfig().getAcceptBackLog());
        logger.info("Socket Option SO_REUSEADDRESS: " + config.getSocketConfig().isReuseAddress());
        logger.info("Socket Option TCP_NODELAY: " + config.getSocketConfig().isTcpNoDelay());
        logger.info("Socket Option SO_LINGER: " + config.getSocketConfig().getSoLinger());
        config.setHostname(this.config.clientAddress);
        config.setPort(this.config.clientPort);
        if (this.config.useSSL) {
            config.setKeyStorePassword(this.config.sslKeyStorePassword);
            try {
                InputStream stream = new FileInputStream(this.config.sslKeyStore);
                config.setKeyStore(stream);
            } catch (FileNotFoundException e) {
                logger.error("Keystore file not found: " + this.config.sslKeyStore);
                throw new StartUpException();
            }
        }

        final SocketIOServer server = new SocketIOServer(config);
        server.addMultiTypeEventListener("auth", new MultiTypeEventListener() {
            @Override
            public void onData(SocketIOClient client, MultiTypeArgs data, AckRequest ackSender) throws Exception {
                Integer userId = data.get(0);
                String hash = data.get(1);
                User user = storage.getUser(userId);
                if (user == null) {
                    logger.debug("Auth user {} with hash {} failed. User not registered.", userId, hash);
                    client.disconnect();
                } else if (!user.isValid(hash)) {
                    logger.debug("Auth user {} with hash {} failed. Hash mismatch.", userId, hash);
                    client.disconnect();
                } else {
                    logger.debug("Auth user {} with hash {} success", userId, hash);
                    user.addClient(client);
                    storage.addClient(userId, client);
                    storage.addUser(userId, user);
                }
            }
        }, Integer.class, String.class);
        server.addDisconnectListener(new DisconnectListener() {
            @Override
            public void onDisconnect(SocketIOClient client) {
                storage.removeClient(client);
            }
        });

        try {
            logger.info("Start client server on " + this.config.clientAddress + ":" + this.config.clientPort);
            server.start();
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new StartUpException();
        }

        return server;
    }

    public void setupAdminServer(final Storage storage) throws StartUpException {
        try {
            logger.info("Start admin server on " + this.config.adminAddress + ":" + this.config.adminPort);
            adminServer = new NettyAdmin(storage, config);
            adminServer.run();
            logger.info("Admin server started");
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new StartUpException();
        }
    }

    public void run() throws InterruptedException, StartUpException {
        try {
            logger = LoggerFactory.getLogger(Server.class);
            final Storage storage = new Storage();
            server = setUpSocketServer(storage);
            setupAdminServer(storage);
            logger.info("Startup finished");
        } catch (StartUpException e) {
            logger.error("Server did not start");
            throw e;
        }
    }

    public void stop() {
        server.stop();
        adminServer.stop();
        logger.info("Shutting down");
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            final Server server = new Server();
            server.setCommandLine(args);
            server.run();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    server.stop();
                }
            });
        } catch (StartUpException e) {
            //
            System.exit(1);
        }
    }
}