package dev.martynoff.tagify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallbackServer {
    private int port;
    private String host;
    private HttpServer server;
    private String code;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CallbackServer(String host, int port) {
        this.port = port;
        this.host = host;
        code = null;
        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        server.createContext("/callback", httpExchange -> {
            String requestParamValue = null;
            if ("GET".equals(httpExchange.getRequestMethod())) {
                requestParamValue = handleGetRequest(httpExchange);
            }
            handleResponse(httpExchange, requestParamValue);
        });
        server.setExecutor(executorService);
        server.start();
    }

    private String handleGetRequest(HttpExchange httpExchange) {
        String requestUri = httpExchange.getRequestURI().toString();
        Map<String, String> requestUriMap = new HashMap<>();
        Pattern pattern = Pattern.compile("\\w+=[\\w-]+");
        Matcher matcher = pattern.matcher(requestUri);
        while (matcher.find()) {
            String[] paramVal = matcher.group().split("=");
            requestUriMap.put(paramVal[0], paramVal[1]);
        }
        if (requestUriMap.containsKey("code")) {
            code = requestUriMap.get("code");
            return "OK. You can close this window now.";
        }
        return "Something went wrong";
    }

    private void handleResponse(HttpExchange httpExchange, String requestParamValue) {
        try (DataOutputStream output = new DataOutputStream(httpExchange.getResponseBody())) {
            httpExchange.sendResponseHeaders(200, requestParamValue.length());
            output.write(requestParamValue.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void stop() {
        executorService.shutdown();
        server.stop(0);
    }

    String getCode() {
        return code;
    }

    String getUriString() {
        return "http://" + host + ":" + port + "/callback";
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
