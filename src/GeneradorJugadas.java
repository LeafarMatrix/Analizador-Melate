import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Orquesta la generacion de jugadas: aplica una lista de estrategias
 * AjustePeso sobre el historial para construir un mapa de pesos, anula el
 * peso de numeros excluidos (salvo que esten protegidos), y muestrea
 * combinaciones que cumplan reglas estructurales basicas (suma en rango,
 * paridad balanceada, dispersion, al menos un par consecutivo).
 *
 * MEJORAS respecto a la version anterior:
 *   - generarVarias(): genera N jugadas garantizando diversidad minima entre
 *     ellas (controlada por minDiferencia). Antes el codigo llamante
 *     invocaba generar() N veces sin control de similitud.
 *   - La regla de diversidad se aplica comparando cuantos numeros comparten
 *     dos jugadas: si comparten mas de (6 - minDiferencia), se descarta la
 *     candidata y se reintenta.
 *
 * Nota importante: nada de esto mejora la probabilidad real de acertar un
 * sorteo aleatorio. Solo prioriza numeros segun las estrategias activas y
 * evita combinaciones estadisticamente atipicas (todo par, todo en una
 * franja angosta, etc), utiles para diversificar jugadas dentro de un
 * presupuesto.
 */
public class GeneradorJugadas {
    public static final int TOTAL_NUMEROS = 56;
    public static final int NUMEROS_POR_SORTEO = 6;
    private static final int MAX_INTENTOS = 30_000;

    /** Minimo de numeros DISTINTOS que deben tener dos jugadas entre si. */
    private static final int MIN_DIFERENCIA_DEFECTO = 3;

    private final int sumaMin;
    private final int sumaMax;
    private final Set<Integer> excluidos;
    private final Set<Integer> protegidos;
    private final int minDiferencia;

    public GeneradorJugadas(int sumaMin, int sumaMax, Set<Integer> excluidos, Set<Integer> protegidos) {
        this(sumaMin, sumaMax, excluidos, protegidos, MIN_DIFERENCIA_DEFECTO);
    }

    public GeneradorJugadas(int sumaMin, int sumaMax, Set<Integer> excluidos,
                            Set<Integer> protegidos, int minDiferencia) {
        this.sumaMin = sumaMin;
        this.sumaMax = sumaMax;
        this.excluidos = excluidos;
        this.protegidos = protegidos;
        this.minDiferencia = minDiferencia;
    }

    /** Ejecuta las estrategias en orden y devuelve el mapa de pesos resultante. */
    public Map<Integer, Double> calcularPesos(List<Sorteo> historial, List<AjustePeso> ajustes) {
        Map<Integer, Double> pesos = new HashMap<>();
        for (AjustePeso ajuste : ajustes) {
            ajuste.aplicar(pesos, historial);
        }
        return pesos;
    }

    /**
     * Genera N jugadas diversas a partir del mapa de pesos.
     * Cada jugada nueva debe diferir en al menos minDiferencia numeros
     * de todas las jugadas ya aceptadas.
     *
     * @param pesos        mapa de pesos ya calculado
     * @param ultimoSorteo numeros del sorteo mas reciente (para el nudge heuristico)
     * @param cantidad     cuantas jugadas generar
     * @return lista de jugadas aceptadas (puede ser menor que cantidad si
     *         los criterios son muy restrictivos)
     */
    public List<List<Integer>> generarVarias(Map<Integer, Double> pesos,
                                              List<Integer> ultimoSorteo,
                                              int cantidad) {
        List<Integer> bolsa = construirBolsaPonderada(pesos);
        List<List<Integer>> aceptadas = new ArrayList<>();
        int intentos = 0;

        while (aceptadas.size() < cantidad && intentos < MAX_INTENTOS) {
            intentos++;
            List<Integer> candidata = muestrear(bolsa, ultimoSorteo);
            if (!cumpleReglas(candidata)) continue;
            if (!esDiversa(candidata, aceptadas)) continue;
            aceptadas.add(candidata);
        }

        if (aceptadas.size() < cantidad) {
            System.err.printf("Aviso: solo se generaron %d/%d jugadas con los criterios actuales " +
                    "(suma %d-%d, diferencia min %d). Considera ampliar el rango de suma o " +
                    "reducir minDiferencia.%n",
                    aceptadas.size(), cantidad, sumaMin, sumaMax, minDiferencia);
        }
        return aceptadas;
    }

