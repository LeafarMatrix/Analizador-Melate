import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalizadorParesTrios {

    private final Map<String, Integer> frecuenciaPares = new HashMap<>();
    private final Map<String, Integer> frecuenciaTrios = new HashMap<>();

    public void analizar(List<Sorteo> historial) {
        frecuenciaPares.clear();
        frecuenciaTrios.clear();

        for (Sorteo s : historial) {
            List<Integer> nums = new ArrayList<>(s.numeros());
            Collections.sort(nums);
            int n = nums.size();

            for (int i = 0; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    frecuenciaPares.merge(nums.get(i) + "-" + nums.get(j), 1, Integer::sum);
                }
            }

            for (int i = 0; i < n - 2; i++) {
                for (int j = i + 1; j < n - 1; j++) {
                    for (int k = j + 1; k < n; k++) {
                        frecuenciaTrios.merge(nums.get(i) + "-" + nums.get(j) + "-" + nums.get(k), 1, Integer::sum);
                    }
                }
            }
        }
    }

    public List<EntradaFrecuencia<List<Integer>>> topPares(int n) {
        return top(frecuenciaPares, n);
    }

    public List<EntradaFrecuencia<List<Integer>>> topTrios(int n) {
        return top(frecuenciaTrios, n);
    }

    public int frecuenciaPar(int a, int b) {
        int min = Math.min(a, b), max = Math.max(a, b);
        return frecuenciaPares.getOrDefault(min + "-" + max, 0);
    }

    public Map<String, Integer> getFrecuenciaPares() {
        return Collections.unmodifiableMap(frecuenciaPares);
    }

    private List<EntradaFrecuencia<List<Integer>>> top(Map<String, Integer> mapa, int n) {
        return mapa.entrySet().stream()
             	.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())   
                .limit(n)
                .map(e -> new EntradaFrecuencia<>(parseClave(e.getKey()), e.getValue()))
                .toList();
    }

    private List<Integer> parseClave(String clave) {
        List<Integer> nums = new ArrayList<>();
        for (String parte : clave.split("-")) {
            nums.add(Integer.parseInt(parte));
        }
        return nums;
    }
}
