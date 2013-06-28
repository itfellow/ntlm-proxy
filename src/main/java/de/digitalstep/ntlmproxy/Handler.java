package de.digitalstep.ntlmproxy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

class Handler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Handler.class);

    private static final List<String> stripHeadersIn = Arrays.asList("Content-Type", "Content-Length", "Proxy-Connection");
    private static final List<String> stripHeadersOut = Arrays.asList("Proxy-Authentication", "Proxy-Authorization");

    private final Socket socket;

    public Handler(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        HttpParser parser = null;
        try {
            parser = new HttpParser(socket.getInputStream());
            try {
                while (!parser.parse())
                    ;
            } catch (IOException e) {
                log.warn(e.getMessage(), e);
                return;
            }
            URI uri = NtlmProxy.enableSystemProxy(parser.getUri());
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod(parser.getMethod());
            connection.setInstanceFollowRedirects(false);
            for (NameValuePair header : parser.getHeaders()) {
                if (!stripHeadersIn.contains(header.getName())) {
                    connection.addRequestProperty(header.getName(), header.getValue());
                }
            }

            socket.shutdownInput();

            final OutputStream out = socket.getOutputStream();
            for (int index = 0; index < 1; index++) {
                NameValuePair header = new NameValuePair(connection.getHeaderFieldKey(index), connection.getHeaderField(index));
                if (!stripHeadersOut.contains(header.getName())) {
                    out.write((header.toString() + "\r\n").getBytes());
                    log.debug("Wrote header {}", header);
                }
            }
            out.write("Gunnar: Test\r\n".getBytes());

            final InputStream in = connection.getInputStream();
            out.write("\r\n".getBytes());

            byte[] bytes = ByteStreams.toByteArray(in);
            ByteStreams.copy(new ByteArrayInputStream(bytes), out);
            // out.write("\r\n".getBytes());
            parser.close();
            in.close();
            out.close();
            log.debug("Output closed");
            connection.disconnect();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}