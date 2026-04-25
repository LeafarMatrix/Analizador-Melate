import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class GeneradorMatrix {
    public static List<Integer> generarJugadaMaestra(String rutaHistorico) {
        Map<Integer, Double> mapaPesos = new HashMap<>();
        List<String> lineas = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(rutaHistorico))) {
            String linea;
            br.readLine(); 
            while ((linea = br.readLine()) != null) { lineas.add(linea); }
        } catch (IOException e) { System.err.println("Error: " + e.getMessage()); }

        // 1. CÁLCULO DE PESOS EXPONENCIALES (0.96)
        for (int i = 0; i < lineas.size(); i++) {
            String[] datos = lineas.get(i).split(",");
            if (datos.length < 8) continue;
            
            double peso = Math.pow(0.96, i); 
            for (int j = 2; j <= 7; j++) {
                int num = Integer.parseInt(datos[j].trim());
                mapaPesos.put(num, mapaPesos.getOrDefault(num, 0.0) + peso);
            }
        }

        // 2. TUNING DE TENDENCIA (Hotfix 1.15% para números <= 15)
        if (!lineas.isEmpty()) {
            String[] ultimoSorteo = lineas.get(0).split(",");
            for (int j = 2; j <= 7; j++) {
                int num = Integer.parseInt(ultimoSorteo[j].trim());
                if (num <= 15) {
                    mapaPesos.put(num, mapaPesos.getOrDefault(num, 0.0) * 1.15);
                }
            }
        }

        // --- LÓGICA DE EXCLUSIÓN DE COLISIÓN (Sorteo 4203) ---
        List<Integer> colision4203 = Arrays.asList(5, 25, 29, 38, 41, 48);
        
        List<Integer> candidatos = mapaPesos.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .filter(n -> !colision4203.contains(n) || n == 41) // Mantenemos el 41 por ser Ancla
                .collect(Collectors.toList());

        List<Integer> seleccionados = new ArrayList<>(candidatos.stream().limit(6).collect(Collectors.toList()));
        
        // 3. VALIDACIÓN DE INTEGRIDAD Y REBALANCEO
        if (!validarSuma(seleccionados)) {
            for (int k = 6; k < candidatos.size(); k++) {
                seleccionados.set(5, candidatos.get(k));
                if (validarSuma(seleccionados)) break;
            }
        }

        Collections.sort(seleccionados);
        return seleccionados;
    }

    public static boolean validarSuma(List<Integer> lista) {
        int suma = lista.stream().mapToInt(Integer::intValue).sum();
        return suma >= 130 && suma <= 190;
    }
}