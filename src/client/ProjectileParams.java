package client;

/**
 * Rappresenta i parametri di un proiettile per la simulazione balistica.
 */
public class ProjectileParams {
    private double velocity;    // Velocità iniziale (m/s)
    private double angle;       // Angolo di lancio (gradi)
    private double mass;        // Massa (kg)
    private double dragCoeff;   // Coefficiente di drag

    /**
     * Costruttore con tutti i parametri.
     */
    public ProjectileParams(double velocity, double angle, double mass, double dragCoeff) {
        this.velocity = velocity;
        this.angle = angle;
        this.mass = mass;
        this.dragCoeff = dragCoeff;
    }

    /**
     * Valida i parametri del proiettile.
     * @return null se valido, messaggio di errore altrimenti
     */
    public String validate() {
        StringBuilder errors = new StringBuilder();

        if (velocity <= 0) {
            errors.append("Velocità deve essere > 0. ");
        }
        if (velocity > 10000) {
            errors.append("Velocità troppo alta (max 10000 m/s). ");
        }
        if (angle < 0 || angle > 90) {
            errors.append("Angolo deve essere tra 0 e 90 gradi. ");
        }
        if (mass <= 0) {
            errors.append("Massa deve essere > 0. ");
        }
        if (mass > 1000) {
            errors.append("Massa troppo alta (max 1000 kg). ");
        }
        if (dragCoeff <= 0) {
            errors.append("Coefficiente di drag deve essere > 0. ");
        }
        if (dragCoeff > 2) {
            errors.append("Coefficiente di drag troppo alto (max 2). ");
        }

        return errors.length() > 0 ? errors.toString().trim() : null;
    }

    /**
     * Converte i parametri nel formato del protocollo.
     * @return stringa "SIMULATE velocity angle mass dragCoeff"
     */
    public String toProtocolString() {
        return String.format(java.util.Locale.US, "SIMULATE %.4f %.4f %.4f %.4f",
                velocity, angle, mass, dragCoeff);
    }

    // Getters
    public double getVelocity() { return velocity; }
    public double getAngle() { return angle; }
    public double getMass() { return mass; }
    public double getDragCoeff() { return dragCoeff; }

    // Setters
    public void setVelocity(double velocity) { this.velocity = velocity; }
    public void setAngle(double angle) { this.angle = angle; }
    public void setMass(double mass) { this.mass = mass; }
    public void setDragCoeff(double dragCoeff) { this.dragCoeff = dragCoeff; }

    @Override
    public String toString() {
        return String.format("ProjectileParams[v=%.2f m/s, angle=%.1f°, mass=%.3f kg, Cd=%.3f]",
                velocity, angle, mass, dragCoeff);
    }

    // Factory methods per esempi preimpostati
    public static ProjectileParams medievalCannon() {
        return new ProjectileParams(100, 45, 5, 0.47);
    }

    public static ProjectileParams modernBullet() {
        return new ProjectileParams(800, 30, 0.15, 0.295);
    }

    public static ProjectileParams parabolicThrow() {
        return new ProjectileParams(20, 60, 0.5, 0.47);
    }
}
