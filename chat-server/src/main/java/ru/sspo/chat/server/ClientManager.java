package ru.sspo.chat.server;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

public class ClientManager implements Runnable {

    private final Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String name;

    public final static ArrayList<ClientManager> clients = new ArrayList<>();

    public ClientManager(Socket socket) {
        this.socket = socket;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // first we'll get from new client is his name
            name = bufferedReader.readLine();
            clients.add(this);
            System.out.println(name + " logged into the chat.");
            broadcastMessage("Server: " + name + " logged into the chat.");
        } catch (IOException e) {
            closeEverything(socket, bufferedWriter, bufferedReader);
        }
    }

    @Override
    public void run() {
        String messageFromClient;

        while (socket.isConnected()) {
            try {
                messageFromClient = bufferedReader.readLine();
                if (messageFromClient == null) {    // for Linux and macOS only, cause when client terminates his process he spams NULL strings
                    closeEverything(socket, bufferedWriter, bufferedReader);
                    break;
                }
                handleMessage(messageFromClient);
            } catch (IOException e) {
                closeEverything(socket, bufferedWriter, bufferedReader);
                break;
            }
        }
    }

    private void handleMessage(String message) {
        if (message.substring(message.indexOf(":") + 2).startsWith("@")) {
            String recipientName = message.split(" ")[1].substring(1);
            ClientManager recipient = clients.stream()
                    .filter(clientManager -> clientManager.name.equals(recipientName))
                    .findFirst()
                    .orElse(null);
            if (recipient != null) {
                try {
                    recipient.bufferedWriter.write(message);
                    recipient.bufferedWriter.newLine();
                    recipient.bufferedWriter.flush();
                } catch (IOException e) {
                    closeEverything(socket, bufferedWriter, bufferedReader);
                }
            } else {
                broadcastMessage(message);
            }
        } else {
            broadcastMessage(message);
        }
    }

    private void broadcastMessage(String message) {
        for (ClientManager clientManager : clients) {
            try {
                if (!clientManager.name.equals(name)) { // or any other identifier like uuid
                    clientManager.bufferedWriter.write(message);
                    clientManager.bufferedWriter.newLine();
                    clientManager.bufferedWriter.flush();
                }
            } catch (IOException e) {
                closeEverything(socket, bufferedWriter, bufferedReader);
            }
        }
    }

    private void closeEverything(Socket socket, BufferedWriter bufferedWriter, BufferedReader bufferedReader) {
        // remove client from the collection
        removeClient();
        try {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private void removeClient() {
        clients.remove(this);
        System.out.println(name + " left the chat.");
        broadcastMessage("Server: " + name + " left the chat.");
    }
}
