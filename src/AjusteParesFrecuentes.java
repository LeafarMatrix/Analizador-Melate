import java.util.List;
import java.util.Map;

/**
 * Estrategia (AjustePeso) que incrementa el peso de los numeros que forman
 * parte de pares historicamente frecuentes.
 *
 * Funciona sobre el mapa ya poblado por PesoBaseRecencia: suma a cada numero
 * un bonus proporcional a la frecuencia acumulada de todos los pares en los
 * que ese numero participa dentro del top-N de pares.
 *
 * Parametros configurables:
 *   topPares    - cuantos pares del ranking considerar (defecto: 20)
 *   factorBonus - multiplicador del bonus por frecuencia de par (defecto: 0.5)
 *
 * Ejemplo: si el par (14, 27) aparecio 87 veces y factorBonus=0.5, tanto el
 * 14 como el 27 reciben +43.5 sobre su peso actual.
 */
public class AjusteParesFrecuentes implements AjustePeso {

    private static final int    TOP_PARES_DEFECTO    = 20;
    private static final double FACTOR_BONUS_DEFECTO = 0.5;

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
        for (EntradaFrecuencia<List<Integer>> entrada : analizador.topPares(topPares)) {
            double bonus = entrada.frecuencia() * factorBonus;
            for (int num : entrada.combinacion()) {
                pesos.merge(num, bonus, Double::sum);
            }
        }
    }
}