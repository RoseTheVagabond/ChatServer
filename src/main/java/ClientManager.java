import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class ClientManager {
    private final Socket socket;
    private final int port;
    private final ChatServer server;
    private final BufferedReader in;
    private final PrintWriter out;
    private String username;
    private volatile boolean running = true;

    public ClientManager(Socket socket, ChatServer server) throws IOException {
        this.socket = socket;
        this.port = socket.getPort();
        this.server = server;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    public void start() throws IOException {
        //First message from the server (confirms the client has connected and username is entered)
        sendMessage("Welcome to the chat server!");

        username = in.readLine();
        if (username == null || username.trim().isEmpty()) {
            username = "Anonymous";
        }

        //Save data about the client on the server
        server.registerClient(username, this);

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

        disconnect();
    }

    void disconnect() {
        if (!running) return;
        running = false;

        try {
            //Removes disconnected client from server's data
            server.removeClient(username);

            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(String message) {
        //Get a list of banned phrases
        if (message.equals("!banned")) {
            sendMessage("Banned phrases: " + String.join(", ", server.getBannedPhrases()));
            return;
        }

        if (message.startsWith("@")) {
            handleDirectMessage(message);
        } else {
            //Send to all users (default behaviour)
            server.broadcastMessage(username, message);
        }
    }

    private void handleDirectMessage(String message) {
        String[] parts = message.split(" ", 2);
        if (parts.length < 2) return;

        String recipients = parts[0].substring(1);
        String content = parts[1];

        //Send to all except specified users
        if (recipients.startsWith("!")) {
            Set<String> excludedUsers = new HashSet<>(Arrays.asList(
                    recipients.substring(1).split(",")
            ));
            Set<String> actualRecipients = new HashSet<>(server.getClientList());
            actualRecipients.removeAll(excludedUsers);
            server.broadcastMessage(username, content, actualRecipients);
        } else {
            //Send to specific users
            Set<String> targetUsers = new HashSet<>(Arrays.asList(recipients.split(",")));
            server.broadcastMessage(username, content, targetUsers);
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }
}