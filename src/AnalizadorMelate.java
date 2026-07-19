import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Herramienta unificada para analizar el historico de sorteos de Melate y
 * Melate Retro, generar jugadas sugeridas y verificar boletos contra un
 * resultado oficial.
 *
 * Sustituye a:
 *   - AnalizadorMelate.java / AnalizadorMelateRetro.java (version anterior):
 *     duplicaban la logica de parseo/estadisticas entre los dos juegos.
 *   - AnalizadorEfectividad.java / GeneradorMatrix.java: pipeline separado
 *     que solo cubria Melate, pero con mejor diseño (patron Strategy via
 *     AjustePeso). Esa arquitectura es la que se adopto aqui como base
 *     comun para ambos juegos.
 *
 * Ahora hay un solo punto de entrada, con las estrategias de ponderacion
 * (AjustePeso) compartidas entre Melate y Melate Retro.
 *
 * IMPORTANTE: en un sorteo aleatorio y justo, la frecuencia historica de un
 * numero NO tiene poder predictivo sobre el proximo sorteo -- cada sorteo es
 * independiente de los anteriores. Esta herramienta no "predice" resultados:
 * ayuda a generar jugadas diversas dentro de tu presupuesto y a evitar
 * patrones estadisticamente atipicos (todo par, todo consecutivo, etc).
 *
 * Uso:
 *   java AnalizadorMelate generar   [-i entrada] [-o salida] [-n cantidad]
 *                                    [--producto MELATE|RETRO]
 *                                    [--suma-min n] [--suma-max n]
 *                                    [--excluir n1,n2,..] [--proteger n1,n2,..]
 *   java AnalizadorMelate verificar -j n1,..,n6 -r n1,..,n6 [-a adicional]
 *                                    [--modalidad MELATE|REVANCHA|REVANCHITA] [--min n]
 *   java AnalizadorMelate -h | --help
 */
public class AnalizadorMelate {

    static final String ARCHIVO_MELATE = "historico_melate.txt";
    static final String ARCHIVO_RETRO = "historico_retro.csv";
    static final String SALIDA_DEFECTO = "jugadas_generadas.csv";
    static final int SUMA_MIN_DEFECTO = 130;
    static final int SUMA_MAX_DEFECTO = 190;
    static final double DECAIMIENTO_DEFECTO = 0.96;

    public static void main(String[] args) {
        if (args.length == 0) {
            ejecutarGenerar(new String[0]); // comportamiento por defecto: generar para Melate
            return;
        }

        String comando = args[0];
        String[] resto = Arrays.copyOfRange(args, 1, args.length);

        switch (comando) {
            case "generar" -> ejecutarGenerar(resto);
            case "verificar" -> ejecutarVerificar(resto);
            case "-h", "--help" -> mostrarAyuda();
            default -> {
                System.err.println("Comando no reconocido: " + comando);
                mostrarAyuda();
            }
        }
    }

    private static void mostrarAyuda() {
        System.out.printf("""
            Uso:
              java AnalizadorMelate generar   [-i entrada] [-o salida] [-n cantidad] [--producto MELATE|RETRO]
                                               [--suma-min n] [--suma-max n] [--excluir n1,n2,..] [--proteger n1,n2,..]
              java AnalizadorMelate verificar -j n1,..,n6 -r n1,..,n6 [-a adicional] [--modalidad MELATE|REVANCHA|REVANCHITA] [--min n]
              java AnalizadorMelate -h

            generar:
              -i           archivo historico de entrada (por defecto segun --producto)
              -o           archivo de salida para las jugadas (por defecto: %s)
              -n           cantidad de jugadas a generar (por defecto: 4)
              --producto   MELATE (usa %s) o RETRO (usa %s); por defecto MELATE
              --suma-min   suma minima aceptada (por defecto: %d)
              --suma-max   suma maxima aceptada (por defecto: %d)
              --excluir    numeros a evitar, separados por coma (ej. numeros de una combinacion perdedora conocida)
              --proteger   numeros que se salvan de --excluir aunque esten en esa lista

            verificar:
              -j          tu jugada, 6 numeros separados por coma
              -r          resultado oficial del sorteo, 6 numeros separados por coma
              -a          numero adicional del sorteo oficial (opcional)
              --modalidad MELATE (min 3 aciertos, adicional cubre el caso de 2+adicional),
                          REVANCHA (min 3), REVANCHITA (min 6)
              --min       aciertos minimos para premio si no usas --modalidad
            %n""", SALIDA_DEFECTO, ARCHIVO_MELATE, ARCHIVO_RETRO, SUMA_MIN_DEFECTO, SUMA_MAX_DEFECTO);
    }

    // =========================================================
    // MODO: GENERAR
    // =========================================================

