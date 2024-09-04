package com.project4;

import javafx.application.Platform;

/**
 * Service class for client interaction. Pairs client and GUI interaction controller.
 */
public class ClientService {
    private static Client currentClient;
    private static InteractController currentController;

    /**
     * Set the current client instance.
     * 
     * @param client The client instance to set
     */
    public static void setCurrentClient(Client client) {
        currentClient = client;
    }

    /**
     * Get the current client instance.
     * 
     * @return The current client instance
     */
    public static Client getCurrentClient() {
        return currentClient;
    }

    /**
     * Set the current controller instance.
     * 
     * @param controller The controller instance to set
     */
    public static synchronized void setCurrentController(InteractController controller) {
        if (controller.globalArea != null) {
            currentController = controller; //set client's controller
            System.out.println("CurrentController set with globalArea initialized.");
        } else {
            System.out.println("Attempted to set CurrentController without globalArea being initialized.");
        }
    }

    /**
     * Get the current controller instance.
     * 
     * @return The current controller instance
     */
    public static InteractController getCurrentController() {
        return currentController;
    }

    /**
     * Safely append a message to the current controller's message area.
     * 
     * @param message The message to append
     */
    public static void safelyAppendMessage(String message) {
        InteractController controller = getCurrentController();
        if (controller != null) {
            Platform.runLater(() -> controller.appendMessage(message)); //appends message if controller is set
        } else {
            System.out.println("No valid controller instance available for appending messages.");
        }
    }
}
