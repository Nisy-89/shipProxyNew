package com.ship.proxy.shipProxy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@SpringBootApplication
@RestController
public class ShipProxyApplication {
	private static final String OFFSHORE_PROXY_HOST = "localhost";
	private static final int OFFSHORE_PROXY_PORT = 9090;
	private static PrintWriter out;
	private static BufferedReader in;
	private static final BlockingQueue<RequestResponsePair> requestQueue = new LinkedBlockingQueue<>();

	public static void main(String[] args) {
		SpringApplication.run(ShipProxyApplication.class, args);
		startProxyThread();
	}

	private static void startProxyThread() {
		new Thread(() -> {
			try {
				Socket socket = new Socket(OFFSHORE_PROXY_HOST, OFFSHORE_PROXY_PORT);
				out = new PrintWriter(socket.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				while (true) {
					RequestResponsePair requestResponsePair = requestQueue.take();
					synchronized (requestResponsePair) {
						out.println(requestResponsePair.request);
						out.flush();

						// Read response from offshore proxy
						StringBuilder response = new StringBuilder();
						String line;
						while ((line = in.readLine()) != null && !line.isEmpty()) {
							response.append(line).append("\n");
						}

						requestResponsePair.response = response.toString();
						requestResponsePair.notify();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
	}

	@RequestMapping("/**")
	public ResponseEntity<String> handleRequest(@RequestBody(required = false) String body,
												@RequestHeader HttpHeaders headers,
												HttpServletRequest request) {
		try {
			String requestUrl = request.getRequestURL().toString();
			if (request.getQueryString() != null) {
				requestUrl += "?" + request.getQueryString();
			}

			String method = request.getMethod();
			String proxyRequest = method + " " + requestUrl + " HTTP/1.1\n" +
					headers.toString() + "\n\n" +
					(body != null ? body : "");

			RequestResponsePair requestResponsePair = new RequestResponsePair(proxyRequest);
			requestQueue.add(requestResponsePair);

			synchronized (requestResponsePair) {
				requestResponsePair.wait();
			}

			return ResponseEntity.ok(requestResponsePair.response);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
		}
	}

	private static class RequestResponsePair {
		String request;
		String response;

		RequestResponsePair(String request) {
			this.request = request;
		}
	}
}