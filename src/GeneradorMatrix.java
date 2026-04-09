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
                int num = Integer.parseInt(datos[j]);
                mapaPesos.put(num, mapaPesos.getOrDefault(num, 0.0) + peso);
            }
        }

        // 2. TUNING DE TENDENCIA RECIENTE (Hotfix para números bajos)
        if (!lineas.isEmpty()) {
            String[] ultimoSorteo = lineas.get(0).split(",");
            for (int j = 2; j <= 7; j++) {
                int num = Integer.parseInt(ultimoSorteo[j]);
                if (num <= 15) {
                    // Inyectamos un bono del 15% de peso extra si el número es bajo
                    // Esto ayuda a detectar rachas como la del domingo pasado
                    mapaPesos.put(num, mapaPesos.getOrDefault(num, 0.0) * 1.15);
                }
            }
        }

        List<Integer> candidatos = mapaPesos.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Integer> seleccionados = candidatos.stream().limit(6).collect(Collectors.toList());
        
        // 3. VALIDACIÓN DE INTEGRIDAD (130-190)
        if (!validarSuma(seleccionados)) {
            System.out.println("⚠️ Rebalanceando para cumplir rango 130-190...");
            seleccionados.stream().mapToInt(Integer::intValue).sum();
            // Lógica DBA: Si la suma es baja, buscamos subirla con el siguiente candidato más alto
            // Si es alta, buscamos bajarla.
            for (int k = 6; k < candidatos.size(); k++) {
                seleccionados.set(5, candidatos.get(k));
                if (validarSuma(seleccionados)) break;
            }
        }

        Collections.sort(seleccionados);
        return seleccionados;
    }

    public static boolean validarSuma(List<Integer> jugada) {
        int suma = jugada.stream().mapToInt(Integer::intValue).sum();
        return suma >= 130 && suma <= 190;
    }
}
