package xyz.fabianpineda.desarrollomovil.transqa.db;

/**
 * Define la tabla/entidad Sesion en una base de datos relacional cualquiera.
 *
 * Cada entrada tiene una fecha de inicio y una fecha final; si no se encuentra una fecha final, se
 * asume qu la sesión aún no ha terminado.
 *
 * Adicionalmente, una Sesion contiene 0 o más capturas de coordenadas "Geolocalizacion"; cada
 * entrada en la tabla Geolocalización está asociada a una Sesion por la ID de sesión.
 *
 * No se debe incluir información en este objeto que esté dirigida a una DB/(R)DBMS específica.
 */
public final class Sesion {
    // Nombre de tabla.
    public static final String TABLA_SESION = "Sesion";

    // Nombres de campos/columnas.
    public static final String TABLA_SESION_ID = "id";
    public static final String TABLA_SESION_NOMBRE = "nombre";
    public static final String TABLA_SESION_FECHA_INICIO = "fecha_inicio";
    public static final String TABLA_SESION_FECHA_FIN = "fecha_fin";

    /*
     * Índices de cada campo. Por favor actualizar si se altera el orden de los campos o si se
     * agregan o eliminan campos.
     */
    public static final int TABLA_SESION_ID_INDICE = 0;
    public static final int TABLA_SESION_NOMBRE_INDICE = 1;
    public static final int TABLA_SESION_FECHA_INICIO_INDICE = 2;
    public static final int TABLA_SESION_FECHA_FIN_INDICE = 3;
}
