package com.project4;

//import java.net.*;
import java.io.*;
import java.util.*;

import javafx.application.Platform;
import javafx.scene.control.ListView;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;

/**
 * The server; controls client connections
 */
public class Server {

	private static ServerSocket serverSocket; //The server's socket
	private static boolean running = true; //boolean representing if the server is running or not

	/**
	 * Constructor for Server
	 * 
	 * @param serverSocket The server's socket
	 * @param serverListView ListView for server logs
	 */
	public Server(ServerSocket serverSocket, ListView<String> serverListView) {
		Server.serverSocket = serverSocket;
	}

	/**
	 * Starts the server socket and listens for incoming connections.
	 * 
	 * @param userListView The list of users connected; for display on GUI
	 * @param logListView List of server logs
	 * @throws IOException
	 */
	public void startServerSocket(ListView<String> userListView, ListView<String> logListView) throws IOException {
		Platform.runLater(() -> logListView.getItems().add("Server running on port: " + serverSocket.getLocalPort()));
		try {
			while (running && !serverSocket.isClosed()) {
				Socket clientSocket = serverSocket.accept(); //accept connection to server socket
				ClientManager client = new ClientManager(clientSocket, logListView, userListView); //init client manager
				Thread newThread = new Thread(client);
				newThread.start(); //start threaded client manager to allow multiple clients to run concurrently
			}
		} catch (IOException e) {
			Platform.runLater(() -> logListView.getItems().add("Server stopped."));
			throw e;
		}
	}

	/**
	 * Stops the server
	 */
	public static void stop() {
		running = false;
		try {
			serverSocket.close(); //close server socket
		} catch (IOException e) { //or print error if socket cannot be closed
			System.out.println("Error closing server socket: " + e.getMessage());
		}
	}

}

/**
 * ClientManager class to manage client connections
 */
class ClientManager implements Runnable {

	public static ArrayList<ClientManager> clients = new ArrayList<>(); //list of client managers
	public static ArrayList<String> usernames = new ArrayList<>(); //list of client usernames
	private Socket clientSocket; //the current client's socket 
	private BufferedReader bufRead; //allows for reading messages between client and server
	private BufferedWriter bufWrite; //allows for writing messages between client and server
	private String username; //a username
	private InteractController interactController; //An interact controller for communication with GUI
	public volatile ListView<String> logListView; //Server logs as a list to display
	public volatile ListView<String> userListView; //users as a list to display
	public volatile OutputStream out; //An output stream

	/**
	 * Represents a client manager that handles communication with a client.
	 * 
	 * @param clientSocket The socket associated with the client.
	 * @param logListView  The ListView to display log messages.
	 * @param userListView The ListView to display connected users.
	 */
	public ClientManager(Socket clientSocket, ListView<String> logListView, ListView<String> userListView) {
		try {
			this.clientSocket = clientSocket;
			out = clientSocket.getOutputStream(); //the client output stream
			bufRead = new BufferedReader(new InputStreamReader(
					clientSocket.getInputStream())); //buffered reader using client input strean
			bufWrite = new BufferedWriter(new OutputStreamWriter(
					out)); //buffered writer using client output strean
			this.logListView = logListView; 
			this.userListView = userListView;
			while (true) {
				username = bufRead.readLine(); //read username

				// ensures client connecting has a unique username
				if (usernames.contains(username)) {
					bufWrite.write("##USERNAMETAKEN");
					bufWrite.newLine();
					bufWrite.flush(); //communicates that username taken
				} else {
					bufWrite.write("##USERNAMEOK");
					bufWrite.newLine();
					bufWrite.flush();
					System.out.println(username + " connected"); //communicates that username is OK

					Platform.runLater(() -> {
						userListView.getItems().add(username);
						logListView.getItems().add(username + " connected");
					});
					for (String username_ : usernames) {
						bufWrite.write("##ONLINEUSER" + username_);
						bufWrite.newLine();
						bufWrite.flush(); //write currently online users to client
					}

					clients.add(this);
					usernames.add(username); //add client details to list

					for (ClientManager client_ : clients) {
						if (!client_.username.equals(username)) {
							client_.bufWrite.write("##CLIENTJOIN" + username);
							client_.bufWrite.newLine();
							client_.bufWrite.flush(); //convey client joins
						}
					}

					break;

				}
			}

		} catch (Exception e) {
			System.out.println("Error initialising client");
		}
	}