    private static void ejecutarGenerar(String[] args) {
        String producto = "MELATE";
        String entrada = null;
        String salida = SALIDA_DEFECTO;
        int cantidad = 4;
        int sumaMin = SUMA_MIN_DEFECTO;
        int sumaMax = SUMA_MAX_DEFECTO;
        Set<Integer> excluidos = new HashSet<>();
        Set<Integer> protegidos = new HashSet<>();

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-i" -> entrada = args[++i];
                    case "-o" -> salida = args[++i];
                    case "-n" -> cantidad = Integer.parseInt(args[++i]);
                    case "--producto" -> producto = args[++i].toUpperCase();
                    case "--suma-min" -> sumaMin = Integer.parseInt(args[++i]);
                    case "--suma-max" -> sumaMax = Integer.parseInt(args[++i]);
                    case "--excluir" -> excluidos.addAll(parseNumeros(args[++i]));
                    case "--proteger" -> protegidos.addAll(parseNumeros(args[++i]));
                    default -> System.err.println("Opcion ignorada: " + args[i]);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Falta un valor despues de una de las opciones.");
            mostrarAyuda();
            return;
        }

        if (entrada == null) {
            entrada = "RETRO".equals(producto) ? ARCHIVO_RETRO : ARCHIVO_MELATE;
        }

        Path rutaEntrada = Path.of(entrada);
        if (!Files.exists(rutaEntrada)) {
            System.err.println("No se encontro el archivo de historico: " + entrada);
            return;
        }

        List<Sorteo> historial;
        try {
            historial = HistorialParser.parsear(rutaEntrada);
        } catch (IOException e) {
            System.err.println("Error leyendo el historico: " + e.getMessage());
            return;
        }

        if (historial.isEmpty()) {
            System.err.println("El historico no contiene sorteos validos.");
            return;
        }

        System.out.println("Producto: " + producto + " | Entrada: " + entrada
                + " | Sorteos cargados: " + historial.size()
                + " (lineas descartadas por formato invalido: " + HistorialParser.ultimosDescartados + ")");

        List<AjustePeso> ajustes = List.of(
                new PesoBaseRecencia(DECAIMIENTO_DEFECTO),
                new AjusteTendenciaReciente(),
                new AjusteVaciosCriticos()
        );

        GeneradorJugadas generador = new GeneradorJugadas(sumaMin, sumaMax, excluidos, protegidos);
        Map<Integer, Double> pesos = generador.calcularPesos(historial, ajustes);
        imprimirTopPesos(pesos);

        List<Integer> anterior = historial.get(0).numeros();

        List<List<Integer>> jugadas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            List<Integer> jugada = generador.generar(pesos, anterior);
            jugadas.add(jugada);
            char letra = (char) ('A' + i);
            System.out.printf("Jugada %s: %s (suma=%d)%n", letra, jugada, suma(jugada));
        }

        try {
            exportarJugadas(Path.of(salida), jugadas, producto);
            System.out.println("Jugadas guardadas en: " + salida);
        } catch (IOException e) {
            System.err.println("No se pudieron guardar las jugadas generadas: " + e.getMessage());
        }
    }

    private static void imprimirTopPesos(Map<Integer, Double> pesos) {
        List<Integer> top = pesos.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        System.out.println("Top 10 numeros por peso ponderado: " + top);
    }

    private static int suma(List<Integer> l) {
        return l.stream().mapToInt(Integer::intValue).sum();
    }

    private static void exportarJugadas(Path salida, List<List<Integer>> jugadas, String producto) throws IOException {
        boolean esNuevo = !Files.exists(salida);
        try (BufferedWriter bw = Files.newBufferedWriter(salida, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (esNuevo) {
                bw.write("fecha,producto,letra,numeros,suma,pares,impares");
                bw.newLine();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String fecha = sdf.format(new Date());
            for (int i = 0; i < jugadas.size(); i++) {
                List<Integer> j = jugadas.get(i);
                long pares = j.stream().filter(n -> n % 2 == 0).count();
                char letra = (char) ('A' + i);
                bw.write(String.format("%s,%s,%c,%s,%d,%d,%d",
                        fecha, producto, letra,
                        j.stream().map(String::valueOf).collect(Collectors.joining("-")),
                        suma(j), pares, j.size() - pares));
                bw.newLine();
            }
        }
    }

    // =========================================================
    // MODO: VERIFICAR
    // =========================================================

    private static void ejecutarVerificar(String[] args) {
        List<Integer> jugada = null;
        List<Integer> resultado = null;
        Integer adicional = null;
        String modalidad = null;
        Integer minManual = null;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-j" -> jugada = parseNumeros(args[++i]);
                    case "-r" -> resultado = parseNumeros(args[++i]);
                    case "-a" -> adicional = Integer.parseInt(args[++i]);
                    case "--modalidad" -> modalidad = args[++i].toUpperCase();
                    case "--min" -> minManual = Integer.parseInt(args[++i]);
                    default -> System.err.println("Opcion ignorada: " + args[i]);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Falta un valor despues de una de las opciones.");
            mostrarAyuda();
            return;
        }

        if (jugada == null || resultado == null) {
            System.err.println("Faltan argumentos obligatorios: -j (tu jugada) y -r (resultado oficial).");
            mostrarAyuda();
            return;
        }

        int minimo = minManual != null ? minManual : switch (modalidad == null ? "" : modalidad) {
            case "REVANCHITA" -> 6;
            case "REVANCHA" -> 3;
            default -> 3; // MELATE u otra modalidad no reconocida
        };
        boolean contarAdicional = adicional != null && "MELATE".equals(modalidad);

        Verificador.verificar(jugada, resultado, adicional, minimo, contarAdicional,
                modalidad == null ? "PERSONALIZADA" : modalidad);
    }

    private static List<Integer> parseNumeros(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }
}
