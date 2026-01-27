import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class TimeClient {

    public static void main(String[] args) {
        String hostname = "localhost";
        int port = 9876;

        try (DatagramSocket socket = new DatagramSocket()) {
            // Timeout opzionale: se il server non risponde in 5 secondi, il client lancia un'eccezione
            socket.setSoTimeout(5000);

            // 1. Invio richiesta (payload vuoto o messaggio a piacere)
            byte[] sendBuffer = "TIME_REQUEST".getBytes();
            InetAddress address = InetAddress.getByName(hostname);

            DatagramPacket packetToSend = new DatagramPacket(
                    sendBuffer, sendBuffer.length, address, port);

            socket.send(packetToSend);
            System.out.println("Richiesta inviata.");

            // 2. Attesa risposta
            byte[] receiveBuffer = new byte[1024];
            DatagramPacket packetReceived = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            socket.receive(packetReceived); // Bloccante

            // 3. Stampa risultato
            String response = new String(packetReceived.getData(), 0, packetReceived.getLength());
            System.out.println("Server dice: " + response);

        } catch (IOException e) {
            System.err.println("Errore o Timeout: " + e.getMessage());
        }
    }
}