	/**
	 * Sets the interact controller
	 * 
	 * @param interactController
	 */
	public void setInteractController(InteractController interactController) {
		this.interactController = interactController;
	}

	/**
	 * Run method for the client manager
	 */
	@Override
	public void run() {
		String msg;
		try {
			// Continuously read messages from the client
			while (!clientSocket.isClosed()) {
				msg = bufRead.readLine();
				// If the client disconnects, close all streams and break out of the loop
				if (msg != null && msg.equals("##DISCONNECT")) {
					closeAllStreamsBroadcast();
					break;
					// Handle whisper messages
				} else if (msg != null && msg.startsWith("##WHISPER")) {
					handleWhisperMessage(msg);
					// Handle call messages
				} else if (msg != null && msg.startsWith("##CALLING")) {
					handleCalling(msg);
					// Handle call accept messages
				} else if (msg != null && msg.startsWith("##ACCEPTED")) {
					handleAccept(msg);
					// Handle call decline messages
				} else if (msg != null && msg.startsWith("##DECLINED")) {
					handleDecline(msg);
					// Handle voice note messages
				} else if (msg != null && msg.startsWith("##VOICENOTE")) {
					handleVoiceNoteServer(msg);
					// Handle end call messages
				} else if (msg != null && msg.startsWith("##ENDCALL")) {
					handleEndCall(msg);
					// Handle unavailable messages
				} else if (msg != null && msg.startsWith("##UNAVAILABLE")) {
					handleUnavailable(msg);
					// Broadcast messages to all clients
				} else if (msg != null) {
					broadcastMessage(msg);
				}
			}
		} catch (Exception e) {
			closeAllStreamsBroadcast();
		}
	}

	/**
	 * Handles whisper messages
	 * 
	 * @param msg
	 */
	private void handleWhisperMessage(String msg) {
		try {
			String[] parts = msg.split(",", 3);

			String targetUsername = parts[1];
			String whisperMsg = parts[2]; //message broken up for further processing
			ClientManager targetClient = findClientByUsername(targetUsername);

			// Sends message back to whisperer to print to output
			bufWrite.write("##WHISPERTO," + targetUsername + "," + whisperMsg);
			bufWrite.newLine();
			bufWrite.flush();

			// Sends message to whisperee to print to output
			if (targetClient != null) {
				targetClient.bufWrite.write("##WHISPERFROM," + username + "," + whisperMsg);
				targetClient.bufWrite.newLine();
				targetClient.bufWrite.flush();
				Platform.runLater(() -> {
					logListView.getItems().add("Whispered from " + username + ": " + whisperMsg);
				});
			}
		} catch (IOException e) {
			System.err.println("Error handling whisper message: " + e.getMessage());
		}
	}

	/**
	 * Broadcasts message to all clients
	 * 
	 * @param msg the message to broadcast
	 * @throws IOException
	 */
	private void broadcastMessage(String msg) throws IOException {

		// Sends message back to client to print to output
		bufWrite.write("You: " + msg);
		bufWrite.newLine();
		bufWrite.flush();

		Platform.runLater(() -> {
			logListView.getItems().add(username + " sent message: " + msg);
		});
		// Sends message to all other clients to print to output
		for (ClientManager client_ : clients) {
			if (!client_.username.equals(this.username)) {
				client_.bufWrite.write(username + ": " + msg);
				client_.bufWrite.newLine();
				client_.bufWrite.flush();
			}
		}
	}

	/**
	 * Handles calling.
	 * 
	 * @param msg Calling message containing callee, its port and its address
	 */
	private void handleCalling(String msg) {

		String[] parts = msg.split(","); //breaks up received message for further processing
		String callee = parts[1];
		String callerPort = parts[2];
		String callerAddress = clientSocket.getInetAddress().getHostAddress(); //gets address of caller from socket

		// Send message to callee that caller is calling
		ClientManager targetClient = findClientByUsername(callee);
		try {
			if (targetClient != null) { //notify callee
				targetClient.bufWrite.write("##CALLING," + username + "," + callerPort + "," + callerAddress);
				targetClient.bufWrite.newLine();
				targetClient.bufWrite.flush();

				Platform.runLater(() -> { //log in server ListView
					logListView.getItems().add(username + " is calling " + callee);
				});
			}
		} catch (Exception e) {

		}

	}

