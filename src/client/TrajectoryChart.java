package client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Visualizza la traiettoria balistica con JFreeChart.
 */
public class TrajectoryChart {

    /**
     * Punto della traiettoria.
     */
    public static class TrajectoryPoint {
        public final double x;
        public final double y;
        public final double t;

        public TrajectoryPoint(double x, double y, double t) {
            this.x = x;
            this.y = y;
            this.t = t;
        }
    }

    /**
     * Dati della simulazione parsati dalla risposta del server.
     */
    public static class SimulationData {
        public double velocity;
        public double angle;
        public double mass;
        public double dragCoeff;
        public double maxRange;
        public double maxHeight;
        public double flightTime;
        public List<TrajectoryPoint> points = new ArrayList<>();
    }

    /**
     * Parsa i dati della traiettoria dalla risposta del server.
     * Cerca la sezione TRAJECTORY_DATA_START ... TRAJECTORY_DATA_END
     *
     * @param serverResponse risposta completa del server
     * @return SimulationData con i dati parsati, o null se non trovati
     */
    public static SimulationData parseTrajectoryData(String serverResponse) {
        int startIdx = serverResponse.indexOf("TRAJECTORY_DATA_START");
        int endIdx = serverResponse.indexOf("TRAJECTORY_DATA_END");

        if (startIdx == -1 || endIdx == -1) {
            return null;
        }

        String dataSection = serverResponse.substring(startIdx, endIdx);
        String[] lines = dataSection.split("\n");

        SimulationData data = new SimulationData();

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("PARAMS:")) {
                String paramsStr = line.substring(7).trim();
                if (!paramsStr.isEmpty()) {
                    String[] params = paramsStr.split(",");
                    if (params.length >= 4) {
                        data.velocity = Double.parseDouble(params[0]);
                        data.angle = Double.parseDouble(params[1]);
                        data.mass = Double.parseDouble(params[2]);
                        data.dragCoeff = Double.parseDouble(params[3]);
                    }
                }
            } else if (line.startsWith("RESULTS:")) {
                String[] results = line.substring(8).split(",");
                if (results.length >= 3) {
                    data.maxRange = Double.parseDouble(results[0]);
                    data.maxHeight = Double.parseDouble(results[1]);
                    data.flightTime = Double.parseDouble(results[2]);
                }
            } else if (line.startsWith("POINTS:")) {
                String pointsStr = line.substring(7);
                String[] pointsArray = pointsStr.split(";");
                for (String p : pointsArray) {
                    String[] coords = p.split(",");
                    if (coords.length >= 3) {
                        double x = Double.parseDouble(coords[0]);
                        double y = Double.parseDouble(coords[1]);
                        double t = Double.parseDouble(coords[2]);
                        data.points.add(new TrajectoryPoint(x, y, t));
                    }
                }
            }
        }

        return data.points.isEmpty() ? null : data;
    }

    /**
     * Mostra il grafico della traiettoria in una finestra Swing.
     *
     * @param data dati della simulazione
     */
    public static void showChart(SimulationData data) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Traiettoria Balistica - JFreeChart");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JFreeChart chart = createChart(data);
            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new Dimension(800, 600));

            frame.setContentPane(chartPanel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    /**
     * Crea il grafico JFreeChart.
     */
    private static JFreeChart createChart(SimulationData data) {
        XYSeries series = new XYSeries("Traiettoria");

        for (TrajectoryPoint p : data.points) {
            series.add(p.x, p.y);
        }

        XYSeriesCollection dataset = new XYSeriesCollection(series);

        String title = String.format("Traiettoria Balistica (v₀=%.1f m/s, θ=%.1f°)",
                data.velocity, data.angle);

        JFreeChart chart = ChartFactory.createXYLineChart(
                title,
                "Distanza (m)",
                "Altezza (m)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // Personalizza aspetto
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, new Color(0, 100, 180));
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6));
        plot.setRenderer(renderer);

        // Aggiungi annotazioni con i risultati
        String subtitle = String.format("Gittata: %.2f m | Altezza max: %.2f m | Tempo: %.2f s",
                data.maxRange, data.maxHeight, data.flightTime);
        chart.addSubtitle(new org.jfree.chart.title.TextTitle(subtitle));

        return chart;
    }

    /**
     * Metodo di convenienza: parsa e mostra il grafico.
     *
     * @param serverResponse risposta del server
     * @return true se il grafico è stato mostrato, false se i dati non sono validi
     */
    public static boolean parseAndShow(String serverResponse) {
        SimulationData data = parseTrajectoryData(serverResponse);
        if (data != null) {
            showChart(data);
            return true;
        }
        return false;
    }
}
