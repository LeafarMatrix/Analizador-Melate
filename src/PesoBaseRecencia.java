import java.util.List;
import java.util.Map;

/**
 * Estrategia base: asigna a cada numero un peso que decae exponencialmente
 * segun que tan antigua es la aparicion (indice 0 = sorteo mas reciente).
 * Debe ejecutarse primero en la lista de ajustes: las demas estrategias
 * asumen que el mapa ya tiene una base de pesos sobre la cual sumar o
 * multiplicar.
 */
public class PesoBaseRecencia implements AjustePeso {
    private final double decaimiento;

    public PesoBaseRecencia(double decaimiento) {
        this.decaimiento = decaimiento;
    }

    @Override
    public void aplicar(Map<Integer, Double> pesos, List<Sorteo> historial) {
        for (int i = 0; i < historial.size(); i++) {
            double peso = Math.pow(decaimiento, i);
            for (int num : historial.get(i).numeros()) {
                pesos.merge(num, peso, Double::sum);
            }
        }
    }
}
