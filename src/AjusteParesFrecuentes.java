import java.util.List;
import java.util.Map;

/**
 * Estrategia (AjustePeso) que incrementa el peso de los numeros que forman
 * parte de pares historicamente frecuentes.
 *
 * Version corregida: la anterior sumaba la frecuencia CRUDA del par
 * (ej. 40-90 apariciones en miles de sorteos) multiplicada por 0.5, lo que
 * generaba bonos de 20-45 puntos que aplastaban por completo la base de
 * PesoBaseRecencia (tipicamente 0-8) y el resto de ajustes. En la practica
 * el generador terminaba dependiendo casi solo de esta estrategia.
 *
 * Ahora se compara la frecuencia observada del par contra la frecuencia
 * ESPERADA bajo un sorteo uniforme (6 numeros de 56 sin sesgo), y solo se
 * bonifica el "exceso" relativo. Esto mantiene el bono en una escala
 * comparable (0-5 aprox.) al resto de estrategias y evita que un par con
 * mucho historial (simplemente por llevar mas sorteos jugados) domine el
 * resultado.
 *
 * Parametros configurables:
 *   topPares    - cuantos pares del ranking considerar (defecto: 20)
 *   factorBonus - multiplicador del exceso relativo (defecto: 1.2)
 *
 * Nota: aun con esta correccion, esto sigue siendo una heuristica de
 * diversificacion. Los sorteos son independientes; la frecuencia pasada de
 * un par no aumenta la probabilidad real de que vuelva a salir.
 */
public class AjusteParesFrecuentes implements AjustePeso {

    private static final int    TOP_PARES_DEFECTO    = 20;
    private static final double FACTOR_BONUS_DEFECTO = 1.2;

    /** P(dos numeros especificos coincidan en un mismo sorteo de 6 de 56) = (6*5)/(56*55). */
    private static final double PROB_PAR = 30.0 / (56.0 * 55.0);

    private final AnalizadorParesTrios analizador;
    private final int    topPares;
    private final double factorBonus;

    public AjusteParesFrecuentes(AnalizadorParesTrios analizador) {
        this(analizador, TOP_PARES_DEFECTO, FACTOR_BONUS_DEFECTO);
    }

    public AjusteParesFrecuentes(AnalizadorParesTrios analizador, int topPares, double factorBonus) {
        this.analizador  = analizador;
        this.topPares    = topPares;
        this.factorBonus = factorBonus;
    }

    @Override
    public void aplicar(Map<Integer, Double> pesos, List<Sorteo> historial) {
        int n = historial.size();
        if (n == 0) return;
        double esperado = Math.max(1.0, n * PROB_PAR);

        for (EntradaFrecuencia<List<Integer>> entrada : analizador.topPares(topPares)) {
            double exceso = (entrada.frecuencia() / esperado) - 1.0;
            if (exceso <= 0) continue; // no bonificar pares en linea o por debajo de lo esperado
            double bonus = exceso * factorBonus;
            for (int num : entrada.combinacion()) {
                pesos.merge(num, bonus, Double::sum);
            }
        }
    }
}