import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

/**
 * MelateAnalizadorAvanzado
 *
 * Lee el historico oficial de sorteos (historico_melate.txt, formato CSV
 * NPRODUCTO,CONCURSO,R1..R6,R7,BOLSA,FECHA), calcula la frecuencia real de
 * aparicion de cada numero y genera UNA combinacion sugerida usando muestreo
 * aleatorio ponderado por esa frecuencia.
 *
 * NOTA IMPORTANTE (leer antes de usar):
 * El Melate es un sorteo de extraccion fisica independiente en cada edicion.
 * La frecuencia historica de un numero NO afecta la probabilidad de que
 * salga en el proximo sorteo (falacia del jugador). Este programa no predice
 * resultados ni mejora las probabilidades reales de ganar: solo ofrece un
 * criterio de seleccion basado en datos reales, en vez de numeros puramente
 * al azar o de heuristicas inventadas.
 */
public class MelateAnalizadorAvanzado {

    private static final int NUM_MIN = 1;
    private static final int NUM_MAX = 56;
    private static final int NUMEROS_POR_JUGADA = 6;

    private static final String ARCHIVO_HISTORICO = "historico_melate.txt";
    private static final String ARCHIVO_ULTIMO_CONCURSO = "ultimo_concurso.txt";

    public static void main(String[] args) {
        try {
            List<int[]> sorteos = leerHistorico(ARCHIVO_HISTORICO);
            System.out.println("Sorteos leidos del historico: " + sorteos.size());

            if (sorteos.isEmpty()) {
                System.out.println("No hay datos suficientes en " + ARCHIVO_HISTORICO + ". Aborta.");
                return;
            }

            Map<Integer, Integer> frecuencia = calcularFrecuencia(sorteos);
            List<Map.Entry<Integer, Integer>> ranking = ordenarPorFrecuencia(frecuencia);

            int proximoConcurso = leerProximoConcurso(ARCHIVO_ULTIMO_CONCURSO);

            int[] combinacion = generarCombinacionPonderada(frecuencia, proximoConcurso);

            imprimirResumen(sorteos.size(), ranking, combinacion);
            escribirReporte(proximoConcurso, sorteos.size(), ranking, combinacion);

        } catch (IOException e) {
            System.out.println("Error leyendo o escribiendo archivos: " + e.getMessage());
        }
    }

