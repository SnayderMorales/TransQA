package xyz.fabianpineda.desarrollomovil.transqa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.Serializable;

import xyz.fabianpineda.desarrollomovil.transqa.geolocalizacion.ServicioGeolocalizacion;

//@RuntimePermissions
public class MainActivity extends AppCompatActivity {
    private static IntentFilter filtroAccionesServicio;
    static {
        filtroAccionesServicio = new IntentFilter();

        filtroAccionesServicio.addAction(ServicioGeolocalizacion.SERVICIO_ACCION_DESCONOCIDA);
        filtroAccionesServicio.addAction(ServicioGeolocalizacion.SERVICIO_ACCION_NINGUNA);
        filtroAccionesServicio.addAction(ServicioGeolocalizacion.SERVICIO_ACCION_SERVICIO_REINICIADO);
        filtroAccionesServicio.addAction(ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SERVICIO);
        filtroAccionesServicio.addAction(ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SERVICIO);
        filtroAccionesServicio.addAction(ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SESION);
        filtroAccionesServicio.addAction(ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SESION);
        filtroAccionesServicio.addAction(ServicioGeolocalizacion.SERVICIO_ACCION_GPS_INICIADO);
        filtroAccionesServicio.addAction(ServicioGeolocalizacion.SERVICIO_ACCION_GPS_TERMINADO);
    }

    private static final int BOTON_ACCION_ERROR = -1;
    private static final int BOTON_ACCION_DESCONOCIDA = 0;
    private static final int BOTON_ACCION_SESION_INICIAR = 1;
    private static final int BOTON_ACCION_SESION_TERMINAR = 2;

    private static final int TEXTO_BOTON_ACCION_SESION_INICIAR = R.string.toggle_servicio_iniciar;
    private static final int TEXTO_BOTON_ACCION_SESION_TERMINAR = R.string.toggle_servicio_terminar;
    private static final int TEXTO_BOTON_ACCION_SESION_REINICIAR = R.string.toggle_servicio_reiniciar;

    private LocalBroadcastManager receptor;

    private TextView mensajesServicio;
    private Button botonToggleServicio;
    private int accion;

    private void notificarUsuario(String mensaje) {
        if (mensaje == null) {
            return;
        }

        mensajesServicio.append(mensaje + "\n");
    }

    private void notificarUsuario(int recursoMensaje) {
        notificarUsuario(getString(recursoMensaje));
    }

    private void botonToggleServicioPresionado() {
        botonToggleServicio.setEnabled(false);

        switch(accion) {
            case BOTON_ACCION_SESION_INICIAR:
                notificarUsuario(R.string.anuncio_sesion_iniciando);
                startService(new Intent(ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SESION, null, this, ServicioGeolocalizacion.class));
                break;
            case BOTON_ACCION_SESION_TERMINAR:
                notificarUsuario(R.string.anuncio_sesion_terminando);
                startService(new Intent(ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SESION, null, this, ServicioGeolocalizacion.class));
                break;
            case BOTON_ACCION_DESCONOCIDA:
            case BOTON_ACCION_ERROR:
            default:
                // No debería ocurrir, pero se podría manejar errores aquí.
                android.util.Log.d(MainActivity.class.getCanonicalName(), "Botón toggle servicio recibió opción inválida '" + accion + "'.");
                break;
        }
    }

    private void actualizarBotonServicio(boolean activado, int accion, int recursoTexto) {
        this.accion = accion;
        botonToggleServicio.setText(recursoTexto);
        botonToggleServicio.setEnabled(activado);
    }

