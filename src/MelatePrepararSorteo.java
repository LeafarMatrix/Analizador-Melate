import java.util.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.LocalDateTime;

/**
 * MelatePrepararSorteo
 * -----------------------------------------------------------------------
 * Combina en un solo paso lo que antes eran dos programas separados:
 *
 *   1) Actualiza "historico_melate.txt" descargando el CSV oficial de
 *      Loteria Nacional y agregando solo los concursos nuevos
 *      (misma logica que MelateAutoActualizador).
 *   2) Con el historico ya actualizado, corre un analisis de frecuencias
 *      (calientes/frios/ponderado) y genera jugadas sugeridas filtradas
 *      por suma y diversidad, listas para el PROXIMO sorteo.
 *
 * Pensado para correrse la noche/manana ANTES de cada sorteo (miercoles,
 * viernes, domingo) y tener tus jugadas listas con el dato mas fresco.
 *
 * USO:
 *   javac MelatePrepararSorteo.java
 *   java MelatePrepararSorteo
 * -----------------------------------------------------------------------
 */
public class MelatePrepararSorteo {

    static final String URL_CSV = "https://www.loterianacional.gob.mx/Documentos/Historicos/Melate.csv";
    static final String ARCHIVO_HISTORICO = "historico_melate.txt";
    static final String ARCHIVO_MARCADOR = "ultimo_concurso.txt";
    static final String ARCHIVO_LOG = "actualizacion_melate.log";

    static final int MIN_NUM = 1;
    static final int MAX_NUM = 56;
    static final int NUMS_PER_DRAW = 6;
    static final int SUMA_MIN = 130;
    static final int SUMA_MAX = 190;
    static final int DIVERSIDAD_MIN_DECENAS = 3;

