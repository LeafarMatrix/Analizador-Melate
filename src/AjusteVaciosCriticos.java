import java.util.List;
import java.util.Map;

/**
 * Inyecta peso extra a numeros "atrasados" (que llevan mas sorteos sin salir
 * de lo que le tocaria bajo un modelo uniforme), bajo la hipotesis de que
 * tienden a "regresar" tras una ausencia prolongada. Es una heuristica de
 * diversificacion, no una ventaja estadistica real: en un sorteo aleatorio
 * cada numero tiene la misma probabilidad en cada tirada, sin memoria de lo
 * que paso antes.
 *
 * Version corregida: antes se usaba una zona fija y arbitraria (20-30) sin
 * ninguna justificacion, que ni siquiera se recalculaba con base en cuando
 * aparecio cada numero por ultima vez dentro de esa zona. Ahora se calcula,
 * para CADA numero del 1 al 56, cuantos sorteos han pasado desde su ultima
 * aparicion ("hueco"), se compara contra el hueco esperado bajo un sorteo
 * uniforme (56/6 ~= 9.33 sorteos) y solo se bonifica a los que superan ese
 * umbral, en proporcion a cuanto se han atrasado.
 */
public class AjusteVaciosCriticos implements AjustePeso {
    private static final double PESO_EXTRA_MAX = 4.0;
    /** Umbral: solo se considera "atrasado" si el hueco supera 1.5x lo esperado. */
    private static final double UMBRAL_SOBRE_ESPERADO = 1.5;

    @Override
    public void aplicar(Map<Integer, Double> pesos, List<Sorteo> historial) {
        int n = historial.size();
        if (n == 0) return;

        double huecoEsperado = (double) GeneradorJugadas.TOTAL_NUMEROS
                / GeneradorJugadas.NUMEROS_POR_SORTEO; // ~9.33 sorteos

        for (int num = 1; num <= GeneradorJugadas.TOTAL_NUMEROS; num++) {
            int hueco = huecoDesdeUltimaAparicion(num, historial);
            double razon = hueco / huecoEsperado;
            if (razon <= UMBRAL_SOBRE_ESPERADO) continue;
            double bonus = Math.min(PESO_EXTRA_MAX, razon - 1.0);
            pesos.merge(num, bonus, Double::sum);
        }
    }

    /** Sorteos transcurridos (indice 0 = mas reciente) desde la ultima vez que salio el numero. */
    private int huecoDesdeUltimaAparicion(int num, List<Sorteo> historial) {
        for (int i = 0; i < historial.size(); i++) {
            if (historial.get(i).numeros().contains(num)) return i;
        }
        return historial.size(); // nunca aparecio en el historico cargado
    }
}
