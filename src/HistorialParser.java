import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser tolerante para los CSV de historico de sorteos. Melate y Melate
 * Retro comparten el mismo layout de columnas (solo cambia el nombre de las
 * columnas: F1..F7 en Retro, R1..R7 en Melate):
 *
 *   NPRODUCTO,CONCURSO,<6 numeros>,<adicional>,BOLSA,FECHA
 *
 * Se asume que el archivo viene ordenado del sorteo mas reciente al mas
 * antiguo, como los CSV oficiales. Las lineas con formato invalido no
 * detienen el parseo: se cuentan en ultimosDescartados para que quien llame
 * pueda reportarlas en vez de que fallen en silencio.
 */
public class HistorialParser {
    public static int ultimosDescartados = 0;

    public static List<Sorteo> parsear(Path ruta) throws IOException {
        ultimosDescartados = 0;
        List<Sorteo> sorteos = new ArrayList<>();
        List<String> lineas = Files.readAllLines(ruta);
        if (lineas.isEmpty()) return sorteos;

        for (int i = 1; i < lineas.size(); i++) { // linea 0 = encabezado
            String linea = lineas.get(i).trim();
            if (linea.isEmpty()) continue;
            String[] campos = linea.split(",");
            if (campos.length < 8) {
                ultimosDescartados++;
                continue;
            }
            try {
                int concurso = Integer.parseInt(campos[1].trim());
                List<Integer> numeros = new ArrayList<>();
                for (int j = 2; j <= 7; j++) numeros.add(Integer.parseInt(campos[j].trim()));
                Integer adicional = campos.length > 8 ? parseOpcional(campos[8]) : null;
                String fecha = campos.length > 10 ? campos[10].trim() : "";
                sorteos.add(new Sorteo(concurso, numeros, adicional, fecha));
            } catch (NumberFormatException e) {
                ultimosDescartados++;
            }
        }
        return sorteos;
    }

    private static Integer parseOpcional(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
