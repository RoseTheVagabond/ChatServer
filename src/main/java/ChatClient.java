import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;

public class ChatClient extends Thread {
    private final String serverAddress;
    private final int serverPort;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running = true;
    private ClientGUI gui;
    private String username;

    public ChatClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        SwingUtilities.invokeLater(this::showNameEntryDialog);
    }

    private void showNameEntryDialog() {
        JFrame nameDialog = new JFrame("Enter Username");
        nameDialog.setSize(300, 100);
        nameDialog.setLayout(new BorderLayout());
        nameDialog.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextField nameField = new JTextField();
        JButton connectButton = new JButton("Connect");

        connectButton.addActionListener(e -> {
            username = nameField.getText().trim();
            if (!username.isEmpty()) {
                nameDialog.dispose();
                initializeChatGUI();
            }
        });

        nameField.addActionListener(e -> {
            username = nameField.getText().trim();
            if (!username.isEmpty()) {
                nameDialog.dispose();
                initializeChatGUI();
            }
        });

        nameDialog.add(new JLabel("Enter your username:"), BorderLayout.NORTH);
        nameDialog.add(nameField, BorderLayout.CENTER);
        nameDialog.add(connectButton, BorderLayout.SOUTH);

        nameDialog.setLocationRelativeTo(null);
        nameDialog.setVisible(true);
    }

    private void initializeChatGUI() {
        gui = new ClientGUI(username);
        gui.setVisible(true);
        start();  // Start the client connection thread
    }

    @Override
    public void run() {
        try {
            // Connect to server
            socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Read and display server's initial messages (username prompt, etc.)
            String serverMessage;
            while ((serverMessage = in.readLine()) != null) {
                String finalServerMessage = serverMessage;
                SwingUtilities.invokeLater(() -> gui.appendMessage(finalServerMessage));

                // Check if it's the username prompt
                if (serverMessage.contains("Please enter your username:")) {
                    // Send username to server
                    out.println(username);
                    break;
                }
            }

            // Start message receiver thread
            new Thread(this::receiveMessages).start();
        } catch (IOException e) {
            gui.appendMessage("Error connecting to server: " + e.getMessage());
            shutdown();
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                final String finalMessage = message;
                SwingUtilities.invokeLater(() -> gui.appendMessage(finalMessage));

                if (message.contains("Server is shutting down")) {
                    shutdown();
                    break;
                }
            }
        } catch (IOException e) {
            if (running) {
                SwingUtilities.invokeLater(() ->
                        gui.appendMessage("Lost connection to server")
                );
                shutdown();
            }
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    public void shutdown() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        gui.appendMessage("Disconnected from server");
        gui.dispose();
        System.exit(0);
    }

    // Inner class for ClientGUI
    class ClientGUI extends JFrame {
        private final JTextArea chatArea;
        private final JTextField messageField;
        private final JButton sendButton;

        public ClientGUI(String username) {
            setTitle(username);
            setSize(500, 400);
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    // Trigger shutdown when window is closed
                    ChatClient.this.shutdown();
                }
            });
            setLayout(new BorderLayout());

            // Chat display area
            chatArea = new JTextArea();
            chatArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(chatArea);
            add(scrollPane, BorderLayout.CENTER);

            // Message input panel
            JPanel messagePanel = new JPanel(new BorderLayout());
            messageField = new JTextField();
            sendButton = new JButton("Send");

            sendButton.addActionListener(e -> {
                String message = messageField.getText().trim();
                if (!message.isEmpty()) {
                    ChatClient.this.sendMessage(message);
                    messageField.setText("");
                }
            });

            messageField.addActionListener(e -> {
                String message = messageField.getText().trim();
                if (!message.isEmpty()) {
                    ChatClient.this.sendMessage(message);
                    messageField.setText("");
                }
            });

            messagePanel.add(messageField, BorderLayout.CENTER);
            messagePanel.add(sendButton, BorderLayout.EAST);

            add(messagePanel, BorderLayout.SOUTH);
        }

        public void appendMessage(String message) {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ChatClient("localhost", 8080);
        });
    }
}