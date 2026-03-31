
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.io.FileWriter;
import java.io.PrintWriter;
	


public class AnalizadorEfectividad {
	
	public static void analizarEfectividad(String rutaHistorial, List<Integer> resultadoReal, int adicionalReal) {
	    System.out.println("\n=== REPORTANDO EFECTIVIDAD DE LA MATRIX (SORTEO 4193) ===");
	    
	    try (BufferedReader br = new BufferedReader(new FileReader(rutaHistorial))) {
	        String linea;
	        List<Integer> jugadaActual = new ArrayList<>();
	        
	        while ((linea = br.readLine()) != null) {
	            if (linea.startsWith("COMBINACIÓN:")) {
	                // Extraer números del formato [n1, n2, n3, n4, n5, n6]
	                String numerosStr = linea.substring(linea.indexOf("[") + 1, linea.indexOf("]"));
	                jugadaActual = Arrays.stream(numerosStr.split(","))
	                                     .map(String::trim)
	                                     .map(Integer::parseInt)
	                                     .collect(Collectors.toList());
	                
	                procesarComparacion(jugadaActual, resultadoReal, adicionalReal);
	            }
	        }
	    } catch (IOException e) {
	        System.err.println("Error al leer historial: " + e.getMessage());
	    }
	}

	private static void procesarComparacion(List<Integer> jugada, List<Integer> resultado, int adicional) {
	    List<Integer> aciertos = jugada.stream().filter(resultado::contains).collect(Collectors.toList());
	    boolean tieneAdicional = jugada.contains(adicional);
	    
	    // Cálculo de Delta (Cercanía promedio)
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

        List<Integer> aciertos = jugada.stream()
                .filter(resultadoReal::contains)
                .collect(Collectors.toList());

        boolean tieneAdicional = jugada.contains(adicionalReal);
        double deltaPromedio = calcularDelta(jugada, resultadoReal);

        System.out.println("\n==========================================");
        System.out.println("   REPORTE DE LA MATRIX - SORTEO 4193");
        System.out.println("==========================================");
        System.out.println("TU JUGADA: " + jugada);
        System.out.println("RESULTADO: " + resultadoReal + " [Ad: " + adicionalReal + "]");
        System.out.println("------------------------------------------");
        System.out.println("NATURALES DETECTADOS: " + aciertos.size() + " " + aciertos);
        System.out.println("¿ADICIONAL (11) PRESENTE?: " + (tieneAdicional ? "SÍ (¡CASI!)" : "NO"));
        System.out.printf("INDICE DE CERCANÍA (DELTA): %.2f \n", deltaPromedio);
        
        interpretarDelta(deltaPromedio);
        System.out.println("==========================================\n");
    }

    private static double calcularDelta(List<Integer> jugada, List<Integer> resultado) {
        double sumaDiferencias = 0;
        for (Integer n : jugada) {
            // Buscamos la distancia al número más cercano del resultado oficial
            int diferenciaMinima = resultado.stream()
                    .mapToInt(r -> Math.abs(r - n))
                    .min().orElse(56);
            sumaDiferencias += diferenciaMinima;
        }
        return sumaDiferencias / 6.0;
    }

    private static void interpretarDelta(double delta) {
        System.out.print("ESTADO DEL ALGORITMO: ");
        if (delta <= 2.5) System.out.println("🔥 ¡CRÍTICO! Estuviste encima de los números.");
        else if (delta <= 4.5) System.out.println("🟢 ESTABLE. El peso exponencial está calibrado.");
        else System.out.println("🟡 DISPERSO. Requiere ajuste de pesos (0.95 -> 0.92).");
    }
    
    public static void exportarEfectividadCSV(String jugada, int aciertos, double delta) {
        String rutaCSV = "reporte_precision_matrix.csv";
        File file = new File(rutaCSV);
        boolean existe = file.exists();

        try (FileWriter fw = new FileWriter(rutaCSV, true); 
             PrintWriter pw = new PrintWriter(fw)) {
            
            if (!existe) pw.println("Fecha,Jugada,Aciertos,Delta"); // Encabezado para Excel
            
            pw.printf("%s,\"%s\",%d,%.2f\n", new Date().toString(), jugada, aciertos, delta);
            
            System.out.println("✅ Datos exportados a " + rutaCSV);
        } catch (IOException e) {
            System.err.println("Error al exportar CSV: " + e.getMessage());
        }
    }
    public static void generarResumenMarzo() {
        String rutaCSV = "reporte_precision_matrix.csv";
        double mejorDelta = 56.0;
        int totalAciertos = 0;
        int jugadasProcesadas = 0;

        System.out.println("\n==========================================");
        System.out.println("   RESUMEN MENSUAL: MARZO 2026");
        System.out.println("==========================================");

        try (BufferedReader br = new BufferedReader(new FileReader(rutaCSV))) {
            String linea;
            br.readLine(); // Saltar encabezado

            while ((linea = br.readLine()) != null) {
                String[] columnas = linea.split(",");
                if (columnas.length >= 4) {
                    int aciertos = Integer.parseInt(columnas[2]);
                    double delta = Double.parseDouble(columnas[3]);

                    if (delta < mejorDelta) mejorDelta = delta;
                    totalAciertos += aciertos;
                    jugadasProcesadas++;
                }
            }

            System.out.println("JUGADAS AUDITADAS: " + jugadasProcesadas);
            System.out.println("TOTAL ACIERTOS NATURALES: " + totalAciertos);
            System.out.printf("MEJOR CERCANÍA (MIN DELTA): %.2f \n", mejorDelta);
            
            evaluarProgresoMensual(mejorDelta);

        } catch (IOException e) {
            System.err.println("Aún no hay datos suficientes en el CSV.");
        }
        System.out.println("==========================================\n");
    }

    private static void evaluarProgresoMensual(double mejorDelta) {
        if (mejorDelta < 3.0) {
            System.out.println("RESULTADO: 🏆 MARZO EXITOSO. El algoritmo está en zona de premios.");
        } else {
            System.out.println("RESULTADO: 🔧 EN CALIBRACIÓN. Abril requiere ajuste de vecinos.");
        }
    }
    public static void encontrarNumeroRey() {
        String rutaCSV = "reporte_precision_matrix.csv";
        Map<Integer, Double> puntajeNumeros = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(rutaCSV))) {
            String linea;
            br.readLine(); // Saltar encabezado

            while ((linea = br.readLine()) != null) {
                String[] columnas = linea.split(",");
                if (columnas.length >= 4) {
                    // Limpiar la jugada de comillas y corchetes
                    String jugadaLimpia = columnas[1].replace("\"", "").replace("[", "").replace("]", "");
                    double delta = Double.parseDouble(columnas[3]);
                    
                    // Un Delta bajo da más puntos (Inversa del Delta)
                    double puntosPorAparicion = 10.0 / (delta + 1.0); 

                    String[] nums = jugadaLimpia.split(",");
                    for (String n : nums) {
                        int num = Integer.parseInt(n.trim());
                        puntajeNumeros.put(num, puntajeNumeros.getOrDefault(num, 0.0) + puntosPorAparicion);
                    }
                }
            }

            // Ordenar para encontrar el Top 1
            int numeroRey = puntajeNumeros.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(-1);

            System.out.println("\n👑 EL NÚMERO REY DE MARZO ES: " + numeroRey);
            System.out.println("Este número tuvo la mayor 'Presión de Éxito' en tus auditorías.");
            System.out.println("Sugerencia: Úsalo como base fija para tu primer boleto de Abril.");

        } catch (IOException e) {
            System.err.println("Error al procesar el Número Rey: " + e.getMessage());
        }
    }
}