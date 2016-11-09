package xyz.fabianpineda.desarrollomovil.transqa.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class SQLite extends SQLiteOpenHelper {
        @Override
        public void onConfigure(SQLiteDatabase db){
            // https://www.sqlite.org/foreignkeys.html#fk_enable
            // https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#setForeignKeyConstraintsEnabled(boolean)
            // https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper.html#onConfigure(android.database.sqlite.SQLiteDatabase)

            super.onConfigure(db);
            db.setForeignKeyConstraintsEnabled(true);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL(SesionSQLite.SQL_DESTRUIR_TABLA_SESION);
            db.execSQL(GeolocalizacionSQLite.SQL_DESTRUIR_TABLA_GEOLOCALIZACION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SesionSQLite.SQL_CREAR_TABLA_SESION);
            db.execSQL(GeolocalizacionSQLite.SQL_CREAR_TABLA_GEOLOCALIZACION);
        }

        public SQLite(Context contexto) {
            super(contexto, DB.DB_NOMBRE + ".db", null, DB.DB_VERSION);
        }
}