    /** Lee el CSV oficial y devuelve la lista de sorteos como arreglos [R1..R6]. */
    private static List<int[]> leerHistorico(String ruta) throws IOException {
        List<int[]> sorteos = new ArrayList<>();
        Path path = Path.of(ruta);
        if (!Files.exists(path)) {
            throw new IOException("No se encontro el archivo: " + ruta);
        }

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String linea = br.readLine(); // encabezado, se descarta
            while ((linea = br.readLine()) != null) {
                if (linea.isBlank()) {
                    continue;
                }
                String[] campos = linea.split(",");
                // Formato: NPRODUCTO,CONCURSO,R1,R2,R3,R4,R5,R6,R7,BOLSA,FECHA
                if (campos.length < 8) {
                    continue;
                }
                try {
                    int[] numeros = new int[NUMEROS_POR_JUGADA];
                    for (int i = 0; i < NUMEROS_POR_JUGADA; i++) {
                        numeros[i] = Integer.parseInt(campos[2 + i].trim());
                    }
                    sorteos.add(numeros);
                } catch (NumberFormatException nfe) {
                    // Linea corrupta o mal formada: se ignora y se sigue.
                }
            }
        }
        return sorteos;
    }

    /** Cuenta cuantas veces aparecio cada numero (1..56) en el historico. */
    private static Map<Integer, Integer> calcularFrecuencia(List<int[]> sorteos) {
        Map<Integer, Integer> frecuencia = new HashMap<>();
        for (int n = NUM_MIN; n <= NUM_MAX; n++) {
            frecuencia.put(n, 0);
        }
        for (int[] sorteo : sorteos) {
            for (int n : sorteo) {
                frecuencia.merge(n, 1, Integer::sum);
            }
        }
        return frecuencia;
    }

    private static List<Map.Entry<Integer, Integer>> ordenarPorFrecuencia(Map<Integer, Integer> frecuencia) {
        List<Map.Entry<Integer, Integer>> lista = new ArrayList<>(frecuencia.entrySet());
        lista.sort((a, b) -> b.getValue() - a.getValue());
        return lista;
    }

    private static int leerProximoConcurso(String ruta) {
        try {
            String contenido = Files.readString(Path.of(ruta), StandardCharsets.UTF_8).trim();
            return Integer.parseInt(contenido) + 1;
        } catch (Exception e) {
            // Si no se puede leer, se usa la hora actual como respaldo para la semilla.
            return (int) (System.currentTimeMillis() / 1000L);
        }
    }

    /**
     * Genera una combinacion de 6 numeros distintos mediante muestreo aleatorio
     * ponderado por la frecuencia historica de cada numero.
     *
     * La semilla se fija con el numero del proximo concurso para que el
     * resultado sea reproducible (misma entrada -> misma salida), no para
     * predecir nada: sigue siendo una eleccion aleatoria entre 20+ millones
     * de combinaciones posibles.
     */
    private static int[] generarCombinacionPonderada(Map<Integer, Integer> frecuencia, long semilla) {
        Random random = new Random(semilla);

        List<Integer> numeros = new ArrayList<>(frecuencia.keySet());
        List<Integer> pesos = new ArrayList<>();
        int sumaPesos = 0;
        for (int n : numeros) {
            int peso = frecuencia.get(n) + 1; // +1 para que ningun numero tenga peso 0
            pesos.add(peso);
            sumaPesos += peso;
        }

        TreeSet<Integer> seleccion = new TreeSet<>();
        while (seleccion.size() < NUMEROS_POR_JUGADA) {
            int objetivo = random.nextInt(sumaPesos);
            int acumulado = 0;
            for (int i = 0; i < numeros.size(); i++) {
                acumulado += pesos.get(i);
                if (objetivo < acumulado) {
                    seleccion.add(numeros.get(i));
                    break;
                }
            }
        }

        int[] resultado = new int[NUMEROS_POR_JUGADA];
        int idx = 0;
        for (int n : seleccion) {
            resultado[idx++] = n;
        }
        return resultado;
    }

    private static void imprimirResumen(int totalSorteos, List<Map.Entry<Integer, Integer>> ranking, int[] combinacion) {
        System.out.println("Total de sorteos analizados: " + totalSorteos);
        System.out.println();
        System.out.println("Top 10 numeros mas frecuentes:");
        for (int i = 0; i < 10 && i < ranking.size(); i++) {
            Map.Entry<Integer, Integer> e = ranking.get(i);
            double pct = 100.0 * e.getValue() / totalSorteos;
            System.out.printf("  %02d -> %d veces (%.2f%%)%n", e.getKey(), e.getValue(), pct);
        }
        System.out.println();
        System.out.println("Top 10 numeros menos frecuentes:");
        int n = ranking.size();
        for (int i = n - 10; i < n; i++) {
            Map.Entry<Integer, Integer> e = ranking.get(i);
            double pct = 100.0 * e.getValue() / totalSorteos;
            System.out.printf("  %02d -> %d veces (%.2f%%)%n", e.getKey(), e.getValue(), pct);
        }
        System.out.println();
        System.out.println("Combinacion generada: " + formatearCombinacion(combinacion));
        System.out.println("Suma: " + sumar(combinacion));
        System.out.println("Paridad: " + describirParidad(combinacion));
    }

    private static void escribirReporte(int proximoConcurso, int totalSorteos,
                                         List<Map.Entry<Integer, Integer>> ranking, int[] combinacion) throws IOException {
        String nombreArchivo = "estrategia_" + proximoConcurso + ".txt";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (PrintWriter out = new PrintWriter(new FileWriter(nombreArchivo, StandardCharsets.UTF_8))) {
            out.println("============================================================");
            out.println("  ANALISIS DE FRECUENCIA - SORTEO " + proximoConcurso);
            out.println("  Generado: " + LocalDateTime.now().format(fmt));
            out.println("  Sorteos historicos analizados: " + totalSorteos);
            out.println("============================================================");
            out.println();
            out.println("COMBINACION SUGERIDA: " + formatearCombinacion(combinacion));
            out.println("Suma: " + sumar(combinacion) + " | Paridad: " + describirParidad(combinacion));
            out.println();
            out.println("Metodo: muestreo aleatorio ponderado por frecuencia historica");
            out.println("de cada numero en " + totalSorteos + " sorteos pasados.");
            out.println();
            out.println("Top 10 numeros mas frecuentes en el historico:");
            for (int i = 0; i < 10 && i < ranking.size(); i++) {
                Map.Entry<Integer, Integer> e = ranking.get(i);
                double pct = 100.0 * e.getValue() / totalSorteos;
                out.printf("  %02d -> %d veces (%.2f%%)%n", e.getKey(), e.getValue(), pct);
            }
            out.println();
            out.println("------------------------------------------------------------");
            out.println("AVISO: El Melate es un sorteo aleatorio de extraccion fisica.");
            out.println("La frecuencia historica de un numero NO influye en el proximo");
            out.println("sorteo. Esta combinacion no tiene mayor probabilidad de ganar");
            out.println("que cualquier otra; es solo un criterio de seleccion basado");
            out.println("en datos reales, en vez de una eleccion puramente al azar.");
            out.println("------------------------------------------------------------");
        }

        System.out.println();
        System.out.println("Reporte escrito en: " + nombreArchivo);
    }

    private static String formatearCombinacion(int[] combinacion) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < combinacion.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(combinacion[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static int sumar(int[] combinacion) {
        int suma = 0;
        for (int n : combinacion) suma += n;
        return suma;
    }

    private static String describirParidad(int[] combinacion) {
        int pares = 0;
        for (int n : combinacion) {
            if (n % 2 == 0) pares++;
        }
        int impares = combinacion.length - pares;
        return pares + "P / " + impares + "I";
    }
}
