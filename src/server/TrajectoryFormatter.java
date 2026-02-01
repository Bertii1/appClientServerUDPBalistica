package server;

import server.BallisticCalculator.SimulationResult;
import server.BallisticCalculator.Point2D;
import java.util.List;
import java.util.Locale;

/**
 * Formatta i risultati della simulazione balistica
 * incluso un grafico ASCII della traiettoria.
 */
public class TrajectoryFormatter {

    private static final int GRAPH_WIDTH = 60;
    private static final int GRAPH_HEIGHT = 20;

    /**
     * Formatta i risultati completi della simulazione.
     */
    public static String formatResults(SimulationResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append("===== RISULTATI SIMULAZIONE =====\n\n");

        // Parametri
        sb.append(String.format("PARAMETRI: v0=%.1f m/s, angle=%.1f°, mass=%.3f kg, Cd=%.3f\n\n",
                result.initialVelocity, result.angle, result.mass, result.dragCoeff));

        // Risultati principali
        sb.append("RISULTATI:\n");
        sb.append(String.format("  - Gittata:      %.2f m\n", result.maxRange));
        sb.append(String.format("  - Altezza max:  %.2f m\n", result.maxHeight));
        sb.append(String.format("  - Tempo volo:   %.2f s\n\n", result.flightTime));

        // Grafico ASCII
        sb.append("TRAIETTORIA:\n");
        sb.append(generateAsciiGraph(result.trajectory, result.maxRange, result.maxHeight));
        sb.append("\n");

        // Punti campionati (versione ridotta per output testuale)
        sb.append("PUNTI CAMPIONATI:\n");
        for (int i = 0; i < result.trajectory.size(); i++) {
            Point2D p = result.trajectory.get(i);
            if (i < 10 || i >= result.trajectory.size() - 3 || i % 5 == 0) {
                sb.append(String.format("  t=%.1fs: (%.2f, %.2f)\n", p.t, p.x, p.y));
            } else if (i == 10) {
                sb.append("  ...\n");
            }
        }

        sb.append("\n==================================\n");

        // Dati strutturati per grafico JFreeChart (parsabili dal client)
        sb.append("TRAJECTORY_DATA_START\n");
        sb.append(String.format(Locale.US, "PARAMS:%.2f,%.2f,%.3f,%.3f\n",
                result.initialVelocity, result.angle, result.mass, result.dragCoeff));
        sb.append(String.format(Locale.US, "RESULTS:%.2f,%.2f,%.2f\n",
                result.maxRange, result.maxHeight, result.flightTime));
        sb.append("POINTS:");
        for (int i = 0; i < result.trajectory.size(); i++) {
            Point2D p = result.trajectory.get(i);
            if (i > 0) sb.append(";");
            sb.append(String.format(Locale.US, "%.2f,%.2f,%.2f", p.x, p.y, p.t));
        }
        sb.append("\n");
        sb.append("TRAJECTORY_DATA_END");

        return sb.toString();
    }

    /**
     * Genera un grafico ASCII della traiettoria.
     */
    private static String generateAsciiGraph(List<Point2D> trajectory,
                                             double maxRange, double maxHeight) {
        StringBuilder sb = new StringBuilder();

        // Crea griglia
        char[][] grid = new char[GRAPH_HEIGHT][GRAPH_WIDTH];
        for (int i = 0; i < GRAPH_HEIGHT; i++) {
            for (int j = 0; j < GRAPH_WIDTH; j++) {
                grid[i][j] = ' ';
            }
        }

        // Calcola scale
        double xScale = (maxRange > 0) ? (GRAPH_WIDTH - 5) / maxRange : 1;
        double yScale = (maxHeight > 0) ? (GRAPH_HEIGHT - 3) / maxHeight : 1;

        // Disegna assi
        for (int i = 0; i < GRAPH_HEIGHT - 1; i++) {
            grid[i][3] = '|';
        }
        for (int j = 3; j < GRAPH_WIDTH; j++) {
            grid[GRAPH_HEIGHT - 2][j] = '-';
        }
        grid[GRAPH_HEIGHT - 2][3] = '+';

        // Disegna punti traiettoria
        for (Point2D p : trajectory) {
            int gx = 4 + (int)(p.x * xScale);
            int gy = GRAPH_HEIGHT - 3 - (int)(p.y * yScale);

            if (gx >= 4 && gx < GRAPH_WIDTH && gy >= 0 && gy < GRAPH_HEIGHT - 2) {
                grid[gy][gx] = '*';
            }
        }

        // Aggiungi etichette asse Y
        String maxYLabel = String.format("%.0f", maxHeight);
        String midYLabel = String.format("%.0f", maxHeight / 2);

        if (maxYLabel.length() <= 3) {
            for (int i = 0; i < maxYLabel.length(); i++) {
                grid[1][i] = maxYLabel.charAt(i);
            }
        }

        // Aggiungi etichette asse X
        String maxXLabel = String.format("%.0fm", maxRange);
        int xLabelPos = Math.min(GRAPH_WIDTH - maxXLabel.length(), GRAPH_WIDTH - 5);
        for (int i = 0; i < maxXLabel.length() && xLabelPos + i < GRAPH_WIDTH; i++) {
            grid[GRAPH_HEIGHT - 1][xLabelPos + i] = maxXLabel.charAt(i);
        }

        // Etichetta origine
        grid[GRAPH_HEIGHT - 1][3] = '0';

        // Label "Y(m)" e "X(m)"
        grid[0][0] = 'Y';
        grid[0][1] = '(';
        grid[0][2] = 'm';
        grid[GRAPH_HEIGHT - 1][5] = 'X';
        grid[GRAPH_HEIGHT - 1][6] = '(';
        grid[GRAPH_HEIGHT - 1][7] = 'm';
        grid[GRAPH_HEIGHT - 1][8] = ')';

        // Costruisci stringa
        for (int i = 0; i < GRAPH_HEIGHT; i++) {
            sb.append(new String(grid[i]));
            sb.append("\n");
        }

        return sb.toString();
    }
}
