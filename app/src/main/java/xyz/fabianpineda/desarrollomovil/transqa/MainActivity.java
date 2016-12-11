package xyz.fabianpineda.desarrollomovil.transqa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import xyz.fabianpineda.desarrollomovil.transqa.geolocalizacion.ServicioGeolocalizacion;

/**
 * Define la interfáz de usuario de la aplicación. Recibe y muestra respuestas de
 * ServicioGeolocalizacion.
 *
 * Permite al usuario iniciar o terminar el servicio, y iniciar y terminar
 * sesiones de geolocalización.
 *
 * La clase simplemente enumera todas las posibles respuestas (acciones) de ServicioGeolocalizacion
 * que desea "manejar" y ejecuta una función distinta para cada tipo de acción.
 *
 * Si a futuro se desea manejar más acciones del servicio, entonces se deben modificar las
 * propiedades "filtroAccionesServicio" y ReceptorServicioGeolocalizacion.onReceive.
 */
//@RuntimePermissions
public class MainActivity extends AppCompatActivity {
    /**
     * Filtro de intents "mensaje" aceptados por esta Activity.
     *
     * Cada entrada en este filtro debe corresponder a una posible respuesta de ServicioGeolocalizacion
     * que se desea manejar en esta Activity. Eliminar una entrada en este objeto causará que la
     * Activity deje de responder a la acción especificada por la entrada.
     *
     * Por cada entrada agregada en este filtro, debe haber una código para manejar este tipo de
     * acción definida en ReceptorServicioGeolocalizacion.onReceive.
     */
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
        filtroAccionesServicio.addAction(ServicioGeolocalizacion.SERVICIO_ACCION_GPS_DESACTIVADO); // Terminar esto. Asegurarse de que funciona. Manejar permisos.
    }

    /** Formato para horas y/o fechas mostradas antes de cada mensaje en mensajesServicio */
    static final DateFormat formatoFechas = new SimpleDateFormat("HH:mm:ss");

    /** Formato para cada mensaje en mensajesServicio. Actualmente es una fecha seguida por un mensaje. */
    static final String formatoMensajesNotificacion = "%s - %s.\n";

    /*
     * Constantes que identifican las acciones que realiza el boton "toggle" de la Activity la
     * próxima vez que sea presionado. Ver: botonToggleServicioPresionado
     */
    private static final int BOTON_ACCION_ERROR = -1;           // Para marcar explícitamente un error.
    private static final int BOTON_ACCION_DESCONOCIDA = 0;      // "Valor por defecto." Es un error.
    private static final int BOTON_ACCION_SESION_INICIAR = 1;   // Inicia el servicio y/o una sesión.
    private static final int BOTON_ACCION_SESION_TERMINAR = 2;  // Termina el servicio y/o una sesión.

    /*
     * Constantes que definen los posibles "textos" del botón en todos sus posibles estados/acciones.
     *
     * No se recomienda implementar la acción TEXTO_BOTON_ACCION_SESION_REINICIAR, ya que actualmente
     * ServicioGeolocalizacion es capáz de reiniciarse a si mismo.
     */
    private static final int TEXTO_BOTON_ACCION_SESION_INICIAR = R.string.toggle_servicio_iniciar;
    private static final int TEXTO_BOTON_ACCION_SESION_TERMINAR = R.string.toggle_servicio_terminar;
    private static final int TEXTO_BOTON_ACCION_SESION_REINICIAR = R.string.toggle_servicio_reiniciar;

    /**
     * Valores semi-persistentes de la aplicación. Guarda y comparte información de estado con
     * ServicioGeolocalizacion.
     *
     * Es importante mencionar que este objeto sólo es leído desde MainActivity, mientras que
     * ServicioGeolocalizacion lee y _escribe_ en este objeto.
     */
    private SharedPreferences preferencias;

    /**
     * Recibe mensajes de acciones realizadas por ServicioGeolocalizacion.
     *
     * Por cada *tipo* de mensaje enviado desde ServicioGeolocalizacion a esta Activity, debe haber
     * una entrada correspondiente a la acción (mensaje) en filtroAccionesServicio y en
     * ReceptorServicioGeolocalizacion.onReceive
     */
    private LocalBroadcastManager receptor;

    // ID de la sesión actual o de la última sesión terminada. Es 0 si no se cumple lo anterior.
    private long sesionID;
    private String sesionNombre;            // Nombre de sesión.
    private String sesionFechaInicio;       // Fecha inicio de sesión, o de sesion anterior terminada
    private String sesionFechaFin;          // Fecha de fin de última sesión, o vacío si no hay.

    private TextView infoNombreSesion;      // Mostrará el nombre de la sesión actual seguido por ID
    private TextView infoEstadoSesion;      // Muestra si hay sesión en progreso o si ha termindo
    private TextView infoFechaInicioSesion; // Muestra fecha inicio sesión actual o anterior terminada
    private TextView infoFechaFinSesion;    // Muestra fecha fin de última sesión, si existe.
    private TextView mensajesServicio;      // Muestra información detallada al usuario, con fecha.

    private Button botonToggleServicio;     // Botón toggle "inteligente." Inicia/termina seesion/servicio

    /**
     * Su valor será la próxima acción que realizará el botón al ser presionado. Siempre debe tomar
     * el valor de alguna de las constantes BOTON_ACCION_*.
     */
    private int accion;

    /**
     * Obtiene una representación textual de la fecha actual.
     *
     * @return la fecha actual como String en el formato de la propiedad "formatoFechas"
     */
    static String fechaLocalAhora() {
        return formatoFechas.format(new Date());
    }

    /**
     * Imprime una nueva "linea" en el TextView "mensajesServicio" de la Activity.
     *
     * Todos los mensajes sigue el formato especificado en "formatoMensajesNotificacion" que
     * idealmente debe contener una fecha seguida por el mensaje.
     *
     * Este método acepta como entrada un String.
     * Ver: notificarUsuario(int).
     *
     * @param mensaje String del mensaje a mostrar al usuario.
     */
    private void notificarUsuario(String mensaje) {
        if (mensaje == null || mensaje.trim().compareTo("") == 0) {
            return;
        }

        mensajesServicio.append(String.format(formatoMensajesNotificacion, fechaLocalAhora(), mensaje));
    }

    /**
     * Imprime una nueva "linea" en el TextView "mensajesServicio" de la Activity.
     *
     * Todos los mensajes sigue el formato especificado en "formatoMensajesNotificacion" que
     * idealmente debe contener una fecha seguida por el mensaje.
     *
     * Este método acepta como entrada un int correspondiente a un recurso String en R.string.*
     * Ver: notificarUsuario(String)
     *
     * @param recursoMensaje Recurso String del mensaje a mostrar al usuario.
     */
    private void notificarUsuario(int recursoMensaje) {
        notificarUsuario(getString(recursoMensaje));
    }

    /**
     * Método llamado cada vez que el botón toggle es presionado mientras que no está desactivado.
     *
     * La función ejecutada por el botón debe ser igual al valor de "accion". Esta variable puede
     * tener como valor el valor de cualquier constante BOTON_ACCION_*. El método debe estar listo
     * para manejar cada "caso / accion soportada."
     */
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
                // No deberían ocurrir, pero se podría manejar como error aquí.
                android.util.Log.d(MainActivity.class.getCanonicalName(), "Botón toggle servicio recibió opción inválida '" + accion);
                break;
        }
    }

    /**
     * Método llamado en respuesta a varios mensaje emitidos por ServicioGeolocalizacion.
     *
     * Sus parámetros determinarán el texto y acción del botón toggle, dejándolo listo para tomar
     * una nueva acción a discreción del usuario.
     *
     * @param activado true si el botón permanecerá activado. false si permancerá desactivado.
     * @param accion acción a realizar la siguiente vez que sea presionado el botón. Debe ser el valor de alguna de las constantes BOTON_ACCION_*
     * @param recursoTexto ID de un recurso String en R.strings.* a usar como texto del botón.
     */
    private void actualizarBotonServicio(boolean activado, int accion, int recursoTexto) {
        this.accion = accion;
        botonToggleServicio.setText(recursoTexto);
        botonToggleServicio.setEnabled(activado);
    }

    /**
     * Posible respuesta a ReceptorServicioGeolocalizacion.onReceive.
     *
     * Ocurre si se recibe una acción que está en "filtroAccionesServicio", pero que no está siendo
     * manejada en ReceptorServicioGeolocalizacion.onReceive. Ocurre también si se especificó
     * explícitamente la acción SERVICIO_ACCION_DESCONOCIDA.
     *
     * Se recomienda ignorar este tipo de mensajes y se recomienda no hacer una solicitud de este
     * tipo de acción a ServicioGeolocalizacion explícitamente. No hará nada más que iniciar el
     * servicio (pero no GPS ni sesión) si no está iniciado. En t odo caso, se recomienda iniciar
     * el servicio con SERVICIO_ACCION_INICIAR_SERVICIO para evitar posibles inconsistencias en el
     * estado interno del servicio si se está iniciando por primera vez.
     *
     * El método existe para facilitar la depuración. Se pueden mostrar mensajes dentro del cuerpo
     * de este método.
     *
     * @param tipoRespuesta Código de respuesta. Fue exitosa la operación o no? Ver: ServicioGeolocalizacion.SERVICIO_RESPUESTA_*
     * @param datos Datos adicionales recibidos con la respuesta.
     */
    private void accionDesconocida(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_DESCONOCIDA + ": respuesta tipo " + tipoRespuesta);
    }

    /**
     * Posible respuesta a ReceptorServicioGeolocalizacion.onReceive.
     *
     * Ocurre si se recibe una respuesta de tipo SERVICIO_ACCION_NINGUNA de ServicioGeolocalizacion.
     *
     * Se recomienda ignorar este tipo de mensajes y se recomienda no hacer una solicitud de este
     * tipo de acción a ServicioGeolocalizacion explícitamente. No hará nada más que iniciar el
     * servicio (pero no GPS ni sesión) si no está iniciado. En t odo caso, se recomienda iniciar
     * el servicio con SERVICIO_ACCION_INICIAR_SERVICIO para evitar posibles inconsistencias en el
     * estado interno del servicio si se está iniciando por primera vez.
     *
     * El método existe para facilitar la depuración. Se pueden mostrar mensajes dentro del cuerpo
     * de este método.
     *
     * @param tipoRespuesta Código de respuesta. Fue exitosa la operación o no? Ver: ServicioGeolocalizacion.SERVICIO_RESPUESTA_*
     * @param datos Datos adicionales recibidos con la respuesta.
     */
    private void accionNinguna(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_NINGUNA + ": respuesta tipo " + tipoRespuesta);
    }

    /**
     * Posible respuesta a ReceptorServicioGeolocalizacion.onReceive.
     *
     * Ocurre si se recibe una respuesta de tipo SERVICIO_ACCION_SERVICIO_REINICIADO de ServicioGeolocalizacion.
     *
     * Aunque existe una acción TEXTO_BOTON_ACCION_SESION_REINICIAR para el boton de control, se
     * recomienda no hacer solicitudes de tipo SERVICIO_ACCION_SERVICIO_REINICIADO explícitamente.
     *
     * Es posible, sin embargo, mostrar mensajes si el servicio es reiniciado usando este método.
     * Puede ser mostrado al usuario o usarse únicamente para depurar.
     *
     * @param tipoRespuesta Código de respuesta. Fue exitosa la operación o no? Ver: ServicioGeolocalizacion.SERVICIO_RESPUESTA_*
     * @param datos Datos adicionales recibidos con la respuesta.
     */
    private void accionServicioReiniciado(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_SERVICIO_REINICIADO + ": respuesta tipo " + tipoRespuesta);
    }

    /**
     * Posible respuesta a ReceptorServicioGeolocalizacion.onReceive.
     *
     * Ocurre si se recibe una respuesta de tipo SERVICIO_ACCION_INICIAR_SERVICIO de ServicioGeolocalizacion.
     *
     * Si el servicio devuelve esta respuesta y no hay una opción en progreso, entonces el botón
     * cambiará su acción y texto a "iniciar sesión" y será activado para que el usuario inicie una
     * nueva sesión cuando lo desee. Si el servicio devuelve esta respuesta y hay una sesión en
     * progreso, entonces el botón cambia su acción y texto a "terminar sesión"
     *
     * @param tipoRespuesta Código de respuesta. Fue exitosa la operación o no? Ver: ServicioGeolocalizacion.SERVICIO_RESPUESTA_*
     * @param datos Datos adicionales recibidos con la respuesta.
     */
    private void accionIniciarServicio(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SERVICIO + ": respuesta tipo " + tipoRespuesta);

        if (tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_OK || tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_NO_CAMBIOS) {
            // Servicio iniciado explícitamente y exitosamente.
            if (ServicioGeolocalizacion.operando()) {
                actualizarBotonServicio(true, BOTON_ACCION_SESION_TERMINAR, TEXTO_BOTON_ACCION_SESION_TERMINAR);
            } else {
                actualizarBotonServicio(true, BOTON_ACCION_SESION_INICIAR, TEXTO_BOTON_ACCION_SESION_INICIAR);
            }
        }
    }

    /**
     * Posible respuesta a ReceptorServicioGeolocalizacion.onReceive.
     *
     * Ocurre si se recibe una respuesta de tipo SERVICIO_ACCION_TERMINAR_SERVICIO de ServicioGeolocalizacion.
     *
     * Se recomienda no hacer solicitudes de tipo SERVICIO_ACCION_TERMINAR_SERVICIO al servicio
     * directamente; el servicio se termina sólo si lo considera necesario. Las únicas posibles
     * excepciones a esta regla son accionIniciarSesion y accionTerminarSesion, ya que debe ser
     * capaces de intentar "matar" el servicio si su estado interno es inconsistente y no permite
     * iniciar o terminar sesiones, respectivamente.
     *
     * Si el servicio fue terminado exitosamente por el usuario (que esto no debería ser posible),
     * entonces se vuelve a iniciar el servicio y se reactiva el botón. Si el servicio fue reiniciado
     * por si mismo o por Android, entonces no se hace nada ya que se reiniciará a si mismo.
     *
     * @param tipoRespuesta Código de respuesta. Fue exitosa la operación o no? Ver: ServicioGeolocalizacion.SERVICIO_RESPUESTA_*
     * @param datos Datos adicionales recibidos con la respuesta.
     */
    private void accionTerminarServicio(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SERVICIO + ": respuesta tipo " + tipoRespuesta);

        if (tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_OK) {
            /*
             * Servicio terminado explícitamente.
             *
             * Esto no significa que se haya terminado correctamente la sesión o el GPS.
             * Se intenta iniciar el servicio nuevamente, sin sesión.
             */
            botonToggleServicio.setEnabled(false);
            startService(new Intent(ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SERVICIO, null, this, ServicioGeolocalizacion.class));
        } else if (tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_VACIA) {
            /*
             * El servidor está siendo reiniciado por Android.
             *
             * El servicio continuará operando automáticamente sin interrumpir la sesión
             * actual (si existe.)
             */
        }
    }

    /**
     * Posible respuesta a ReceptorServicioGeolocalizacion.onReceive.
     *
     * Ocurre si se recibe una respuesta de tipo SERVICIO_ACCION_INICIAR_SESION de ServicioGeolocalizacion.
     *
     * Si el servicio comunica que se acaba de iniciar una sesión exitosamente, entonces el botón
     * cambiará su texto y acción a "terminar sesión" y será activado. Si este tipo de respuesta
     * indica que no fue exitosa la operación, entonces se notifica al usuario y se actualiza el
     * botón o se reinicia el servicio, cual sea necesario.
     *
     * @param tipoRespuesta Código de respuesta. Fue exitosa la operación o no? Ver: ServicioGeolocalizacion.SERVICIO_RESPUESTA_*
     * @param datos Datos adicionales recibidos con la respuesta.
     */
    private void accionIniciarSesion(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_INICIAR_SESION + ": respuesta tipo " + tipoRespuesta);

        if (tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_OK) {
            // Sesión creada exitosamente. Se informa usuario y se actualiza el botón.
            notificarUsuario(R.string.anuncio_sesion_iniciada);
            actualizarBotonServicio(true, BOTON_ACCION_SESION_TERMINAR, TEXTO_BOTON_ACCION_SESION_TERMINAR);
        } else {
            // Si no se pudo crear una nueva sesión, entonces primero se informa al usuario
            if (datos == null) {
                notificarUsuario(R.string.error_creando_sesion);
            } else {
                notificarUsuario(datos.toString());
            }

            if (ServicioGeolocalizacion.operando()) {
                /*
                 * Si hay una sesión en progreso, entonces el cliente (o probablemente el servicio)
                 * debe estar en un estado inconsistente ya que normalmente el botón no debe
                 * permitir enviar solicitudes de inicio de sesión mientras hay una sesión en progreso.
                 *
                 * Para remediar el error, el botón cambiará para presentarle al usuario la opción
                 * de terminar la sesión existente.
                 *
                 * TODO: tratar esto como un error real en lugar de intentar remediarlo?
                 */
                actualizarBotonServicio(true, BOTON_ACCION_SESION_TERMINAR, TEXTO_BOTON_ACCION_SESION_TERMINAR);
            } else {
                /*
                 * Si no hay una sesión existente y no se pudo crear una nueva sesión, entonces se
                 * intenta reiniciar el servicio para reestablecer su estado interno.
                 */
                botonToggleServicio.setEnabled(false);
                startService(new Intent(ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SERVICIO, null, this, ServicioGeolocalizacion.class));
            }
        }
    }

    /**
     * Posible respuesta a ReceptorServicioGeolocalizacion.onReceive.
     *
     * Ocurre si se recibe una respuesta de tipo SERVICIO_ACCION_TERMINAR_SESION de ServicioGeolocalizacion.
     *
     * Si una sesión ha sido cerrada exitosamente, entonces se informa esto al usuario y el botón
     * cambiará su texto y acción a "iniciar nueva sesión." Si no se pudo terminar la sesión
     * actual, entonces se intenta matar el servicio para reestablecer su estado interno, ya que
     * es evidente que su estado interno es inconsistente. Si se recibe esta respuesta mientras que
     * no hay una sesión en progreso, entonces hay un error del lado del cliente (o probablemente
     * en el servicio) ya que el botón no debe mostrar la opción "terminar sesión" si no hay una
     * sesión en progreso.
     *
     * @param tipoRespuesta Código de respuesta. Fue exitosa la operación o no? Ver: ServicioGeolocalizacion.SERVICIO_RESPUESTA_*
     * @param datos Datos adicionales recibidos con la respuesta.
     */
    private void accionTerminarSesion(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SESION + ": respuesta tipo " + tipoRespuesta);

        if (tipoRespuesta == ServicioGeolocalizacion.SERVICIO_RESPUESTA_OK) {
            // Si la sesión fue cerrada exitosamente, el botón cambia a "iniciar nueva sesión"
            notificarUsuario(R.string.anuncio_sesion_terminada);
            actualizarBotonServicio(true, BOTON_ACCION_SESION_INICIAR, TEXTO_BOTON_ACCION_SESION_INICIAR);
        } else {
            // Si no se pudo cerrar la sesión, primero se informa al usuario
            if (datos == null) {
                notificarUsuario(R.string.error_terminando_sesion);
            } else {
                notificarUsuario(datos.toString());
            }

            if (ServicioGeolocalizacion.operando()) {
                /*
                 * Si no se pudo cerrar una sesión existente, en progreso, entonces se intenta
                 * matar el servicio y posteriormente reiniciarlo ya que es evidente que su
                 * estado interno no es consistente.
                 */
                botonToggleServicio.setEnabled(false);
                startService(new Intent(ServicioGeolocalizacion.SERVICIO_ACCION_TERMINAR_SERVICIO, null, this, ServicioGeolocalizacion.class));
            } else {
                /*
                 * Como fue mencionado anteriormente, el cliente no debería poder ofrecer la opción
                 * de terminar seesión si n ohay una sesión en progreso.
                 *
                 * TODO: manejar esto como un error? Es el "mismo" caso de accionIniciarSesion.
                 */
                actualizarBotonServicio(true, BOTON_ACCION_SESION_INICIAR, TEXTO_BOTON_ACCION_SESION_INICIAR);
            }
        }
    }

    /**
     * Posible respuesta a ReceptorServicioGeolocalizacion.onReceive.
     *
     * Ocurre si se recibe una respuesta de tipo SERVICIO_ACCION_GPS_INICIADO de ServicioGeolocalizacion.
     *
     * Llamado si la captura de coordenadas a intervalos de tiempo regulares es iniciada. Es usado
     * principalmente para indicarle al usuario que comenzó la captura.
     *
     * No se deben hacer solicitudes de tipo SERVICIO_ACCION_GPS_INICIADO a ServicioGeolocalizacion.
     * Las respuestas son enviadas indirectamente por SERVICIO_ACCION_INICIAR_SESION y
     * SERVICIO_ACCION_TERMINAR_SESION cuando inician o terminan la captura de coordenadas,
     * respectivamente.
     *
     * @param tipoRespuesta Código de respuesta. Fue exitosa la operación o no? Ver: ServicioGeolocalizacion.SERVICIO_RESPUESTA_*
     * @param datos Datos adicionales recibidos con la respuesta.
     */
    private void accionGPSIniciado(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_GPS_INICIADO + ": respuesta tipo " + tipoRespuesta);
        notificarUsuario(R.string.anuncio_gps_iniciado);
    }

    /**
     * Posible respuesta a ReceptorServicioGeolocalizacion.onReceive.
     *
     * Ocurre si se recibe una respuesta de tipo SERVICIO_ACCION_GPS_TERMINADO de ServicioGeolocalizacion.
     *
     * Llamado si la captura de coordenadas a intervalos de tiempo regulares es terminada. Es usado
     * principalmente para indicarle al usuario que terminó la captura.
     *
     * No se deben hacer solicitudes de tipo SERVICIO_ACCION_GPS_TERMINADO a ServicioGeolocalizacion.
     * Las respuestas son enviadas indirectamente por SERVICIO_ACCION_INICIAR_SESION y
     * SERVICIO_ACCION_TERMINAR_SESION cuando inician o terminan la captura de coordenadas,
     * respectivamente.
     *
     * No confundir con SERVICIO_ACCION_GPS_DESACTIVADO o accionGPSDesactivado()
     *
     * @param tipoRespuesta Código de respuesta. Fue exitosa la operación o no? Ver: ServicioGeolocalizacion.SERVICIO_RESPUESTA_*
     * @param datos Datos adicionales recibidos con la respuesta.
     */
    private void accionGPSTerminado(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_GPS_TERMINADO + ": respuesta tipo " + tipoRespuesta);
        notificarUsuario(R.string.anuncio_gps_terminado);
    }

    /**
     * Posible respuesta a ReceptorServicioGeolocalizacion.onReceive.
     *
     * Ocurre si se recibe una respuesta de tipo SERVICIO_ACCION_GPS_DESACTIVADO de ServicioGeolocalizacion.
     *
     * No se deben hacer solicitudes de tipo SERVICIO_ACCION_GPS_DESACTIVADO ya que estos mensajes
     * son emitidos indirectamente por algunas acciones de ServicioGeolocalizacion. En especial,
     * este mensaje es emitido cuando se detecta que el usuario explícitamente desactivó el GPS
     * mientras el servicio estaba operando (en una sesión) o si el proveedor "GPS" (sensor, o modo
     * de alta precisión; el modo "wi-fi" es insuficiente e inaceptable) es desactivado mientras
     * hay una sesión abierta.
     *
     * Actualmente se le hace saber al usuario que el GPS fue desactivado y que la aplicaicón lo
     * requiere para que las sesiones permanezcan abiertas, sin interrupción.
     *
     * No confundir con SERVICIO_ACCION_GPS_TERMINADO o accionGPSTerminado()
     *
     * @param tipoRespuesta Código de respuesta. Fue exitosa la operación o no? Ver: ServicioGeolocalizacion.SERVICIO_RESPUESTA_*
     * @param datos Datos adicionales recibidos con la respuesta.
     */
    private void accionGPSDesactivado(int tipoRespuesta, Serializable datos) {
        android.util.Log.d(MainActivity.class.getCanonicalName(), ServicioGeolocalizacion.SERVICIO_ACCION_GPS_DESACTIVADO + ": respuesta tipo " + tipoRespuesta);
        notificarUsuario(R.string.error_geolocalizacion_desactivado);
    }

    /**
     * Define un receptor privado de mensajes de ServicioGeolocalizacion.
     *
     * Su método onReceive toma distintas acciones dependiendo del mensaje recibido a manera de Intent.
     */
    private final class ReceptorServicioGeolocalizacion extends BroadcastReceiver {
        /**
         * Extrae información del mensaje (intent) recibido de ServicioGeolocalizacion y toma
         * decisiones basándose en su contenido. En especial, basñandose en su "acción."
         *
         * La "accion" debe coincidir con alguno de los valores de
         * ServicioGeolocalizacion.SERVICIO_ACCION_* para que este método haga algo. Si accion
         * coincide, entonces el flujo de ejecución es despachado por este método a un método
         * accion*() de esta Activity que corresponda a la acción; el código de esta función
         * accion*() debe manejar adecuadamente la respuesta recibida.
         *
         * Tenga en cuenta que a este receptor sólo llegarán mensajes que están definidos en la
         * propiedad "filtroAccionesServicio."
         *
         * @param context Contexto Android. Típicamente MainActivity en este caso.
         * @param intent Datos recibidos. Contiene una "acción" y opcionalmente "datos" adicionales.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                // TODO: error! manejar? Por ahora se ignora silenciosamente.
                return;
            }

            /*
             * Se determina acción, tipo de respuesta y datos.
             *
             * Mientras que los datos son opcionales, tipoRespuesta puede indicar si la operación
             * (de la respuesta recibida) fue exitosa o no. Ver: SERVICIO_RESPUESTA_*
             *
             * Por ejemplo, si accion es SERVICIO_ACCION_INICIAR_SESION y tipoRespuesta
             * es igual a SERVICIO_RESPUESTA_ERROR, entonces esto indica que la última operación
             * de inicio de sesión fracasó. Si esto ocurre, la Activity debe mostrarle al usuario
             * que no se pudo iniciar una nueva sesión y se debe arreglar el error.
             */
            String accion = intent.getAction();
            int tipoRespuesta = intent.getIntExtra(ServicioGeolocalizacion.SERVICIO_RESPUESTA_PROPIEDAD_TIPO, ServicioGeolocalizacion.SERVICIO_RESPUESTA_VACIA);
            Serializable datos = intent.getSerializableExtra(ServicioGeolocalizacion.SERVICIO_RESPUESTA_PROPIEDAD_DATOS);

            // Despachando a handler de respuesta adecuado:
            switch (accion) {
                case ServicioGeolocalizacion.SERVICIO_ACCION_GPS_INICIADO:
                    accionGPSIniciado(tipoRespuesta, datos);
                    break;
                case ServicioGeolocalizacion.SERVICIO_ACCION_GPS_TERMINADO:
                    accionGPSTerminado(tipoRespuesta, datos);
                    break;
                case ServicioGeolocalizacion.SERVICIO_ACCION_GPS_DESACTIVADO:
                    accionGPSDesactivado(tipoRespuesta, datos);
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

            // En tdo caso es importante que el usuario sepa los datos de la sesión actual (si hay)
            actualizarVistasInformacion();
        }
    }

    /**
     * Actualiza la información de la sesión actual mostrada al usuario.
     *
     * Si no hay una sesión en progreso, entonces se muestra la información de la última sesión
     * cerrada y es mostrada como "terminada." Si no hay una "última sesión cerrada", entonces se
     * muestra que no hay sesión.
     *
     * Es importante mencionar que, para lograr lo anterior, se hace una distinción entre la seesión
     * actual o real de la aplicación (valor en Serviciogeolocalizacion) y la sesión mostrada al
     * usuario en esta Activity. Por esto existen dos propiedades para el ID de la sesión:
     * SERVICIO_PREFERENCIA_ID_SESION_EN_PROGRESO y SERVICIO_PREFERENCIA_ID_SESION_A_MOSTRAR.
     *
     * También es importante mencionar que para lograr lo anterior, no sólo
     * SERVICIO_PREFERENCIA_ID_SESION_A_MOSTRAR muestra un valor posiblemente desactualizado (es
     * decir, acerca de una sesión anterior terminada). Las propiedades
     * SERVICIO_PREFERENCIA_NOMBRE_SESION, SERVICIO_PREFERENCIA_FECHA_INICIO_SESION y
     * SERVICIO_PREFERENCIA_FECHA_FIN_SESION funcionan de la misma manera. Y en el caso de esta
     * última propiedad, su valor puede ser "vacío" si hay una sesión en progreso, ya que no ha
     * sido terminada.
     */
    private void actualizarVistasInformacion() {
        sesionID = preferencias.getLong(ServicioGeolocalizacion.SERVICIO_PREFERENCIA_ID_SESION_A_MOSTRAR, ServicioGeolocalizacion.SERVICIO_PREFERENCIA_ID_DEFAULT );
        sesionNombre = preferencias.getString(ServicioGeolocalizacion.SERVICIO_PREFERENCIA_NOMBRE_SESION, ServicioGeolocalizacion.SERVICIO_PREFERENCIA_NOMBRE_SESION_DEFAULT).trim();
        sesionFechaInicio = preferencias.getString(ServicioGeolocalizacion.SERVICIO_PREFERENCIA_FECHA_INICIO_SESION, ServicioGeolocalizacion.SERVICIO_PREFERENCIA_FECHA_DEFAULT);
        sesionFechaFin = preferencias.getString(ServicioGeolocalizacion.SERVICIO_PREFERENCIA_FECHA_FIN_SESION, ServicioGeolocalizacion.SERVICIO_PREFERENCIA_FECHA_DEFAULT);

        if (sesionNombre.compareTo("") == 0) {
            sesionNombre = getString(R.string.sesion_nombre_ninguno);
        }

        if (sesionID > 0) {
            infoNombreSesion.setText(String.format(getString(R.string.info_sesion_nombre_formato), sesionNombre, sesionID));
        } else {
            infoNombreSesion.setText(getString(R.string.info_sesion_ninguna));
        }

        infoEstadoSesion.setText(String.format(getString(R.string.info_sesion_estado_formato), ServicioGeolocalizacion.operando() ? getString(R.string.sesion_estado_activa) : getString(R.string.sesion_estado_finalizada)));
        infoFechaInicioSesion.setText(String.format(getString(R.string.info_sesion_fecha_inicio_formato), sesionFechaInicio));
        infoFechaFinSesion.setText(String.format(getString(R.string.info_sesion_fecha_fin_formato), sesionFechaFin));
    }

    /**
     * Ejecutado cada vez que la Activity se hace visible cuando antes no lo estaba.
     *
     * Actualiza la información visible de la sesión actual al regresar a la Activity. La
     * información es leída de la propiedad "preferencias"; un objeto de preferencias a nivel
     * de aplicación compartido con ServicioGeolocalizacion.
     */
    @Override
    protected void onResume() {
        actualizarVistasInformacion();
        super.onResume();
    }

    /**
     * Define el estado inicial de la Activity cuando es creada; carga la interfáz de usuario,
     * asigna _handlers_ a eventos de sus widgets (Views) y se registra como receptor de mensajes
     * de ServicioGeolocalizacion. Finalmente, se asegura de que ServicioGeolocalizacion esté
     * iniciado; lo inicia si no ha sido iniciado.
     *
     * @param savedInstanceState No usado. Estado previo de la Activity antes de ser destruida.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferencias = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        infoNombreSesion = (TextView) findViewById(R.id.infoNombreSesion);
        infoEstadoSesion = (TextView) findViewById(R.id.infoEstadoSesion);
        infoFechaInicioSesion = (TextView) findViewById(R.id.infoFechaInicioSesion);
        infoFechaFinSesion = (TextView) findViewById(R.id.infoFechaFinSesion);

        mensajesServicio = (TextView) findViewById(R.id.mensajesServicio);
        mensajesServicio.append(String.format(formatoMensajesNotificacion, fechaLocalAhora(), getString(R.string.anuncio_binevenido)));

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
