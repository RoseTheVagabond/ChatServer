import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class ClientHandler {
    private final Socket socket;
    private final ChatServer server;
    private final BufferedReader in;
    private final PrintWriter out;
    private String username;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, ChatServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    public Socket getSocket() {
        return socket;
    }

    public void start() throws IOException {
        // Send welcome message and prompt for username
        sendMessage("Welcome to the chat server! Please enter your username:");

        // Read username from client
        username = in.readLine();
        if (username == null || username.trim().isEmpty()) {
            username = "Anonymous";
        }

        // Register client
        server.registerClient(username, this);

        // Send instructions
        sendMessage("Instructions:");
        sendMessage("- To send to all: just type your message");
        sendMessage("- To send to specific user: @username message");
        sendMessage("- To send to multiple users: @user1,user2 message");
        sendMessage("- To send to all except some: @!user1,user2 message");
        sendMessage("- To get banned phrases: !banned");

        String message;
        while (running && (message = in.readLine()) != null) {
            handleMessage(message);
        }

        // Client disconnected
        disconnect();
    }

    void disconnect() {
        if (!running) return;
        running = false;

        try {
            server.removeClient(username);

            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(String message) {
        if (message.equals("!banned")) {
            sendMessage("Banned phrases: " + String.join(", ", server.getBannedPhrases()));
            return;
        }

        if (message.startsWith("@")) {
            handleDirectMessage(message);
        } else {
            server.broadcastMessage(username, message);
        }
    }

    private void handleDirectMessage(String message) {
        String[] parts = message.split(" ", 2);
        if (parts.length < 2) return;

        String recipients = parts[0].substring(1);
        String content = parts[1];

        if (recipients.startsWith("!")) {
            // Send to all except specified users
            Set<String> excludedUsers = new HashSet<>(Arrays.asList(
                    recipients.substring(1).split(",")
            ));
            Set<String> actualRecipients = new HashSet<>(server.getClientList());
            actualRecipients.removeAll(excludedUsers);
            server.broadcastMessage(username, content, actualRecipients);
        } else {
            // Send to specific users
            Set<String> targetUsers = new HashSet<>(Arrays.asList(recipients.split(",")));
            server.broadcastMessage(username, content, targetUsers);
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }
}