import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class Server {
    private final int port;
    private final String serverName;
    private final Set<String> bannedPhrases;

    //ConcurrentHashMap instead of a regular one to help with changing data by multiple ClientManagers
    private final Map<String, ClientManager> clients = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private final ServerGUI gui;
    private Thread serverThread;

    public Server() throws IOException {
        String configFile = "/Users/rubyrover/Desktop/PJATK/UTP/Project2/src/main/java/server_config.properties";
        Map<String, String> config = loadConfiguration(configFile);

        this.port = Integer.parseInt(config.get("server.port"));
        this.serverName = config.get("server.name");
        this.bannedPhrases = new HashSet<>();
        updateBannedPhrases(config.get("banned.phrases").split(","));

        this.gui = new ServerGUI(this);
        gui.setVisible(true);
    }

    private Map<String, String> loadConfiguration(String configFile) throws IOException {
        Map<String, String> config = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        config.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        }

        // Validate required configuration
        if (!config.containsKey("server.port")) {
            config.put("server.port", "8080");
        }
        if (!config.containsKey("server.name")) {
            config.put("server.name", "Server");
        }
        if (!config.containsKey("banned.phrases")) {
            config.put("banned.phrases", "");
        }

        return config;
    }

    public void updateBannedPhrases(String[] phrases) {
        bannedPhrases.clear();
        for (String phrase : phrases) {
            if (!phrase.trim().isEmpty()) {
                bannedPhrases.add(phrase.trim().toLowerCase());
            }
        }

        try {
            File configFile = new File("/Users/rubyrover/Desktop/PJATK/UTP/Project2/src/main/java/server_config.properties");
            BufferedReader reader = new BufferedReader(new FileReader(configFile));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("banned.phrases=")) {
                    content.append(line).append("\n");
                }
            }
            reader.close();

            content.append("banned.phrases=").append(String.join(",", bannedPhrases));

            BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
            writer.write(content.toString());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> gui.updateBannedPhrasesList());
        //Notify clients that banned phrases have been updated
        broadcastMessage(null, "Banned phrases have been updated");
    }

    public Set<String> getBannedPhrases() {
        return bannedPhrases;
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(port);
            gui.appendLog(serverName + " started on port " + port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    if (running) {
                        new Thread(() -> handleNewClient(clientSocket)).start();
                    } else {
                        clientSocket.close();
                    }
                } catch (SocketException e) {
                    if (running) {
                        gui.appendLog("Server error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            gui.appendLog("Server error: " + e.getMessage());
        }
    }

    public void start() {
        serverThread = new Thread(this::runServer);
        serverThread.start();
    }

    private void handleNewClient(Socket clientSocket) {
        try {
            ClientManager manager = new ClientManager(clientSocket, this);
            manager.start();
        } catch (IOException e) {
            if (running) {
                gui.appendLog("Error handling client: " + e.getMessage());
            }
            try {
                clientSocket.close();
            } catch (IOException ex) {
                e.printStackTrace();
            }
        }
    }

    public void registerClient(String username, ClientManager manager) {
        clients.put(username, manager);
        broadcastMessage(null, username + " has joined the chat");
        sendClientList();
    }

    public void removeClient(String username) {
        clients.remove(username);
        broadcastMessage(null, username + " has left the chat");
        sendClientList();
    }

    public Set<String> getClientList() {
        return new HashSet<>(clients.keySet());
    }

    public String containsBannedPhrase(String message) {
        return bannedPhrases.stream()
                .filter(phrase -> message.toLowerCase().contains(phrase.toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    public void broadcastMessage(String sender, String message, Set<String> recipients) {
        String bannedWord = containsBannedPhrase(message);

        if (bannedWord != null) {
            ClientManager manager = clients.get(sender);
            if (manager != null) {
                manager.sendMessage("Server: Message contains banned content ('" + bannedWord + "') and was not sent");
            }
            return;
        }

        for (Map.Entry<String, ClientManager> entry : clients.entrySet()) {
            if (recipients == null || recipients.contains(entry.getKey())) {
                if (!entry.getKey().equals(sender)) {
                    entry.getValue().sendMessage(
                            (sender != null ? sender + ": " : "") + message
                    );
                }
            }
        }
    }

    public void broadcastMessage(String sender, String message) {
        broadcastMessage(sender, message, null);
    }

    private void sendClientList() {
        String clientList = "Connected clients: " + String.join(", ", getClientList());
        broadcastMessage(null, clientList);
    }

    class ServerGUI extends JFrame {
        private final JTextArea logArea;
        private final JList<String> bannedPhrasesList;
        private final DefaultListModel<String> bannedPhrasesModel;
        private final JTextField newPhraseField;

        public ServerGUI(Server server) {
            setTitle(serverName + " - Server Console");
            setSize(900, 400);
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    //Inform clients that server is shutting down
                    broadcastMessage(null, "Server is shutting down");
                    System.exit(0);
                }
            });
            setLayout(new BorderLayout());

            //Log area
            logArea = new JTextArea();
            logArea.setEditable(false);
            JScrollPane logScrollPane = new JScrollPane(logArea);
            add(logScrollPane, BorderLayout.CENTER);

            //Banned phrases panel
            JPanel bannedPhrasesPanel = new JPanel(new BorderLayout());
            bannedPhrasesModel = new DefaultListModel<>();
            bannedPhrasesList = new JList<>(bannedPhrasesModel);
            JScrollPane listScrollPane = new JScrollPane(bannedPhrasesList);
            bannedPhrasesPanel.add(listScrollPane, BorderLayout.CENTER);

            //Add new banned phrase panel
            JPanel addPhrasePanel = new JPanel(new FlowLayout());
            newPhraseField = new JTextField(20);
            JButton addButton = new JButton("Add Phrase");
            JButton removeButton = new JButton("Remove Selected");

            addButton.addActionListener(e -> {
                String newPhrase = newPhraseField.getText().trim().toLowerCase();
                if (!newPhrase.isEmpty()) {
                    server.updateBannedPhrases(
                            Stream.concat(
                                    bannedPhrases.stream(),
                                    Stream.of(newPhrase)
                            ).toArray(String[]::new)
                    );
                    newPhraseField.setText("");
                }
            });

            removeButton.addActionListener(e -> {
                String selectedPhrase = bannedPhrasesList.getSelectedValue();
                if (selectedPhrase != null) {
                    Set<String> updatedPhrases = new HashSet<>(bannedPhrases);
                    updatedPhrases.remove(selectedPhrase);
                    server.updateBannedPhrases(updatedPhrases.toArray(new String[0]));
                }
            });

            addPhrasePanel.add(new JLabel("New Banned Phrase:"));
            addPhrasePanel.add(newPhraseField);
            addPhrasePanel.add(addButton);
            addPhrasePanel.add(removeButton);

            bannedPhrasesPanel.add(addPhrasePanel, BorderLayout.SOUTH);
            add(bannedPhrasesPanel, BorderLayout.EAST);

            //Updating banned phrases at the start of the program (double-checking that it is loaded correctly)
            updateBannedPhrasesList();
        }

        public void appendLog(String message) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }

        public void updateBannedPhrasesList() {
            SwingUtilities.invokeLater(() -> {
                bannedPhrasesModel.clear();
                for (String phrase : bannedPhrases) {
                    bannedPhrasesModel.addElement(phrase);
                }
            });
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Server server = new Server();
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}