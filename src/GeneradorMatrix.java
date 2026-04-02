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

        for (int i = 0; i < lineas.size(); i++) {
            String[] datos = lineas.get(i).split(",");
            if (datos.length < 8) continue;
            
            double peso = Math.pow(0.96, i); 

            for (int j = 2; j <= 7; j++) {
                int num = Integer.parseInt(datos[j]);
                mapaPesos.put(num, mapaPesos.getOrDefault(num, 0.0) + peso);
            }
        }

        // Ordenamos todos los números por su peso de mayor a menor
        List<Integer> candidatos = mapaPesos.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Intentamos armar la jugada ideal (Top 6)
        List<Integer> seleccionados = candidatos.stream().limit(6).collect(Collectors.toList());
        
        // Si no cumple la suma, hacemos un ajuste técnico (DBA Tuning)
        if (!validarSuma(seleccionados)) {
            System.out.println("⚠️ Jugada del Top 6 fuera de rango (" + seleccionados.stream().mapToInt(Integer::intValue).sum() + "). Aplicando balanceo...");
            // Tomamos los 5 mejores y buscamos un 6to número en el Top 12 que equilibre la suma
            int sumaParcial = seleccionados.stream().limit(5).mapToInt(Integer::intValue).sum();
            for (int k = 5; k < Math.min(candidatos.size(), 12); k++) {
                int nuevoNum = candidatos.get(k);
                if (sumaParcial + nuevoNum >= 130 && sumaParcial + nuevoNum <= 190) {
                    seleccionados.set(5, nuevoNum);
                    break;
                }
            }
        }

        Collections.sort(seleccionados);
        return seleccionados;
    }

    public static boolean validarSuma(List<Integer> jugada) {
        int suma = jugada.stream().mapToInt(Integer::intValue).sum();
        return suma >= 130 && suma <= 190;
    }
    
    public static int calcularSuma(List<Integer> jugada) {
        return jugada.stream().mapToInt(Integer::intValue).sum();
    }
}