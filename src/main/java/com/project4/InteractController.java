package com.project4;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.control.ListView;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * The interaction controller: handles interactions between the client and GUI.
 */
public class InteractController {

    @FXML
    private TextField textLogin; //The "login" text: used for getting the username from the GUI

    @FXML
    private TextField fieldMessage; //The message to send

    @FXML
    public volatile ListView<String> userListView; //A list of current online user strings

    @FXML
    public TextArea globalArea; //represents the global text area

    @FXML
    public volatile TextArea textWhisper; //represents whisper text area

    @FXML
    private volatile TabPane tabPane; //Represents a tab area in the GUI

    @FXML
    private Tab whispersTab; //The tab for whisper messages

    @FXML
    private Tab globalTab; //The tab for global messages

    @FXML
    private TextField textAddress; //The IP address of the server for establishing client connection

    @FXML
    private TextField textPort; //The port of the server for establishing client connection

    @FXML
    private Button buttonServerStart; //The interactable button representing a server start

    @FXML
    private TextArea textCall; //area for appending call messages

    @FXML
    private Button recordButton; //interactable button for recording voice notes

    @FXML
    private ListView<String> vnListView; //List of voice notes

    private List<String> messageQueue = new ArrayList<>(); //message queue for if global message area is null

    private String username; //The client's username

    private volatile Client client = null; //The client for this interaction controller

    private volatile Boolean onWhisper = false; //boolean representing if the client is currently viewing the "whisper" tab
    private Hashtable<String, ArrayList<String>> whisperMessages = new Hashtable<>(); //Hashtable of whisper messages

    private volatile boolean bRecord = false; //boolean representing if voice note recording is currently happening
    private volatile File audioFile; // Reference to the audio file
    private volatile TargetDataLine line; //dataline containing audio input

    /**
     * Displays the whisper messages for the selected user.
     * 
     * This gets triggered when the user clicks on an online user in the listview
     * of online users.
     * It then retrieves the selected user, and appends its whisper messages to the whisper area
     * 
     * @param event The mouse event that triggered the method.
     */
    @FXML
    void displayWhisperMessages(MouseEvent event) {
        // System.out.println("This is happening");
        String whisperee = getSelectedUser();
        if (whisperee.startsWith("*")) {
            whisperNotification(whisperee);
            whisperee = whisperee.substring(1);
        }
        ArrayList<String> messages = whisperMessages.get(whisperee);
        textWhisper.clear();
        for (String message : messages) {
            // System.out.println("//" + message);
            appendWhisperMessage(message);
        }

    }

    /**
     * Handles the sign in process.
     * 
     * This method handles the sign in process. It retrieves the username, server
     * address, and server port from the text fields on the UI. It then initializes
     * the client connection to the server and sends the username to the server.
     * 
     * @param event The action event that triggered the method.
     */
    @FXML
    private void handleSignIn(ActionEvent event) {
        try {
            //get username, address and port
            username = textLogin.getText();
            String serverAddress = textAddress.getText();
            int serverPort = Integer.parseInt(textPort.getText());

            // initialise the client connection to the server
            if (client == null) {
                Socket socket = new Socket(serverAddress, serverPort);
                textAddress.setDisable(true);
                textPort.setDisable(true); //disables text fields for address and port while connection set up
                client = new Client(socket, this); //init client with socket containing server info and this interaction controller
                ClientService.setCurrentClient(client); //sets the client service client instance to the newly created client
                client.receiver(); //start client receiving messages from server
                client.startVoip(); //start VoIP
            }
            client.sendUserName(username);
        } catch (Exception e) {
            // System.out.println("Server is offline : " + e.getMessage());
            // e.printStackTrace();
            Platform.runLater(() -> showErrorDialog("Server not available of given address and port"));
        }

        if (client != null) {
            while (!client.checkedUsername) {
                try {
                    Thread.sleep(500); //sleep client thread to avoid race conditions on usernames
                } catch (InterruptedException e) {
                    // e.printStackTrace();
                }
            }
            client.checkedUsername = false;
            if (client.usernameOK) { //if client username allowed, start main GUI
                try {

                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/project4/GUI_Main.fxml"));
                    loader.setController(this);
                    Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                    stage.setScene(new Scene(loader.load()));
                    stage.setTitle(username); //starts main GUI

                    stage.setOnCloseRequest(e -> {
                        if (client != null) {
                            client.disconnect();
                        }
                    });

                    stage.show();

                    flushMessageQueue(); //flush global message queue
                } catch (Exception e) {
                    System.out.println("Error occurred loading Main GUI: " + e.getMessage());
                }
            }
        }

    }

