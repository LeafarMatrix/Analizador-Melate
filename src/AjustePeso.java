import java.util.List;
import java.util.Map;

/**
 * Estrategia de ajuste de pesos (patron Strategy). Recibe el mapa de pesos
 * acumulado hasta el momento y el historial completo de sorteos, y puede
 * modificar el mapa in-place para favorecer u opacar numeros segun su propio
 * criterio. Permite agregar nuevas heuristicas sin tocar el generador
 * principal ni las demas estrategias.
 *
 * La primera estrategia de la lista debe encargarse de poblar el mapa con
 * una base (ver PesoBaseRecencia); las siguientes suman o multiplican sobre
 * esa base.
 */
public interface AjustePeso {
    void aplicar(Map<Integer, Double> pesos, List<Sorteo> historial);
}
