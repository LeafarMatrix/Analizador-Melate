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

public class AnalizadorMelate {

    static final String ARCHIVO_MELATE      = "historico_melate.txt";
    static final String ARCHIVO_RETRO       = "historico_retro.csv";
    static final String SALIDA_DEFECTO      = "jugadas_generadas.csv";
    static final int    SUMA_MIN_DEFECTO    = 130;
    static final int    SUMA_MAX_DEFECTO    = 190;
    static final double DECAIMIENTO_DEFECTO = 0.96;

    public static void main(String[] args) {
        if (args.length == 0) { ejecutarGenerar(new String[0]); return; }
        String comando = args[0];
        String[] resto = Arrays.copyOfRange(args, 1, args.length);
        switch (comando) {
            case "generar"      -> ejecutarGenerar(resto);
            case "verificar"    -> ejecutarVerificar(resto);
            case "-h","--help"  -> mostrarAyuda();
            default -> { System.err.println("Comando no reconocido: " + comando); mostrarAyuda(); }
        }
    }

    private static void mostrarAyuda() {
        System.out.printf("""
            Uso:
              java AnalizadorMelate generar   [-i entrada] [-o salida] [-n cantidad]
                                               [--producto MELATE|RETRO]
                                               [--suma-min n] [--suma-max n]
                                               [--diversidad n]
                                               [--top-pares n] [--top-trios n]
                                               [--excluir n1,n2,..] [--proteger n1,n2,..]
              java AnalizadorMelate verificar  -j n1,..,n6 -r n1,..,n6 [-a adicional]
                                               [--modalidad MELATE|REVANCHA|REVANCHITA] [--min n]
              java AnalizadorMelate -h
            %n""");
    }

    // =========================================================
    // MODO: GENERAR
    // =========================================================

    private static void ejecutarGenerar(String[] args) {
        String       producto   = "MELATE";
        String       entrada    = null;
        String       salida     = SALIDA_DEFECTO;
        int          cantidad   = 4;
        int          sumaMin    = SUMA_MIN_DEFECTO;
        int          sumaMax    = SUMA_MAX_DEFECTO;
        int          diversidad = 3;
        int          topPares   = 10;
        int          topTrios   = 5;
        Set<Integer> excluidos  = new HashSet<>();
        Set<Integer> protegidos = new HashSet<>();

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-i"           -> entrada    = args[++i];
                    case "-o"           -> salida     = args[++i];
                    case "-n"           -> cantidad   = Integer.parseInt(args[++i]);
                    case "--producto"   -> producto   = args[++i].toUpperCase();
                    case "--suma-min"   -> sumaMin    = Integer.parseInt(args[++i]);
                    case "--suma-max"   -> sumaMax    = Integer.parseInt(args[++i]);
                    case "--diversidad" -> diversidad = Integer.parseInt(args[++i]);
                    case "--top-pares"  -> topPares   = Integer.parseInt(args[++i]);
                    case "--top-trios"  -> topTrios   = Integer.parseInt(args[++i]);
                    case "--excluir"    -> excluidos.addAll(parseNumeros(args[++i]));
                    case "--proteger"   -> protegidos.addAll(parseNumeros(args[++i]));
                    default -> System.err.println("Opcion ignorada: " + args[i]);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Falta un valor despues de una de las opciones.");
            mostrarAyuda();
            return;
        }

