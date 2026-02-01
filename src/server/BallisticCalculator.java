package server;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcola traiettorie balistiche con resistenza dell'aria.
 * Utilizza integrazione numerica (metodo di Eulero).
 */
public class BallisticCalculator {

    // Costanti fisiche
    private static final double G = 9.81;              // Accelerazione gravitazionale (m/s²)
    private static final double AIR_DENSITY = 1.225;   // Densità aria a livello del mare (kg/m³)
    private static final double FRONTAL_AREA = 0.01;   // Area frontale proiettile (m²)
    private static final double DT = 0.01;             // Passo temporale (s)
    private static final double SAMPLE_INTERVAL = 0.1; // Intervallo campionamento (s)

    /**
     * Punto 2D per la traiettoria.
     */
    public static class Point2D {
        public final double x;
        public final double y;
        public final double t;

        public Point2D(double x, double y, double t) {
            this.x = x;
            this.y = y;
            this.t = t;
        }
    }

    /**
     * Risultato della simulazione.
     */
    public static class SimulationResult {
        public final double maxRange;
        public final double maxHeight;
        public final double flightTime;
        public final List<Point2D> trajectory;
        public final double initialVelocity;
        public final double angle;
        public final double mass;
        public final double dragCoeff;

        public SimulationResult(double maxRange, double maxHeight, double flightTime,
                               List<Point2D> trajectory, double v0, double angle,
                               double mass, double dragCoeff) {
            this.maxRange = maxRange;
            this.maxHeight = maxHeight;
            this.flightTime = flightTime;
            this.trajectory = trajectory;
            this.initialVelocity = v0;
            this.angle = angle;
            this.mass = mass;
            this.dragCoeff = dragCoeff;
        }
    }

    /**
     * Esegue la simulazione e ritorna i risultati formattati.
     */
    public static String simulate(double velocity, double angle, double mass, double dragCoeff) {
        SimulationResult result = calculateTrajectory(velocity, angle, mass, dragCoeff);
        return TrajectoryFormatter.formatResults(result);
    }

    /**
     * Calcola la traiettoria con resistenza dell'aria.
     */
    public static SimulationResult calculateTrajectory(double velocity, double angleDeg,
                                                        double mass, double dragCoeff) {
        // Converti angolo in radianti
        double angleRad = Math.toRadians(angleDeg);

        // Velocità iniziali
        double vX = velocity * Math.cos(angleRad);
        double vY = velocity * Math.sin(angleRad);

        // Posizione iniziale
        double x = 0;
        double y = 0;
        double t = 0;

        // Variabili per tracciamento
        double maxHeight = 0;
        double lastSampleTime = 0;
        List<Point2D> trajectory = new ArrayList<>();

        // Aggiungi punto iniziale
        trajectory.add(new Point2D(x, y, t));

        // Loop di simulazione
        while (y >= 0) {
            // Calcola velocità totale
            double speed = Math.sqrt(vX * vX + vY * vY);

            if (speed > 0) {
                // Calcola forza di drag: F_drag = 0.5 * rho * Cd * A * v²
                double dragForce = 0.5 * AIR_DENSITY * dragCoeff * FRONTAL_AREA * speed * speed;

                // Calcola accelerazioni (drag si oppone al moto)
                double aX = -(dragForce * vX / speed) / mass;
                double aY = -G - (dragForce * vY / speed) / mass;

                // Aggiorna velocità (metodo di Eulero)
                vX += aX * DT;
                vY += aY * DT;
            } else {
                // Solo gravità se fermo
                vY -= G * DT;
            }

            // Aggiorna posizione
            x += vX * DT;
            y += vY * DT;
            t += DT;

            // Traccia altezza massima
            if (y > maxHeight) {
                maxHeight = y;
            }

            // Campiona ogni SAMPLE_INTERVAL secondi
            if (t - lastSampleTime >= SAMPLE_INTERVAL) {
                trajectory.add(new Point2D(x, Math.max(0, y), t));
                lastSampleTime = t;
            }

            // Sicurezza: evita loop infiniti
            if (t > 1000) {
                break;
            }
        }

        // Aggiungi punto finale (impatto)
        if (trajectory.get(trajectory.size() - 1).t < t - DT) {
            trajectory.add(new Point2D(x, 0, t));
        }

        return new SimulationResult(x, maxHeight, t, trajectory, velocity, angleDeg, mass, dragCoeff);
    }

    /**
     * Metodo main per testing del calcolo.
     */
    public static void main(String[] args) {
        System.out.println("=== TEST BALLISTIC CALCULATOR ===\n");

        // Test 1: Lancio classico a 45 gradi senza drag significativo
        System.out.println("Test 1: Proiettile leggero a 45°");
        System.out.println(simulate(100, 45, 0.5, 0.47));

        System.out.println("\n" + "=".repeat(50) + "\n");

        // Test 2: Lancio verticale
        System.out.println("Test 2: Lancio quasi verticale");
        System.out.println(simulate(50, 85, 1.0, 0.47));

        System.out.println("\n" + "=".repeat(50) + "\n");

        // Test 3: Tiro teso
        System.out.println("Test 3: Tiro teso a 15°");
        System.out.println(simulate(200, 15, 0.1, 0.3));

        // Verifica teorica (senza drag): gittata = v²*sin(2θ)/g
        double v = 100, theta = 45;
        double theoreticalRange = (v * v * Math.sin(Math.toRadians(2 * theta))) / G;
        System.out.println("\n[VERIFICA] Gittata teorica senza drag per v=100, θ=45°: "
                          + String.format("%.2f m", theoreticalRange));
        System.out.println("(La gittata reale sarà minore a causa del drag)");
    }
}
