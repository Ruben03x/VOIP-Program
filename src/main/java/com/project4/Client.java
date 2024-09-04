package com.project4;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import javafx.application.Platform;
import javafx.scene.control.ButtonType;

/**
 * Client class for handling client processes in VoIP voice calling.
 */
public class Client {

	public volatile ArrayList<String> clients = new ArrayList<>(); //list of clients
	private Socket socket = null; //the client socket
	private BufferedReader bufRead = null; //buffered read for communication with the server
	private BufferedWriter bufWrite = null; //buffered write for server communication
	private InteractController interactController; //controls interacts between the user and UI
	public Boolean checkedUsername = false; //has client username been checked against others
	public Boolean usernameOK = false; //is the client username valid

	/**
	 * Client constructor, starts the neccessary streams for communication with the
	 * server.
	 *
	 * @param socket             This client's socket
	 * @param interactController The InteractController to communicate with the GUI
	 */
	public Client(Socket socket, InteractController interactController) {
		try { //initialize instance variables
			this.socket = socket;
			this.bufRead = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			this.bufWrite = new BufferedWriter(new OutputStreamWriter(
					socket.getOutputStream()));
			this.interactController = interactController;
		} catch (IOException e) {
			closeAllSreams(bufRead, bufWrite, socket);
		}

	}

	/**
	 * Sends the username to the Server.
	 *
	 * @param username Username chosen by user.
	 */
	public void sendUserName(String username) {
		try {
			bufWrite.write(username);
			bufWrite.newLine();
			bufWrite.flush(); //actual send to server
		} catch (Exception e) { //catch write exceptions and close streams 

			closeAllSreams(bufRead, bufWrite, socket);
		}
	}

	/**
	 * Sends the message to the server.
	 *
	 * @param message The message the user wants to send.
	 */
	public void sendMessage(String message) {
		try {
			bufWrite.write(message);
			bufWrite.newLine();
			bufWrite.flush();
		} catch (Exception e) {
			closeAllSreams(bufRead, bufWrite, socket);
		}
	}

