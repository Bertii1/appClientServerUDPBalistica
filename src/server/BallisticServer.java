package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Server UDP per il calcolo delle traiettorie balistiche.
 * Gestisce sessioni client tramite indirizzo IP:porta.
 * Ogni pacchetto UDP ricevuto viene processato dal thread pool.
 */
public class BallisticServer {
    private static final int PORT = 5000;
    private static final int MAX_THREADS = 10;
    private static final int BUFFER_SIZE = 65535;
    private static final String USERS_FILE = "data/users.txt";
    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000; // 5 minuti

    private DatagramSocket serverSocket;
    private ExecutorService threadPool;
    private Map<String, String> users;
    private volatile boolean running = true;

    // Sessioni client: chiave = "ip:porta", valore = stato sessione
    private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    /**
     * Rappresenta una sessione client UDP.
     */
    public static class ClientSession {
        public final InetAddress address;
        public final int port;
        public String username;
        public boolean authenticated;
        public int authAttempts;
        public long lastActivity;

        public ClientSession(InetAddress address, int port) {
            this.address = address;
            this.port = port;
            this.username = null;
            this.authenticated = false;
            this.authAttempts = 0;
            this.lastActivity = System.currentTimeMillis();
        }

        public String getKey() {
            return address.getHostAddress() + ":" + port;
        }

        public void touch() {
            this.lastActivity = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - lastActivity > SESSION_TIMEOUT_MS;
        }
    }

    public BallisticServer() {
        this.threadPool = Executors.newFixedThreadPool(MAX_THREADS);
        this.users = new ConcurrentHashMap<>();
    }

    /**
     * Carica le credenziali utente dal file.
     */
    private void loadUsers() {
        try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        users.put(parts[0].trim(), parts[1].trim());
                        log("Utente caricato: " + parts[0]);
                    }
                }
            }
            log("Caricati " + users.size() + " utenti dal file");
        } catch (FileNotFoundException e) {
            log("ATTENZIONE: File utenti non trovato: " + USERS_FILE);
            users.put("admin", "password123");
            users.put("filippo", "test2024");
            log("Creati utenti di default");
        } catch (IOException e) {
            log("Errore lettura file utenti: " + e.getMessage());
        }
    }

    /**
     * Verifica le credenziali di un utente.
     */
    public boolean authenticate(String username, String password) {
        String storedPassword = users.get(username);
        return storedPassword != null && storedPassword.equals(password);
    }

    /**
     * Ottiene o crea una sessione per il client.
     */
    public ClientSession getOrCreateSession(InetAddress address, int port) {
        String key = address.getHostAddress() + ":" + port;
        return sessions.computeIfAbsent(key, k -> new ClientSession(address, port));
    }

    /**
     * Rimuove la sessione di un client.
     */
    public void removeSession(String key) {
        sessions.remove(key);
    }

    /**
     * Invia una risposta UDP al client.
     * Gestisce la frammentazione per risposte grandi.
     */
    public void sendResponse(String response, InetAddress address, int port) throws IOException {
        byte[] data = response.getBytes("UTF-8");

        if (data.length <= BUFFER_SIZE) {
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            synchronized (serverSocket) {
                serverSocket.send(packet);
            }
        } else {
            // Frammentazione per risposte molto grandi
            int maxChunkSize = BUFFER_SIZE - 20; // header margine
            int totalChunks = (int) Math.ceil((double) data.length / maxChunkSize);

            for (int i = 0; i < totalChunks; i++) {
                int offset = i * maxChunkSize;
                int length = Math.min(maxChunkSize, data.length - offset);

                String header = "FRAG:" + (i + 1) + "/" + totalChunks + ":";
                byte[] headerBytes = header.getBytes("UTF-8");
                byte[] chunk = new byte[headerBytes.length + length];
                System.arraycopy(headerBytes, 0, chunk, 0, headerBytes.length);
                System.arraycopy(data, offset, chunk, headerBytes.length, length);

                DatagramPacket packet = new DatagramPacket(chunk, chunk.length, address, port);
                synchronized (serverSocket) {
                    serverSocket.send(packet);
                }

                // Piccola pausa tra frammenti per evitare perdita
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Avvia il server.
     */
    public void start() {
        loadUsers();

        try {
            serverSocket = new DatagramSocket(PORT);
            log("Ballistic Server UDP avviato su porta " + PORT);
            log("In attesa di pacchetti...");

            // Shutdown hook per chiusura graceful
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

            // Thread per pulizia sessioni scadute
            ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
            cleaner.scheduleAtFixedRate(this::cleanExpiredSessions, 60, 60, TimeUnit.SECONDS);

            while (running) {
                try {
                    byte[] receiveBuffer = new byte[BUFFER_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

                    serverSocket.receive(receivePacket);

                    String clientKey = receivePacket.getAddress().getHostAddress() + ":" + receivePacket.getPort();
                    log("Pacchetto ricevuto da " + clientKey);

                    // Crea handler per questo pacchetto e invialo al thread pool
                    ClientHandler handler = new ClientHandler(this, receivePacket);
                    threadPool.execute(handler);

                } catch (SocketException e) {
                    if (running) {
                        log("Errore socket: " + e.getMessage());
                    }
                }
            }

            cleaner.shutdownNow();

        } catch (IOException e) {
            log("Errore avvio server: " + e.getMessage());
        }
    }

    /**
     * Pulisce le sessioni scadute.
     */
    private void cleanExpiredSessions() {
        int removed = 0;
        for (Map.Entry<String, ClientSession> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                sessions.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log("Rimosse " + removed + " sessioni scadute. Sessioni attive: " + sessions.size());
        }
    }

    /**
     * Chiude il server in modo graceful.
     */
    public void shutdown() {
        log("Shutdown in corso...");
        running = false;

        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }

        log("Server terminato.");
    }

    /**
     * Log con timestamp.
     */
    public static void log(String message) {
        System.out.println("[SERVER] " + message);
    }

    public static void main(String[] args) {
        BallisticServer server = new BallisticServer();
        server.start();
    }
}
