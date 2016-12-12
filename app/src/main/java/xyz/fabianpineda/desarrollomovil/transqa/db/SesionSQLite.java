package xyz.fabianpineda.desarrollomovil.transqa.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Información de tabla Sesion en SQLite.
 *
 * Usado para crear y operar con la tabla "Sesion" en SQLite3 en la base de datos "DB".
 *
 * TODO: mover statements SQL dentro de métodos y hacerlos constantes, propiedades de clase.
 */
public final class SesionSQLite {
    /**
     * Definición de estructura de tabla Sesion. Esquema.
     */
    static final String SQL_CREAR_TABLA_SESION = String.format(
        "CREATE TABLE %s (" +
            "%s INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE," +
            "%s VARCHAR(255) NOT NULL DEFAULT ''," +
            "%s DATETIME NOT NULL DEFAULT (datetime('now', 'localtime'))," +
            "%s DATETIME" +
        ");",

        Sesion.TABLA_SESION,

        Sesion.TABLA_SESION_ID,
        Sesion.TABLA_SESION_NOMBRE,
        Sesion.TABLA_SESION_FECHA_INICIO,
        Sesion.TABLA_SESION_FECHA_FIN
    );

    /**
     * SQL para destruir la tabla Sesion.
     */
    static final String SQL_DESTRUIR_TABLA_SESION = String.format(
        "DROP TABLE IF EXISTS %s;",
        Sesion.TABLA_SESION
    );

    /**
     * Inicia una nueva Sesion; crea un nuevo registro Sesion y regresa sus datos.
     *
     * @param db Conexion abierta a una SQLiteDatabase con la base de datos "DB", con permisos de lectura y escritura
     * @param nombre Nombre o comentario de sesión. Puede ser null. Null será tratado como una String vacía.
     *
     * @return Un objeto Cursor con los resultados (información) de la nueva Sesion creada, o null si no se pudo crear. Debe ser cerrado posteriormente llamado su método close()
     */
    public static final Cursor iniciarSesion(SQLiteDatabase db, String nombre) {
        Cursor temp;
        long id = 0;

        String n = "";

        if (nombre != null) {
            n = nombre.trim();
        }

        ContentValues valoresNuevaSesion = new ContentValues();
        valoresNuevaSesion.put(Sesion.TABLA_SESION_NOMBRE, n);

        id = db.insert(Sesion.TABLA_SESION, null, valoresNuevaSesion);

        // Regresa el Cursor si y solo si contiene un resultado después de inserción.
        if (id > 0 && (temp = seleccionarSesion(db, id)) != null && temp.moveToFirst()) {
            return temp;
        }

        // Registro no existe. Regresando null.
        return null;
    }

    /**
     * Selecciona una Sesion por ID y regresa sus datos.
     *
     * @param db Conexion abierta a una SQLiteDatabase con la base de datos "DB", con permisos de lectura y escritura
     * @param id ID de la sesión siendo buscada.
     *
     * @return Un Cursor con los resultados de la consulta; con el registro cuya ID de sesión es "id". Regresa null si no existe la Sesion. El Cursor debe ser cerrado posteriormente usando su método close()
     */
    public static final Cursor seleccionarSesion(SQLiteDatabase db, long id) {
        Cursor temp;
        String consulta = String.format(
            "SELECT * FROM %s WHERE %s = %s LIMIT 1",
            Sesion.TABLA_SESION,
            Sesion.TABLA_SESION_ID,
            String.valueOf(id)
        );

        // Sólo se regresa el Cursor si existe un registro Sesion con ID "id"
        if ((temp = db.rawQuery(consulta, null)) != null && temp.moveToFirst()) {
            return temp;
        }

        // Registro no existe. Regresando null.
        return null;
    }

    /**
     * Termina una sesión abierta, agregando una fecha de terminación.
     *
     * @param db Conexion abierta a una SQLiteDatabase con la base de datos "DB", con permisos de lectura y escritura
     * @param id ID de la sesión abierta que se desea cerrar.
     *
     * @return Un Cursor con la información de la sesión cerrada. Regresa null si la sesión no existe o si ya estaba terminada. En este último caso, la fecha de terminación de sesión quedará intacta.
     */
    public static final Cursor terminarSesion(SQLiteDatabase db, long id) {
        Cursor sesion;

        String update = String.format(
            "UPDATE %s SET %s = (datetime('now','localtime')) WHERE %s = %d",
            Sesion.TABLA_SESION,
            Sesion.TABLA_SESION_FECHA_FIN,
            Sesion.TABLA_SESION_ID,
            id
        );

        // Es un error si la sesión no existe o si "id" es un ID inválido.
        if (id < 1 || (sesion = seleccionarSesion(db, id)) == null || !sesion.moveToFirst()) {
            return null;
        }

        // Si la sesión existe y no tiene fecha de terminación, entonces la sesión está abierta y puede ser terminada.
        if (sesion.getString(Sesion.TABLA_SESION_FECHA_FIN_INDICE) == null) {
            sesion.close();

            db.execSQL(update);

            // Esto nunca debería ocurrir. Pero si ocurre (fila actualizada deja de existir por alguna razón) se regresa null. Es un error.
            if ((sesion = seleccionarSesion(db, id)) == null || !sesion.moveToFirst() || sesion.getString(Sesion.TABLA_SESION_FECHA_FIN_INDICE) == null) {
                return null;
            }

            // La sesión existe, estaba abierta y fue cerrada exitosamente. Se regresa su Cursor.
            return sesion;
        }

        // La sesión existe pero ya estaba cerrada. Quedará intacta y se regresará null. Es un error.
        return null;
    }
}
