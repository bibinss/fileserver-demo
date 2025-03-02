package org.example;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StaticFileServer {

    private static final int PORT = 8080;
    private static final String ROOT_DIR = "/path/to/your/files";

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new FileHandler());
        server.setExecutor(null); // default executor
        System.out.println("Server started on http://localhost:" + PORT);
        server.start();
    }

    static class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();
            Path filePath = Paths.get(ROOT_DIR, requestPath).normalize();

            // Prevent directory traversal
            if (!filePath.startsWith(Paths.get(ROOT_DIR))) {
                sendResponse(exchange, 403, "403 Forbidden");
                return;
            }

            File file = filePath.toFile();

            if (!file.exists()) {
                sendResponse(exchange, 404, "404 Not Found");
                return;
            }

            if (file.isDirectory()) {
                sendDirectoryListing(exchange, file, requestPath);
            } else {
                sendFile(exchange, file);
            }
        }

        private void sendFile(HttpExchange exchange, File file) throws IOException {
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                contentType = URLConnection.guessContentTypeFromName(file.getName());
            }
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, file.length());

            try (OutputStream os = exchange.getResponseBody();
                 FileInputStream fs = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = fs.read(buffer)) != -1) {
                    os.write(buffer, 0, count);
                }
            }
        }

        private void sendDirectoryListing(HttpExchange exchange, File dir, String requestPath) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<html><head><title>Index of ").append(requestPath).append("</title></head><body>");
            html.append("<h1>Index of ").append(requestPath).append("</h1>");
            html.append("<ul>");

            // Parent directory link if not root
            if (!requestPath.equals("/")) {
                String parent = requestPath.endsWith("/") ? requestPath.substring(0, requestPath.length() - 1) : requestPath;
                int lastSlash = parent.lastIndexOf('/');
                String parentPath = (lastSlash > 0) ? parent.substring(0, lastSlash) : "/";
                html.append("<li><a href=\"").append(parentPath).append("\">../</a></li>");
            }

            for (File file : dir.listFiles()) {
                String name = file.getName();
                String link = requestPath.endsWith("/") ? requestPath + name : requestPath + "/" + name;
                if (file.isDirectory()) {
                    link += "/";
                    name += "/";
                }
                html.append("<li><a href=\"").append(link).append("\">").append(name).append("</a></li>");
            }

            html.append("</ul></body></html>");

            byte[] bytes = html.toString().getBytes();
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
            byte[] bytes = responseText.getBytes();
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}

