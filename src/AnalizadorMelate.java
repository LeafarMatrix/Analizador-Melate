import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class AnalizadorMelate {

	public static void main(String[] args) {
        String archivoRuta = "historico_melate.txt";
        File file = new File(archivoRuta);

        if (!file.exists()) {
            System.err.println("Error: No se encuentra 'historico_melate.txt'");
        } else {
            System.out.println("--- INICIANDO ANÁLISIS DINÁMICO ---");
            for (int i = 1; i <= 5; i++) {
                System.out.print("Generando juego " + i + "... ");
                analizarHistorico(archivoRuta);
            }
            System.out.println("--- PROCESO FINALIZADO ---");
            
            System.out.println("\n--- VERIFICANDO BOLETO SORTEO 4188 ---");
            
            // Resultados oficiales del 18 de Marzo 2026
            List<Integer> ganadorMelate = Arrays.asList(25, 38, 48, 49, 52, 56);
            List<Integer> ganadorRevancha = Arrays.asList(2, 29, 31, 43, 51, 53);
            List<Integer> ganadorRevanchita = Arrays.asList(11, 23, 29, 30, 43, 50);  
            
            // Tu línea E del boleto (la que tuvo los 2 aciertos en Melate)
            List<Integer> miApuestaE = Arrays.asList(6, 13, 32, 33, 45, 48); 

            // Verificación automática
            verificarBoleto(miApuestaE, ganadorMelate, "MELATE NATURAL");
            verificarBoleto(miApuestaE, ganadorRevancha, "REVANCHA");
            verificarBoleto(miApuestaE, ganadorRevanchita, "REVANCHITA");
        }
    }

    // Filtro de dispersión de Rafael
    private static boolean esDispersa(List<Integer> numeros) {
        int contiguos = 0;
        for (int i = 0; i < numeros.size() - 1; i++) {
            if (numeros.get(i + 1) - numeros.get(i) <= 2) {
                contiguos++;
            }
        }
        return contiguos <= 2;
    }

    public static void analizarHistorico(String ruta) {
        Map<Integer, Double> frecuenciaNumeros = new HashMap<>();
        Map<Integer, Integer> ultimaAparicionDistancia = new HashMap<>();
  
        for (int n = 1; n <= 56; n++) {
            ultimaAparicionDistancia.put(n, 999);
        }

      //  int limiteSorteosRecientes = 100;
        List<String> todasLasLineas = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(ruta))) {
            String linea;
            br.readLine(); // Saltar encabezado (NPRODUCTO, CONCURSO, R1...)
            while ((linea = br.readLine()) != null) {
                if (!linea.trim().isEmpty()) todasLasLineas.add(linea);
            }

            int totalLineas = todasLasLineas.size();
      //      int inicioTendencia = Math.max(0, totalLineas - limiteSorteosRecientes);

       
         // DENTRO DE analizarHistorico, en el bucle de las líneas:
            for (int i = 0; i < totalLineas; i++) {
                String[] campos = todasLasLineas.get(i).split(",");
                if (campos.length >= 8) {
                    // Calculamos el peso: los sorteos más nuevos (cercanos al índice 0) valen más
                    // Si es el sorteo más reciente, peso es 100. Si es el sorteo 100, peso es 1.
                    double peso = Math.max(0, 100 - ((double) i)); 

                    for (int j = 2; j <= 7; j++) {
                        try {
                            int num = Integer.parseInt(campos[j].trim());
                            if (num >= 1 && num <= 56) {
                                // En lugar de +1, sumamos el peso
                                // Cambia frecuenciaNumeros a Map<Integer, Double>
                                frecuenciaNumeros.put(num, frecuenciaNumeros.getOrDefault(num, 0.0) + peso);
                                
                                // Mantenemos la distancia igual
                                ultimaAparicionDistancia.put(num, i); 
                            }
                        } catch (NumberFormatException e) {}
                    }
                }
            } 
            
     //        

            List<Integer> calientes = obtenerTopNumeros(frecuenciaNumeros, 12);
            List<Integer> frios = ultimaAparicionDistancia.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(12).map(Map.Entry::getKey).collect(Collectors.toList());

            if (totalLineas > 0) {
                // CAMBIO CLAVE: Usamos el índice 0 para tomar la primera línea después del encabezado
                // que según tu archivo es la del 13/03/2026
                String ultimaLineaRaw = todasLasLineas.get(0); 
                String[] camposUltima = ultimaLineaRaw.split(",");
                
                List<Integer> anterior = new ArrayList<>();
                try {
                    // Extraemos R1 a R6 (Índices 2 al 7)
                    for (int j = 2; j <= 7; j++) {
                        anterior.add(Integer.parseInt(camposUltima[j].trim()));
                    }
                } catch (Exception e) {
                    System.err.println("Error al leer el primer registro: " + e.getMessage());
                }

                System.out.println("DEBUG - Sorteo anterior REAL detectado (el primero del archivo): " + anterior);
                
                String sugerenciaFinal = generarSugerenciaPro(calientes, frios, anterior);
                exportarSugerencia(sugerenciaFinal);
            }

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static String generarSugerenciaPro(List<Integer> calientes, List<Integer> frios, List<Integer> anterior) {
        Random rand = new Random();
        List<Integer> sugerencia;
        int intentos = 0;

        do {
            Set<Integer> conjunto = new TreeSet<>();
            
            // --- NUEVO: FILTRO DE VECINOS ---
            // Elegimos un número al azar del sorteo anterior y le sumamos o restamos 1
            int numeroBase = anterior.get(rand.nextInt(anterior.size()));
            int vecino = rand.nextBoolean() ? numeroBase + 1 : numeroBase - 1;
            
            // Aseguramos que el vecino esté en el rango legal (1-56)
            if (vecino < 1) vecino = 2;
            if (vecino > 56) vecino = 55;
            conjunto.add(vecino);

            // --- EL RESTO DE TU LÓGICA DE MEZCLA ---
            // 2 Calientes + 1 Frío + 2 Azar (ajustado para dejar espacio al vecino)
            while (conjunto.size() < 3) conjunto.add(calientes.get(rand.nextInt(calientes.size())));
            while (conjunto.size() < 4) conjunto.add(frios.get(rand.nextInt(frios.size())));
            while (conjunto.size() < 6) conjunto.add(rand.nextInt(56) + 1);
            
            sugerencia = new ArrayList<>(conjunto);
            intentos++;
            
        } while ((!validarReglas(sugerencia, anterior) || !esDispersa(sugerencia)) && intentos < 30000);

        StringBuilder sb = new StringBuilder();
        sb.append("COMBINACIÓN (Con Filtro Vecinos): ").append(sugerencia).append(" SUMA: ")
          .append(sugerencia.stream().mapToInt(Integer::intValue).sum());
      
        System.out.println(sb.toString() + " FECHA: " + new Date());
        return sb.toString() + "\nFECHA: " + new Date();
    }

    private static boolean validarReglas(List<Integer> lista, List<Integer> sorteoAnterior) {
        Collections.sort(lista);
        int pares = 0, suma = 0, consecutivos = 0, totalPrimos = 0, repetidosAnterior = 0;
        Map<Integer, Integer> conteoDecadas = new HashMap<>();
        Map<Integer, Integer> conteoTerminaciones = new HashMap<>();
        
        List<Integer> primos = Arrays.asList(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53);

        for (int i = 0; i < lista.size(); i++) {
            int actual = lista.get(i);
            suma += actual;
            if (actual % 2 == 0) pares++;
            if (primos.contains(actual)) totalPrimos++;
            if (sorteoAnterior.contains(actual)) repetidosAnterior++;

            int decada = actual / 10;
            conteoDecadas.put(decada, conteoDecadas.getOrDefault(decada, 0) + 1);

            int term = actual % 10;
            conteoTerminaciones.put(term, conteoTerminaciones.getOrDefault(term, 0) + 1);

            if (i < lista.size() - 1 && lista.get(i + 1) - actual == 1) consecutivos++;
        }

        boolean decadasSanas = conteoDecadas.values().stream().allMatch(v -> v <= 3);
        boolean termSanas = conteoTerminaciones.values().stream().allMatch(v -> v <= 2);

     // REGLA DE CONSECUTIVOS ACTUALIZADA
        // consecutivos == 1 -> Un par (ej. 30, 31)
        // consecutivos == 2 -> Dos pares (ej. 12, 13 y 40, 41) O un trío (ej. 12, 13, 14)
        boolean tieneConsecutivosInteresantes = (consecutivos >= 1 && consecutivos <= 2);

        return (suma >= 140 && suma <= 210) && 
               (repetidosAnterior <= 1) &&      
               (pares >= 2 && pares <= 4) && 
               tieneConsecutivosInteresantes && // <--- Cambio aquí
               (conteoDecadas.size() >= 3) && 
               decadasSanas && termSanas && 
               (totalPrimos >= 1 && totalPrimos <= 2);
        
       
    }

    private static void exportarSugerencia(String contenido) {
        try (FileWriter fw = new FileWriter("historial_jugadas_melate.txt", true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println("-------------------------");
            pw.println(contenido);
        } catch (IOException e) {
            System.err.println("Error al exportar.");
        }
    }

    
    private static List<Integer> obtenerTopNumeros(Map<Integer, Double> mapa, int cantidad) {
        return mapa.entrySet().stream()
                // Ordenamos de mayor a menor peso acumulado
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(cantidad)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    private static void verificarBoleto(List<Integer> jugada, List<Integer> resultado, String modalidad) {
        List<Integer> aciertos = jugada.stream()
                .filter(resultado::contains)
                .collect(Collectors.toList());

        System.out.println("Resultados para " + modalidad + ":");
        System.out.println("Tu jugada: " + jugada);
        System.out.println("Aciertos (" + aciertos.size() + "): " + aciertos);
        
        if (aciertos.size() >= 2) {
            System.out.println("¡FELICIDADES! Tienes premio en " + modalidad);
        } else {
            System.out.println("Sigue participando en " + modalidad);
        }
        System.out.println("-------------------------");
    }
       
}