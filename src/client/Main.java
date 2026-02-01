package client;

import java.io.Console;
import java.util.Scanner;

/**
 * Interfaccia utente a console per il calcolatore balistico.
 */
public class Main {
    private static Scanner scanner = new Scanner(System.in);
    private static BallisticClient client;

    public static void main(String[] args) {
        try {
            printWelcome();

            client = new BallisticClient();

            // Connessione al server
            System.out.print("Connessione al server...");
            if (!client.connect()) {
                System.out.println(" FALLITA");
                System.out.println("Assicurati che il server sia in esecuzione su localhost:5000");
                return;
            }
            System.out.println(" OK");

            // Autenticazione
            if (!login()) {
                client.disconnect();
                return;
            }

            // Menu principale
            mainMenu();

            // Disconnessione
            client.disconnect();
            System.out.println("\nArrivederci!");
        } finally {
            scanner.close();
        }
    }

    private static void printWelcome() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("       CALCOLATORE BALISTICO v1.0");
        System.out.println("   Simulazione traiettorie con resistenza aria");
        System.out.println("=".repeat(50) + "\n");
    }

    private static boolean login() {
        int attempts = 0;
        final int MAX_ATTEMPTS = 3;

        while (attempts < MAX_ATTEMPTS) {
            System.out.print("\nUsername: ");
            String username = scanner.nextLine().trim();

            System.out.print("Password: ");
            String password = readPassword();

            String response = client.authenticate(username, password);

            if (response != null && response.equals("OK")) {
                System.out.println("\n[OK] Autenticazione riuscita!\n");
                return true;
            } else {
                attempts++;
                System.out.println("\n" + (response != null ? response : "Errore di connessione"));
                if (attempts < MAX_ATTEMPTS) {
                    System.out.println("Tentativi rimasti: " + (MAX_ATTEMPTS - attempts));
                }
            }
        }

        System.out.println("\nTroppi tentativi falliti. Uscita.");
        return false;
    }

    private static String readPassword() {
        Console console = System.console();
        if (console != null) {
            char[] passwordChars = console.readPassword();
            return new String(passwordChars);
        } else {
            // Fallback se console non disponibile (es. IDE)
            return scanner.nextLine();
        }
    }

    private static void mainMenu() {
        while (true) {
            System.out.println("\n=== MENU PRINCIPALE ===");
            System.out.println("1. Nuova simulazione");
            System.out.println("2. Esempi preimpostati");
            System.out.println("3. Guida comandi");
            System.out.println("4. Esci");
            System.out.print("\nScelta: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    newSimulation();
                    break;
                case "2":
                    presetExamples();
                    break;
                case "3":
                    showHelp();
                    break;
                case "4":
                    return;
                default:
                    System.out.println("Scelta non valida.");
            }
        }
    }

    private static void newSimulation() {
        System.out.println("\n--- NUOVA SIMULAZIONE ---\n");

        double velocity = readDouble("Velocità iniziale (m/s): ", 1, 10000);
        double angle = readDouble("Angolo di lancio (0-90°): ", 0, 90);
        double mass = readDouble("Massa proiettile (kg): ", 0.001, 1000);

        System.out.print("Coefficiente drag (default 0.47 per sfera): ");
        String dragInput = scanner.nextLine().trim();
        double dragCoeff = dragInput.isEmpty() ? 0.47 : Double.parseDouble(dragInput);

        ProjectileParams params = new ProjectileParams(velocity, angle, mass, dragCoeff);

        System.out.println("\nParametri: " + params);
        System.out.println("\n[Invio richiesta al server...]\n");

        String result = client.sendSimulation(params);
        displayResultWithChart(result);

        waitForEnter();
    }

    private static void presetExamples() {
        System.out.println("\n--- ESEMPI PREIMPOSTATI ---");
        System.out.println("1. Cannone medievale (v=100 m/s, θ=45°, m=5 kg)");
        System.out.println("2. Proiettile moderno (v=800 m/s, θ=30°, m=0.15 kg)");
        System.out.println("3. Lancio parabolico (v=20 m/s, θ=60°, m=0.5 kg)");
        System.out.println("4. Torna al menu");
        System.out.print("\nScelta: ");

        String choice = scanner.nextLine().trim();
        ProjectileParams params = null;

        switch (choice) {
            case "1":
                params = ProjectileParams.medievalCannon();
                break;
            case "2":
                params = ProjectileParams.modernBullet();
                break;
            case "3":
                params = ProjectileParams.parabolicThrow();
                break;
            case "4":
                return;
            default:
                System.out.println("Scelta non valida.");
                return;
        }

        System.out.println("\nSimulazione: " + params);
        System.out.println("\n[Invio richiesta al server...]\n");

        String result = client.sendSimulation(params);
        displayResultWithChart(result);

        waitForEnter();
    }

    /**
     * Mostra i risultati testuali e apre il grafico JFreeChart (se disponibile).
     */
    private static void displayResultWithChart(String result) {
        // Mostra output testuale (senza i dati strutturati)
        int dataStart = result.indexOf("TRAJECTORY_DATA_START");
        if (dataStart > 0) {
            System.out.println(result.substring(0, dataStart));
        } else {
            System.out.println(result);
        }

        // Apri grafico JFreeChart (se la libreria e' disponibile)
        try {
            if (TrajectoryChart.parseAndShow(result)) {
                System.out.println("[Grafico JFreeChart aperto in una nuova finestra]");
            }
        } catch (NoClassDefFoundError e) {
            System.out.println("[JFreeChart non disponibile - solo output testuale]");
        }
    }

    private static void showHelp() {
        String help = client.sendHelp();
        System.out.println("\n" + help);
        waitForEnter();
    }

    private static double readDouble(String prompt, double min, double max) {
        while (true) {
            System.out.print(prompt);
            try {
                String input = scanner.nextLine().trim();
                double value = Double.parseDouble(input);
                if (value >= min && value <= max) {
                    return value;
                }
                System.out.printf("Valore deve essere tra %.2f e %.2f%n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("Inserisci un numero valido.");
            }
        }
    }

    private static void waitForEnter() {
        System.out.print("\nPremi INVIO per continuare...");
        scanner.nextLine();
    }
}
