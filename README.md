# Chat Application

A Java-based client-server chat application that allows multiple clients to connect to a server and exchange messages.

## Overview

This project implements a chat system with two main components:
- **Server**: Acts as a communication layer between clients
- **Clients**: Connect to the server and exchange messages with other clients

## Features

### Server Features
- Loads configuration from a file (port, name, banned phrases)
- Supports an unlimited number of client connections
- Broadcasts messages between clients
- Filters messages containing banned phrases
- Real-time management of banned phrases through GUI
- Notifies all clients when a user connects or disconnects

### Client Features
- GUI-based interface for sending and receiving messages
- Custom username for identification
- Message targeting options:
  - Send to all users (default)
  - Send to a specific user (`@username message`)
  - Send to multiple specific users (`@user1,user2 message`)
  - Send to all except some users (`@!user1,user2 message`)
- Query server for banned phrases (`!banned`)
- Receive notifications when users connect/disconnect
- Error handling for server disconnection

## Setup and Configuration

### Server Configuration
The server loads its configuration from a properties file with the following parameters:
```
server.port=8080
server.name=ChatServer
banned.phrases=badword1,badword2,badword3
```
### Running the Application
1. Start the server application first
   ```
   java Server
   ```
2. Launch one or more client applications
   ```
   java Client
   ```
3. When starting a client, enter your username in the prompt and connect

## Technical Details

The application uses:
- Java Swing for the GUI
- Socket programming for network communication
- Multithreading for handling multiple client connections
- Concurrent collections for thread-safe client management

### Architecture
- **Server.java**: Main server application with GUI for monitoring and configuration
- **ClientManager.java**: Handles individual client connections on the server
- **Client.java**: Client application with GUI for user interaction

## Usage Examples

### Starting a Conversation
1. Launch the server application
2. Start one or more client applications
3. Enter your username and connect to the server
4. Type a message and press "Send" to broadcast to all connected users

### Sending Direct Messages
To send a message to specific users:
```
@username Hello there!
```
To send a message to multiple users:
```
@user1,user2 Hello to both of you!
```
To send a message to everyone except specific users:
```
@!user1,user2 This is for everyone else!
```
### Checking Banned Phrases
To see the list of phrases that are banned by the server:
```
!banned
```
## Implementation Notes

- The server uses a `ConcurrentHashMap` to safely manage client connections
- Messages are filtered on the server-side for banned content
- The server GUI provides real-time monitoring and moderation tools
- Clients receive instructions on message targeting options upon connection
