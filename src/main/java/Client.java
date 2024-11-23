import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;

public class Client {
    private final String serverAddress;
    private final int serverPort;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running = true;
    private ClientWindow gui;
    private String username;

    public Client(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        SwingUtilities.invokeLater(this::getClientName);
    }

    //Method to fetch username of the client, so that the client's window is named after them
    private void getClientName() {
        JFrame nameFrame = new JFrame("Enter Username");
        nameFrame.setSize(300, 100);
        nameFrame.setLayout(new BorderLayout());
        nameFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextField nameField = new JTextField();
        JButton connectButton = new JButton("Connect");

        connectButton.addActionListener(e -> {
            username = nameField.getText().trim();
            if (!username.isEmpty()) {
                nameFrame.dispose();
                openClientWindow();
            }
        });

        nameField.addActionListener(e -> {
            username = nameField.getText().trim();
            if (!username.isEmpty()) {
                nameFrame.dispose();
                openClientWindow();
            }
        });

        nameFrame.add(new JLabel("Enter your username:"), BorderLayout.NORTH);
        nameFrame.add(nameField, BorderLayout.CENTER);
        nameFrame.add(connectButton, BorderLayout.SOUTH);

        nameFrame.setLocationRelativeTo(null);
        nameFrame.setVisible(true);
    }

    private void openClientWindow() {
        gui = new ClientWindow(username);
        gui.setVisible(true);
        connect();
    }

    public void connect() {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String serverMessage;
            while ((serverMessage = in.readLine()) != null) {
                gui.appendMessage(serverMessage);

                //If the first message from the server was sent, it means that the username has been registered:)
                if (serverMessage.contains("Welcome to the chat server!")) {
                    out.println(username);
                    break;
                }
            }
            //Start a new thread for receiving messages
            new Thread(new Runnable() {
                @Override
                public void run() {
                    receiveMessages();
                }
            }).start();

        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                //Create and show error popup if the server is unreachable
                JFrame errorFrame = new JFrame("Connection Error");
                errorFrame.setSize(300, 100);
                errorFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                errorFrame.setLayout(new BorderLayout());

                JLabel errorMessage = new JLabel("Failed to connect to the server", SwingConstants.CENTER);
                errorFrame.add(errorMessage, BorderLayout.CENTER);

                JButton okButton = new JButton("OK");
                okButton.addActionListener(event -> {
                    errorFrame.dispose();
                    shutdown();
                });

                JPanel buttonPanel = new JPanel();
                buttonPanel.add(okButton);
                errorFrame.add(buttonPanel, BorderLayout.SOUTH);

                errorFrame.setLocationRelativeTo(null);
                errorFrame.setVisible(true);
            });
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                final String finalMessage = message;
                SwingUtilities.invokeLater(() -> gui.appendMessage(finalMessage));

                //If the server is closing, display a suitable message and shutdown after 3 seconds
                if (message.contains("Server is shutting down")) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    shutdown();
                    break;
                }
            }
        } catch (IOException e) {
            if (running) {
                SwingUtilities.invokeLater(() ->
                        gui.appendMessage("Lost connection to server")
                );
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
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

    //Inner class for client's window (named after them)
    class ClientWindow extends JFrame {
        private final JTextArea chatArea;
        private final JTextField messageField;
        private final JButton sendButton;

        public ClientWindow(String username) {
            setTitle(username);
            setSize(500, 400);

            //Custom behaviour when closing (notify others)
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    shutdown();
                }
            });
            setLayout(new BorderLayout());

            //Chat display area
            chatArea = new JTextArea();
            chatArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(chatArea);
            add(scrollPane, BorderLayout.CENTER);

            //Panel for sending messages
            JPanel messagePanel = new JPanel(new BorderLayout());
            messageField = new JTextField();
            sendButton = new JButton("Send");

            sendButton.addActionListener(e -> {
                String message = messageField.getText().trim();
                if (!message.isEmpty()) {
                    sendMessage(message);
                    messageField.setText("");
                }
            });

            messageField.addActionListener(e -> {
                String message = messageField.getText().trim();
                if (!message.isEmpty()) {
                    sendMessage(message);
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
        //Specifies the server address and port number to which the client connects
        SwingUtilities.invokeLater(() -> {
            new Client("localhost", 8080);
        });
    }
}