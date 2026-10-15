package network;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** One bounded request per connection; no automatic retries of ambiguous writes. */
public record CacheClient(String host, int port) {
    public CacheClient {
        if (host == null || host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("invalid endpoint");
    }

    public String request(String command) throws IOException {
        if (command.length() > LineIO.MAX_LINE || command.contains("\n") || command.contains("\r"))
            throw new IllegalArgumentException("invalid frame");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            socket.setSoTimeout(3000);
            var output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            output.write(command + "\n");
            output.flush();
            String response = LineIO.read(new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)));
            if (response == null) throw new EOFException("missing response");
            return response;
        }
    }
}
