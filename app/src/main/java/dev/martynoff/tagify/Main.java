package dev.martynoff.tagify;

import com.google.common.net.InternetDomainName;

import java.io.*;

public class Main {

    public static final String callbackPortParam = "callbackPort";
    public static final String callbackAddressParam = "callbackAddress";
    public static final String spotifyClientIdParam = "spotifyClientId";
    public static final String configDelimiter = "=";
    public static final File configFile = new File("./config.cfg");

    public static void main(String[] args) {


        try {
            String[][] paramValues = readParamValuePairs(args);
            for (String[] paramValue : paramValues) {
                String param = paramValue[0];
                String value = paramValue[1];
                if (param.startsWith("set")) {
                    String configParam = param.substring(3);
                    configParam = configParam.substring(0, 1).toLowerCase() + configParam.substring(1);
                    boolean isNewValueAssigned = replaceValue(configParam, value);
                    if (isNewValueAssigned) {
                        System.out.println(configParam + " was set to " + value);
                        return;
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Error reading arguments: " + e.getMessage());
            return;
        }

        String[] params = null;
        String spotifyClientId;
        String host;
        int port;
        try {
            params = readConfig();
            spotifyClientId = params[0];
            host = params[1];
            port = Integer.parseInt(params[2]);
        } catch (Exception e) {
            System.out.println("Config file error: " + e.getMessage());
            return;
        }

        System.out.println();
        System.out.println("Starting Tagify with the following parameters:");
        System.out.println("---------------------------------------------");
        System.out.println(spotifyClientIdParam + configDelimiter + spotifyClientId);
        System.out.println(callbackAddressParam + configDelimiter + host);
        System.out.println(callbackPortParam + configDelimiter + port);
        System.out.println("---------------------------------------------");
        System.out.println();

        App app = new App(spotifyClientId, host, port);
        try {
            app.run();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static String[] readConfig() throws IOException {
        String[] params = new String[3];

        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(configFile, true))) {
                    bw.write(spotifyClientIdParam + configDelimiter + "\n");
                    bw.write(callbackAddressParam + configDelimiter + "localhost" + "\n");
                    bw.write(callbackPortParam + configDelimiter + "42069" + "\n");
                }
            } catch (IOException e) {
                throw new IOException("Something went wrong when reading from config file.");
            }
        }

        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;
            boolean[] requiredParams = new boolean[3];
            while ((line = br.readLine()) != null) {
                String value;
                if (line.startsWith(spotifyClientIdParam + configDelimiter)) {
                    value = line.substring(line.indexOf(configDelimiter) + 1);
                    if (!value.matches("[\\da-f]{32}")) {
                        throw new IllegalArgumentException("Invalid Spotify client ID specified: " + value);
                    }
                    requiredParams[0] = true;
                    params[0] = value;
                }
                if (line.startsWith(callbackAddressParam + configDelimiter)) {
                    value = line.substring(line.indexOf(configDelimiter) + 1);
                    if (!InternetDomainName.isValid(value)) {
                        throw new IllegalArgumentException("Invalid callback address specified: " + value);
                    }
                    requiredParams[1] = true;
                    params[1] = value;
                }
                if (line.startsWith(callbackPortParam + configDelimiter)) {
                    value = line.substring(line.indexOf(configDelimiter) + 1);
                    if (!value.matches("\\d+") || Integer.parseInt(value) - 65535 < -65535) {
                        throw new IllegalArgumentException("Invalid callback port specified: " + value);
                    }
                    requiredParams[2] = true;
                    params[2] = value;
                }
            }
            for (int i = 0; i < requiredParams.length; i++) {
                if (!requiredParams[i]) {
                    if (i == 0) {
                        throw new IllegalArgumentException("Spotify client ID is not specified.");
                    }
                    if (i == 1) {
                        throw new IllegalArgumentException("Callback address is not specified");
                    }
                    if (i == 2) {
                        throw new IllegalArgumentException("Callback port is not specified");
                    }
                }
            }
        }
        return params;
    }

    private static boolean replaceValue(String param, String newValue) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {;
            String line;
            StringBuilder sb = new StringBuilder();
            boolean isParamPresent = false;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(param + configDelimiter)) {
                    line = line.replaceAll("(?<==).*", newValue);
                    isParamPresent = true;
                }
                sb.append(line);
                sb.append("\n");
            }
            if (isParamPresent) {
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(configFile))) {
                    bw.write(sb.toString());
                    return true;
                }
            }
        }
        return false;
    }

    private static String[][] readParamValuePairs(String[] args) throws IllegalArgumentException  {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Invalid arguments specified.");
        }
        int size = args.length / 2;
        String[][] paramValues = new String[size][2];
        int counter = 0;
        for (int i = 0; i < args.length; i++) {
            String[] paramValue = new String[2];
            paramValue[0] = args[i];
            paramValue[1] = args[++i];
            paramValues[counter] = paramValue;
            counter++;
        }
        return paramValues;
    }
}
