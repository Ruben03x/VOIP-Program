package com.project4;

import java.io.IOException;
import java.net.ServerSocket;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;

/**
 * Controller class for the server interaction UI.
 */
public class InteractControllerServer {
    @FXML
    private Text serverHost; // Text element displaying host information

    @FXML
    private TextArea serverPort; // TextArea for port input

    @FXML
    private Button buttonServerStart; // Button for starting the server

    @FXML
    private ListView<String> userListViewServer; // ListView displaying online users

    @FXML
    public volatile ListView<String> serverListView; // ListView for server logs

    private Server server;

    /**
     * Initializes the InteractController.
     *
     * This method is called automatically after the FXML fields are populated.
     * It sets the initial focus to the start button and attaches the event handler for starting the server.
     */
    public void initialize() {
        System.out.println("InteractControllerServer initialized.");
        Platform.runLater(() -> buttonServerStart.requestFocus());
        buttonServerStart.setOnAction(event -> startServer());
    }

    /**
     * Starts the server.
     * 
     * Parses the port number from the TextArea and starts the server on that port.
     * Displays error messages if the port number is invalid or if the server fails to start.
     */
    private void startServer() {
        try {
            int port = Integer.parseInt(serverPort.getText().trim()); // Get port number from TextArea
            server = new Server(new ServerSocket(port), serverListView); //create Server object
            new Thread(() -> {
                try { //try start server socket; if not possible at the moment, display error
                    server.startServerSocket(userListViewServer, serverListView);
                } catch (IOException e) {
                    Platform.runLater(() -> serverListView.getItems().add("Error starting server: " + e.getMessage()));
                }
            }).start();
        } catch (NumberFormatException e) { //errors
            serverListView.getItems().add("Invalid Port Number");
        } catch (IOException e) {
            serverListView.getItems().add("Could not start server: " + e.getMessage());
        }
    }

    /**
     * Logs a message to the server UI.
     * 
     * Adds the message to the serverListView if it is initialized, otherwise prints to the console.
     * 
     * @param message The message to log.
     */
    public void logToUi(String message) {
        Platform.runLater(() -> {
            if (serverListView != null) {
                serverListView.getItems().add(message); //add message to server log
            } else {
                System.out.println("Server ListView is not initialized");
            }
        });
    }

    /**
     * Stops the server.
     * 
     * Calls the stop method in the Server class to properly close the ServerSocket and any other resources.
     * Logs a message to the server UI indicating that the server has stopped.
     */
    public void stopServer() {
        if (server != null) {
            server.stop(); // Implement this method in the Server class to properly close the ServerSocket and any other resources.
            logToUi("Server stopped.");
        }
    }
}
