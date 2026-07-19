import java.util.List;
import java.util.stream.Collectors;

/**
 * Verifica cuantos aciertos tiene una jugada contra un resultado oficial y
 * si alcanza el minimo de aciertos definido para la modalidad (MELATE,
 * REVANCHA, REVANCHITA o una modalidad personalizada con --min).
 */
public class Verificador {
    public static void verificar(List<Integer> jugada, List<Integer> resultado, Integer adicional,
                                  int minimo, boolean contarAdicional, String modalidad) {
        List<Integer> aciertos = jugada.stream().filter(resultado::contains).collect(Collectors.toList());
        boolean tieneAdicional = adicional != null && jugada.contains(adicional);
        int total = aciertos.size();

        System.out.println("[" + modalidad + "] Aciertos (" + total + "): " + aciertos
                + (tieneAdicional ? " + ADICIONAL (" + adicional + ")" : ""));

        boolean premio = contarAdicional
                ? (total >= minimo || (total == minimo - 1 && tieneAdicional))
                : total >= minimo;

        System.out.println(premio ? ">>> PREMIO DETECTADO <<<" : "Sin premio (minimo requerido: " + minimo + ")");
    }
}
