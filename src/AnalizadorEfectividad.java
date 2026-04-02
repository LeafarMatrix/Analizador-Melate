import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class AnalizadorEfectividad {
    
    // --- NUEVO MÉTODO MAIN PARA INTEGRAR TODO ---
	public static void main(String[] args) {
	    String rutaHistorico = "historico_melate.txt"; 
	    
	    System.out.println("🚀 INICIANDO PIPELINE MATRIX - PROCESO DE ESCRITURA");
	    
	    // 1. Identificar al líder actual
	    int rey = obtenerNumeroRey();
	    System.out.println("👑 EL NÚMERO REY DETECTADO ES: " + rey);

	    // 2. Generar la jugada maestra
	    List<Integer> sugerencia = GeneradorMatrix.generarJugadaMaestra(rutaHistorico);
	    System.out.println("\n🎯 JUGADA MAESTRA GENERADA: " + sugerencia);
	    
	    // --- AQUÍ EMPIEZA LA ESCRITURA QUE FALTABA ---

	    // 3. PERSISTENCIA EN TXT: Guardamos la jugada para que no se pierda
	    // Como no tenemos un método específico de guardado de jugada en tu código, 
	    // lo escribimos directamente aquí para asegurar que se guarde:
	    try (FileWriter fw = new FileWriter("historial_jugadas_melate.txt", true);
	         PrintWriter pw = new PrintWriter(fw)) {
	        pw.println("-------------------------");
	        pw.println("COMBINACIÓN: " + sugerencia);
	        pw.println("FECHA: " + new Date().toString());
	        System.out.println("✅ Jugada escrita en historial_jugadas_melate.txt");
	    } catch (IOException e) {
	        System.err.println("Error al escribir TXT: " + e.getMessage());
	    }

	    // 4. PERSISTENCIA EN CSV: Creamos el registro de precisión inicial
	    // Usamos el método exportarEfectividadCSV que ya tienes definido
	    exportarEfectividadCSV(sugerencia.toString(), 0, 0.0); 
	    // (Se guarda con 0 aciertos porque aún no sucede el sorteo)

	    // 5. CIERRE DE MARZO: Si quieres generar el reporte final de marzo
	    generarResumenMarzo();

	    System.out.println("\n=== PIPELINE FINALIZADO: ARCHIVOS ACTUALIZADOS ===");
	}
	
	// 4. Método para escribir en el CSV de control
    public static void exportarEfectividadCSV(String jugada, int aciertos, double delta) {
        String rutaCSV = "reporte_precision_matrix.csv";
        File file = new File(rutaCSV);
        boolean existe = file.exists();

        try (FileWriter fw = new FileWriter(rutaCSV, true); 
             PrintWriter pw = new PrintWriter(fw)) {
            
            // Si el archivo es nuevo, escribimos el header (columnas)
            if (!existe) pw.println("Fecha,Jugada,Aciertos,Delta"); 
            
            pw.printf("%s,\"%s\",%d,%.2f\n", new Date().toString(), jugada, aciertos, delta);
            
            System.out.println("✅ Registro de precisión exportado a " + rutaCSV);
        } catch (IOException e) {
            System.err.println("Error al exportar CSV: " + e.getMessage());
        }
    }

    // 5. Método para el cierre estadístico de Marzo
   
    public static void generarResumenMarzo() {
        String rutaCSV = "reporte_precision_matrix.csv";
        double mejorDelta = 56.0;
        int totalAciertos = 0;
        int jugadasProcesadas = 0;

        System.out.println("\n==========================================");
        System.out.println("   RESUMEN ESTADÍSTICO: CIERRE DE CICLO");
        System.out.println("==========================================");

        File f = new File(rutaCSV);
        if (!f.exists()) {
            System.out.println("ℹ️ No hay datos suficientes todavía.");
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(rutaCSV))) {
            String linea;
            br.readLine(); // Saltar encabezado

            while ((linea = br.readLine()) != null) {
                // Usamos una expresión regular para separar por coma, manejando posibles espacios
                String[] columnas = linea.split(",");
                if (columnas.length >= 4) {
                    try {
                        // LIMPIEZA CLAVE: .trim() elimina espacios antes de convertir
                        int aciertos = Integer.parseInt(columnas[2].trim());
                        double delta = Double.parseDouble(columnas[3].trim());

                        if (delta < mejorDelta && delta > 0) mejorDelta = delta;
                        totalAciertos += aciertos;
                        jugadasProcesadas++;
                    } catch (NumberFormatException e) {
                        // Si una línea está mal formateada, la saltamos para no romper el proceso
                        continue; 
                    }
                }
            }

            if (jugadasProcesadas > 0) {
                System.out.println("SORTEOS ANALIZADOS: " + jugadasProcesadas);
                System.out.println("ACIERTOS TOTALES REGISTRADOS: " + totalAciertos);
                System.out.printf("PUNTUACIÓN MÁXIMA (MENOR DELTA): %.2f \n", mejorDelta);
            } else {
                System.out.println("ℹ️ Esperando primer sorteo auditado para mostrar estadísticas.");
            }

        } catch (IOException e) {
            System.err.println("Error al leer CSV: " + e.getMessage());
        }
        System.out.println("==========================================\n");
    }
    
    
    
    public static void analizarEfectividad(String rutaHistorial, List<Integer> resultadoReal, int adicionalReal) {
        System.out.println("\n=== REPORTANDO EFECTIVIDAD DE LA MATRIX (SORTEO 4194) ===");
        try (BufferedReader br = new BufferedReader(new FileReader(rutaHistorial))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (linea.startsWith("COMBINACIÓN:")) {
                    String numerosStr = linea.substring(linea.indexOf("[") + 1, linea.indexOf("]"));
                    List<Integer> jugadaActual = Arrays.stream(numerosStr.split(","))
                                         .map(String::trim).map(Integer::parseInt).collect(Collectors.toList());
                    procesarComparacion(jugadaActual, resultadoReal, adicionalReal);
                }
            }
        } catch (IOException e) { System.err.println("Error: " + e.getMessage()); }
    }

    private static void procesarComparacion(List<Integer> jugada, List<Integer> resultado, int adicional) {
        List<Integer> aciertos = jugada.stream().filter(resultado::contains).collect(Collectors.toList());
        boolean tieneAdicional = jugada.contains(adicional);
        double sumaDelta = 0;
        for (Integer n : jugada) {
            int cercania = resultado.stream().mapToInt(r -> Math.abs(r - n)).min().orElse(56);
            sumaDelta += cercania;
        }
        double deltaFinal = sumaDelta / 6.0;
        System.out.printf("Jugada: %s | Aciertos: %d | Adicional: %s | Delta: %.2f\n", 
                          jugada, aciertos.size(), (tieneAdicional ? "SÍ" : "NO"), deltaFinal);
        if (deltaFinal < 3.0) System.out.println("   🔥 ¡ALERTA! Cercanía crítica detectada.");
    }

    public static void ejecutarAuditoria(List<Integer> jugada, List<Integer> resultadoReal, int adicionalReal) {
        Collections.sort(jugada);
        Collections.sort(resultadoReal);
        List<Integer> aciertos = jugada.stream().filter(resultadoReal::contains).collect(Collectors.toList());
        double deltaPromedio = calcularDelta(jugada, resultadoReal);
        System.out.println("\n==========================================");
        System.out.println("   REPORTE DE LA MATRIX - SORTEO 4194"); // Actualizado para último sorteo
        System.out.println("==========================================");
        System.out.println("TU JUGADA: " + jugada);
        System.out.println("RESULTADO: " + resultadoReal + " [Ad: " + adicionalReal + "]");
        System.out.println("NATURALES: " + aciertos.size() + " " + aciertos);
        System.out.printf("INDICE DE CERCANÍA (DELTA): %.2f \n", deltaPromedio);
        interpretarDelta(deltaPromedio);
        System.out.println("==========================================\n");
    }

    private static double calcularDelta(List<Integer> jugada, List<Integer> resultado) {
        double sumaDiferencias = 0;
        for (Integer n : jugada) {
            int dif = resultado.stream().mapToInt(r -> Math.abs(r - n)).min().orElse(56);
            sumaDiferencias += dif;
        }
        return sumaDiferencias / 6.0;
    }

    // --- INTERPRETACIÓN ACTUALIZADA ---
    private static void interpretarDelta(double delta) {
        System.out.print("ESTADO DEL ALGORITMO: ");
        if (delta <= 2.5) System.out.println("🔥 ¡CRÍTICO! Estuviste encima de los números.");
        else if (delta <= 4.0) System.out.println("🟢 ESTABLE. Calibración 0.96 funcionando.");
        else System.out.println("🟡 DISPERSO. Requiere ajuste de pesos o revisión del Rey.");
    }

    public static int obtenerNumeroRey() {
        String rutaCSV = "reporte_precision_matrix.csv";
        File archivo = new File(rutaCSV);
        Map<Integer, Double> puntajeNumeros = new HashMap<>();

        // 1. Verificación de existencia del archivo
        if (!archivo.exists() || archivo.length() == 0) {
            System.out.println("ℹ️ CSV de auditoría vacío. Usando Rey del histórico principal...");
            List<Integer> top = GeneradorMatrix.generarJugadaMaestra("historico_melate.txt");
            return top.get(top.size() - 1); // Retorna el de mayor peso
        }

        // 2. Proceso de minería sobre el CSV
        try (BufferedReader br = new BufferedReader(new FileReader(rutaCSV))) {
            String linea = br.readLine(); // Saltar encabezado
            
            while ((linea = br.readLine()) != null) {
                String[] columnas = linea.split(",");
                if (columnas.length >= 4) {
                    // Limpiar formato de la jugada registrada
                    String jugadaLimpia = columnas[1].replace("\"", "").replace("[", "").replace("]", "");
                    double delta = Double.parseDouble(columnas[3]);
                    
                    // Algoritmo de puntuación: a menor delta, más puntos al número
                    double puntos = 10.0 / (delta + 1.0); 

                    String[] nums = jugadaLimpia.split(",");
                    for (String n : nums) {
                        int num = Integer.parseInt(n.trim());
                        puntajeNumeros.put(num, puntajeNumeros.getOrDefault(num, 0.0) + puntos);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error procesando Rey: " + e.getMessage());
            return 29; // Retorno de seguridad (tu número ancla)
        }

        return puntajeNumeros.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(29);
    }
    
    // ... (Mantén tus métodos de CSV y ResumenMarzo igual)
}
