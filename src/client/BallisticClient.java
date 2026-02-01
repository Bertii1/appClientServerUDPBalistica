package client;

import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Client UDP per connettersi al server di calcolo balistico.
 * Utilizza DatagramSocket per la comunicazione senza connessione.
 *
 * Gestisce la riassemblatura di risposte frammentate dal server.
 */
public class BallisticClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;
    private static final int BUFFER_SIZE = 65535;
    private static final int TIMEOUT_MS = 10000; // 10 secondi timeout

    private String host;
    private int port;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private boolean connected = false;
    private boolean authenticated = false;

    public BallisticClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public BallisticClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Inizializza il socket UDP e risolve l'indirizzo del server.
     * @return true se il socket e' stato creato con successo
     */
    public boolean connect() {
        try {
            serverAddress = InetAddress.getByName(host);
            socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
            connected = true;
            return true;
        } catch (SocketException | UnknownHostException e) {
            System.err.println("Errore inizializzazione UDP: " + e.getMessage());
            return false;
        }
    }

    /**
     * Autentica l'utente con il server.
     * @return messaggio di risposta dal server
     */
    public String authenticate(String username, String password) {
        if (!connected) {
            return "ERROR Non connesso al server";
        }

        String response = sendAndReceive("AUTH " + username + " " + password);

        if (response != null && response.startsWith("OK")) {
            authenticated = true;
        }
        return response;
    }

    /**
     * Invia una richiesta di simulazione.
     * @return risultati della simulazione
     */
    public String sendSimulation(ProjectileParams params) {
        if (!connected || !authenticated) {
            return "ERROR Non autenticato";
        }

        String validation = params.validate();
        if (validation != null) {
            return "ERROR Parametri non validi: " + validation;
        }

        String response = sendAndReceive(params.toProtocolString());

        if (response == null) {
            return "ERROR Timeout: nessuna risposta dal server";
        }

        // Estrai contenuto tra BEGIN_RESULT e END_RESULT
        return extractResult(response);
    }

    /**
     * Invia una richiesta di aiuto.
     */
    public String sendHelp() {
        if (!connected || !authenticated) {
            return "ERROR Non autenticato";
        }

        String response = sendAndReceive("HELP");
        if (response == null) {
            return "ERROR Timeout: nessuna risposta dal server";
        }
        return extractResult(response);
    }

    /**
     * Invia un messaggio al server e riceve la risposta.
     * Gestisce la riassemblatura di risposte frammentate.
     */
    private String sendAndReceive(String message) {
        try {
            // Invia il comando
            byte[] sendData = message.getBytes("UTF-8");
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);
            socket.send(sendPacket);

            // Ricevi la risposta
            byte[] receiveBuffer = new byte[BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);

            String response = new String(receivePacket.getData(), 0, receivePacket.getLength(), "UTF-8");

            // Controlla se e' una risposta frammentata
            if (response.startsWith("FRAG:")) {
                return reassembleFragments(response);
            }

            return response;

        } catch (SocketTimeoutException e) {
            System.err.println("Timeout: il server non ha risposto entro " + TIMEOUT_MS + "ms");
            return null;
        } catch (IOException e) {
            System.err.println("Errore comunicazione UDP: " + e.getMessage());
            return null;
        }
    }

    /**
     * Riassembla una risposta frammentata dal server.
     */
    private String reassembleFragments(String firstFragment) throws IOException {
        // Parsa header primo frammento: "FRAG:1/N:data..."
        int firstColon = firstFragment.indexOf(':');
        int secondColon = firstFragment.indexOf(':', firstColon + 1);
        String fragInfo = firstFragment.substring(firstColon + 1, secondColon);
        String[] fragParts = fragInfo.split("/");
        int totalFragments = Integer.parseInt(fragParts[1]);

        // Mappa per raccogliere i frammenti
        Map<Integer, String> fragments = new TreeMap<>();

        // Salva il primo frammento
        String data = firstFragment.substring(secondColon + 1);
        fragments.put(1, data);

        // Ricevi i frammenti rimanenti
        int originalTimeout = socket.getSoTimeout();
        socket.setSoTimeout(5000); // timeout piu' breve per frammenti successivi

        try {
            while (fragments.size() < totalFragments) {
                byte[] receiveBuffer = new byte[BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(receivePacket);

                String fragment = new String(receivePacket.getData(), 0, receivePacket.getLength(), "UTF-8");

                if (fragment.startsWith("FRAG:")) {
                    int fc = fragment.indexOf(':');
                    int sc = fragment.indexOf(':', fc + 1);
                    String fi = fragment.substring(fc + 1, sc);
                    String[] fp = fi.split("/");
                    int fragNum = Integer.parseInt(fp[0]);
                    String fragData = fragment.substring(sc + 1);
                    fragments.put(fragNum, fragData);
                }
            }
        } catch (SocketTimeoutException e) {
            System.err.println("Timeout durante riassemblatura frammenti. Ricevuti: " +
                             fragments.size() + "/" + totalFragments);
        } finally {
            socket.setSoTimeout(originalTimeout);
        }

        // Ricomponi la risposta
        StringBuilder result = new StringBuilder();
        for (String frag : fragments.values()) {
            result.append(frag);
        }
        return result.toString();
    }

    /**
     * Estrae il contenuto tra BEGIN_RESULT e END_RESULT.
     */
    private String extractResult(String response) {
        if (response == null) return "";

        int beginIdx = response.indexOf("BEGIN_RESULT");
        int endIdx = response.indexOf("END_RESULT");

        if (beginIdx != -1 && endIdx != -1) {
            String content = response.substring(beginIdx + "BEGIN_RESULT".length(), endIdx).trim();
            return content + "\n";
        }

        return response;
    }

    /**
     * Disconnette dal server.
     */
    public void disconnect() {
        if (connected) {
            try {
                // Invia QUIT (best-effort, non aspettiamo risposta obbligatoriamente)
                byte[] sendData = "QUIT".getBytes("UTF-8");
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);
                socket.send(sendPacket);

                // Prova a ricevere BYE ma non bloccare
                socket.setSoTimeout(1000);
                try {
                    byte[] buf = new byte[256];
                    DatagramPacket recv = new DatagramPacket(buf, buf.length);
                    socket.receive(recv);
                } catch (SocketTimeoutException ignored) {}

            } catch (IOException e) {
                // Ignora errori durante la chiusura
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                connected = false;
                authenticated = false;
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}
