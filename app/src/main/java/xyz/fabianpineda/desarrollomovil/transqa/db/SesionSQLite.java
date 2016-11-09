package xyz.fabianpineda.desarrollomovil.transqa.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import xyz.fabianpineda.desarrollomovil.transqa.tiempo.Tiempo;

public final class SesionSQLite {
    static final String SQL_CREAR_TABLA_SESION = String.format(
        "CREATE TABLE %s (" +
            "%s INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT UNIQUE," +
            "%s DATETIME DEFAULT (datetime('now','localtime'))," +
            "%s DATETIME" +
        ");",

        Sesion.TABLA_SESION,

        Sesion.TABLA_SESION_ID,
        Sesion.TABLA_SESION_FECHA_INICIO,
        Sesion.TABLA_SESION_FECHA_FIN
    );

    static final String SQL_DESTRUIR_TABLA_SESION = String.format(
        "DROP TABLE IF EXISTS %s;",
        Sesion.TABLA_SESION
    );

    public static final long iniciarSesion(SQLiteDatabase db) {
        long resultado = -1;

        db.beginTransaction();
        try {
            // https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#insert(java.lang.String,%20java.lang.String,%20android.content.ContentValues)
            resultado = db.insert(Sesion.TABLA_SESION, Sesion.TABLA_SESION_FECHA_FIN, null);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        return resultado;
    }

    public static final Cursor seleccionarSesion(SQLiteDatabase db, long id) {
        String consulta = String.format(
            "SELECT * FROM %s WHERE %s = %s LIMIT 1",
            Sesion.TABLA_SESION,
            Sesion.TABLA_SESION_ID,
            String.valueOf(id)
        );

        return db.rawQuery(consulta, null);
    }

    public static final boolean terminarSesion(SQLiteDatabase db, long id) {
        String update = String.format(
            "UPDATE %s SET %s = (datetime('now','localtime')) WHERE %s = %s",
            Sesion.TABLA_SESION,
            Sesion.TABLA_SESION_FECHA_FIN,
            Sesion.TABLA_SESION_ID,
            String.valueOf(id)
        );

        Cursor sesion = seleccionarSesion(db, id);
        boolean resultado = false;

        int id_sesion = -1;
        //String fecha_inicio = null;
        String fecha_fin = null;

        if (sesion == null) {
            return resultado;
        } else if (!sesion.moveToFirst()) {
            sesion.close();
            return resultado;
        }

        fecha_fin = sesion.getString(2);

        if (fecha_fin == null || fecha_fin.equals("") || fecha_fin.equals("NULL")) {
            db.beginTransaction();

            try {
                db.execSQL(update);
                db.setTransactionSuccessful();
                resultado = true;
            } finally {
                db.endTransaction();
            }
        } else {
            resultado = false;
        }

        sesion.close();
        return resultado;
    }

    private SesionSQLite() { throw new RuntimeException(); }
}
