import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class AnalizadorMelateRetro {
    private static final int TOTAL_NUMEROS = 56;
    private static final int NUMEROS_POR_SORTEO = 6;
    
    public static void main(String[] args) {
        String archivoEntrada = "historico_retro.csv";
        String archivoSalida = "historial_jugadas_retro.txt";
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-i") && i + 1 < args.length) {
                archivoEntrada = args[i + 1];
            } else if (args[i].equals("-o") && i + 1 < args.length) {
                archivoSalida = args[i + 1];
            } else if (args[i].equals("-h") || args[i].equals("--help")) {
                mostrarAyuda();
                return;
            }
        }
        
        try {
            System.out.println("🚀 Analizando: " + archivoEntrada);
            Map<Integer, Integer> frecuencias = analizarFrecuencias(archivoEntrada);
            
            if (frecuencias.isEmpty()) {
                System.err.println("❌ No hay datos válidos.");
                return;
            }
            
            // Calcular estadísticas
            Estadisticas stats = calcularEstadisticas(frecuencias);
            
            List<Integer> jugadaFinal = generarJugada(frecuencias);
            
            // Calculamos paridad para el reporte
            long pares = jugadaFinal.stream().filter(n -> n % 2 == 0).count();
            String paridad = pares + "P / " + (NUMEROS_POR_SORTEO - pares) + "I";

            guardarJugada(archivoSalida, jugadaFinal, paridad, stats);
            mostrarResumen(jugadaFinal, paridad, stats);
            
            System.out.println("\n✅ Jugada añadida a: " + archivoSalida);
            
        } catch (IOException e) {
            System.err.println("❌ Error de archivo: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Map<Integer, Integer> analizarFrecuencias(String archivo) throws IOException {
        Map<Integer, Integer> frec = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] datos = linea.split(",");
                if (datos.length < 8) continue;
                for (int i = 2; i <= 7; i++) {
                    try {
                        int n = Integer.parseInt(datos[i].trim());
                        if (n >= 1 && n <= TOTAL_NUMEROS) {
                            frec.put(n, frec.getOrDefault(n, 0) + 1);
                        }
                    } catch (NumberFormatException e) {
                        // Ignorar valores no numéricos
                    }
                }
            }
        }
        return frec;
    }

    private static List<Integer> generarJugada(Map<Integer, Integer> frecuencias) {
        List<Integer> bolsa = new ArrayList<>();
        
        // Encontrar la frecuencia máxima para normalización
        int maxFreq = frecuencias.values().stream().max(Integer::compare).orElse(1);
        
        frecuencias.forEach((num, f) -> {
            // Normalizar frecuencias para crear una ponderación más uniforme
            double pesoNormalizado = (double) f / maxFreq;
            // Usar ponderación exponencial para favorecer números calientes más agresivamente
            int peso = (int) Math.ceil(10 * Math.pow(pesoNormalizado, 1.5));
            
            for (int i = 0; i < peso; i++) bolsa.add(num);
        });

        // Asegurar que no nos quedemos en bucle infinito si la bolsa es muy pequeña
        if (bolsa.isEmpty()) {
            // Plan B: generar números aleatorios
            return generarJugadaAleatoria();
        }
        
        Set<Integer> seleccion = new TreeSet<>();
        Random r = new Random();
        int maxIntentos = 1000;
        int intentos = 0;
        
        while (seleccion.size() < NUMEROS_POR_SORTEO && intentos < maxIntentos) {
            seleccion.add(bolsa.get(r.nextInt(bolsa.size())));
            intentos++;
        }
        
        // Si no pudimos obtener suficientes números, completar con aleatorios
        while (seleccion.size() < NUMEROS_POR_SORTEO) {
            seleccion.add(r.nextInt(TOTAL_NUMEROS) + 1);
        }
        
        return new ArrayList<>(seleccion);
    }

    private static List<Integer> generarJugadaAleatoria() {
        Set<Integer> numeros = new TreeSet<>();
        Random r = new Random();
        while (numeros.size() < NUMEROS_POR_SORTEO) {
            numeros.add(r.nextInt(TOTAL_NUMEROS) + 1);
        }
        return new ArrayList<>(numeros);
    }

    private static void guardarJugada(String archivo, List<Integer> jugada, 
                                      String paridad, Estadisticas stats) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(archivo, true))) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            pw.println("=".repeat(60));
            pw.printf("📅 Fecha: %s%n", sdf.format(new Date()));
            pw.printf("🎯 Números sugeridos: %s%n", 
                jugada.stream().map(String::valueOf).collect(Collectors.joining(" - ")));
            pw.printf("⚖️  Paridad: %s%n", paridad);
            
            if (stats != null) {
                pw.printf("📊 Estadísticas: Calientes=%d, Fríos=%d, Promedio=%.2f%n",
                    stats.numerosCalientes.size(),
                    stats.numerosFrios.size(),
                    stats.promedioApariciones);
                pw.printf("📈 Máx frecuencia: %d, Mín frecuencia: %d%n",
                    stats.maxFrecuencia, stats.minFrecuencia);
            }
            
            pw.println("-".repeat(60));
            pw.println();
        }
    }

    private static void mostrarResumen(List<Integer> jugada, String paridad, Estadisticas stats) {
        System.out.println("\n✨ SUGERENCIA GENERADA ✨");
        System.out.println("   Números: " + jugada);
        System.out.println("   Balance: " + paridad);
        if (stats != null) {
            System.out.printf("   📊 Calientes: %d, Fríos: %d%n", 
                stats.numerosCalientes.size(), 
                stats.numerosFrios.size());
        }
    }

    private static void mostrarAyuda() {
        System.out.println("Uso: java AnalizadorMelateRetro [-i entrada.csv] [-o salida.txt]");
        System.out.println("Opciones:");
        System.out.println("  -i archivo.csv  : Archivo de entrada con datos históricos");
        System.out.println("  -o archivo.txt  : Archivo de salida para guardar jugadas");
        System.out.println("  -h, --help      : Muestra esta ayuda");
    }
    
    private static class Estadisticas {
        double promedioApariciones;
        int maxFrecuencia;
        int minFrecuencia;
        List<Integer> numerosCalientes;
        List<Integer> numerosFrios;
    }

    private static Estadisticas calcularEstadisticas(Map<Integer, Integer> frecuencias) {
        Estadisticas stats = new Estadisticas();
        stats.maxFrecuencia = frecuencias.values().stream().max(Integer::compare).orElse(0);
        stats.minFrecuencia = frecuencias.values().stream().min(Integer::compare).orElse(0);
        stats.promedioApariciones = frecuencias.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);
        
        // Números calientes (encima del promedio)
        stats.numerosCalientes = frecuencias.entrySet().stream()
            .filter(e -> e.getValue() > stats.promedioApariciones)
            .map(Map.Entry::getKey)
            .sorted()
            .collect(Collectors.toList());
        
        // Números fríos (debajo del promedio)
        stats.numerosFrios = frecuencias.entrySet().stream()
            .filter(e -> e.getValue() < stats.promedioApariciones)
            .map(Map.Entry::getKey)
            .sorted()
            .collect(Collectors.toList());
        
        return stats;
    }
}