package com.project4;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Client application class to start the client GUI application.
 */
public class ClientApplication extends Application {

    /**
     * Start the client application.
     * 
     * @param primaryStage The primary stage for the application
     */
    @Override
    public void start(Stage primaryStage) {
        try { //set up GUI
            Parent root = FXMLLoader.load(getClass().getResource("/com/project4/GUI_Login.fxml"));
            primaryStage.setScene(new Scene(root));
            primaryStage.setTitle("Login Screen");
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main method to launch the application.
     * 
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        launch(args); //launch javafx GUI
    }
}