    /**
     * Genera una sola jugada (compatibilidad con codigo existente).
     * Para generar varias con diversidad usar generarVarias().
     */
    public List<Integer> generar(Map<Integer, Double> pesos, List<Integer> ultimoSorteo) {
        List<Integer> bolsa = construirBolsaPonderada(pesos);
        int intentos = 0;
        List<Integer> sugerencia;
        do {
            sugerencia = muestrear(bolsa, ultimoSorteo);
            intentos++;
        } while (!cumpleReglas(sugerencia) && intentos < MAX_INTENTOS);

        if (intentos >= MAX_INTENTOS) {
            System.err.println("Aviso: no se hallo una combinacion que cumpliera todas las reglas tras "
                    + MAX_INTENTOS + " intentos; se devuelve la mejor aproximacion encontrada.");
        }
        return sugerencia;
    }

    // ─── helpers privados ────────────────────────────────────────────────────

    private List<Integer> muestrear(List<Integer> bolsa, List<Integer> ultimoSorteo) {
        Random rand = new Random();
        Set<Integer> conjunto = new TreeSet<>();

        // "Vecino del ultimo sorteo": nudge heuristico
        if (ultimoSorteo != null && !ultimoSorteo.isEmpty()) {
            int base = ultimoSorteo.get(rand.nextInt(ultimoSorteo.size()));
            int vecino = rand.nextBoolean() ? base + 1 : base - 1;
            vecino = Math.max(2, Math.min(TOTAL_NUMEROS - 1, vecino));
            conjunto.add(vecino);
        }

        while (conjunto.size() < NUMEROS_POR_SORTEO) {
            int candidato = bolsa.isEmpty()
                    ? rand.nextInt(TOTAL_NUMEROS) + 1
                    : bolsa.get(rand.nextInt(bolsa.size()));
            conjunto.add(candidato);
        }

        return new ArrayList<>(conjunto);
    }

    /**
     * Verifica que la jugada nueva difiera en al menos minDiferencia numeros
     * de cada una de las jugadas ya aceptadas.
     */
    private boolean esDiversa(List<Integer> nueva, List<List<Integer>> aceptadas) {
        Set<Integer> setNueva = new TreeSet<>(nueva);
        for (List<Integer> aceptada : aceptadas) {
            long comunes = aceptada.stream().filter(setNueva::contains).count();
            if (comunes > NUMEROS_POR_SORTEO - minDiferencia) return false;
        }
        return true;
    }

    private List<Integer> construirBolsaPonderada(Map<Integer, Double> pesos) {
        List<Integer> bolsa = new ArrayList<>();
        double max = pesos.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        if (max <= 0) return bolsa;
        pesos.forEach((num, peso) -> {
            if (excluidos.contains(num) && !protegidos.contains(num)) return;
            if (peso <= 0) return;
            double normalizado = peso / max;
            int copias = (int) Math.ceil(10 * Math.pow(normalizado, 1.5));
            for (int i = 0; i < copias; i++) bolsa.add(num);
        });
        return bolsa;
    }

    private boolean cumpleReglas(List<Integer> lista) {
        List<Integer> ordenada = new ArrayList<>(lista);
        Collections.sort(ordenada);

        int suma = ordenada.stream().mapToInt(Integer::intValue).sum();
        long pares = ordenada.stream().filter(n -> n % 2 == 0).count();
        int consecutivos = 0, contiguos = 0;
        for (int i = 0; i < ordenada.size() - 1; i++) {
            int diff = ordenada.get(i + 1) - ordenada.get(i);
            if (diff == 1) consecutivos++;
            if (diff <= 2) contiguos++;
        }

        boolean sumaValida    = suma >= sumaMin && suma <= sumaMax;
        boolean paridadValida = pares >= 2 && pares <= 4;
        boolean dispersa      = contiguos <= 2;
        boolean sinExcluidos  = ordenada.stream()
                .allMatch(n -> !excluidos.contains(n) || protegidos.contains(n));

        return sumaValida && paridadValida && dispersa && consecutivos >= 1 && sinExcluidos;
    }
}
