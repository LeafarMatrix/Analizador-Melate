import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class AnalizadorMelate {

    public static void main(String[] args) {
        String archivoRuta = "historico_melate.txt";
        
        System.out.println("--- ACTUALIZANDO MODELO CON SORTEO 4190 ---");
        
        // 1. RESULTADOS OFICIALES SORTEO 4190 (Domingo 22 Marzo)
        List<Integer> ganadorMelate = Arrays.asList(7, 20, 23, 27, 29, 30); 
        int adicionalOficial = 11;
        List<Integer> ganadorRevancha = Arrays.asList(2, 10, 15, 25, 36, 49);
        List<Integer> ganadorRevanchita = Arrays.asList(22, 25, 34, 43, 44, 55); 

        // Tu Línea C del boleto (La ganadora de hoy)
        List<Integer> miLineaC = Arrays.asList(15, 22, 27, 30, 31, 43);
        
        System.out.println("\n--- VERIFICANDO PREMIOS DEL BOLETO ---");
        verificarBoletoConAdicional(miLineaC, ganadorMelate, adicionalOficial, "MELATE NATURAL");
        verificarBoleto(miLineaC, ganadorRevancha, "REVANCHA");
        verificarBoleto(miLineaC, ganadorRevanchita, "REVANCHITA");

        // 2. GENERACIÓN PARA EL MIÉRCOLES
        System.out.println("\n--- GENERANDO JUEGOS PARA EL PRÓXIMO MIÉRCOLES ---");
        for (int i = 1; i <= 4; i++) {
            System.out.print("Generando juego " + (char)('A' + i - 1) + "... ");
            analizarHistorico(archivoRuta);
        }
    }

    // --- MÉTODOS DE VERIFICACIÓN ---

    private static void verificarBoletoConAdicional(List<Integer> jugada, List<Integer> resultado, int adicional, String modalidad) {
        List<Integer> aciertos = jugada.stream()
                .filter(resultado::contains)
                .collect(Collectors.toList());
        
        boolean tieneAdicional = jugada.contains(adicional);
        int totalAciertos = aciertos.size();

        System.out.println("Resultados para " + modalidad + ":");
        System.out.println("Aciertos naturales: " + aciertos + " | ¿Adicional (11)?: " + (tieneAdicional ? "SÍ" : "NO"));
        
        if ((totalAciertos >= 3) || (totalAciertos == 2 && tieneAdicional)) {
            System.out.println("¡FELICIDADES! Tienes premio en " + modalidad);
        } else {
            System.out.println("Sigue participando.");
        }
        System.out.println("-------------------------");
    }

    private static void verificarBoleto(List<Integer> jugada, List<Integer> resultado, String modalidad) {
        List<Integer> aciertos = jugada.stream()
                .filter(resultado::contains)
                .collect(Collectors.toList());

        System.out.println("Resultados para " + modalidad + ":");
        System.out.println("Aciertos (" + aciertos.size() + "): " + aciertos);
        
        int minimoAciertos = modalidad.equals("REVANCHITA") ? 6 : 3;
        
        if (aciertos.size() >= minimoAciertos) {
            System.out.println("¡FELICIDADES! Tienes premio en " + modalidad);
        } else {
            System.out.println("Sigue participando.");
        }
        System.out.println("-------------------------");
    }

    // --- LÓGICA DE ANÁLISIS ---

    public static void analizarHistorico(String ruta) {
        Map<Integer, Double> frecuenciaNumeros = new HashMap<>();
        Map<Integer, Integer> ultimaAparicionDistancia = new HashMap<>();
        List<String> todasLasLineas = new ArrayList<>();

        for (int n = 1; n <= 56; n++) ultimaAparicionDistancia.put(n, 999);

        try (BufferedReader br = new BufferedReader(new FileReader(ruta))) {
            String linea;
            br.readLine(); // Encabezado
            while ((linea = br.readLine()) != null) {
                if (!linea.trim().isEmpty()) todasLasLineas.add(linea);
            }

            for (int i = 0; i < todasLasLineas.size(); i++) {
                String[] campos = todasLasLineas.get(i).split(",");
                if (campos.length >= 8) {
                    double peso = Math.pow(0.95, i) * 100;
                    for (int j = 2; j <= 7; j++) {
                        try {
                            int num = Integer.parseInt(campos[j].trim());
                            if (num >= 1 && num <= 56) {
                                frecuenciaNumeros.put(num, frecuenciaNumeros.getOrDefault(num, 0.0) + peso);
                                if (ultimaAparicionDistancia.get(num) == 999) ultimaAparicionDistancia.put(num, i);
                            }
                        } catch (Exception e) {}
                    }
                }
            }

            List<Integer> calientes = obtenerTopNumeros(frecuenciaNumeros, 12);
            List<Integer> frios = ultimaAparicionDistancia.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(12).map(Map.Entry::getKey).collect(Collectors.toList());

            if (!todasLasLineas.isEmpty()) {
                List<Integer> anterior = new ArrayList<>();
                String[] camposUltima = todasLasLineas.get(0).split(",");
                for (int j = 2; j <= 7; j++) anterior.add(Integer.parseInt(camposUltima[j].trim()));

                String sugerenciaFinal = generarSugerenciaPro(calientes, frios, anterior);
                exportarSugerencia(sugerenciaFinal);
            }
        } catch (IOException e) { System.err.println("Error: " + e.getMessage()); }
    }

    private static String generarSugerenciaPro(List<Integer> calientes, List<Integer> frios, List<Integer> anterior) {
        Random rand = new Random();
        List<Integer> sugerencia;
        int intentos = 0;
        do {
            Set<Integer> conjunto = new TreeSet<>();
            int numeroBase = anterior.get(rand.nextInt(anterior.size()));
            int vecino = rand.nextBoolean() ? numeroBase + 1 : numeroBase - 1;
            if (vecino < 1) vecino = 2; if (vecino > 56) vecino = 55;
            conjunto.add(vecino);

            while (conjunto.size() < 3) conjunto.add(calientes.get(rand.nextInt(calientes.size())));
            while (conjunto.size() < 4) conjunto.add(frios.get(rand.nextInt(frios.size())));
            while (conjunto.size() < 6) conjunto.add(rand.nextInt(56) + 1);
            
            sugerencia = new ArrayList<>(conjunto);
            intentos++;
        } while ((!validarReglas(sugerencia, anterior) || !esDispersa(sugerencia)) && intentos < 30000);

        String res = "COMBINACIÓN: " + sugerencia + " SUMA: " + sugerencia.stream().mapToInt(Integer::intValue).sum();
        System.out.println(res);
        return res + "\nFECHA: " + new Date();
    }

    private static boolean validarReglas(List<Integer> lista, List<Integer> sorteoAnterior) {
        Collections.sort(lista);
        int pares = 0, suma = 0, consecutivos = 0, totalPrimos = 0;
        List<Integer> primos = Arrays.asList(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53);
        for (int i = 0; i < lista.size(); i++) {
            int n = lista.get(i);
            suma += n;
            if (n % 2 == 0) pares++;
            if (primos.contains(n)) totalPrimos++;
            if (i < lista.size() - 1 && lista.get(i + 1) - n == 1) consecutivos++;
        }
        return (suma >= 130 && suma <= 210) && (consecutivos >= 1) && (pares >= 2 && pares <= 4) && (totalPrimos >= 1 && totalPrimos <= 3);
    }

    private static boolean esDispersa(List<Integer> numeros) {
        int contiguos = 0;
        for (int i = 0; i < numeros.size() - 1; i++) if (numeros.get(i + 1) - numeros.get(i) <= 2) contiguos++;
        return contiguos <= 2;
    }

    private static List<Integer> obtenerTopNumeros(Map<Integer, Double> mapa, int cantidad) {
        return mapa.entrySet().stream().sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())).limit(cantidad).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private static void exportarSugerencia(String contenido) {
        try (FileWriter fw = new FileWriter("historial_jugadas_melate.txt", true); PrintWriter pw = new PrintWriter(fw)) {
            pw.println("-------------------------\n" + contenido);
        } catch (Exception e) {}
    }
}