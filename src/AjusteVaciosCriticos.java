import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inyecta peso extra a numeros de una zona configurable (por defecto 20-30)
 * que no han aparecido en los ultimos N sorteos ("vacios criticos"), bajo la
 * hipotesis de que tienden a "regresar" tras una ausencia prolongada. Igual
 * que AjusteTendenciaReciente, es una heuristica de diversificacion, no una
 * ventaja estadistica real.
 */
public class AjusteVaciosCriticos implements AjustePeso {
    private static final int ZONA_INICIO = 20;
    private static final int ZONA_FIN = 30;
    private static final int VENTANA = 15;
    private static final double PESO_EXTRA = 5.0;

    @Override
    public void aplicar(Map<Integer, Double> pesos, List<Sorteo> historial) {
        Set<Integer> recientes = new HashSet<>();
        for (int i = 0; i < Math.min(VENTANA, historial.size()); i++) {
            recientes.addAll(historial.get(i).numeros());
        }
        for (int n = ZONA_INICIO; n <= ZONA_FIN; n++) {
            if (!recientes.contains(n)) {
                pesos.put(n, pesos.getOrDefault(n, 0.0) + PESO_EXTRA);
            }
        }
    }
}
