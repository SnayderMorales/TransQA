package xyz.fabianpineda.desarrollomovil.transqa.db;

final class Geolocalizacion {
    static final String TABLA_GEOLOCALIZACION = "Geolocalizacion";

    static final String TABLA_GEOLOCALIZACION_ID_SESION = "id_sesion";
    static final String TABLA_GEOLOCALIZACION_LATITUD = "latitud";
    static final String TABLA_GEOLOCALIZACION_LONGITUD = "longitud";
    static final String TABLA_GEOLOCALIZACION_FECHA = "fecha";

    private Geolocalizacion() { throw new RuntimeException(); }
}
