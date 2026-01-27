import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeServerIterative {

    public static void main(String[] args) {
        int port = 9876;
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Server UDP (Single Thread) avviato sulla porta " + port);

            // Buffer riutilizzabile per la ricezione (opzionale, si può anche ricreare)
            byte[] receiveBuffer = new byte[1024];

            while (true) {
                // 1. Preparazione pacchetto di ricezione
                // È buona norma resettare il pacchetto o ricrearlo nel loop per evitare dati "sporchi"
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

                // 2. Attesa bloccante del pacchetto
                // Il server si ferma qui finché non arriva qualcosa.
                // Nessun altro client può essere servito mentre siamo fermi qui o nelle righe successive.
                socket.receive(receivePacket);

                // --- INIZIO ELABORAZIONE ---

                // Recuperiamo i dati del mittente dal pacchetto appena arrivato
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                // Log a video
                System.out.println("Richiesta da: " + clientAddress + ":" + clientPort);

                // Generiamo la data
                String dateString = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());
                String message = "Data corrente: " + dateString;

                // Conversione in byte
                byte[] sendBuffer = message.getBytes();

                // 3. Invio della risposta
                DatagramPacket sendPacket = new DatagramPacket(
                        sendBuffer,
                        sendBuffer.length,
                        clientAddress,
                        clientPort
                );

                socket.send(sendPacket);
                System.out.println("Risposta inviata a " + clientAddress + ":" + clientPort);

                // --- FINE ELABORAZIONE ---
                // Il ciclo ricomincia e il server torna in ascolto su receive()
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}