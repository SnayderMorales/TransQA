package xyz.fabianpineda.desarrollomovil.transqa.db;

/**
 * Define la tabla/entidad Geolocalizacion en una base de datos relacional cualquiera.
 *
 * Cada entrada pertence a una Sesion y contiene un par de coordenadas (latitud y longitud) y fecha
 * de captura.
 *
 * No se debe incluir información en este objeto que esté dirigida a una DB/(R)DBMS específica.
 */
final class Geolocalizacion {
    static final String TABLA_GEOLOCALIZACION = "Geolocalizacion";

    static final String TABLA_GEOLOCALIZACION_ID_SESION = "id_sesion";
    static final String TABLA_GEOLOCALIZACION_LATITUD = "latitud";
    static final String TABLA_GEOLOCALIZACION_LONGITUD = "longitud";
    static final String TABLA_GEOLOCALIZACION_FECHA = "fecha";
}
