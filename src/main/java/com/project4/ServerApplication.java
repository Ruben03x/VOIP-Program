package com.project4;

import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Server application class to start the server GUI application.
 */
public class ServerApplication extends Application {

    private InteractControllerServer controller; //This server's GUI interaction controller

    /**
     * Start the server application.
     * 
     * @param primaryStage The primary stage for the application
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            // Create a FXMLLoader instance to load the FXML file
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/project4/GUI_Server.fxml"));
            // Load the FXML into a Parent node
            Parent root = loader.load();
            // Retrieve the controller set in the FXML file
            controller = loader.getController();

            // Set up the primary stage
            primaryStage.setScene(new Scene(root));
            primaryStage.setTitle("Server Control Panel");
            primaryStage.setOnCloseRequest(event -> {
                // Ensure server stops when window is closed
                controller.stopServer();
                // call System.exit to terminate any remaining threads
                System.exit(0);
            });
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stop the server when the application is stopped.
     */
    @Override
    public void stop() {
        if (controller != null) {
            controller.stopServer(); //shuts down server
        }
    }
    /**
     * Main method to launch the application.
     * 
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        launch(args); //launches server GUI
    }
}
