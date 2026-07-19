import java.util.List;
import java.util.Map;

/**
 * Da un empujon (+15%) a los numeros bajos (<=15) que salieron en el ultimo
 * sorteo registrado, bajo la hipotesis de "racha" de corto plazo. Es una
 * heuristica, no una ley estadistica: los sorteos son independientes entre
 * si, asi que este ajuste solo cambia que combinaciones prioriza el
 * generador, no la probabilidad real de acertar.
 */
public class AjusteTendenciaReciente implements AjustePeso {
    private static final int LIMITE_NUMERO_BAJO = 15;
    private static final double FACTOR = 1.15;

    @Override
    public void aplicar(Map<Integer, Double> pesos, List<Sorteo> historial) {
        if (historial == null || historial.isEmpty()) return;
        for (int num : historial.get(0).numeros()) {
            if (num <= LIMITE_NUMERO_BAJO) {
                pesos.put(num, pesos.getOrDefault(num, 0.0) * FACTOR);
            }
        }
    }
}