    public static void main(String[] args) {
        try {
            System.out.println("=========================================================");
            System.out.println(" PASO 1: Actualizando historico_melate.txt");
            System.out.println("=========================================================");
            actualizarHistorico();

            System.out.println();
            System.out.println("=========================================================");
            System.out.println(" PASO 2: Analizando historico y generando jugadas");
            System.out.println("=========================================================");
            analizarYGenerar();

        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ---------------------- PASO 1: actualizar ----------------------

    static void actualizarHistorico() throws Exception {
        int ultimoConcurso = leerUltimoConcurso();
        log("Ultimo concurso registrado localmente: " + ultimoConcurso);

        String csv = descargarCSV(URL_CSV);
        List<String[]> filas = parsearCSV(csv);
        log("Filas leidas del CSV oficial: " + filas.size());

        List<String> nuevasLineas = new ArrayList<>();
        int maxConcursoEncontrado = ultimoConcurso;

        for (String[] campos : filas) {
            if (campos.length < 11) continue;
            int concurso;
            try {
                concurso = Integer.parseInt(campos[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (concurso <= ultimoConcurso) continue;

            StringBuilder linea = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                if (i > 0) linea.append(",");
                linea.append(campos[2 + i].trim());
            }
            nuevasLineas.add(linea.toString());
            if (concurso > maxConcursoEncontrado) maxConcursoEncontrado = concurso;
        }

        if (nuevasLineas.isEmpty()) {
            log("No hay sorteos nuevos. El historico ya esta al dia (concurso " + ultimoConcurso + ").");
            return;
        }

        try (BufferedWriter bw = Files.newBufferedWriter(
                Paths.get(ARCHIVO_HISTORICO),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (String linea : nuevasLineas) {
                bw.write(linea);
                bw.newLine();
            }
        }

        guardarUltimoConcurso(maxConcursoEncontrado);
        log("Se agregaron " + nuevasLineas.size() + " sorteo(s) nuevo(s). Ultimo concurso ahora: " + maxConcursoEncontrado);
    }

    static String descargarCSV(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("No se pudo descargar el CSV (HTTP " + response.statusCode() + ")");
        }
        return response.body();
    }

    static List<String[]> parsearCSV(String csv) {
        List<String[]> filas = new ArrayList<>();
        for (String linea : csv.split("\\R")) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;
            if (linea.toUpperCase().startsWith("NPRODUCTO")) continue;
            filas.add(linea.split(","));
        }
        return filas;
    }

    static int leerUltimoConcurso() throws IOException {
        Path path = Paths.get(ARCHIVO_MARCADOR);
        if (!Files.exists(path)) return 0;
        String contenido = Files.readString(path).trim();
        if (contenido.isEmpty()) return 0;
        try {
            return Integer.parseInt(contenido);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void guardarUltimoConcurso(int concurso) throws IOException {
        Files.writeString(Paths.get(ARCHIVO_MARCADOR), String.valueOf(concurso));
    }

    static void log(String mensaje) {
        String linea = "[" + LocalDateTime.now() + "] " + mensaje;
        System.out.println(linea);
        try (BufferedWriter bw = Files.newBufferedWriter(
                Paths.get(ARCHIVO_LOG),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            bw.write(linea);
            bw.newLine();
        } catch (IOException ignored) {
        }
    }

    // ---------------------- PASO 2: analizar y generar ----------------------

    static void analizarYGenerar() throws IOException {
        Path path = Paths.get(ARCHIVO_HISTORICO);
        if (!Files.exists(path)) {
            System.out.println("No existe " + ARCHIVO_HISTORICO + " todavia.");
            return;
        }

        List<int[]> sorteos = new ArrayList<>();
        int descartadas = 0;
        for (String linea : Files.readAllLines(path)) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;
            String[] campos = linea.split(",");
            if (campos.length != NUMS_PER_DRAW) { descartadas++; continue; }
            try {
                int[] combinacion = new int[NUMS_PER_DRAW];
                for (int i = 0; i < NUMS_PER_DRAW; i++) combinacion[i] = Integer.parseInt(campos[i].trim());
                sorteos.add(combinacion);
            } catch (NumberFormatException e) {
                descartadas++;
            }
        }

        System.out.println("Sorteos analizados: " + sorteos.size() + " (descartados: " + descartadas + ")");
        if (sorteos.isEmpty()) return;

        int[] frecuencia = new int[MAX_NUM + 1];
        for (int[] s : sorteos) for (int n : s) frecuencia[n]++;

        List<Integer> ordenados = new ArrayList<>();
        for (int n = MIN_NUM; n <= MAX_NUM; n++) ordenados.add(n);
        ordenados.sort((a, b) -> frecuencia[b] - frecuencia[a]);

        System.out.println("\nTop 10 numeros MAS frecuentes: " + ordenados.subList(0, 10));
        System.out.println("Top 10 numeros MENOS frecuentes: " + ordenados.subList(ordenados.size() - 10, ordenados.size()));

        System.out.println("\nFiltro suma: " + SUMA_MIN + "-" + SUMA_MAX +
                " | Diversidad minima: " + DIVERSIDAD_MIN_DECENAS + " decenas distintas");

        List<int[]> jugadas = generarJugadas(frecuencia, 4);
        char etiqueta = 'A';
        System.out.println("\nJugadas sugeridas para el proximo sorteo:");
        for (int[] jugada : jugadas) {
            int suma = Arrays.stream(jugada).sum();
            System.out.println("  Jugada " + etiqueta + ": " + Arrays.toString(jugada) + " (suma=" + suma + ")");
            etiqueta++;
        }
    }

    static List<int[]> generarJugadas(int[] frecuencia, int cantidad) {
        Random r = new Random();
        int maxFreq = Arrays.stream(frecuencia).max().getAsInt();

        List<Integer> pool = new ArrayList<>();
        for (int n = MIN_NUM; n <= MAX_NUM; n++) {
            int peso = frecuencia[n] + 1;
            for (int i = 0; i < peso; i++) pool.add(n);
        }

        List<int[]> jugadas = new ArrayList<>();
        int intentos = 0;
        while (jugadas.size() < cantidad && intentos < 200000) {
            intentos++;
            Set<Integer> combinacion = new TreeSet<>();
            while (combinacion.size() < NUMS_PER_DRAW) {
                combinacion.add(pool.get(r.nextInt(pool.size())));
            }
            int[] arr = combinacion.stream().mapToInt(Integer::intValue).toArray();
            int suma = Arrays.stream(arr).sum();
            if (suma < SUMA_MIN || suma > SUMA_MAX) continue;

            Set<Integer> decenas = new HashSet<>();
            for (int n : arr) decenas.add(n / 10);
            if (decenas.size() < DIVERSIDAD_MIN_DECENAS) continue;

            boolean duplicada = false;
            for (int[] existente : jugadas) {
                if (Arrays.equals(existente, arr)) { duplicada = true; break; }
            }
            if (duplicada) continue;

            jugadas.add(arr);
        }
        return jugadas;
    }
}