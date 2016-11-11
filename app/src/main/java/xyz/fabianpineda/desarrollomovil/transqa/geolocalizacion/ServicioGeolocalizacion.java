package xyz.fabianpineda.desarrollomovil.transqa.geolocalizacion;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;

import java.io.Serializable;

import xyz.fabianpineda.desarrollomovil.transqa.R;
import xyz.fabianpineda.desarrollomovil.transqa.db.GeolocalizacionSQLite;
import xyz.fabianpineda.desarrollomovil.transqa.db.SQLite;
import xyz.fabianpineda.desarrollomovil.transqa.db.SesionSQLite;

public final class ServicioGeolocalizacion extends Service implements LocationListener {
    public static final String SERVICIO_ACCION_DESCONOCIDA = "SERVICIO_GEOLOCALIZACION_ACCION_DESCONOCIDA";
    public static final String SERVICIO_ACCION_NINGUNA = "SERVICIO_GEOLOCALIZACION_ACCION_NINGUNA";
    public static final String SERVICIO_ACCION_SERVICIO_REINICIADO = "SERVICIO_GEOLOCALIZACION_ACCION_SERVICIO_REINICIADO";
    public static final String SERVICIO_ACCION_TERMINAR_SERVICIO = "SERVICIO_GEOLOCALIZACION_ACCION_TERMINAR_SERVICIO";
    public static final String SERVICIO_ACCION_INICIAR_SERVICIO = "SERVICIO_GEOLOCALIZACION_ACCION_INICIAR_SERVICIO";
    public static final String SERVICIO_ACCION_TERMINAR_SESION = "SERVICIO_GEOLOCALIZACION_ACCION_TERMINAR_SESION";
    public static final String SERVICIO_ACCION_INICIAR_SESION = "SERVICIO_GEOLOCALIZACION_ACCION_INICIAR_SESION";
    public static final String SERVICIO_ACCION_GPS_TERMINADO = "SERVICIO_GEOLOCALIZACION_ACCION_GPS_TERMINADO";
    public static final String SERVICIO_ACCION_GPS_INICIADO = "SERVICIO_GEOLOCALIZACION_ACCION_GPS_INICIADO";

    public static final int SERVICIO_RESPUESTA_ERROR = -1;                  // ^ DATOS: String
    public static final int SERVICIO_RESPUESTA_ACCION_DESCONOCIDA = 0;      // ^ DATOS: null
    public static final int SERVICIO_RESPUESTA_VACIA = 1;                   // ^ DATOS: null
    public static final int SERVICIO_RESPUESTA_NO_CAMBIOS = 2;              // ^ DATOS: null
    public static final int SERVICIO_RESPUESTA_OK = 3;                      // ^ DATOS: null

    public static final String SERVICIO_RESPUESTA_PROPIEDAD_TIPO = "SERVICIO_GEOLOCALIZACION_RESPUESTA_TIPO";
    public static final String SERVICIO_RESPUESTA_PROPIEDAD_DATOS = "SERVICIO_GEOLOCALIZACION_RESPUESTA_DATOS";

    private static boolean iniciado = false;

    private static final String SERVICIO_PREFERENCIA_OPERANDO = "SERVICIO_GEOLOCALIZACION_OPERACION";
    private static boolean operando = false;

    private static final String SERVICIO_PREFERENCIA_ID_SESION = "SERVICIO_GEOLOCALIZACION_ID_SESION";
    private static long sesion = -1;

    private static final String SERVICIO_PREFERENCIA_ACCION = "SERVICIO_GEOLOCALIZACION_ACCION";
    private static String accion = SERVICIO_ACCION_DESCONOCIDA;

    private static boolean permisosGPSSuficientes;
    private static boolean proveedorGPSActivado;

    private static final int SERVICIO_MODO_INICIO = START_STICKY;
    private static final int SERVICIO_INTERVALO_TIEMPO_GEOLOCALIZACION_MINIMO = 5000; // Intervalo de tiempo mínimo entre capturas, en milisegundos.
    private static final float SERVICIO_DISTANCIA_GEOLOCALIZACION_MINIMO = 0.0f; // Distancia mínima entre capturas, en metros.

    private LocalBroadcastManager transmisor;

    private SharedPreferences preferencias;
    private LocationManager geolocalizador;
    private SQLiteDatabase db;

    public static boolean operando() {
        return operando;
    }

    /*
     * public static boolean iniciado() {
     *   return iniciado;
     * }
     */

    /*
     * public static long getSesion() {
     *   return sesion;
     * }
     */