    /**
     * Sends a message to the server.
     * 
     * This method sends a message to the server. If the message is a whisper, it
     * sends the message to the specified user. If the message is not a whisper, it
     * sends the message to the server.
     * 
     * @param event The action event that triggered the method.
     * @throws IOException
     */
    @FXML
    public void handleSend(ActionEvent event) throws IOException {
        String message = fieldMessage.getText();
        SingleSelectionModel<Tab> selectedTab = tabPane.getSelectionModel();
        onWhisper = selectedTab.getSelectedIndex() == 1; //sets onWhisper boolean to true if user on whisper tab

        String whisperTo = getSelectedUser(); //gets selected user
        if (onWhisper) { //if on whisper tab, try to send a whisper
            if (whisperTo != null) {
                if (!message.isEmpty()) { //sends whisper message if message not empty and a user is selected
                    client.sendMessage("##WHISPER," + whisperTo + "," + message); //append whisper details
                    fieldMessage.clear();
                } else
                    showErrorDialog("Message cannot be empty.");

            } else {
                // Show an error dialog if no user is selected
                showErrorDialog("Please select a user to whisper to.");
            }
        } else if (!message.isEmpty() && client != null) { //non-whisper send
            fieldMessage.clear();
            client.sendMessage(message);
        } else if (message.isEmpty()) { //empty message error
            showErrorDialog("Message cannot be empty.");
        } else {
            System.out.println("Client is not initialized.");
        }
    }

    /**
     * Handles a call.
     * 
     * If a callee is selected, the caller calls the callee. If not, an error dialog is printed to prompt the caller to select someone to call.
     * @param event The event triggering the call
     */
    @FXML
    void handleCall(ActionEvent event) {
        String callee = getSelectedUser();
        if (callee != null) {
            appendCallMessage("Calling " + callee);
            client.call(callee); //allow caller to call the callee
        } else {
            showErrorDialog("Select someone to call"); //prompt caller to select a callee
        }
    }

    /**
     * Handles ending a call by calling on the client to end the call.
     * 
     * @param event The event triggering the end of the call.
     */
    @FXML
    void handleEnd(ActionEvent event) {
        client.endCall();
    }

    /**
     * Handles a conference call by calling on the client class.
     * @param event The event triggering the conference call connect.
     */
    @FXML
    void handleConference(ActionEvent event) {
        client.conference();
    }

    /**
     * Retrieves the selected user from the user list view.
     * 
     * @return The username of the selected user.
     */
    public String getSelectedUser() {
        return userListView.getSelectionModel().getSelectedItem();
    }

    /**
     * Updates the user list view with the provided list of users.
     * 
     * This method updates the user list view on the UI with the provided list of
     * users.
     * 
     * @param users The list of users to be displayed in the user list view.
     */
    public void updateUserList(ArrayList<String> users) {
        Platform.runLater(() -> userListView.getItems().setAll(users));
        System.out.println("User list updated");
    }

    /**
     * Appends a message to the global area.
     * 
     * This method appends the provided message to the global area on the UI. If the
     * global area is null, the message is queued for later display.
     * 
     * @param message The message to be appended.
     */
    public void appendMessage(String message) {
        Platform.runLater(() -> {
            if (globalArea != null) {
                globalArea.appendText(message + "\n"); //actual message append
            } else {
                System.out.println("globalArea is null, queuing message.");
                messageQueue.add(message); //queues message if global area is null
            }
        });
    }

    /**
     * Appends a whisper message to the whisper area.
     * 
     * This method appends the provided whisper message to the whisper area on the
     * UI. If the whisper area is null, the whisper message is not appended.
     * 
     * @param message The whisper message to be appended.
     */
    public void appendWhisperMessage(String message) {
        Platform.runLater(() -> {
            if (textWhisper != null) {
                textWhisper.appendText(message + "\n"); //actual append
            } else { //if no whisper message, do not append anything and print error
                System.out.println("textWhisper is null, cannot append whisper message.");
            }
        });
    }

