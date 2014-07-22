package com.jaumo.pushinator;

import org.apache.commons.cli.*;

public class Config {

    String clientAddress = "localhost";
    Integer clientPort = 9601;
    String adminAddress = "localhost";
    Integer adminPort = 9600;
    boolean useSSL = false;
    String sslKeyStore = "";
    String sslKeyStorePassword = "";

    Config() {
    }

    Config(CommandLine commandLine) {
        if (commandLine.hasOption("adminAddress")) {
            adminAddress = commandLine.getOptionValue("adminAddress");
        }
        if (commandLine.hasOption("adminPort")) {
            adminPort = Integer.valueOf(commandLine.getOptionValue("adminPort"));
        }
        if (commandLine.hasOption("clientAddress")) {
            clientAddress = commandLine.getOptionValue("clientAddress");
        }
        if (commandLine.hasOption("clientPort")) {
            clientPort = Integer.valueOf(commandLine.getOptionValue("clientPort"));
        }
        if (commandLine.hasOption("ssl")) {
            useSSL = true;
            if (!commandLine.hasOption("sslKeyStore")) {
                throw new IllegalArgumentException("sslKeyStore option missing");
            }
            if (!commandLine.hasOption("sslKeyStorePassword")) {
                throw new IllegalArgumentException("sslKeyStorePassword option missing");
            }

            sslKeyStore = commandLine.getOptionValue("sslKeyStore");
            sslKeyStorePassword = commandLine.getOptionValue("sslKeyStorePassword");
        }
    }

    public static Options buildOptions() {
        final Options gnuOptions = new Options();
        gnuOptions.addOption("clientPort", true, "Client port")
                .addOption("clientAddress", true, "Client address")
                .addOption("adminPort", true, "Admin port")
                .addOption("adminAddress", true, "Admin address")
                .addOption("ssl", false, "Enable SSL")
                .addOption("sslKeyStore", true, "Path to keystore file")
                .addOption("sslKeyStorePassword", true, "Keystore password")
                .addOption("help", false, "Display help");
        return gnuOptions;
    }
}