    private void accionDesconocida(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_DESCONOCIDA + ": respuesta tipo " + tipoRespuesta + ".");
    }

    private void accionNinguna(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_NINGUNA + ": respuesta tipo " + tipoRespuesta + ".");
    }

    private void accionServicioReiniciado(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_SERVICIO_REINICIADO + ": respuesta tipo " + tipoRespuesta + ".");
    }

    private void accionIniciarServicio(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SERVICIO + ": respuesta tipo " + tipoRespuesta + ".");

        if (tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_OK || tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_NO_CAMBIOS) {
            // Servicio iniciado explícitamente y exitosamente.
            if (ServicioGeolocalizacion.operando()) {
                actualizarBotonServicio(true, BOTON_ACCION_SESION_TERMINAR, TEXTO_BOTON_ACCION_SESION_TERMINAR);
            } else {
                actualizarBotonServicio(true, BOTON_ACCION_SESION_INICIAR, TEXTO_BOTON_ACCION_SESION_INICIAR);
            }
        }
    }

    private void accionTerminarServicio(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SERVICIO + ": respuesta tipo " + tipoRespuesta + ".");

        if (tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_OK) {
            // Servicio terminado explícitamente.
            // Esto no significa que se haya terminado correctamente la sesión o el GPS.
            // Se intenta iniciar el servicio nuevamente, sin sesión.
            botonToggleServicio.setEnabled(false);
            startService(new Intent(ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SERVICIO, null, this, ServicioGeolocalizacion.class));
        } else if (tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_VACIA) {
            // El servidor está siendo reiniciado por Android.
            // El servicio continuará operando automáticamente sin interrumpir la sesión actual (si existe)
        }
    }

    private void accionIniciarSesion(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SESION + ": respuesta tipo " + tipoRespuesta + ".");

        if (tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_OK) {
            notificarUsuario(R.string.anuncio_sesion_iniciada);
            actualizarBotonServicio(true, BOTON_ACCION_SESION_TERMINAR, TEXTO_BOTON_ACCION_SESION_TERMINAR);
        } else {
            if (datos == null) {
                notificarUsuario(R.string.error_creando_sesion);
            } else {
                notificarUsuario(datos.toString());
            }

            if (ServicioGeolocalizacion.operando()) {
                actualizarBotonServicio(true, BOTON_ACCION_SESION_TERMINAR, TEXTO_BOTON_ACCION_SESION_TERMINAR);
            } else {
                botonToggleServicio.setEnabled(false);
                startService(new Intent(ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SERVICIO, null, this, ServicioGeolocalizacion.class));
            }
        }
    }

    private void accionTerminarSesion(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SESION+ ": respuesta tipo " + tipoRespuesta + ".");

        if (tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_OK) {
            notificarUsuario(R.string.anuncio_sesion_terminada);
            actualizarBotonServicio(true, BOTON_ACCION_SESION_INICIAR, TEXTO_BOTON_ACCION_SESION_INICIAR);
        } else {
            if (datos == null) {
                notificarUsuario(R.string.error_terminando_sesion);
            } else {
                notificarUsuario(datos.toString());
            }

            if (ServicioGeolocalizacion.operando()) {
                botonToggleServicio.setEnabled(false);
                startService(new Intent(ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SERVICIO, null, this, ServicioGeolocalizacion.class));
            } else {
                actualizarBotonServicio(true, BOTON_ACCION_SESION_INICIAR, TEXTO_BOTON_ACCION_SESION_INICIAR);
            }
        }
    }

    private void accionGPSIniciado(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_GPS_INICIADO + ": respuesta tipo " + tipoRespuesta + ".");
        notificarUsuario(R.string.anuncio_gps_iniciado);
    }

    private void accionGPSTerminado(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_GPS_TERMINADO + ": respuesta tipo " + tipoRespuesta + ".");
        notificarUsuario(R.string.anuncio_gps_terminado);
    }

    private final class ReceptorServicioGeolocalizacion extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                // TODO: error! manejar?
                return;
            }

            String accion = intent.getAction();
            int tipoRespuesta = intent.getIntExtra(ServicioGeolocalizacion.SERVICIO_RESPUESTA_PROPIEDAD_TIPO, ServicioGeolocalizacion.SERVICIO_RESPUESTA_VACIA);
            Serializable datos = intent.getSerializableExtra(ServicioGeolocalizacion.SERVICIO_RESPUESTA_PROPIEDAD_DATOS);

            switch (accion) {
                case ServicioGeolocalizacion.SERVICIO_ACCION_GPS_INICIADO:
                    accionGPSIniciado(tipoRespuesta, datos);
                    break;
                case ServicioGeolocalizacion.SERVICIO_ACCION_GPS_TERMINADO:
                    accionGPSTerminado(tipoRespuesta, datos);
                    break;
                case ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SESION:
                    accionIniciarSesion(tipoRespuesta, datos);
                    break;
                case ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SESION:
                    accionTerminarSesion(tipoRespuesta, datos);
                    break;
                case ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SERVICIO:
                    accionIniciarServicio(tipoRespuesta, datos);
                    break;
                case ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SERVICIO:
                    accionTerminarServicio(tipoRespuesta, datos);
                    break;
                case ServicioGeolocalizacion.SERVICIO_ACCION_SERVICIO_REINICIADO:
                    accionServicioReiniciado(tipoRespuesta, datos);
                    break;
                case ServicioGeolocalizacion.SERVICIO_ACCION_NINGUNA:
                    accionNinguna(tipoRespuesta, datos);
                    break;
                case ServicioGeolocalizacion.SERVICIO_ACCION_DESCONOCIDA:
                default:
                    accionDesconocida(tipoRespuesta, datos);
                    break;
            }
        }

        private ReceptorServicioGeolocalizacion() {};
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mensajesServicio = (TextView) findViewById(R.id.mensajesServicio);

        botonToggleServicio = (Button) findViewById(R.id.toggleServicio);
        botonToggleServicio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                botonToggleServicioPresionado();
            }
        });

        receptor = LocalBroadcastManager.getInstance(this);
        receptor.registerReceiver(new ReceptorServicioGeolocalizacion(), filtroAccionesServicio);

        startService(new Intent(ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SERVICIO, null, this, ServicioGeolocalizacion.class));
    }
}
