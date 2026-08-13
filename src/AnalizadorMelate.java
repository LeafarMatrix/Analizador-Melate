import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
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

    /** Nombre de dia (sin acentos, minuscula) -> constante de Calendar.DAY_OF_WEEK. */
    private static final Map<String, Integer> DIAS_SEMANA = Map.of(
            "domingo",   Calendar.SUNDAY,
            "lunes",     Calendar.MONDAY,
            "martes",    Calendar.TUESDAY,
            "miercoles", Calendar.WEDNESDAY,
            "jueves",    Calendar.THURSDAY,
            "viernes",   Calendar.FRIDAY,
            "sabado",    Calendar.SATURDAY
    );

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
                                               [--dia domingo|lunes|martes|miercoles|jueves|viernes|sabado]
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
        String       diaSemana  = null;

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
                    case "--dia"        -> diaSemana  = args[++i];
                    default -> System.err.println("Opcion ignorada: " + args[i]);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Falta un valor despues de una de las opciones.");
            mostrarAyuda();
            return;
        }

        if (entrada == null) {
            switch (producto) {
                case "RETRO" -> entrada = ARCHIVO_RETRO;
                case "MELATE" -> entrada = ARCHIVO_MELATE;
                default -> {
                    System.err.println("Producto no soportado para 'generar': '" + producto
                            + "'. Usa MELATE o RETRO (o pasa -i para apuntar a un archivo especifico). "
                            + "REVANCHA/REVANCHITA solo aplican al modo 'verificar'.");
                    return;
                }
            }
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

        // Se guarda ANTES de filtrar por dia: la vigencia del archivo se mide contra
        // el sorteo mas reciente de verdad, no contra el ultimo que cayo en el dia
        // filtrado (que puede tener hasta 6 dias de antiguedad por diseño y disparar
        // un aviso de "desactualizado" incluso con el archivo recien actualizado).
        String fechaMasReciente = historial.get(0).fecha();

        if (diaSemana != null) {
            int antes = historial.size();
            historial = filtrarPorDiaSemana(historial, diaSemana);
            if (historial.isEmpty()) {
                System.err.println("No quedaron sorteos tras filtrar por dia '" + diaSemana
                        + "' (revisa el nombre o las fechas del historico). Se aborta.");
                return;
            }
            System.out.printf("Filtro --dia %s: %d -> %d sorteos (solo los que cayeron ese dia de la semana).%n",
                    diaSemana, antes, historial.size());
        }

        System.out.println("Producto: " + producto + " | Entrada: " + entrada
                + " | Sorteos cargados: " + historial.size()
                + " (lineas descartadas por formato invalido: "
                + HistorialParser.ultimosDescartados + ")");
        avisarSiDatosDesactualizados(fechaMasReciente);

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

    /**
     * Filtra el historial dejando solo los sorteos que cayeron en el dia de
     * la semana indicado (ej. "domingo"), util para preparar el generador de
     * cara a un sorteo especifico: Melate se juega miercoles, viernes y
     * domingo, y cada sesion es un sorteo fisico independiente, asi que
     * limitar el historico al mismo dia de la semana del proximo sorteo es
     * una forma razonable (aunque igual heuristica, no predictiva) de
     * acotar el analisis.
     *
     * Nota: al filtrar, la muestra se reduce a ~1/3 del historico total, por
     * lo que las frecuencias de pares/trios y los "huecos" tendran mas
     * ruido estadistico que al usar el historico completo.
     */
    private static List<Sorteo> filtrarPorDiaSemana(List<Sorteo> historial, String diaSemana) {
        Integer objetivo = DIAS_SEMANA.get(normalizarDia(diaSemana));
        if (objetivo == null) {
            System.err.println("Dia de la semana no reconocido: '" + diaSemana
                    + "'. Valores validos: domingo, lunes, martes, miercoles, jueves, viernes, sabado. "
                    + "Se usa el historico completo sin filtrar.");
            return historial;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        sdf.setLenient(false);
        Calendar cal = Calendar.getInstance();
        List<Sorteo> filtrado = new ArrayList<>();
        for (Sorteo s : historial) {
            try {
                Date fecha = sdf.parse(s.fecha());
                cal.setTime(fecha);
                if (cal.get(Calendar.DAY_OF_WEEK) == objetivo) filtrado.add(s);
            } catch (Exception e) {
                // fecha ilegible: el sorteo se omite del filtro, no detiene el proceso
            }
        }
        return filtrado;
    }

    private static String normalizarDia(String s) {
        String sinAcentos = Normalizer.normalize(s.toLowerCase().trim(), Normalizer.Form.NFD);
        return sinAcentos.replaceAll("\\p{M}", "");
    }

    /**
     * Avisa si el sorteo mas reciente del historico cargado tiene mas de unos
     * pocos dias de antiguedad. Esto NO afecta los pesos calculados; es solo
     * un chequeo de higiene de datos para evitar generar jugadas "asertivas"
     * para el sorteo de esta noche usando un historico desactualizado.
     */
    private static void avisarSiDatosDesactualizados(String fechaTexto) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            sdf.setLenient(false);
            Date ultima = sdf.parse(fechaTexto);
            long dias = (new Date().getTime() - ultima.getTime()) / (1000L * 60 * 60 * 24);
            if (dias > 2) {
                System.err.printf("AVISO: el sorteo mas reciente del historico es del %s (hace %d dia(s)). "
                        + "Actualiza el archivo con los resultados mas recientes antes de generar las "
                        + "jugadas para el sorteo de esta noche.%n", fechaTexto, dias);
            }
        } catch (Exception e) {
            System.err.println("AVISO: no se pudo verificar la vigencia del historico (fecha ilegible: '"
                    + fechaTexto + "').");
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