package xyz.fabianpineda.desarrollomovil.transqa.db;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

final public class GeolocalizacionSQLite {
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

    static final String SQL_DESTRUIR_TABLA_GEOLOCALIZACION = String.format(
        "DROP TABLE IF EXISTS %s;",
        Geolocalizacion.TABLA_GEOLOCALIZACION
    );

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
