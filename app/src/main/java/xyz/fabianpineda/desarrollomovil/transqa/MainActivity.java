package xyz.fabianpineda.desarrollomovil.transqa;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import xyz.fabianpineda.desarrollomovil.transqa.db.GeolocalizacionSQLite;
import xyz.fabianpineda.desarrollomovil.transqa.db.SQLite;
import xyz.fabianpineda.desarrollomovil.transqa.db.SesionSQLite;

//@RuntimePermissions
public class MainActivity extends AppCompatActivity {
    private void prueba() {
        // TODO: método temporal de prueba; remover.
        /*
         * Crea una nueva sesión con una fecha de inicio tomada al crear la sesión.
         * Agrega algunos puntos (lat y long) en la tabla de puntos asociandolos a sesión abierta.
         * Cierra la sesión, marcando fecha de cierre como la fecha actual.
         */
        SQLite sqlite = new SQLite(this);
        SQLiteDatabase db = null;

        try {
            sqlite = new SQLite(this);
            db = sqlite.getWritableDatabase();
            android.util.Log.d(MainActivity.class.getCanonicalName(), "DB abierta.");
        } catch (SQLiteException e) {}

        if (sqlite == null || db == null || !db.isOpen() || db.isReadOnly()) {
            android.util.Log.d(MainActivity.class.getCanonicalName(), "Error abriendo DB.");
            return;
        }

        long sesion = SesionSQLite.iniciarSesion(db);
        android.util.Log.d(MainActivity.class.getCanonicalName(), "Sesión: " + String.valueOf(sesion));

        GeolocalizacionSQLite.agregarCoordenadas(db, sesion, 1.0, 1.1);
        GeolocalizacionSQLite.agregarCoordenadas(db, sesion, 1.2, 1.3);
        GeolocalizacionSQLite.agregarCoordenadas(db, sesion, 1.4, 1.5);
        GeolocalizacionSQLite.agregarCoordenadas(db, sesion, 1.6, 1.7);
        GeolocalizacionSQLite.agregarCoordenadas(db, sesion, 1.8, 1.9);
        GeolocalizacionSQLite.agregarCoordenadas(db, sesion, 2.0, 1536.0);
        android.util.Log.d(MainActivity.class.getCanonicalName(), "Agregadas 6 coordenadas a sesión.");

        if (SesionSQLite.terminarSesion(db, sesion)) {
            android.util.Log.d(MainActivity.class.getCanonicalName(), "Sesión cerrada.");
        } else {
            android.util.Log.d(MainActivity.class.getCanonicalName(), "Sesión no cerrada.");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // TODO: depuración; remover.
        prueba();
    }
}