    /*
     * public static String  getAccion() {
     *   return accion;
     * }
     */

    private void responder(String accion, int tipoRespuesta, Serializable datos) {
        Intent respuesta = new Intent(accion);

        respuesta.putExtra(SERVICIO_RESPUESTA_PROPIEDAD_TIPO, tipoRespuesta);

        if (datos != null) {
            respuesta.putExtra(SERVICIO_RESPUESTA_PROPIEDAD_DATOS, datos);
        }

        transmisor.sendBroadcast(respuesta);
    }

    private void responder(String accion, int tipoRespuesta) {
        responder(accion, tipoRespuesta, null);
    }

    private boolean iniciarSesion() {
        boolean resultado = false;

        if (sesion > -1) {
            return resultado; // No se puede crear una nueva sesión si ya existe una.
        }

        resultado = (sesion = SesionSQLite.iniciarSesion(db)) > -1;
        preferencias.edit().putLong(SERVICIO_PREFERENCIA_ID_SESION, sesion).commit();

        return resultado;
    }

    private boolean terminarSesion() {
        boolean resultado = SesionSQLite.terminarSesion(db, sesion);
        preferencias.edit().putLong(SERVICIO_PREFERENCIA_ID_SESION, sesion = -1).commit();

        return resultado;
    }

    private boolean iniciarGPS() {
        permisosGPSSuficientes = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        proveedorGPSActivado = geolocalizador.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!permisosGPSSuficientes) {
            return false;
        }

        if (!proveedorGPSActivado) {
            return false;
        }