	/**
	 * Handles accepting calls
	 * 
	 * @param msg Accept message
	 */
	private void handleAccept(String msg) {
		String[] parts = msg.split(","); //break message into caller string and callee port
		String caller = parts[1];
		String calleePort = parts[2];
		String calleeAddress = clientSocket.getInetAddress().getHostAddress(); //get callee address from socket

		// Send message to caller that callee has accepted the call
		ClientManager targetClient = findClientByUsername(caller);
		try {
			if (targetClient != null) { //notify caller that call accepted
				targetClient.bufWrite.write("##ACCEPTED," + username + "," + calleePort + "," + calleeAddress);
				targetClient.bufWrite.newLine();
				targetClient.bufWrite.flush();

				Platform.runLater(() -> {
					logListView.getItems().add(username + " accepted call from " + caller);
				});
			}
		} catch (Exception e) {

		}
	}

	/**
	 * Handles declining calls
	 * 
	 * @param msg The decline call message
	 */
	private void handleDecline(String msg) {
		String[] parts = msg.split(",");
		String caller = parts[1];

		// Send message to caller that callee is unavailable
		ClientManager targetClient = findClientByUsername(caller);
		try {
			if (targetClient != null) { //notify caller of decline
				targetClient.bufWrite.write("##DECLINED," + username);
				targetClient.bufWrite.newLine();
				targetClient.bufWrite.flush();

				Platform.runLater(() -> {
					logListView.getItems().add(username + " declined call from " + caller);
				});
			}
		} catch (Exception e) {

		}
	}

	/**
	 * Handles ending calls
	 * 
	 * @param message The end call message
	 */
	private void handleEndCall(String message) {
		String[] parts = message.split(",");
		String participant = parts[1];

		// Notify the other participant that the call has ended
		ClientManager targetClient = findClientByUsername(participant);
		try {
			if (targetClient != null) { //notifies participant that call is ended
				targetClient.bufWrite.write("##ENDCALL," + username);
				targetClient.bufWrite.newLine();
				targetClient.bufWrite.flush();

				Platform.runLater(() -> {
					logListView.getItems().add(username + " ended call with " + participant);
				});
			}
		} catch (Exception e) {

		}
	}

	/**
	 * Handles unavailable calls.
	 * 
	 * @param message The unavailable call message
	 */
	private void handleUnavailable(String message) {
		String[] parts = message.split(",");
		String callee = parts[1];

		// Send message to caller that callee is unavailable
		ClientManager targetClient = findClientByUsername(callee);
		try {
			if (targetClient != null) {  //notify caller
				targetClient.bufWrite.write("##UNAVAILABLE," + username);
				targetClient.bufWrite.newLine();
				targetClient.bufWrite.flush();

				Platform.runLater(() -> {
					logListView.getItems().add(username + " is unavailable to take call from " + callee);
				});
			}
		} catch (Exception e) {

		}
	}

	/**
	 * Finds client by username.
	 * 
	 * @param username The client to find
	 * @return client's ClientManager if found in list of connected clients, otherwise return null 
	 */
	private ClientManager findClientByUsername(String username) {
		for (ClientManager client : clients) { //iterate through clients
			if (client.username.equals(username)) {
				return client; //if found, return client
			}
		}
		return null;
	}

