import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Da un empujon a numeros "calientes": los que aparecieron con mas
 * frecuencia de la esperada dentro de una ventana reciente de sorteos. Es
 * una heuristica, no una ley estadistica: los sorteos son independientes
 * entre si, asi que este ajuste solo cambia que combinaciones prioriza el
 * generador, no la probabilidad real de acertar.
 *
 * Version corregida: la regla anterior (+15% fijo a numeros <=15 que salieron
 * en el ULTIMO sorteo) no tenia ninguna base estadistica: el corte en 15 era
 * arbitrario y un solo sorteo es una muestra demasiado chica para hablar de
 * "tendencia". Ahora se usa una ventana de varios sorteos (20 por defecto),
 * se calcula la frecuencia esperada de cada numero en esa ventana bajo un
 * modelo uniforme (ventana * 6/56) y solo se bonifica a los numeros que
 * superan claramente esa expectativa, con un tope para no desbalancear el
 * resto de la ponderacion.
 */
public class AjusteTendenciaReciente implements AjustePeso {
    private static final int    VENTANA               = 20;
    private static final double UMBRAL_SOBRE_ESPERADO = 1.5;
    private static final double FACTOR_EXCESO         = 0.10;
    private static final double TOPE_BOOST            = 0.50; // maximo +50% sobre el peso base

    @Override
    public void aplicar(Map<Integer, Double> pesos, List<Sorteo> historial) {
        if (historial == null || historial.isEmpty()) return;
        int ventana = Math.min(VENTANA, historial.size());
        double esperado = ventana * 6.0 / GeneradorJugadas.TOTAL_NUMEROS;

        Map<Integer, Integer> conteo = new HashMap<>();
        for (int i = 0; i < ventana; i++) {
            for (int num : historial.get(i).numeros()) {
                conteo.merge(num, 1, Integer::sum);
            }
        }

        for (Map.Entry<Integer, Integer> e : conteo.entrySet()) {
            double razon = e.getValue() / esperado;
            if (razon <= UMBRAL_SOBRE_ESPERADO) continue;
            double boost = Math.min(TOPE_BOOST, (razon - 1.0) * FACTOR_EXCESO);
            int num = e.getKey();
            pesos.put(num, pesos.getOrDefault(num, 0.0) * (1.0 + boost));
        }
    }
}