        geolocalizador.requestLocationUpdates(LocationManager.GPS_PROVIDER, SERVICIO_INTERVALO_TIEMPO_GEOLOCALIZACION_MINIMO, SERVICIO_DISTANCIA_GEOLOCALIZACION_MINIMO, this);
        return true;
    }

    private void terminarGPS() {
        permisosGPSSuficientes = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        proveedorGPSActivado = geolocalizador.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!permisosGPSSuficientes) {
            return;
        }

        geolocalizador.removeUpdates(this);
    }

    private void accionIniciarSesion() {
        if (!iniciarGPS()) {
            preferencias.edit()
                .putBoolean(SERVICIO_PREFERENCIA_OPERANDO, operando = false)
                .putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_NINGUNA)
            .commit();

            if (!proveedorGPSActivado) {
                responder(SERVICIO_ACCION_GPS_INICIADO, SERVICIO_RESPUESTA_ERROR, getString(R.string.error_geolocalizacion_no_gps));
            }

            if (!permisosGPSSuficientes) {
                responder(SERVICIO_ACCION_GPS_INICIADO, SERVICIO_RESPUESTA_ERROR, getString(R.string.error_geolocalizacion_no_permisos));
            }

            responder(SERVICIO_ACCION_INICIAR_SESION, SERVICIO_RESPUESTA_ERROR, getString(R.string.error_geolocalizacion_no_gps));
            return;
        }

        responder(SERVICIO_ACCION_GPS_INICIADO, SERVICIO_RESPUESTA_VACIA, null);

        if (!iniciarSesion()) {
            terminarGPS();
            responder(SERVICIO_ACCION_GPS_TERMINADO, SERVICIO_RESPUESTA_VACIA, null);

            responder(SERVICIO_ACCION_INICIAR_SESION, SERVICIO_RESPUESTA_ERROR, String.format(
                getString(R.string.error_creando_sesion_plantilla),
                sesion
            ));

            preferencias.edit()
                .putBoolean(SERVICIO_PREFERENCIA_OPERANDO, operando = false)
                .putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_NINGUNA)
            .commit();

            return;
        }

        preferencias.edit()
            .putBoolean(SERVICIO_PREFERENCIA_OPERANDO, operando = true)
            .putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_NINGUNA)
        .commit();

        responder(SERVICIO_ACCION_INICIAR_SESION, SERVICIO_RESPUESTA_OK, null);
    }

    private void accionTerminarSesion() {
        long sesionActual = sesion;

        terminarGPS();
        responder(SERVICIO_ACCION_GPS_TERMINADO, SERVICIO_RESPUESTA_VACIA, null);

        if (!terminarSesion()) {
            responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_ERROR, String.format(
                    getString(R.string.error_terminando_sesion_plantilla),
                    sesionActual
            ));
        } else {
            responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_OK, null);
        }

        preferencias.edit()
            .putBoolean(SERVICIO_PREFERENCIA_OPERANDO, operando = false)
            .putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_NINGUNA)
        .commit();
    }

    private void accionIniciarServicio() {
        // Prácticamente no hace nada, ya que cualquier startService causa que el servicio inicie si no está iniciado.
        // A pesar de esto, es la manera recomendada de iniciar el servicio porque causa una respuesta a clientes.
        preferencias.edit().putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_NINGUNA).commit();

        if (iniciado) {
            responder(SERVICIO_ACCION_INICIAR_SERVICIO, SERVICIO_RESPUESTA_NO_CAMBIOS, null);
        } else {
            iniciado = true;
            responder(SERVICIO_ACCION_INICIAR_SERVICIO, SERVICIO_RESPUESTA_OK, null);
        }
    }

    private void accionTerminarServicio() {
        preferencias.edit().putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_TERMINAR_SERVICIO).commit();
        stopSelf(); // onDestroy se encarga del resto. Cierra sesión si no está cerrada. Termina GPS.
    }

    private void reanudarOperacion() {
        long sesionActual = sesion;

        if (!operando) {
            responder(SERVICIO_ACCION_SERVICIO_REINICIADO, SERVICIO_RESPUESTA_VACIA, null);
        } else if (sesion <= -1) {
            // Estado inconsistente! Se está operando sobre una sesión inválida. Terminando servicio.
            responder(SERVICIO_ACCION_SERVICIO_REINICIADO, SERVICIO_RESPUESTA_ERROR, getString(R.string.error_geolocalizacion_interno));
            accionTerminarServicio();
        } else {
            if (iniciarGPS()) {
                responder(SERVICIO_ACCION_SERVICIO_REINICIADO, SERVICIO_RESPUESTA_VACIA, null);
            } else {
                responder(SERVICIO_ACCION_SERVICIO_REINICIADO, SERVICIO_RESPUESTA_ERROR, getString(R.string.error_geolocalizacion_no_gps));

                preferencias.edit().putBoolean(SERVICIO_PREFERENCIA_OPERANDO, operando = false).commit();
                responder(SERVICIO_ACCION_GPS_INICIADO, SERVICIO_RESPUESTA_ERROR, getString(R.string.error_geolocalizacion_no_gps));

                if (!terminarSesion()) {
                    responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_ERROR, String.format(
                            getString(R.string.error_terminando_sesion_plantilla),
                            sesionActual
                    ));
                } else {
                    responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_OK, null);
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /*
         * IDEA: se puede agregar un "parámetro" a los intents con un ID que identifique la Activity
         * que inició la solicitud. Esto permitiría hacer dos tipos de respuestas: respuestas que
         * están dirigidas a todas las activities con un BroadcastReceiver propiamente configurado
         * para recibir broadcasts de este servicio, y para la única Activity que inicie la
         * solicitud. Esto podría estar asistido por IntentFilters si llega a ser necesario.
         */
        String accionIntent;

        if (intent == null) {
            // https://developer.android.com/reference/android/app/Service.html#START_STICKY
            reanudarOperacion();
        } else {
            if ((accionIntent = intent.getAction()) == null) {
                accionIntent = SERVICIO_ACCION_DESCONOCIDA;
            }

            switch (accionIntent) {
                case SERVICIO_ACCION_INICIAR_SESION:
                    accionIniciarSesion();
                    break;

                case SERVICIO_ACCION_TERMINAR_SESION:
                    accionTerminarSesion();
                    break;

                case SERVICIO_ACCION_INICIAR_SERVICIO:
                    accionIniciarServicio();
                    break;

                case SERVICIO_ACCION_TERMINAR_SERVICIO:
                    accionTerminarServicio();
                    break;

                case SERVICIO_ACCION_DESCONOCIDA:
                    // Accion no debe ser solicitada explícitamente. Se ignora y se trata como ninguna.

                case SERVICIO_ACCION_SERVICIO_REINICIADO:
                    // Accion no debe ser solicitada explícitamente. Se ignora y se trata como ninguna.

                case SERVICIO_ACCION_GPS_INICIADO:
                    // Accion no debe ser solicitada explícitamente. Se ignora y se trata como ninguna.

                case SERVICIO_ACCION_GPS_TERMINADO:
                    // Accion no debe ser solicitada explícitamente. Se ignora y se trata como ninguna.

                    preferencias.edit().putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_NINGUNA).commit();
                    break;

                case SERVICIO_ACCION_NINGUNA:
                    // Nada que hacer.
                    break;
                default:
                    // Aquí se podría manejar este escenario como un error. Por ahora no hace nada.
                    break;
            }
        }

        iniciado = true;
        return SERVICIO_MODO_INICIO;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Servicio usado sin bind. No es necesario un IBinder.
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Servicio no trabaja con bind; no se permite rebind.
        return false;
    }

    @Override
    public void onRebind(Intent intent) {
        // No es necesario mientras onUbind siempre regrese false.
    }

    @Override
    public void onDestroy() {
        long sesionActual = sesion;

        if (operando) {
            terminarGPS();
            responder(SERVICIO_ACCION_GPS_TERMINADO, SERVICIO_RESPUESTA_VACIA);
        }

        if (accion.equals(SERVICIO_ACCION_TERMINAR_SERVICIO)) {
            responder(SERVICIO_ACCION_TERMINAR_SERVICIO, SERVICIO_RESPUESTA_OK);

            if (sesion > -1) {
                if (!terminarSesion()) {
                    responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_ERROR, String.format(
                        getString(R.string.error_terminando_sesion_plantilla),
                        sesionActual
                    ));
                } else {
                    responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_OK, null);
                }
            }

            preferencias.edit()
                .putBoolean(SERVICIO_PREFERENCIA_OPERANDO, operando = false)
                .putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_DESCONOCIDA)
            .commit();
        } else {
            responder(SERVICIO_ACCION_TERMINAR_SERVICIO, SERVICIO_RESPUESTA_VACIA);
        }

        if (db != null && db.isOpen()) {
            db.close();
        }

        iniciado = false;
    }

    @Override
    public void onCreate() {
        preferencias = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        operando = preferencias.getBoolean(SERVICIO_PREFERENCIA_OPERANDO, false);
        sesion = preferencias.getLong(SERVICIO_PREFERENCIA_ID_SESION, -1);
        accion = preferencias.getString(SERVICIO_PREFERENCIA_ACCION, SERVICIO_ACCION_DESCONOCIDA);

        transmisor = LocalBroadcastManager.getInstance(this);

        geolocalizador = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (geolocalizador == null) {
            throw new RuntimeException(getString(R.string.error_geolocalizacion_no_servicio));
        }

        permisosGPSSuficientes = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        proveedorGPSActivado = geolocalizador.isProviderEnabled(LocationManager.GPS_PROVIDER);

        SQLite auxiliarSQLite = null;
        SQLiteException e = null;
        db = null;

        try {
            auxiliarSQLite = new SQLite(this);
            db = auxiliarSQLite.getWritableDatabase();
        } catch (SQLiteException x) {
            e = x;
        }

        if (auxiliarSQLite == null || db == null || !db.isOpen() || db.isReadOnly()) {
            throw new SQLiteException(getString(R.string.error_sqlite_abrir_db), e);
        }

        iniciado = false;
    }

    @Override
    public void onLocationChanged(Location location) {
        android.util.Log.d(ServicioGeolocalizacion.class.getCanonicalName(), location.toString());

        if (operando) {
            GeolocalizacionSQLite.agregarCoordenadas(db, sesion, location.getLatitude(), location.getLongitude());
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (provider == LocationManager.GPS_PROVIDER) {
            if (status == LocationProvider.OUT_OF_SERVICE) {
                proveedorGPSActivado = false;
            } else if (status == LocationProvider.AVAILABLE) {
                proveedorGPSActivado = true;
            } else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                /*
                 * TODO! Decidir si se debe manejar el caso TEMORARILY_UNAVAILABLE.
                 * https://developer.android.com/reference/android/location/LocationListener.html#onStatusChanged(java.lang.String,%20int,%20android.os.Bundle)
                 *
                 * Por el momento se tolera (y se muestra mensaje DEBUG en Logcat si no está disponible.
                 * Después de to'do, esto causará que no se registren coordenadas (temporalmente).
                 *
                 * IMPORTANTE: Aunque no se están emitiendo avisos de TEMPORARILY_UNAVAILABLE,
                 * no se está reportando la ubicacion en intervales regulares 100% de las veces.
                 * Investigar.
                 */
                android.util.Log.d(xyz.fabianpineda.desarrollomovil.transqa.MainActivity.class.getCanonicalName(), "Estado de LocationProvider GPS_PROVIDER cambiado a TEMPORARILY_UNAVAILABLE.");
            }
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (provider == LocationManager.GPS_PROVIDER) {
            proveedorGPSActivado = true;
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (provider == LocationManager.GPS_PROVIDER) {
            proveedorGPSActivado = false;
        }
    }
}