	/**
	 * Receives messages from the server and handles them accordingly and does all
	 * this in a seperate thread.
	 * When a message is received, it updates it updates the GUI accordingly and
	 * displays the message at its correct output.
	 */
	public void receiver() {
		new Thread(new Runnable() {

			@Override
			public void run() {

				try {
					// message from server
					String msg;

					while (socket.isConnected()) {
						msg = bufRead.readLine();
						System.out.println(msg);
						if (msg == null || msg.equals("##DISCONNECT")) { //close streams upon disconnect from server
							closeAllSreams(bufRead, bufWrite, socket);
							break;
						}
						if (msg.equals("##USERNAMETAKEN")) {
							checkedUsername = true; //username checked
							Platform.runLater(() -> interactController
									.showErrorDialog("Username is taken. Please try a different username.")); //error message if username taken
						}
						if (msg.equals("##USERNAMEOK")) { 
							checkedUsername = true; //username is checked
							usernameOK = true; //username is not taken
						}
						if (msg.startsWith("##WHISPERFROM")) { //handle whisper messages
							String[] parts = msg.split(",", 3); //split message for further processing
							String whisperFrom = parts[1];
							String whisperMsg = parts[2];
							interactController.addWhisperMessage(whisperFrom, whisperFrom + ": " + whisperMsg); //adds whisper message to list controlled by interact controller
							String selectedUser = interactController.getSelectedUser();
							if (selectedUser != null) {
								if (selectedUser.equals(whisperFrom)) {
									interactController.appendWhisperMessage(whisperFrom + ": " + whisperMsg); //add message to GUI through interact controller
								} else {
									interactController.whisperNotification(whisperFrom); //otherwise add notification (*) on client that send the message
								}
							} else {
								interactController.whisperNotification(whisperFrom);
							}
						}
						if (msg.startsWith("##WHISPERTO")) { //handles whisper send
							String[] parts = msg.split(",", 3);
							String whisperTo = parts[1];
							String whisperMsg = parts[2];
							interactController.addWhisperMessage(whisperTo, "You: " + whisperMsg);
							interactController.appendWhisperMessage("You: " + whisperMsg); //show send whisper message in whisper area of GUI
						}

						if (msg.startsWith("##ONLINEUSER")) { 
							clients.add(msg.substring(12)); //add user to list of clients
							interactController.updateUserList(clients); //updates list of users
							interactController.addWhisperee(msg.substring(12)); //add client to whisperee hashmap
						}

						if (msg.startsWith("##CLIENTJOIN")) { //handles client joins
							clients.add(msg.substring(12)); //add client that joined to client list
							System.out.println(msg.substring(12) + " joined");
							interactController.appendMessage(msg.substring(12) + " joined"); //convey that client joined via GUI
							interactController.updateUserList(clients); //update user list view
							interactController.addWhisperee(msg.substring(12)); //add client to whisperee hashmap
						}
						if (msg.startsWith("##CLIENTLEFT")) { //handle clients leaving
							clients.remove(msg.substring(12)); //remove client from list 
							System.out.println(msg.substring(12) + " left");
							interactController.appendMessage(msg.substring(12) + " left");
							interactController.updateUserList(clients); //update list of users on GUI now that someone left
							interactController.removeWhisperee(msg.substring(12)); //remove client from whisperee hashmap
						}
						if (msg.startsWith("##CALLING")) {
							handleIncomingCall(msg); //handles incoming call
						}
						if (msg.startsWith("##ACCEPTED")) {
							handleAccept(msg); //handles a call accept
						}
						if (msg.startsWith("##DECLINED")) {
							handleDecline(); //handles a call decline
						}
						if (msg.startsWith("##UNAVAILABLE")) {
							handleUnavailable(); //handle if the client is unavailable to call
						}
						if (msg.startsWith("##ENDCALL")) {
							stopVoIPSending(); //ends call
						}
						if (msg.startsWith("##RECEIVEVOICENOTE")) {
							handleVoiceNote(msg); //handles voice note when one comes in
						}
						if (msg.charAt(0) != '#') {
							System.out.println(msg);
							interactController.appendMessage(msg);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					closeAllSreams(bufRead, bufWrite, socket);
				}
			}
		}).start();
	}

	/**
	 * Handles incoming call requests from other clients.
	 *
	 * @param message The incoming call message.
	 */
	private void handleIncomingCall(String message) {

		String[] parts = message.split(","); //split message into participant name, port and IP address for processing
		participant = parts[1];
		participantPort = Integer.parseInt(parts[2]);
		participantAddress = parts[3];
		interactController.appendMessage("Incoming call from " + participant);
		if (onCall) {
			interactController.appendMessage("Declined because you are already in a call.");
			sendMessage("##UNAVAILABLE," + participant);
		} else {
			interactController.showIncomingDialogue(participant); //show that another client is trying to establish a call

		}
	}

	/**
	 * Handles the response to an incoming call request
	 *
	 * @param response The response to the incoming call request
	 */
	public void handleIncomingCallResponse(ButtonType response) {
		if (response == ButtonType.YES) {
			interactController.appendMessage("Accepted call from " + participant);
			sendMessage("##ACCEPTED," + participant + "," + port);
			startVoIPSending(); //call accepted; sends message to server and starts VoIP sends
		} else {
			interactController.appendMessage("Declined call from " + participant);
			sendMessage("##DECLINED," + participant); //notify server that call declined
		}
	}

	/**
	 * Handles an accept of an incoming call
	 *
	 * @param response The response to the incoming call request
	 */
	public void handleAccept(String message) {
		String[] parts = message.split(",");
		participant = parts[1];
		participantPort = Integer.parseInt(parts[2]);
		participantAddress = parts[3];
		interactController.appendMessage(participant + " accepted your call"); //notify that client's call request was accepted through GUI
		startVoIPSending(); //start sending
	}

	/**
	 * Handles a decline of an incoming call.
	 */
	public void handleDecline() {
		interactController.appendMessage(participant + " declined your call"); //notify that call was declined
		interactController.showErrorDialog(participant + " declined your call");
	}

	/**
	 * Handles an unavailable clinet during a call request
	 */
	public void handleUnavailable() {
		interactController.appendMessage(participant + " is not available");
		interactController.showErrorDialog(participant + " is not available"); //notify that user is unavailable
	}

	/**
	 * Closes all the streams associated with this client
	 *
	 * @param bufRead BufferedReader, reads from the connected socket.
	 * @param bWriter The BufferedWriter, writes to the connected socket.
	 * @param socket  Socket, connects this client to the server.
	 */
	public void closeAllSreams(BufferedReader bufRead, BufferedWriter bWriter, Socket socket) {
		System.out.println("Server disconnected");
		try {
			if (bufRead != null)
				bufRead.close();
			if (bufWrite != null)
				bufWrite.close();
			if (socket != null)
				socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		;
		System.exit(0);
	}

	/**
	 * Sends a disconnect message to the server and closes all streams
	 */
	public void disconnect() {
		try {
			if (bufWrite != null) {
				bufWrite.write("##DISCONNECT");
				bufWrite.newLine();
				bufWrite.flush();
			} //send disconnect message to server
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			closeAllSreams(bufRead, bufWrite, socket);
			stopVoip(); //stops voice over IP
		}
	}
	private volatile boolean onCall; //boolean that is true if the client is on a call
	private volatile int port; //the port number for the VoIP connection
	private volatile String participant; //Name of participant
	private static Integer participantPort; //Port of participant
	private static String participantAddress; //IP address of participant

	DatagramSocket datagramSend; //Datagram socket for sending datagram packets
	AudioFormat audioFormat = new AudioFormat(48000, 16, 1, true, false); //the format of audio sent: specifies sample rate, size, number of channels, etc
	volatile TargetDataLine targetDataLine;
	DatagramSocket datagramReceive; //Datagram socket for receiving datagram packets
	SourceDataLine sourceDataLine; //SourceDataLine object that handles audio playback and capture

	/**
	 * Starts the VoIP connection.
	 */
	public void startVoip() {
		port = 4000;
		while (true) {
			try {
				datagramReceive = new DatagramSocket(port); //try establish UDP DatagramSocket on specified port for receiving audio packets
				break;
			} catch (Exception e) { //if socket cannot be set up, iterate port number and try establish a new connection
				port++;
			}
		}
		onCall = false;
		receiving(); //initiate receiving of voice data using threaded receiving method

	}

	/**
	 * Calls another client: handles if a client is already on a call and sends an accept message to server if call is to be established.
	 *
	 * @param callee The client to call
	 */
	public void call(String callee) {
		if (onCall) {
			interactController.showErrorDialog("You are already in a call or conference");
		} else {
			participant = callee; //the other client
			sendMessage("##CALLING," + callee + "," + port); //send calling message to server
		}

	}

	/**
	 * Starts sending data over VoIP and signifies that the current client is on a call.
	 */
	private void startVoIPSending() {
		onCall = true;
		sending();
	}

	/**
	 * Ends an incoming call
	 */
	public void endCall() {
		if (participant.equals("conference")) { //if on conference call, disconnect client
			onCall = false;
		} else {
			sendMessage("##ENDCALL," + participant); //send message to server to destroy call
			stopVoIPSending();
		}
	}

	/**
	 * Stops sending voice data by closing necessary streams.
	 */
	public void stopVoIPSending() {
		onCall = false;
		targetDataLine.stop(); 
		targetDataLine.close(); //close audio input data line
		datagramSend.close(); //closes datagram sending socket
		System.out.println("Stopped Sending over voip!");
		interactController.appendMessage("Call ended with " + participant);
	}

	/**
	 * Receives voice data from the other client on a call. Threaded to allow concurrent receiving and sending.
	 */
	private void receiving() {

		new Thread(new Runnable() {

			public void run() { //threaded for concurrent voice receiving

				System.out.println("VoIP receiving started on: " + port);
				try {
					byte[] bytes = new byte[4096];

					DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat); //get audio line information

					sourceDataLine = (SourceDataLine) AudioSystem.getLine(info); //use bidirectional audio data line to specifically get audio output.

					sourceDataLine.open(audioFormat); //open data line for receiving audio in the specified format
					sourceDataLine.start(); //begins playing audio data written to SourceDataLine object

					DatagramPacket packet = new DatagramPacket(bytes, bytes.length);

					while (true) {

						datagramReceive.receive(packet); //receive packets on client's datagram receiving socket
						// System.out.println("Received voice");

						if (onCall) {
							// System.out.println("To speakers");
							sourceDataLine.write(packet.getData(), 0, 4096); //write packet data to the data line
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	/**
	 * Sends voice data to the other client. Threaded to allow concurrent receiving and sending.
	 */
	private void sending() {

		onCall = true;
		new Thread(new Runnable() { //threaded to allow for concurrent sending

			public void run() {
				try {
					System.out.println(
							"Sending VoIP to " + participant + " " + participantAddress + " " + participantPort);
					datagramSend = new DatagramSocket(); //init new socket for receiving data

					DataLine.Info datInfo = new DataLine.Info(TargetDataLine.class, audioFormat); //stores information about target data line
					targetDataLine = (TargetDataLine) AudioSystem.getLine(datInfo); //gets the dataline for audio input
					targetDataLine.open(); //opens data line for sending audio

					AudioInputStream audioInputStream = new AudioInputStream(targetDataLine);
					targetDataLine.start(); //starts receiving audio from input device

					while (onCall) { //actual send process

						byte[] byt = audioInputStream.readNBytes(4096); //read 4096 bytes
						try {
							DatagramPacket dataPack = new DatagramPacket(byt, byt.length,
									InetAddress.getByName(participantAddress), participantPort); //create datagram packet of data to send using 4096 bytes previously read; append the participant IP and port
							datagramSend.send(dataPack); //send packet to other client
						} catch (Exception e) {
						}

					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		}).start();
	}

	/**
	 * Stops the VoIP connection
	 */
	public void stopVoip() {
		sourceDataLine.stop();
		sourceDataLine.close(); //closes datagram receiving dataline
		datagramReceive.close(); //closes datagram receiving socket
	}

	/**
	 * Sends a whisper message to another client.
	 * @param voiceNoteFile file containing voicenote
	 * @param recipientUsername username of client receivng the voicenote
	 */
	public void sendVoiceNoteFile(File voiceNoteFile, String recipientUsername) {
		// Check if the file exists and is not empty
		if (!voiceNoteFile.exists() || voiceNoteFile.length() == 0) {
			System.out.println("Voice note file does not exist or is empty: " + voiceNoteFile.getPath());
			return;
		}

		try {
			// Read the file first to ensure it's ready for transmission
			byte[] fileContent = Files.readAllBytes(voiceNoteFile.toPath());
			System.out.println("Read voice note file successfully: " + voiceNoteFile.getName() + ", Size: "
					+ fileContent.length + " bytes");

			// Send metadata about the voice note to server
			sendMessage("##VOICENOTE," + recipientUsername + "," + voiceNoteFile.getName() + "," + fileContent.length);

			// Now send the actual file content
			OutputStream out = socket.getOutputStream();
			out.write(fileContent); //writes file content to socket output stream
			out.flush(); // Ensure all data is sent

			System.out.println("Voice note sent successfully to server.");
		} catch (IOException e) {
			System.err.println("Error during voice note transmission: " + e.getMessage());
		}
	}


	/**
	 * Handles the received voice note message.
	 * @param message 
	 */
	private void handleVoiceNote(String message) {
		System.out.println("Received voice note message: " + message);
		try {
			String[] parts = message.split(",", 4); //split message for further processing
			if (parts.length != 4) {
				System.out.println("Invalid message format received for voice note.");
				return;
			}

			String senderUsername = parts[1]; //the sender's username
			String voiceNoteFileName = parts[2]; //the voicenote file name
			long fileSize = Integer.parseInt(parts[3]); //the file size of the voice note

			File file = new File("receivedVoiceNotes/" + voiceNoteFileName); //creates new client side file for the received voice note
			if (!file.getParentFile().exists()) {
				file.getParentFile().mkdirs(); // Ensure directory exists
			}
			interactController.addVoiceNoteToListView(file); // Assuming this updates the UI

			// Open file output stream outside of try-with-resources to control when it
			// closes
			FileOutputStream fos = new FileOutputStream(file);
			BufferedOutputStream bos = new BufferedOutputStream(fos);

			byte[] buffer = new byte[4096]; 
			int bytesRead;
			long totalRead = 0;

			// Read the data stream
			while (totalRead < fileSize && (bytesRead = socket.getInputStream().read(buffer)) != -1) { //while we haven't read the entire file
				bos.write(buffer, 0, bytesRead); //write voicenote to buffer
				totalRead += bytesRead;
				System.out.println(
						"Received " + totalRead + " bytes of voice note data of total " + fileSize + " bytes.");
			}

			bos.flush();
			bos.close();
			fos.close(); // Explicitly close file streams after done writing

			// Check completeness and handle accordingly
			if (totalRead == fileSize) {
				System.out.println("Voice note received successfully for " + senderUsername);

			} else {
				System.out.println(
						"Incomplete file received. Expected " + fileSize + " bytes, got " + totalRead + " bytes.");
			}

		} catch (IOException e) {
			System.out.println("Error in receiving voice note: " + e.getMessage());
			e.printStackTrace();
		} catch (NumberFormatException e) {
			System.out.println("Invalid size format in received voice note message.");
		}
	}

	/**
	 * Starts conference call connection
	 */
	public void conference() {
		if (onCall) {
			interactController.showErrorDialog("You are already in a call or conference!");
		} else {
			onCall = true;
			participant = "conference";
			receivingConference(); //set up conference call receiving
			sendingConference(); //set up conference call sending
		}
	}

	/**
	 * Receives voice data from the other clients in the conference.
	 */
	private void receivingConference() {

		new Thread(new Runnable() {

			@SuppressWarnings("deprecation")
			public void run() {
				try {
					// Create source data line for audio output
					DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
					SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
					line.open(audioFormat);
					line.start();

					// Specify multicast group address and port
					String groupAddress = "239.1.2.3";
					int port = 5000; // Use the same port for all senders and receivers

					// Create multicast socket
					InetAddress group = InetAddress.getByName(groupAddress); //represents multicast IP
					MulticastSocket multicastSocket = new MulticastSocket(port); 
					multicastSocket.joinGroup(group); //joins multicast group through the multicast socket

					// Buffer for incoming data
					byte[] buffer = new byte[4096];

					// Receive audio from multicast group and play
					DatagramPacket packet;
					while (onCall) {
						packet = new DatagramPacket(buffer, buffer.length);
						multicastSocket.receive(packet);
						line.write(packet.getData(), 0, packet.getLength());
						// System.out.println("received");
					}
					multicastSocket.close();
					line.stop();
					line.close();

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	/**
	 * Sends voice data to the other clients in the conference 
	 */
	private void sendingConference() {

		new Thread(new Runnable() {

			public void run() {
				try {

					// Create target data line for microphone input
					DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
					TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
					line.open();
					AudioInputStream audioInputStream = new AudioInputStream(line);
					line.start();

					// Specify multicast group address and port
					String groupAddress = "239.1.2.3";
					int port = 5000; // Use the same port for all senders and receivers

					// Create multicast socket
					InetAddress group = InetAddress.getByName(groupAddress);
					MulticastSocket multicastSocket = new MulticastSocket();

					// Capture audio from microphone and send to multicast group
					// byte[] buffer = new byte[4096];
					DatagramPacket packet;
					while (onCall) {
						byte[] audioBytes = audioInputStream.readNBytes(4096);
						packet = new DatagramPacket(audioBytes, audioBytes.length, group, port); //construct packet using multicast information
						multicastSocket.send(packet);
						System.out.println("sending");
					}
					multicastSocket.close();
					line.stop();
					line.close(); //close socket connection when off call

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

}
