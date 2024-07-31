package eu.darkbot.util;

import javax.swing.JOptionPane;

public class Popups {
    // Method to show a message dialog
    public static void showMessage(String title, String message, int messageType) {
        JOptionPane.showMessageDialog(null, message, title, messageType);
    }
}
