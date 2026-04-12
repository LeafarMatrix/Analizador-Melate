import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class AnalizadorEfectividad {
    
    public static void main(String[] args) {
        String rutaHistorico = "historico_melate.txt"; 
        
        // 1. MANTENIMIENTO
        limpiarPipeline(); 
        
        // 2. GENERACIÓN AUTOMÁTICA (Sin captura manual)
        // El sistema consulta al GeneradorMatrix y obtiene la mejor jugada calculada
        List<Integer> miJugada = GeneradorMatrix.generarJugadaMaestra(rutaHistorico);
        
        System.out.println("\n🎲 JUGADA MAESTRA CALCULADA POR LA MATRIX: " + miJugada);
        System.out.println("-----------------------------------------------------------");
        
        // 3. ANÁLISIS DE SIMETRÍA
        ejecutarSimetriaEspejo(miJugada);
        
        // 4. AUDITORÍA OFICIAL (Resultados reales del sorteo 4198)
        List<Integer> melateReal = Arrays.asList(5, 8, 25, 27, 30, 38); 
        List<Integer> revanchaReal = Arrays.asList(9, 12, 19, 26, 36, 49);
        List<Integer> revanchitaReal = Arrays.asList(4, 5, 20, 38, 52, 55);

        System.out.println("🚀 EJECUTANDO AUDITORÍA SOBRE JUGADA AUTOMATIZADA...");
        auditarSorteoCompleto(miJugada, melateReal, revanchaReal, revanchitaReal);
        
        // 5. PROYECCIÓN PARA EL MIÉRCOLES (Sorteo 4200)
        sugerirJugadaCentenaria(rutaHistorico);
        
        // 6. REPORTING ESTADÍSTICO
        generarResumenMarzo();
        
        System.out.println("=== PIPELINE FINALIZADO: SISTEMA 100% AUTÓNOMO ===");
    }

    public static void auditarSorteoCompleto(List<Integer> miJugada, 
                                            List<Integer> resMelate, 
                                            List<Integer> resRevancha, 
                                            List<Integer> resRevanchita) {
        
        System.out.println("\n🔍 AUDITORÍA INTEGRAL - SORTEO 4198");
        System.out.println("==========================================");
        
        ResultadosJuego melate = calcularMetricas("MELATE    ", miJugada, resMelate);
        ResultadosJuego revancha = calcularMetricas("REVANCHA  ", miJugada, resRevancha);
        ResultadosJuego revanchita = calcularMetricas("REVANCHITA", miJugada, resRevanchita);

        ResultadosJuego mejor = melate;
        if (revancha.delta < mejor.delta) mejor = revancha;
        if (revanchita.delta < mejor.delta) mejor = revanchita;

        System.out.println("------------------------------------------");
        System.out.println("🏆 MEJOR DESEMPEÑO: " + mejor.nombreJuego);
        
        // ACTIVACIÓN DEL VAULT: Si detecta éxito (3+ aciertos o delta crítico), respalda la jugada
        if (mejor.aciertos >= 3 || mejor.delta < 1.0) {
            registrarEnVault(miJugada, mejor.aciertos, mejor.delta, mejor.nombreJuego);
        }
        
        exportarEfectividadCSV(miJugada.toString(), mejor.aciertos, mejor.delta);
        System.out.println("==========================================\n");
    }

    public static void sugerirJugadaCentenaria(String rutaHistorico) {
        System.out.println("\n🎯 GENERANDO PROYECCIÓN MAESTRA PARA SORTEO 4200");
        System.out.println("===========================================================");
        
        List<Integer> topActual = GeneradorMatrix.generarJugadaMaestra(rutaHistorico);
        Map<Integer, Integer> frecuenciaEspejo = new HashMap<>();
        int[] ciclos = {4100, 4000, 3900, 3800, 3700};
        
        for (int c : ciclos) {
            List<Integer> hist = obtenerSorteoPorNumero(c);
            for (int n : hist) frecuenciaEspejo.put(n, frecuenciaEspejo.getOrDefault(n, 0) + 1);
        }

        System.out.println("💎 ANÁLISIS DE CONFLUENCIA (Peso 0.96 + Memoria Centenaria):");
        List<Integer> jugadaFinal = new ArrayList<>();
        
        for (int num : topActual) {
            if (frecuenciaEspejo.containsKey(num)) {
                System.out.printf("  ⭐ Número %d: Detectado en ciclos espejo.\n", num);
                jugadaFinal.add(num);
            }
        }

        if (jugadaFinal.size() < 6) {
            for (int num : topActual) {
                if (!jugadaFinal.contains(num)) jugadaFinal.add(num);
                if (jugadaFinal.size() >= 6) break;
            }
        }

        Collections.sort(jugadaFinal);
        int suma = jugadaFinal.stream().mapToInt(Integer::intValue).sum();
        
        System.out.println("\n🚀 JUGADA PROYECTADA PARA EL MIÉRCOLES: " + jugadaFinal);
        System.out.println("📊 SUMA TOTAL: " + suma + (suma >= 130 && suma <= 190 ? " [RANGO ÓPTIMO]" : " [AJUSTAR]"));
        System.out.println("===========================================================\n");
    }

    public static void registrarEnVault(List<Integer> jugada, int aciertos, double delta, String juego) {
        String rutaVault = "vault_exitos_matrix.txt";
        try (FileWriter fw = new FileWriter(rutaVault, true); PrintWriter pw = new PrintWriter(fw)) {
            pw.println("==========================================");
            pw.println("TIMESTAMP: " + new Date().toString());
            pw.println("JUEGO: " + juego + " | JUGADA: " + jugada);
            pw.println("ACIERTOS: " + aciertos + " | DELTA: " + delta);
            pw.println("ESTADO: CALIBRACIÓN EXITOSA");
            pw.println("==========================================\n");
            System.out.println("💾 JUGADA ASEGURADA EN EL VAULT DE SEGURIDAD.");
        } catch (IOException e) { System.err.println("Error en Vault: " + e.getMessage()); }
    }

    // --- MÉTODOS DE APOYO (REVISADOS) ---

    private static ResultadosJuego calcularMetricas(String nombre, List<Integer> jugada, List<Integer> resultado) {
        List<Integer> coincidencias = jugada.stream().filter(resultado::contains).collect(Collectors.toList());
        double delta = calcularDelta(jugada, resultado);
        System.out.printf("[%s] Aciertos: %d %s | Delta: %.2f\n", nombre, coincidencias.size(), coincidencias, delta);
        return new ResultadosJuego(nombre, coincidencias.size(), delta);
    }

    private static double calcularDelta(List<Integer> jugada, List<Integer> resultado) {
        double sumaDiferencias = 0;
        for (Integer n : jugada) {
            int dif = resultado.stream().mapToInt(r -> Math.abs(r - n)).min().orElse(56);
            sumaDiferencias += dif;
        }
        return sumaDiferencias / 6.0;
    }

    public static List<Integer> obtenerSorteoPorNumero(int numeroSorteoBusqueda) {
        String rutaHistorico = "historico_melate.txt";
        List<Integer> resultado = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(rutaHistorico))) {
            br.readLine();
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] datos = linea.split(",");
                if (datos.length >= 8) {
                    String idLimpio = datos[1].trim().replaceAll("[^0-9]", "");
                    if (!idLimpio.isEmpty() && Integer.parseInt(idLimpio) == numeroSorteoBusqueda) {
                        for (int j = 2; j <= 7; j++) resultado.add(Integer.parseInt(datos[j].trim()));
                        break;
                    }
                }
            }
        } catch (Exception e) {}
        return resultado;
    }

    public static void ejecutarSimetriaEspejo(List<Integer> jugadaActual) {
        int[] ciclos = {4100, 4000, 3900, 3800, 3700};
        System.out.println("\n🪞 INICIANDO ESCÁNER DE SIMETRÍA ESPEJO");
        System.out.println("===========================================================");
        for (int ciclo : ciclos) {
            List<Integer> hist = obtenerSorteoPorNumero(ciclo);
            if (!hist.isEmpty()) {
                long coinc = jugadaActual.stream().filter(hist::contains).count();
                System.out.printf("🔹 Ciclo %d: %-18s | Coincidencias: %d\n", ciclo, hist, coinc);
            }
        }
        System.out.println("===========================================================\n");
    }

    public static void limpiarPipeline() {
        try {
            File csvFile = new File("reporte_precision_matrix.csv");
            if (csvFile.exists()) {
                List<String> lineasCsv = Files.readAllLines(csvFile.toPath());
                if (lineasCsv.size() > 1) {
                    Map<String, String> unicos = new LinkedHashMap<>();
                    for (int i = 1; i < lineasCsv.size(); i++) {
                        String[] col = lineasCsv.get(i).split(",");
                        if (col.length > 1) unicos.put(col[1], lineasCsv.get(i));
                    }
                    List<String> salida = new ArrayList<>();
                    salida.add(lineasCsv.get(0));
                    salida.addAll(unicos.values());
                    Files.write(csvFile.toPath(), salida);
                }
            }
        } catch (Exception e) {}
    }

    public static void exportarEfectividadCSV(String jugada, int aciertos, double delta) {
        try (FileWriter fw = new FileWriter("reporte_precision_matrix.csv", true); PrintWriter pw = new PrintWriter(fw)) {
            pw.printf("%s,\"%s\",%d,%.2f\n", new Date().toString(), jugada, aciertos, delta);
        } catch (Exception e) {}
    }

    public static void generarResumenMarzo() {
        try (BufferedReader br = new BufferedReader(new FileReader("reporte_precision_matrix.csv"))) {
            br.readLine();
            int totalAciertos = 0, procesadas = 0;
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] col = linea.split(",");
                totalAciertos += Integer.parseInt(col[2].trim());
                procesadas++;
            }
            System.out.println("\n==========================================");
            System.out.println("   RESUMEN ESTADÍSTICO DE LA MATRIX");
            System.out.println("==========================================");
            System.out.println("SORTEOS ANALIZADOS: " + procesadas);
            System.out.println("ACIERTOS TOTALES: " + totalAciertos);
            System.out.println("==========================================\n");
        } catch (Exception e) {}
    }

    private static class ResultadosJuego {
        String nombreJuego; int aciertos; double delta;
        ResultadosJuego(String n, int a, double d) { this.nombreJuego = n; this.aciertos = a; this.delta = d; }
    }
}