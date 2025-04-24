package com.ship.proxy.shipProxy;

import java.io.*;
import java.net.*;

public class ShipProxyApplication {
	private static final int SHIP_PROXY_PORT = 8080;
	private static final String OFFSHORE_PROXY_HOST = "localhost";
	private static final int OFFSHORE_PROXY_PORT = 9090;

	public static void main(String[] args) {
		System.out.println("Ship Proxy listening on port " + SHIP_PROXY_PORT);
		try (ServerSocket serverSocket = new ServerSocket(SHIP_PROXY_PORT)) {
			while (true) {
				Socket clientSocket = serverSocket.accept();
				new Thread(() -> handleClientConnection(clientSocket)).start();
			}
		} catch (IOException e) {
			System.err.println("Error starting Ship Proxy: " + e.getMessage());
		}
	}

	private static void handleClientConnection(Socket clientSocket) {
		try (
				InputStream clientInput = clientSocket.getInputStream();
				OutputStream clientOutput = clientSocket.getOutputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput))
		) {
			String requestLine = reader.readLine();
			if (requestLine == null || requestLine.isEmpty()) return;

			StringBuilder headers = new StringBuilder();
			headers.append(requestLine).append("\r\n");
			String line;
			while ((line = reader.readLine()) != null && !line.isEmpty()) {
				headers.append(line).append("\r\n");
			}
			headers.append("\r\n");

			System.out.println("Received request:\n" + headers);

			if (requestLine.startsWith("CONNECT")) {
				handleConnect(clientSocket, headers.toString(), clientInput, clientOutput);
			} else {
				handleHttp(clientSocket, headers.toString(), clientInput, clientOutput);
			}

		} catch (IOException e) {
			System.err.println("Error handling client request: " + e.getMessage());
		}
	}

	private static void handleConnect(Socket clientSocket, String requestHeaders, InputStream clientInput, OutputStream clientOutput) throws IOException {
		try (
				Socket offshoreSocket = new Socket(OFFSHORE_PROXY_HOST, OFFSHORE_PROXY_PORT);
				InputStream offshoreIn = offshoreSocket.getInputStream();
				OutputStream offshoreOut = offshoreSocket.getOutputStream()
		) {
			// Send CONNECT to offshore proxy
			offshoreOut.write(requestHeaders.getBytes());
			offshoreOut.flush();

			// Read response from offshore proxy
			BufferedReader offshoreReader = new BufferedReader(new InputStreamReader(offshoreIn));
			StringBuilder responseHeaders = new StringBuilder();
			String line;
			while ((line = offshoreReader.readLine()) != null && !line.isEmpty()) {
				responseHeaders.append(line).append("\r\n");
			}
			responseHeaders.append("\r\n");

			clientOutput.write(responseHeaders.toString().getBytes());
			clientOutput.flush();

			// Start tunnel
			Thread t1 = new Thread(() -> relay(clientInput, offshoreOut));
			Thread t2 = new Thread(() -> relay(offshoreIn, clientOutput));
			t1.start();
			t2.start();
		}
	}

	private static void handleHttp(Socket clientSocket, String requestHeaders, InputStream clientInput, OutputStream clientOutput) throws IOException {
		try (
				Socket offshoreSocket = new Socket(OFFSHORE_PROXY_HOST, OFFSHORE_PROXY_PORT);
				InputStream offshoreIn = offshoreSocket.getInputStream();
				OutputStream offshoreOut = offshoreSocket.getOutputStream()
		) {
			// Forward HTTP request to offshore proxy
			offshoreOut.write(requestHeaders.getBytes());
			offshoreOut.flush();

			// Copy body if content-length or POST body is expected (optional enhancement)

			// Read full response and send back to client
			relay(offshoreIn, clientOutput);
		}
	}

	private static void relay(InputStream in, OutputStream out) {
		try {
			byte[] buffer = new byte[8192];
			int len;
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
				out.flush();
			}
		} catch (IOException e) {
			// silent disconnect
		}
	}
}
