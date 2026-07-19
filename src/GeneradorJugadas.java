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

    private final int sumaMin;
    private final int sumaMax;
    private final Set<Integer> excluidos;
    private final Set<Integer> protegidos;

    public GeneradorJugadas(int sumaMin, int sumaMax, Set<Integer> excluidos, Set<Integer> protegidos) {
        this.sumaMin = sumaMin;
        this.sumaMax = sumaMax;
        this.excluidos = excluidos;
        this.protegidos = protegidos;
    }

    /** Ejecuta las estrategias en orden y devuelve el mapa de pesos resultante. */
    public Map<Integer, Double> calcularPesos(List<Sorteo> historial, List<AjustePeso> ajustes) {
        Map<Integer, Double> pesos = new HashMap<>();
        for (AjustePeso ajuste : ajustes) {
            ajuste.aplicar(pesos, historial);
        }
        return pesos;
    }

    /** Genera una jugada de 6 numeros a partir del mapa de pesos ya calculado. */
    public List<Integer> generar(Map<Integer, Double> pesos, List<Integer> ultimoSorteo) {
        List<Integer> bolsa = construirBolsaPonderada(pesos);
        Random rand = new Random();
        List<Integer> sugerencia;
        int intentos = 0;
        do {
            Set<Integer> conjunto = new TreeSet<>();

            // "Vecino del ultimo sorteo": nudge heuristico que suele usar Rafael.
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

            sugerencia = new ArrayList<>(conjunto);
            intentos++;
        } while (!cumpleReglas(sugerencia) && intentos < MAX_INTENTOS);

        if (intentos >= MAX_INTENTOS) {
            System.err.println("Aviso: no se hallo una combinacion que cumpliera todas las reglas tras "
                    + MAX_INTENTOS + " intentos; se devuelve la mejor aproximacion encontrada.");
        }
        return sugerencia;
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

        boolean sumaValida = suma >= sumaMin && suma <= sumaMax;
        boolean paridadValida = pares >= 2 && pares <= 4;
        boolean dispersa = contiguos <= 2;
        boolean sinExcluidosDuros = ordenada.stream().allMatch(n -> !excluidos.contains(n) || protegidos.contains(n));

        return sumaValida && paridadValida && dispersa && consecutivos >= 1 && sinExcluidosDuros;
    }
}