	/**
	 * Handles voice note server
	 * 
	 * @param message Voice note message containing recipient's username, the voicenote file name and the file size
	 * @throws InterruptedException
	 */
	private void handleVoiceNoteServer(String message) throws InterruptedException {
		try {
			// Parse and validate the incoming message format
			String[] parts = message.split(",", 4);
			if (parts.length != 4) {
				System.out.println("Invalid voice note message format.");
				return;
			}

			// Extract message details
			String recipientUsername = parts[1];
			String voiceNoteFileName = parts[2];
			long fileSize = Long.parseLong(parts[3]);

			// Logging receipt
			System.out.println(
					username + " sending voice note to " + recipientUsername + " [" + voiceNoteFileName + ", Size: "
							+ fileSize + " bytes]");

			// Ensure the directory exists
			File file = new File("./voiceNotes/" + voiceNoteFileName);
			File parentDirectory = file.getParentFile();
			if (!parentDirectory.exists() && !parentDirectory.mkdirs()) {
				System.out.println("Failed to create directory: " + parentDirectory.getAbsolutePath());
				return;
			}

			// Open file output stream outside of try-with-resources to control when it
			// closes
			FileOutputStream fos = new FileOutputStream(file);
			BufferedOutputStream bos = new BufferedOutputStream(fos);

			// Keep the input stream and socket open for continued use
			byte[] buffer = new byte[4096];
			int bytesRead;
			long totalRead = 0;

			System.out.println("Receiving voice note data... " + fileSize + " bytes expected.");

			// Read the data stream
			while (totalRead < fileSize && (bytesRead = clientSocket.getInputStream().read(buffer)) != -1) {
				bos.write(buffer, 0, bytesRead);
				totalRead += bytesRead;
				System.out.println(
						"Received " + totalRead + " bytes of voice note data of total " + fileSize + " bytes.");
			}

			bos.flush();
			bos.close();
			fos.close(); // Explicitly close file streams after done writing

			// Check completeness and handle accordingly
			if (totalRead == fileSize) {
				System.out.println("Voice note received successfully for " + recipientUsername);
				notifyAndSendFileToClient(file, recipientUsername);
			} else {
				System.out.println(
						"Incomplete file received. Expected " + fileSize + " bytes, got " + totalRead + " bytes.");
			}
		} catch (IOException e) { //catch errors
			System.out.println("Failed to receive voice note: " + e.getMessage());
			e.printStackTrace();
		} catch (NumberFormatException e) {
			System.out.println("Invalid file size received in voice note message.");
		}
	}

	/**
	 * Notifies and sends file to client.
	 * 
	 * @param voiceNoteFile The voice note file to send
	 * @param recipientUsername The recipient's username
	 */
	public void notifyAndSendFileToClient(File voiceNoteFile, String recipientUsername) {
		ClientManager targetClient = findClientByUsername(recipientUsername);
		try {

			byte[] fileContent = Files.readAllBytes(voiceNoteFile.toPath());
			// Notify the target client
			targetClient.bufWrite.write("##RECEIVEVOICENOTE," + recipientUsername + "," + voiceNoteFile.getName() + ","
					+ fileContent.length);
			targetClient.bufWrite.newLine();
			targetClient.bufWrite.flush();

			targetClient.out.write(fileContent); //actual voice note file write

			targetClient.out.flush(); //flush output to actually send voice note

			System.out.println("Voice note sent successfully to " + targetClient.username);

			Platform.runLater(() -> { //log voice note send
				logListView.getItems().add("Voice note sent to " + recipientUsername);
			});

		} catch (IOException e) {
			System.out.println("Error sending voice note to client: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Closes all streams broadcast
	 */
	public void closeAllStreamsBroadcast() {
		System.out.println(username + " disconnected");
		clients.remove(this); //remove this client
		usernames.remove(username);

		Platform.runLater(() -> {
			logListView.getItems().add(username + " disconnected");
			userListView.getItems().remove(username);
		});
		// Broadcast to all clients that a client has left
		try {
			for (ClientManager client : clients) {
				if (!client.username.equals(this.username)) {
					client.bufWrite.write("##CLIENTLEFT" + username);
					client.bufWrite.newLine();
					client.bufWrite.flush();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		closeAllStreams();
	}

	/**
	 * Closes all streams
	 */
	public void closeAllStreams() {
		try {

			if (bufRead != null)
				bufRead.close();
			if (bufWrite != null)
				bufWrite.close();
			if (clientSocket != null)
				clientSocket.close(); //close all streams
		} catch (IOException e) {
			// e.printStackTrace();
		}
	}
}
