import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MelateAutoActualizador
 * -----------------------------------------------------------------------
 * Descarga el histórico OFICIAL de Melate publicado por Lotería Nacional
 * y agrega a "historico_melate.txt" únicamente los sorteos con número de
 * concurso mayor al último que ya tienes registrado (evita duplicados y
 * evita tener que comparar combinaciones número por número).
 *
 * Mantiene un archivo pequeño "ultimo_concurso.txt" con el número de
 * concurso más reciente ya procesado.
 *
 * PENSADO PARA EJECUTARSE SOLO, vía tarea programada (cron / Task
 * Scheduler), después de cada sorteo (miércoles, viernes y domingo,
 * ~21:15 hrs + margen). Ver instrucciones de programación al final
 * de este archivo.
 *
 * USO MANUAL:
 *   javac MelateAutoActualizador.java
 *   java MelateAutoActualizador
 * -----------------------------------------------------------------------
 */
public class MelateAutoActualizador {

    // URL pública del histórico oficial de Melate (Lotería Nacional)
    static final String URL_CSV = "https://www.loterianacional.gob.mx/Documentos/Historicos/Melate.csv";

    static final String ARCHIVO_HISTORICO = "historico_melate.txt";
    static final String ARCHIVO_MARCADOR = "ultimo_concurso.txt";
    static final String ARCHIVO_LOG = "actualizacion_melate.log";

    public static void main(String[] args) {
        try {
            ejecutar();
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void ejecutar() throws Exception {
        int ultimoConcurso = leerUltimoConcurso();
        log("Último concurso registrado localmente: " + ultimoConcurso);

        String csv = descargarCSV(URL_CSV);
        List<String[]> filas = parsearCSV(csv);
        log("Filas leídas del CSV oficial: " + filas.size());

        List<String> nuevasLineas = new ArrayList<>();
        int maxConcursoEncontrado = ultimoConcurso;

        for (String[] campos : filas) {
            if (campos.length < 11) {
				continue;
			}
            int concurso;
            try {
                concurso = Integer.parseInt(campos[1].trim());
            } catch (NumberFormatException e) {
                continue; // encabezado u otra fila no numérica
            }
            if (concurso <= ultimoConcurso) {
				continue;
			}

            // Se conserva la fila completa (NPRODUCTO,CONCURSO,R1..R6,R7,BOLSA,FECHA)
            // para que el formato coincida con el resto de historico_melate.txt
            // y con lo que espera MelateAnalizadorAvanzado.
            StringBuilder linea = new StringBuilder();
            for (int i = 0; i < campos.length; i++) {
                if (i > 0) {
					linea.append(",");
				}
                linea.append(campos[i].trim());
            }
            nuevasLineas.add(linea.toString());
            if (concurso > maxConcursoEncontrado) {
				maxConcursoEncontrado = concurso;
			}
        }

        if (nuevasLineas.isEmpty()) {
            log("No hay sorteos nuevos. Todo al día.");
            return;
        }

        try (BufferedWriter bw = Files.newBufferedWriter(
                Paths.get(ARCHIVO_HISTORICO),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (String linea : nuevasLineas) {
                bw.write(linea);
                bw.newLine();
            }
        }

        guardarUltimoConcurso(maxConcursoEncontrado);
        log("Se agregaron " + nuevasLineas.size() + " sorteo(s) nuevo(s). " +
                "Último concurso ahora: " + maxConcursoEncontrado);
    }

    static String descargarCSV(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("No se pudo descargar el CSV (HTTP " + response.statusCode() + ")");
        }
        return response.body();
    }

    static List<String[]> parsearCSV(String csv) {
        List<String[]> filas = new ArrayList<>();
        for (String linea : csv.split("\\R")) {
            linea = linea.trim();
            if (linea.isEmpty() || linea.toUpperCase().startsWith("NPRODUCTO")) {
				continue;
			}
            filas.add(linea.split(","));
        }
        return filas;
    }

    static int leerUltimoConcurso() throws IOException {
        Path path = Paths.get(ARCHIVO_MARCADOR);
        if (!Files.exists(path)) {
			return 0;
		}
        String contenido = Files.readString(path).trim();
        if (contenido.isEmpty()) {
			return 0;
		}
        try {
            return Integer.parseInt(contenido);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void guardarUltimoConcurso(int concurso) throws IOException {
        Files.writeString(Paths.get(ARCHIVO_MARCADOR), String.valueOf(concurso));
    }

    static void log(String mensaje) {
        String linea = "[" + LocalDateTime.now() + "] " + mensaje;
        System.out.println(linea);
        try (BufferedWriter bw = Files.newBufferedWriter(
                Paths.get(ARCHIVO_LOG),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            bw.write(linea);
            bw.newLine();
        } catch (IOException ignored) {
        }
    }
}

/*
 * -----------------------------------------------------------------------
 * PROGRAMAR EJECUCIÓN AUTOMÁTICA
 * -----------------------------------------------------------------------
 *
 * Primero compílalo una vez:
 *   javac MelateAutoActualizador.java
 *
 * Los sorteos son miércoles, viernes y domingo a las 21:15. Programa la
 * tarea para que corra con margen, por ejemplo a las 23:00 esos días.
 *
 * WINDOWS (Task Scheduler / schtasks):
 *   schtasks /Create /SC WEEKLY /D WED,FRI,SUN /ST 23:00 ^
 *     /TN "MelateAutoActualizador" ^
 *     /TR "java -cp C:\ruta\a\tu\carpeta MelateAutoActualizador"
 *
 * LINUX/macOS (cron) - edita con `crontab -e` y agrega:
 *   0 23 * * 3,5,0 cd /ruta/a/tu/carpeta && java MelateAutoActualizador >> cron.log 2>&1
 *   (3=miércoles, 5=viernes, 0=domingo)
 *
 * Revisa "actualizacion_melate.log" después de cada corrida para
 * confirmar que se agregaron los sorteos nuevos.
 * -----------------------------------------------------------------------
 */