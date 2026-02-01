package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * Gestisce un singolo pacchetto UDP ricevuto da un client.
 * Implementa il protocollo di autenticazione e simulazione via UDP.
 *
 * A differenza di TCP, ogni pacchetto e' una richiesta indipendente.
 * Lo stato della sessione (autenticazione) e' mantenuto nel server
 * tramite la mappa delle sessioni (chiave = ip:porta).
 */
public class ClientHandler implements Runnable {
    private static final int MAX_AUTH_ATTEMPTS = 3;

    private final BallisticServer server;
    private final DatagramPacket receivedPacket;
    private final InetAddress clientAddress;
    private final int clientPort;
    private final String message;

    public ClientHandler(BallisticServer server, DatagramPacket packet) {
        this.server = server;
        this.receivedPacket = packet;
        this.clientAddress = packet.getAddress();
        this.clientPort = packet.getPort();
        this.message = new String(packet.getData(), 0, packet.getLength()).trim();
    }

    @Override
    public void run() {
        try {
            BallisticServer.ClientSession session = server.getOrCreateSession(clientAddress, clientPort);
            session.touch();

            log("Comando ricevuto: '" + message + "' da " + session.getKey());

            if (message.startsWith("AUTH ")) {
                handleAuthentication(session);
            } else if (message.equalsIgnoreCase("QUIT") || message.equalsIgnoreCase("EXIT")) {
                handleQuit(session);
            } else if (!session.authenticated) {
                sendResponse("ERROR Non autenticato. Invia prima: AUTH username password");
            } else if (message.startsWith("SIMULATE ")) {
                handleSimulation(session, message.substring(9).trim());
            } else if (message.equalsIgnoreCase("HELP")) {
                handleHelp();
            } else {
                sendResponse("ERROR Comando sconosciuto. Usa HELP per la lista comandi.");
            }

        } catch (IOException e) {
            log("Errore I/O: " + e.getMessage());
        }
    }

    /**
     * Gestisce il comando AUTH.
     */
    private void handleAuthentication(BallisticServer.ClientSession session) throws IOException {
        if (session.authenticated) {
            sendResponse("OK Gia' autenticato come " + session.username);
            return;
        }

        if (session.authAttempts >= MAX_AUTH_ATTEMPTS) {
            sendResponse("ERROR Troppi tentativi falliti. Riprova piu' tardi.");
            return;
        }

        String authData = message.substring(5).trim();
        String[] parts = authData.split(" ", 2);

        if (parts.length != 2) {
            sendResponse("ERROR Formato: AUTH username password");
            return;
        }

        String user = parts[0].trim();
        String pass = parts[1].trim();

        if (server.authenticate(user, pass)) {
            session.username = user;
            session.authenticated = true;
            session.authAttempts = 0;
            log("Utente '" + user + "' autenticato con successo da " + session.getKey());
            sendResponse("OK");
        } else {
            session.authAttempts++;
            int remaining = MAX_AUTH_ATTEMPTS - session.authAttempts;
            if (remaining > 0) {
                sendResponse("ERROR Credenziali non valide. Tentativi rimasti: " + remaining);
                log("Autenticazione fallita per " + session.getKey() + ". Rimasti: " + remaining);
            } else {
                sendResponse("ERROR Troppi tentativi falliti. Sessione bloccata.");
                log("Sessione bloccata per " + session.getKey());
            }
        }
    }

    /**
     * Gestisce una richiesta di simulazione.
     */
    private void handleSimulation(BallisticServer.ClientSession session, String params) throws IOException {
        String[] parts = params.split("\\s+");

        if (parts.length != 4) {
            sendResponse("BEGIN_RESULT\nERROR Formato: SIMULATE velocity angle mass dragCoeff\nEND_RESULT");
            return;
        }

        try {
            double velocity = Double.parseDouble(parts[0]);
            double angle = Double.parseDouble(parts[1]);
            double mass = Double.parseDouble(parts[2]);
            double dragCoeff = Double.parseDouble(parts[3]);

            // Validazione parametri
            StringBuilder errors = new StringBuilder();
            if (velocity <= 0) errors.append("velocity deve essere > 0; ");
            if (angle < 0 || angle > 90) errors.append("angle deve essere tra 0 e 90; ");
            if (mass <= 0) errors.append("mass deve essere > 0; ");
            if (dragCoeff <= 0) errors.append("dragCoeff deve essere > 0; ");

            if (errors.length() > 0) {
                sendResponse("BEGIN_RESULT\nERROR Parametri invalidi: " + errors.toString() + "\nEND_RESULT");
                return;
            }

            log("Simulazione richiesta da '" + session.username + "': v=" + velocity +
                ", angle=" + angle + ", mass=" + mass + ", cd=" + dragCoeff);

            // Esegui simulazione
            String result = BallisticCalculator.simulate(velocity, angle, mass, dragCoeff);

            // Invia risultato con delimitatori
            sendResponse("BEGIN_RESULT\n" + result + "\nEND_RESULT");

            log("Risultati inviati al client '" + session.username + "'");

        } catch (NumberFormatException e) {
            sendResponse("BEGIN_RESULT\nERROR Parametri devono essere numeri validi\nEND_RESULT");
        }
    }

    /**
     * Invia la guida dei comandi.
     */
    private void handleHelp() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN_RESULT\n");
        sb.append("=== COMANDI DISPONIBILI ===\n");
        sb.append("SIMULATE velocity angle mass dragCoeff\n");
        sb.append("  - velocity: velocita' iniziale in m/s (> 0)\n");
        sb.append("  - angle: angolo di lancio in gradi (0-90)\n");
        sb.append("  - mass: massa del proiettile in kg (> 0)\n");
        sb.append("  - dragCoeff: coefficiente di drag (> 0, tipico 0.47 per sfere)\n");
        sb.append("\n");
        sb.append("HELP  - Mostra questo messaggio\n");
        sb.append("QUIT  - Disconnetti dal server\n");
        sb.append("END_RESULT");
        sendResponse(sb.toString());
    }

    /**
     * Gestisce la disconnessione del client.
     */
    private void handleQuit(BallisticServer.ClientSession session) throws IOException {
        sendResponse("BYE");
        server.removeSession(session.getKey());
        log("Client '" + (session.username != null ? session.username : session.getKey()) + "' disconnesso");
    }

    /**
     * Invia una risposta al client.
     */
    private void sendResponse(String response) throws IOException {
        server.sendResponse(response, clientAddress, clientPort);
    }

    private void log(String message) {
        BallisticServer.log(message);
    }
}
