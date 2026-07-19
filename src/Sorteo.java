import java.util.List;

/**
 * Representa un sorteo individual: numero de concurso, los 6 numeros
 * ganadores, el numero adicional (columna F7/R7 del CSV, si existe) y la
 * fecha en texto tal como viene en el historico.
 */
public record Sorteo(int concurso, List<Integer> numeros, Integer adicional, String fecha) {
}
