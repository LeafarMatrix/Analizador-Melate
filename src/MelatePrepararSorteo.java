/**
 * MelatePrepararSorteo
 * -----------------------------------------------------------------------
 * Runner combinado: ejecuta en orden
 *   1) MelateAutoActualizador.ejecutar()  -> descarga y agrega sorteos nuevos
 *   2) MelateAnalizadorAvanzado.main()    -> analiza el historico actualizado
 *                                             y genera 1 combinacion sugerida
 *
 * Pensado para reemplazar la llamada directa a MelateAutoActualizador en la
 * tarea programada (Task Scheduler), de modo que cada corrida deje el
 * historico al dia Y genere de una vez el reporte estrategia_<concurso>.txt
 * con la combinacion para el proximo sorteo.
 *
 * USO MANUAL:
 *   javac --release 17 -encoding UTF-8 MelateAutoActualizador.java MelateAnalizadorAvanzado.java MelatePrepararSorteo.java
 *   java MelatePrepararSorteo
 * -----------------------------------------------------------------------
 */
public class MelatePrepararSorteo {

    public static void main(String[] args) {
        System.out.println("=== PASO 1: Actualizando historico ===");
        try {
            MelateAutoActualizador.ejecutar();
        } catch (Exception e) {
            System.out.println("No se pudo completar la actualizacion: " + e.getMessage());
            System.out.println("Se continua con el analisis usando el historico existente.");
        }

        System.out.println();
        System.out.println("=== PASO 2: Analizando historico y generando combinacion ===");
        MelateAnalizadorAvanzado.main(args);

        System.out.println();
        System.out.println("=== LISTO ===");
        System.out.println("Revisa el archivo estrategia_<numero_de_concurso>.txt generado en esta carpeta.");
    }
}
