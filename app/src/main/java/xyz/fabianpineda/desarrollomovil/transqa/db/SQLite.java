package xyz.fabianpineda.desarrollomovil.transqa.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Ayuda a crear, y abrir y actualizar la base de datos "DB", usando SQLite3.
 */
public final class SQLite extends SQLiteOpenHelper {
    /**
     * SQLite almacena las bases de datos en memoria o a manera de archivos. Por el momento se
     * están usando archivos.
     *
     * Aunque el archivo no requiere una "extensión", su uso hace entender intuitivamente que el
     * archivo es una base de datos.
     *
     * El nombre de la base de datos en "DB" es concatenado con este sufijo ("extensión") y es
     * guardado en el directorio "databases" dentro de los archivos de la aplicación.
     */
    static final String SQLITE_SUFIJO_NOMBRE_ARCHIVO_DB = ".db";

    /**
     * Statements SQL ejecutados cada vez que una base de datos "DB" es creada o abierta.
     *
     * Se usan Foreign Keys, y esta funcionalidad debe ser activada manualmente en SQLite3 de
     * acuerdo a su documentación oficial.
     *
     * https://www.sqlite.org/foreignkeys.html#fk_enable
     * https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#setForeignKeyConstraintsEnabled(boolean)
     * https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper.html#onConfigure(android.database.sqlite.SQLiteDatabase)
     *
     * @param db La base de datos SQLite3. Manejado por Android.
     */
    @Override
    public void onConfigure(SQLiteDatabase db){
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    /**
     * Maneja la "migración" a nuevas versiones de la base de datos.
     *
     * TODO: actualmente elimina los contenidos antiguos de la base de datos y la vuelve a crear,
     * TODO: idealmente, podría simplemente preservar los datos y alterar estructura de tablas.
     *
     * @param db La base de datos SQLite3. Manejado por Android.
     * @param oldVersion Versión anterior de la base de datos.
     * @param newVersion Nueva versión de la base de datos.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SesionSQLite.SQL_DESTRUIR_TABLA_SESION);
        db.execSQL(GeolocalizacionSQLite.SQL_DESTRUIR_TABLA_GEOLOCALIZACION);
    }

    /**
     * Statements SQL ejecutados al crear una nueva base de datos SQLite3 "DB".
     *
     * Crea las tablas definidas por y para esta aplicación.
     *
     * @param db La base de datos SQLite3. Manejado por Android.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SesionSQLite.SQL_CREAR_TABLA_SESION);
        db.execSQL(GeolocalizacionSQLite.SQL_CREAR_TABLA_GEOLOCALIZACION);
    }

    /**
     * Crea una nueva base de datos usando un contexto y nombre especificado. No usa un
     * CursorFactory y/o DatabaseErrorHandler.
     *
     * El nombre de la base de datos es la concatenación del nombre de la base de datos especificado
     * en "DB" con el sufijo de nombre definido en esta clase.
     *
     * @param contexto
     */
    public SQLite(Context contexto) {
        super(contexto, DB.DB_NOMBRE + SQLITE_SUFIJO_NOMBRE_ARCHIVO_DB, null, DB.DB_VERSION);
    }
}
