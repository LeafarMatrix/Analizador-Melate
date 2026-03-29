import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class AnalizadorMelate {

    public static void main(String[] args) {
        String archivoRuta = "historico_melate.txt";
        
        System.out.println("--- ACTUALIZANDO MODELO CON SORTEO 4192 ---");
        
        // 1. RESULTADOS OFICIALES SORTEO 4192 (Viernes 2 Marzo)
     
        
        List<Integer> ganadorMelate = Arrays.asList(8, 24, 29, 33, 41, 50); 
        int adicionalOficial = 23; 
        
        
        List<Integer> ganadorRevancha = Arrays.asList(7,16,27,29,34,37);
        List<Integer> ganadorRevanchita = Arrays.asList(2,3,10,22,31,47); 

        // Tu Línea C del boleto
        List<Integer> miLineaC = Arrays.asList(15, 22, 27, 30, 31, 43);
        
        System.out.println("\n--- VERIFICANDO PREMIOS DEL BOLETO ---");
        verificarBoletoConAdicional(miLineaC, ganadorMelate, adicionalOficial, "MELATE NATURAL");
        verificarBoleto(miLineaC, ganadorRevancha, "REVANCHA");
        verificarBoleto(miLineaC, ganadorRevanchita, "REVANCHITA");

        // 2. GENERACIÓN OPTIMIZADA
        System.out.println("\n--- GENERANDO JUEGOS PARA EL PRÓXIMO MIÉRCOLES ---");
        ejecutarProcesoGeneracion(archivoRuta, 4);
    }

    // --- MÉTODOS DE VERIFICACIÓN ---

    private static void verificarBoletoConAdicional(List<Integer> jugada, List<Integer> resultado, int adicional, String modalidad) {
        List<Integer> aciertos = jugada.stream().filter(resultado::contains).collect(Collectors.toList());
        boolean tieneAdicional = jugada.contains(adicional);
        int total = aciertos.size();

        System.out.println("[" + modalidad + "] Aciertos: " + aciertos + (tieneAdicional ? " + ADICIONAL (" + adicional + ")" : ""));
        if (total >= 3 || (total == 2 && tieneAdicional)) {
            System.out.println(">>> ¡PREMIO DETECTADO EN " + modalidad + "! <<<");
        } else {
            System.out.println("Sin premio suficiente.");
        }
        System.out.println("-------------------------");
    }

    private static void verificarBoleto(List<Integer> jugada, List<Integer> resultado, String modalidad) {
        List<Integer> aciertos = jugada.stream().filter(resultado::contains).collect(Collectors.toList());
        int min = modalidad.equals("REVANCHITA") ? 6 : 3;

        System.out.println("[" + modalidad + "] Aciertos (" + aciertos.size() + "): " + aciertos);
        if (aciertos.size() >= min) {
            System.out.println(">>> ¡PREMIO DETECTADO EN " + modalidad + "! <<<");
        } else {
            System.out.println("Sin premio (Mínimo requerido: " + min + ")");
        }
        System.out.println("-------------------------");
    }

    // --- LÓGICA DE ANÁLISIS Y GENERACIÓN ---

    public static void ejecutarProcesoGeneracion(String ruta, int cantidadJuegos) {
        Map<Integer, Double> frecuenciaNumeros = new HashMap<>();
        Map<Integer, Integer> ultimaAparicionDistancia = new HashMap<>();
        List<String> todasLasLineas = new ArrayList<>();

        for (int n = 1; n <= 56; n++) ultimaAparicionDistancia.put(n, 999);

        try (BufferedReader br = new BufferedReader(new FileReader(ruta))) {
            String linea;
            br.readLine(); // Saltar encabezado
            while ((linea = br.readLine()) != null) {
                if (!linea.trim().isEmpty()) todasLasLineas.add(linea);
            }

            if (todasLasLineas.isEmpty()) return;

            // Análisis de pesos exponenciales
            for (int i = 0; i < todasLasLineas.size(); i++) {
                String[] campos = todasLasLineas.get(i).split(",");
                if (campos.length >= 8) {
                    double peso = Math.pow(0.95, i) * 100;
                    for (int j = 2; j <= 7; j++) {
                        int num = Integer.parseInt(campos[j].trim());
                        frecuenciaNumeros.put(num, frecuenciaNumeros.getOrDefault(num, 0.0) + peso);
                        if (ultimaAparicionDistancia.get(num) == 999) ultimaAparicionDistancia.put(num, i);
                    }
                }
            }

            List<Integer> calientes = obtenerTopNumeros(frecuenciaNumeros, 12);
            List<Integer> frios = ultimaAparicionDistancia.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(12).map(Map.Entry::getKey).collect(Collectors.toList());

            // Obtener el sorteo anterior real para la lógica de vecinos
            List<Integer> anterior = new ArrayList<>();
            String[] camposUltima = todasLasLineas.get(0).split(",");
            for (int j = 2; j <= 7; j++) anterior.add(Integer.parseInt(camposUltima[j].trim()));

            // Generar los juegos solicitados
            for (int i = 0; i < cantidadJuegos; i++) {
                char letra = (char) ('A' + i);
                System.out.print("Generando juego " + letra + "... ");
                String sugerencia = generarSugerenciaPro(calientes, frios, anterior);
                exportarSugerencia(sugerencia);
            }

        } catch (IOException e) {
            System.err.println("Error procesando historial: " + e.getMessage());
        }
    }

    private static String generarSugerenciaPro(List<Integer> calientes, List<Integer> frios, List<Integer> anterior) {
        Random rand = new Random();
        List<Integer> sugerencia;
        int intentos = 0;
        do {
            Set<Integer> conjunto = new TreeSet<>();
            // Lógica de vecinos de Rafael
            int numBase = anterior.get(rand.nextInt(anterior.size()));
            int vecino = rand.nextBoolean() ? numBase + 1 : numBase - 1;
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

 // --- AJUSTE DE REGLAS PARA EL DOMINGO 29 ---
    private static boolean validarReglas(List<Integer> lista, List<Integer> sorteoAnterior) {
        Collections.sort(lista);
        int pares = 0, suma = 0, consecutivos = 0;
        List<Integer> primos = Arrays.asList(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53);

        for (int i = 0; i < lista.size(); i++) {
            int n = lista.get(i);
            suma += n;
            if (n % 2 == 0) pares++;
            if (primos.contains(n)) {
			}
            if (i < lista.size() - 1 && lista.get(i + 1) - n == 1) consecutivos++;
        }

        // CAMBIO: Elevamos la suma a 140-220 porque los números altos (50+) están despertando
        boolean sumaValida = (suma >= 140 && suma <= 220);
        
        // CAMBIO: Filtro de "Inercia de Marzo" (El 29 y 41 están repitiendo mucho)
        boolean tieneNumerosCalientes = lista.contains(29) || lista.contains(41) || lista.contains(23);

        return sumaValida && (consecutivos >= 1) && (pares >= 2 && pares <= 4) && tieneNumerosCalientes;
    }

    private static boolean esDispersa(List<Integer> numeros) {
        int contiguos = 0;
        for (int i = 0; i < numeros.size() - 1; i++) {
            if (numeros.get(i + 1) - numeros.get(i) <= 2) contiguos++;
        }
        return contiguos <= 2;
    }

    private static List<Integer> obtenerTopNumeros(Map<Integer, Double> mapa, int cantidad) {
        return mapa.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(cantidad).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private static void exportarSugerencia(String contenido) {
        try (FileWriter fw = new FileWriter("historial_jugadas_melate.txt", true); 
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println("-------------------------\n" + contenido);
        } catch (Exception e) {}
    }
}