    /**
     * Appends a call message to the call area.
     * 
     * This method appends the provided call message to the call area on the UI. If
     * the call area is null, the call message is not appended.
     * 
     * @param message The call message to be appended.
     */
    public void appendCallMessage(String message) {
        Platform.runLater(() -> {
            if (textCall != null) {
                textCall.appendText(message + "\n"); //actual append
            } else { //if no call text, print error
                System.out.println("textCall is null.");
            }
        });
    }

    /**
     * Flushes the message queue by appending messages to the global area.
     * 
     * This method is used to display messages in the global area on the GUI.
     */
    private void flushMessageQueue() {
        Platform.runLater(() -> {
            if (globalArea != null) {
                messageQueue.forEach(msg -> globalArea.appendText(msg + "\n")); //append each message to global area
                messageQueue.clear();
            }
        });
    }

    /**
     * Initializes the InteractController.
     * 
     * This method is called upon initialization of the InteractController. It
     * checks if the global area is null and prints a message to the console.
     */
    public void initialize() {
        System.out.println("InteractController initialized. globalArea null? " + (globalArea == null));
    }

    /**
     * Shows an error dialog with the specified message.
     * 
     * @param message The error message to be displayed.
     */
    public void showErrorDialog(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait(); //shows error and waits for user response
        });
    }

    /**
     * Shows an incoming call dialogue.
     * 
     * This method displays an incoming call dialogue with the specified message.
     * 
     * @param message The message to be displayed in the dialogue.
     */
    public void showIncomingDialogue(String message) {
        Platform.runLater(() -> {

            Alert alert = new Alert(AlertType.CONFIRMATION); //uses alert type to force confirmation from user
            alert.setContentText("Accept call from " + message + "?");
            alert.setTitle("Incoming Call");
            alert.setHeaderText(null);
            alert.getButtonTypes().setAll(ButtonType.YES,
                    ButtonType.NO); //set yes and no buttons
            handleCallResponse(alert.showAndWait().get());
        });
    }

    /**
     * Handles the response to an incoming call.
     * 
     * This method handles the response to an incoming call. If the response is
     * positive, the client accepts the call. If the response is negative, the
     * client rejects the call.
     * 
     * @param response The response to the incoming call.
     */
    public void handleCallResponse(ButtonType response) {
        client.handleIncomingCallResponse(response);
    }

    /**
     * Addss the user to the hashmap.
     *
     * @param whisperee Username of the whisperee.
     */
    public void addWhisperee(String whisperee) {
        whisperMessages.put(whisperee, new ArrayList<String>());
    }

    /**
     * removes the user from the hashmap.
     *
     * @param whisperee Username of the whisperee.
     */
    public void removeWhisperee(String whisperee) {
        whisperMessages.remove(whisperee); //remove user
    }

    /**
     * Adds the whisper to the corresponding chat.
     *
     * @param whisperee Username of the user that sent the whisper or sent the
     *                  whisper.
     * @param message   The message to be added
     */
    public void addWhisperMessage(String whisperee, String message) {
        ArrayList<String> messages = whisperMessages.get(whisperee); //gets whispers of whisperee
        messages.add(message);
        // whisperMessages.put(whisperee, messages);
    }

    /**
     * Adds a star, or removes a star from the user that send a whisper to this
     * user.
     *
     * @param whisperer Username of the whisperer.
     */
    public void whisperNotification(String whisperer) {
        // Find the index of the item to update
        ArrayList<String> usernames = client.clients;
        int indexToUpdate = -1;
        for (int i = 0; i < usernames.size(); i++) {
            if (whisperer.equals(usernames.get(i))) { //whisperer found
                indexToUpdate = i; //user index found
                break;
            }
        }
        // Add * to username if user found and does not have a * already, else remove it
        if (indexToUpdate != -1) {
            if (!whisperer.startsWith("*")) //add * if not already there
                usernames.set(indexToUpdate, "*" + whisperer);
            else
                usernames.set(indexToUpdate, whisperer.substring(1)); //remove star if noot already there
        }

        updateUserList(usernames); //update user list for star to show to other users

    }

    /**
     * Toggles the recording of a voice note.
     * 
     * This method toggles the recording of a voice note. If the recording is
     * started, the method creates a new audio file and starts recording audio data
     * from the microphone. If the recording is stopped, the method stops the
     * recording and sends the voice note file to the selected user.
     */
    public void toggleRecording() {
        String selectedUser = userListView.getSelectionModel().getSelectedItem();
        if (!bRecord) { //if not recording
            // Start recording
            bRecord = true;
            String voiceNoteFileName = username + "_" + System.currentTimeMillis() + ".wav";
            audioFile = new File("./voiceNotes/" + voiceNoteFileName); //filename format for voice note
            if (!audioFile.getParentFile().exists()) {
                audioFile.getParentFile().mkdirs(); // Ensure directory exists, if not make one
            }
    
            AudioFormat format = new AudioFormat(8000, 16, 2, true, true); //specific audio format
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format); //append format information
    
            try {
                if (line != null) {
                    line.close(); // Close previous line if open
                    line = null;
                }
                line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format); //open audio line with format specified
                line.start(); //start recording
    
                Thread recordingThread = new Thread(() -> { //threaded recording 
                    try (AudioInputStream stream = new AudioInputStream(line)) { //try for audio input
                        AudioSystem.write(stream, AudioFileFormat.Type.WAVE, audioFile); //write audio stream to audio file 
                        // Optional GUI update or further handling here
                    } catch (IOException e) {
                        System.out.println("I/O exception: " + e.getMessage());
                    } finally {
                        if (line != null) { //finally, stop record
                            line.stop();
                            line.close();
                            line = null;
                        }
                    }
                });
                recordingThread.start(); //start recording thread
                updateRecordButtonLabel("Stop Recording"); //update button on GUI to allow for stopping recording
            } catch (LineUnavailableException e) { //if no audio line being recorded, allow for starting of recording
                e.printStackTrace();
                updateRecordButtonLabel("Start Recording");
            }
        } else {
            // Stop recording
            bRecord = false;
            if (line != null) {
                line.stop();
                line.close();
                line = null;
            }
            updateRecordButtonLabel("Start Recording");
            if (selectedUser != null && !selectedUser.isEmpty()) {
                // Send the voice note file
                client.sendVoiceNoteFile(audioFile, selectedUser);
            } else {
                // Handle case when no user is selected
                // e.g., show error message to user
            }
        }
    }

    /**
     * Updates the label of the record button.
     * 
     * This method updates the label of the record button on the UI. It is executed
     * on the JavaFX application thread.
     * 
     * @param label The new label for the record button.
     */
    public void updateRecordButtonLabel(String label) {
        if (recordButton == null) { //no record button, print error
            System.out.println("Record button is null");
        } else {
            Platform.runLater(() -> {
                recordButton.setText(label); //set text to that provided
            });
        }
    }

    public void addVoiceNoteToListView(File audioFile) { //add a voice note to the list
        System.out.println("Adding voice note to list view");
        Platform.runLater(() -> {
            // Add entry to the ListView with an event handler for playback
            vnListView.getItems().add(audioFile.getName()); //add audio file name
            vnListView.setOnMouseClicked(event -> {
                String selectedMessage = vnListView.getSelectionModel().getSelectedItem();
                if (selectedMessage != null) { //play selected message if found
                    playAudioFile(audioFile);
                }
            });
        });
    }

    /**
     * Plays an audio file.
     * 
     * This method plays the provided audio file using the JavaFX MediaPlayer class.
     * 
     * @param audioFile The audio file to be played.
     */
    private void playAudioFile(File audioFile) {
        String mediaPath = audioFile.toURI().toString(); //get path to file as string
        Media media = new Media(mediaPath); //media (audio) to play from path
        MediaPlayer mediaPlayer = new MediaPlayer(media); //init media player with audio
        mediaPlayer.play(); //play audio with media player
    }

    /**
     * Stops the recording of a voice note.
     * 
     * This method stops the recording of a voice note and closes the audio line.
     */
    @FXML
    void recordVoiceNote() {
        toggleRecording(); //turn recording on/off
    }

}