        if (entrada == null) entrada = "RETRO".equals(producto) ? ARCHIVO_RETRO : ARCHIVO_MELATE;

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
                + " (lineas descartadas por formato invalido: "
                + HistorialParser.ultimosDescartados + ")");

        AnalizadorParesTrios analizador = new AnalizadorParesTrios();
        analizador.analizar(historial);
        imprimirTopParesTrios(analizador, topPares, topTrios);

        List<AjustePeso> ajustes = List.of(
                new PesoBaseRecencia(DECAIMIENTO_DEFECTO),
                new AjusteTendenciaReciente(),
                new AjusteVaciosCriticos(),
                new AjusteParesFrecuentes(analizador)
        );

        GeneradorJugadas generador = new GeneradorJugadas(
                sumaMin, sumaMax, excluidos, protegidos, diversidad);
        Map<Integer, Double> pesos = generador.calcularPesos(historial, ajustes);
        imprimirTopPesos(pesos);

        List<Integer> ultimoSorteo = historial.get(0).numeros();
        System.out.printf("Filtro suma: %d-%d | Diversidad minima: %d numeros distintos%n",
                sumaMin, sumaMax, diversidad);

        List<List<Integer>> jugadas = generador.generarVarias(pesos, ultimoSorteo, cantidad);

        for (int i = 0; i < jugadas.size(); i++) {
            List<Integer> j = jugadas.get(i);
            char letra = (char) ('A' + i);
            System.out.printf("Jugada %s: %s (suma=%d)%n", letra, j, suma(j));
        }

        try {
            exportarJugadas(Path.of(salida), jugadas, producto);
            System.out.println("Jugadas guardadas en: " + salida);
        } catch (IOException e) {
            System.err.println("No se pudieron guardar las jugadas: " + e.getMessage());
        }
    }

    // ─── helpers de impresion ─────────────────────────────────────────────────

    private static void imprimirTopPesos(Map<Integer, Double> pesos) {
        List<Integer> top = pesos.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        System.out.println("Top 10 numeros por peso ponderado: " + top);
    }

    private static void imprimirTopParesTrios(AnalizadorParesTrios analizador,
                                               int topPares, int topTrios) {
        System.out.println("Top " + topPares + " pares mas frecuentes:");
        for (EntradaFrecuencia<List<Integer>> e : analizador.topPares(topPares)) {  // ✅ corregido
            System.out.printf("  %s -> %d veces%n", e.combinacion(), e.frecuencia());
        }
        System.out.println("Top " + topTrios + " trios mas frecuentes:");
        for (EntradaFrecuencia<List<Integer>> e : analizador.topTrios(topTrios)) {  // ✅ corregido
            System.out.printf("  %s -> %d veces%n", e.combinacion(), e.frecuencia());
        }
    }

    // ─── helpers de exportacion ──────────────────────────────────────────────

    private static int suma(List<Integer> l) {
        return l.stream().mapToInt(Integer::intValue).sum();
    }

    private static void exportarJugadas(Path salida, List<List<Integer>> jugadas,
                                         String producto) throws IOException {
        boolean esNuevo = !Files.exists(salida);
        try (BufferedWriter bw = Files.newBufferedWriter(
                salida, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (esNuevo) {
                bw.write("fecha,producto,letra,numeros,suma,pares,impares");
                bw.newLine();
            }
            String fecha = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
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
        List<Integer> jugada    = null;
        List<Integer> resultado = null;
        Integer adicional       = null;
        String  modalidad       = null;
        Integer minManual       = null;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-j"          -> jugada    = parseNumeros(args[++i]);
                    case "-r"          -> resultado = parseNumeros(args[++i]);
                    case "-a"          -> adicional = Integer.parseInt(args[++i]);
                    case "--modalidad" -> modalidad = args[++i].toUpperCase();
                    case "--min"       -> minManual = Integer.parseInt(args[++i]);
                    default -> System.err.println("Opcion ignorada: " + args[i]);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Falta un valor despues de una de las opciones.");
            mostrarAyuda();
            return;
        }

        if (jugada == null || resultado == null) {
            System.err.println("Faltan argumentos obligatorios: -j y -r.");
            mostrarAyuda();
            return;
        }

        int minimo = minManual != null ? minManual : switch (modalidad == null ? "" : modalidad) {
            case "REVANCHITA" -> 6;
            case "REVANCHA"   -> 3;
            default           -> 3;
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