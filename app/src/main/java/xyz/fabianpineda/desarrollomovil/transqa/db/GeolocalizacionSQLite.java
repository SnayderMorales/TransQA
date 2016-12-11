package xyz.fabianpineda.desarrollomovil.transqa.db;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/**
 * Información de tabla Geolocalizacion en SQLite.
 *
 * Usado para crear y operar con la tabla "Geolocalizacion" en SQLite3 en la base de datos "DB".
 *
 * TODO: mover statements SQL dentro de métodos y hacerlos constantes, propiedades de clase.
 */
final public class GeolocalizacionSQLite {
    /**
     * Estructura de tabla Geolocalización. Esquema.
     */
    static final String SQL_CREAR_TABLA_GEOLOCALIZACION = String.format(
        "CREATE TABLE %s (" +
            "%s INTEGER NOT NULL," +
            "%s REAL NOT NULL," +
            "%s REAL NOT NULL," +
            "%s DATETIME DEFAULT (datetime('now','localtime'))," +

            "FOREIGN KEY (%s) REFERENCES Sesion(%s)" +
        ");",
        Geolocalizacion.TABLA_GEOLOCALIZACION,
        Geolocalizacion.TABLA_GEOLOCALIZACION_ID_SESION,
        Geolocalizacion.TABLA_GEOLOCALIZACION_LATITUD,
        Geolocalizacion.TABLA_GEOLOCALIZACION_LONGITUD,
        Geolocalizacion.TABLA_GEOLOCALIZACION_FECHA,
        Geolocalizacion.TABLA_GEOLOCALIZACION_ID_SESION, Sesion.TABLA_SESION_ID
    );

    /**
     * SQL de SQLite3 para eliminar tabla Geolocalizacion.
     */
    static final String SQL_DESTRUIR_TABLA_GEOLOCALIZACION = String.format(
        "DROP TABLE IF EXISTS %s;",
        Geolocalizacion.TABLA_GEOLOCALIZACION
    );

    /**
     * Crea un nuevo registro con latitud, longitud y fecha asociado a una Sesion por su ID.
     *
     * @param db Objeto SQLiteDatabase con una conexión abierta a la base de datos "DB", permisos de lectura y escritura
     *
     * @param id_sesion ID de la sesión a la que esta entrada pertenece.
     * @param latitud Coordenada. Latitud.
     * @param longitud Coordenada. Longitud.
     *
     * @return rowid (ID de fila) del registro insertado en tabla Geolocalizacion. -1 en error.
     */
    public static final long agregarCoordenadas(SQLiteDatabase db, long id_sesion, double latitud, double longitud) {
        ContentValues valores = new ContentValues();
        valores.put(Geolocalizacion.TABLA_GEOLOCALIZACION_ID_SESION, id_sesion);
        valores.put(Geolocalizacion.TABLA_GEOLOCALIZACION_LATITUD, latitud);
        valores.put(Geolocalizacion.TABLA_GEOLOCALIZACION_LONGITUD, longitud);

        long resultado = -1;

        db.beginTransaction();
        try {
            resultado = db.insert(Geolocalizacion.TABLA_GEOLOCALIZACION, null, valores);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        return resultado;
    };
}
