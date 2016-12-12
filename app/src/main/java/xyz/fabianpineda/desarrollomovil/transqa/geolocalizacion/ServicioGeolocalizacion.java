package xyz.fabianpineda.desarrollomovil.transqa.geolocalizacion;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
import xyz.fabianpineda.desarrollomovil.transqa.db.Sesion;
import xyz.fabianpineda.desarrollomovil.transqa.db.SesionSQLite;

/**
 * Servicio de geolocalización que es ejecutado en el fondo, persistentemente, que captura y
 * registra semi-persistentemente la ubicación (latitud, longitud) del dispositivo ordenadamente,
 * por fecha y hora en una base de datos (SQLite3) local.
 *
 * Se trata de un servicio tipo "started service" (en contraste a un "bound service") es iniciado
 * por y recibe mensajes por medio de intents enviados por otros componentes "clientes" (ej.
 * Activities). De esta manera, el servicio persiste y opera mientras la interfáz de usuario de la
 * aplicación no es visible o no existe, sin intervención contínua de parte de/los usuario(s). Toda
 * sesión es terminada si es apagado el dispositivo.
 *
 * El servicio recibe instrucciones por medio de Intents enviados desde componentes "clientes."
 * Esto lo Hacen usando el método startService. Cada vez que el método es ejecutado, el servicio es
 * iniciado y opcionalmente (en este caso siempre) las "instrucciones" (acciones) a realizar son
 * recibidas en este objeto en el método onStartCommand.
 *
 * Dentro de onStartCommand, dependiendo de la acción solicitada por componentes clientes (las
 * operaciones soportadas están definidas en las constantes SERVICIO_ACCION_*), el método despacha
 * el flujo de la ejecución del servicio a una serie de métodos que realizan las tareas solicitadas,
 * siempre enviándole un código (tipo) de resultado al cliente por cada acción, sea una acción
 * solicitada explícitamente por el usuario, o una acción ejecutada implícitamente y automaticamente
 * por el servicio. Por ejemplo, iniciar una sesión requiere que sea iniciado el GPS y que el
 * servicio esté iniciado; el componente cliente recibirá mensajes en este orden: inicio de
 * servicio, inicialización de GPS, y inicio de sesión, todos con un código de resultado asociado.
 * Los posibles códigos de respuesta están definidos en las constantes SERVICIO_RESPUESTA_*
 *
 * Los componentes clientes interesados (restringidos en AndroidManifest.xml a clientes que hacen
 * parte del paquete de esta aplicación y no de aplicaciones distintas de terceros) pueden
 * registrarse como "broadcast receivers" usando el método registerReceiver de una instancia de un
 * LocalBroadcastManager.
 *
 * Es importante saber que, entre todas las operaciones (acciones) soportadas por este servicio, los
 * clientes deben usar explícitamente sólo un subconjunto de  todas las acciones. Esto se debe a que
 * algunas acciones son ejecutadas implícitamente por el servicio y no tienen o no deberían tener
 * algún efecto si se solicitan explícitamente. Un cliente, sin embargo, puede responder a
 * respuestas de cualquier tipo de acción soportada por el servicio, incluyendo acciones que los
 * clientes no deben solicitar explícitamente. Por ejemplo, la accion "iniciar GPS" no debe ser
 * solicitada explícitamente, pero un cliente "broadcast receiver" puede responder a respuestas
 * de esta acción para informarle al usuario que el GPS ha sido inicializado exitosamente.
 *
 * Este servicio hace uso de propiedades compartidas con todos los demás componentes de esta
 * aplicación (SharedPreferences). Mientras que el servicio tiene permisos de escritura con este
 * objeto, los demás componentes deben idealmente tener sólo acceso de lectura. En estas
 * preferencias se almacenan y comparten datos sobre la sesión actual, como su ID y nombre. Teniendo
 * acceso de sólo lectura los demás componentes, toda comunicación y otros datos deben ser enviados
 * a este servicio por medio de startService, usando Intents.
 *
 * Si el servicio está operando y con una sesión iniciada, normalmente la sesión no será
 * interrumpida hasta que el usuario envía una solicitud de "terminar sesión". Una sesión puede ser
 * terminada automáticamente por el servicio (como error) si se cumplen ciertas condiciones. Para
 * nombrar unos ejemplos: el GPS es desactivado, usuario desactiva modo de alta precisión (o modo
 * GPS), el usuario elimina los permisos de "GPS" en Android 6.0 o más reciente, etc.. Pero en
 * tdo caso, si el servicio es matado por Android, el servidor intentará reiniciarse inmediatamente
 * y continuará la sesión. Cerrar todas las Activities de la aplicación no interrumpen de ninguna
 * manera las sesiones abiertas y la captura de coordenadas continuará normalmente.
 *
 * Internamente, se usa LocationManager como "API" para capturar coordenadas. No está siendo usado
 * el "Fused Location Provider" de las APIs Google Play.
 */
public final class ServicioGeolocalizacion extends Service implements LocationListener {
    /**
     * Listado de tipos de acciones (Intents) aceptados por el Servicio.
     *
     * Actualmente sólo responde al evento de sistema ACTION_SHUTDOWN para terminar sesiones en
     * progreso al apagar el dispositivo.
     *
     * TODO: mover a AndroidManifest.xml?
     */
    private static final IntentFilter filtroAccionesSistema;
    static {
        filtroAccionesSistema = new IntentFilter();
        filtroAccionesSistema.addAction(Intent.ACTION_SHUTDOWN);
    }

    // Configuración del servicio. Usar con cuidado.
    private static final int SERVICIO_MODO_INICIO = START_STICKY;                       // El servicio se reiniciará tan pronto como sea posible si es "matado" por Android.
    private static final int SERVICIO_INTERVALO_TIEMPO_GEOLOCALIZACION_MINIMO = 5000;   // Intervalo de tiempo mínimo entre capturas, en milisegundos. Es un "hint"; Android puede tardar más de 5000 ms.
    private static final float SERVICIO_DISTANCIA_GEOLOCALIZACION_MINIMO = 0.0f;        // Distancia mínima entre capturas, en metros. Es cero ya que la captura es actualmente por tiempo, no por distancia.

    // Acciones que pueden ser solicitadas directamente por componentes clientes.
    public static final String SERVICIO_ACCION_INICIAR_SERVICIO = "SERVICIO_GEOLOCALIZACION_ACCION_INICIAR_SERVICIO";
    public static final String SERVICIO_ACCION_TERMINAR_SESION = "SERVICIO_GEOLOCALIZACION_ACCION_TERMINAR_SESION";
    public static final String SERVICIO_ACCION_INICIAR_SESION = "SERVICIO_GEOLOCALIZACION_ACCION_INICIAR_SESION";

    // Acciones que deben ser usadas cuidadosamente y idealmente nunca por componentes clientes.
    public static final String SERVICIO_ACCION_TERMINAR_SERVICIO = "SERVICIO_GEOLOCALIZACION_ACCION_TERMINAR_SERVICIO";

    // Acciones que nunca deben ser solicitadas por componentes clientes pero a las que pueden responder como "broadcast receivers"
    public static final String SERVICIO_ACCION_DISPOSITIVO_APAGADO = "SERVICIO_GEOLOCALIZACION_ACCION_DISPOSITIVO_APAGADO";
    public static final String SERVICIO_ACCION_SERVICIO_REINICIADO = "SERVICIO_GEOLOCALIZACION_ACCION_SERVICIO_REINICIADO";
    public static final String SERVICIO_ACCION_GPS_TERMINADO = "SERVICIO_GEOLOCALIZACION_ACCION_GPS_TERMINADO";
    public static final String SERVICIO_ACCION_GPS_INICIADO = "SERVICIO_GEOLOCALIZACION_ACCION_GPS_INICIADO";
    public static final String SERVICIO_ACCION_GPS_DESACTIVADO = "SERVICIO_GEOLOCALIZACION_ACCION_GPS_DESACTIVADO";

    // Acciones que indican errores o acciones inválidas. No hace sentido solicitarlas directamente en ningún caso.
    public static final String SERVICIO_ACCION_NINGUNA = "SERVICIO_GEOLOCALIZACION_ACCION_NINGUNA";
    public static final String SERVICIO_ACCION_DESCONOCIDA = "SERVICIO_GEOLOCALIZACION_ACCION_DESCONOCIDA";

    /*
     * Los tipos de respuesta que pueden ser enviados como mensajes con resultados de acciones a los
     * componentes clientes usando el método responder(String, int) a manera de Intents.
     *
     * Algunos tipos de respuesta puede enviar de vuelta a los clientes datos adicionales usando
     * responder(String, int, Serializable). El tipo de datos de estos "datos" adicionales para
     * las respuestas es:
     *
     *      * SERVICIO_RESPUESTA_ERROR                  String  Opcional. Un mensaje de error.
     *      * Todos los demás:                          null    Ninguno/ignorado/No usado.
     *
     * TODO: las respuestas en muchos casos (la mayoría!?) son muy ambiguas. Se debería
     * TODO: "estandarizar", limpiar o crear más respuestas. Se debe verificar toda respuesta.
     */
    public static final int SERVICIO_RESPUESTA_ERROR = -1;
    public static final int SERVICIO_RESPUESTA_ACCION_DESCONOCIDA = 0;
    public static final int SERVICIO_RESPUESTA_VACIA = 1;
    public static final int SERVICIO_RESPUESTA_NO_CAMBIOS = 2;
    public static final int SERVICIO_RESPUESTA_OK = 3;

    /*
     * Algunas acciones aceptan parámetros adicionales que serán recibidos en onStartCommand (a
     * trevés de un Intent), mientras que otras opciones requieren estos datos adicionales. En
     * la mayoría de los casos, las acciones no necesitan estos datos.
     *
     * Si un método accion*() acepta un Bundle (es decir, accion*(Bundle)), entonces ese método
     * recibirá un Bundle contenido en el Intent recibido en onStartCommand, y puede o no contener
     * la llave SERVICIO_PARAMETRO_ACCION con un valor de un tipo que varía de acción a acción. Si
     * un método de una acción acepta datos adicionales, entonces debe comprobar la existencia de
     * esta clave y proveer valores por defecto si no está presente. Si una acción requiere estos
     * datos, entonces debe generar un error y ser cancelada si no está presente esta llave.
     *
     * Los tipos de datos son los siguientes para las acciones
     *
     *      * Para acciones que *requieren* datos adicionales:
     *          * Ninguna acción *requiere* datos adicionales por el momento.
     *      * Para acciones que *aceptan* datos adicionales:
     *          * SERVICIO_ACCION_INICIAR_SESION: String. El nombre de la sesión que será iniciada.
     *
     * Todas las acciones no listadas aquí ignoran la llave SERVICIO_PARAMETRO_ACCION. También es
     * seguro asumir que las acciones usadas internamente que no deben solicitar los clientes no
     * usan ningún tipo de datos adicionales a manera de Bundle y su valor será a menudo null.
     *
     * A futuro podrían ser agregados más parámetros.
     */
    public static final String SERVICIO_PARAMETRO_ACCION = "SERVICIO_GEOLOCALIZACION_PARAMETRO_ACCION"; // Debe ser una String vacía o no; null es aceptable.

    /*
     * Listado de parámetros *requeridos* que son aceptados por algunas acciones.
     * Estas propiedades pueden estar disponibles en Intents recibidos en onStartCommand.
     */
    // Ninguna acción *requiere* datos adicionales por el momento.

    /*
     * Las propiedades que contiene tdo Intent de respuesta enviado componentes clientes.
     *
     * Algunos tipos de respuesta no usan la propiedad "datos", pero todas las respuestas contienen,
     * en adición a una "accion", una propiedad "tipo", que indica el tipo de respuesta, que
     * puede ser cualquier valor SERVICIO_RESPUESTA_*.
     *
     * Los clientes interesados pueden entonces tomar decisiones dependiendo de la combinación de
     * la acción con el tipo de respuesta, y opcionalmetne usar datos adicionales.
     */
    public static final String SERVICIO_RESPUESTA_PROPIEDAD_TIPO = "SERVICIO_GEOLOCALIZACION_RESPUESTA_TIPO";
    public static final String SERVICIO_RESPUESTA_PROPIEDAD_DATOS = "SERVICIO_GEOLOCALIZACION_RESPUESTA_DATOS";

    /*
     * Listado de preferencias almacenadas en un SharedPreferences a nivel de aplicación.
     *
     * Entre todas estas propiedades, es importante mencionar que algunas son usadas simplemente
     * para mostrar información "presentable" a los clientes. Sus valores no necesariamente
     * contienen valores actuales y reales correspondientes al estado interno del servicio.
     *
     * Sólo las siguientes propiedades contienen valores que siempre corresponden a los valores
     * actuales del servicio y son principalmente usados para restaurar el estado del servicio
     * despues de haber sido matado y reiniciado por Androi:
     *
     *      * SERVICIO_PREFERENCIA_ID_SESION_EN_PROGRESO
     *      * SERVICIO_PREFERENCIA_OPERANDO
     *      * SERVICIO_PREFERENCIA_ACCION
     *
     * Para asegurarse del funcionamiento correcto del servicio, se recomienda que los demás
     * componentes tengan acceso de "sólo lectura" a estas preferencias compartidas.
     *
     * Algunas propiedades tienen valores por defecto si no contienen un valor asignado explícitamente.
     * Vea las propiedades SERVICIO_PREFERENCIA_*_DEFAULT.
     *
     * TODO: cambiar preferencias "únicas" de este objeto a propiedades a nivel de componente?
     */
    public static final String SERVICIO_PREFERENCIA_OPERANDO = "SERVICIO_GEOLOCALIZACION_OPERACION";
    public static final String SERVICIO_PREFERENCIA_ID_SESION_EN_PROGRESO = "SERVICIO_GEOLOCALIZACION_ID_SESION_EN_PROGRESO";
    public static final String SERVICIO_PREFERENCIA_ID_SESION_A_MOSTRAR = "SERVICIO_GEOLOCALIZACION_ID_SESION_A_MOSTRAR_PROGRESO";
    public static final String SERVICIO_PREFERENCIA_NOMBRE_SESION = "SERVICIO_GEOLOCALIZACION_NOMBRE_SESION";
    public static final String SERVICIO_PREFERENCIA_FECHA_INICIO_SESION = "SERVICIO_GEOLOCALIZACION_FECHA_INICIO_SESION";
    public static final String SERVICIO_PREFERENCIA_FECHA_FIN_SESION = "SERVICIO_GEOLOCALIZACION_FECHA_FIN_SESION";
    public static final String SERVICIO_PREFERENCIA_ACCION = "SERVICIO_GEOLOCALIZACION_ACCION";

    // Valores por defecto para algunas preferencias compartidas a nivel de aplicación.
    public static final long SERVICIO_PREFERENCIA_ID_DEFAULT = 0L;
    public static final String SERVICIO_PREFERENCIA_NOMBRE_SESION_DEFAULT = "Sin nombre";
    public static final String SERVICIO_PREFERENCIA_FECHA_DEFAULT = "/";

    /*
     * Objetos usados por el servicio.
     *
     * Note que el objeto "transmisor" es usado como un receptor de mensajes del sistema para poder
     * terminar sesiones cuando el dispositivo esté siendo apagado.
     */
    private LocalBroadcastManager transmisor;   // Emisor de mensajes usado para informar clientes.
    private SharedPreferences preferencias;     // Preferencias compartidas a nivel de aplicación.
    private LocationManager geolocalizador;     // Usado para obtener info. de geolocalización.
    private SQLiteDatabase db;                  // Para persistir los registros de las sesiones.

    // Usados para determinar el estado y/o soporte/presencia del GPS (proveedores) del dispositivo.
    private static boolean permisosGPSSuficientes;
    private static boolean proveedorGPSActivado;

    // Estado de operación del servicio.
    private static String accion = SERVICIO_ACCION_DESCONOCIDA;     // Acción siendo ejecutada o a ejecutar.
    private static boolean operando = false;                        // Ver: operando(). True si hay sesión en progreso.
    private static boolean iniciado = false;                        // Usado para controlar "reinicio". True si servicio está iniciado.

    // Información de sesión actual, si existe.
    private static long sesionIDActual;         // ID real de sesión en progreso, o un a valor < 1 si no hay.
    private static long sesionIDAMostrar;       // ID de sesión actual o de ID recientemente terminada. Para presentar a clientes.
    private static String sesionNombre;         // Nombre o comentario de Sesión. Para presentar a clientes.
    private static String sesionFechaInicio;    // Fecha de inicio de sesión actual. Para presentar a clientes.
    private static String sesionFechaFin;       // Fecha de fin de sesión recientemente terminada, si está disponible. Para presentar a clientes.

    /**
     * Hace saber a los clientes si hay una sesión en progreso.
     *
     * @return true si hay una sesión en progreso. false en todo otro caso.
     */
    public static boolean operando() {
        return operando;
    }

    /**
     * Envía una respuesta a todos los clientes interesados.
     *
     * Esta versión del método es usada si se desean enviar datos adicionales con la respuesta. Si
     * no se desean enviar datos adicionales, se puede usar null como valor para "datos", o
     * simplemente se puede llamar responder(String, int).
     *
     * Notablemente, si el tipo de respuesta es SERVICIO_RESPUESTA_ERROR, se recomienda incluir
     * como "datos" una String que contenga un mensaje de error a mostrar; una causa del error.
     *
     * Ver: responder(String, int)
     *
     * @param accion La acción a la que los clientes deben responder. Ver: SERVICIO_ACCION_*
     * @param tipoRespuesta El código de respuesta asociado a la acción. Ver: SERVICIO_RESPUESTA_*
     * @param datos Datos adicionales enviados a clientes por medio de Intents. Contendrá adicionalmente la propiedad "accion" que será igual al valor del parámetro "accion".
     */
    private void responder(String accion, int tipoRespuesta, Serializable datos) {
        Intent respuesta = new Intent(accion);

        respuesta.putExtra(SERVICIO_RESPUESTA_PROPIEDAD_TIPO, tipoRespuesta);

        if (datos != null) {
            respuesta.putExtra(SERVICIO_RESPUESTA_PROPIEDAD_DATOS, datos);
        }

        transmisor.sendBroadcast(respuesta);
    }

    /**
     * Envía una respuesta a todos los clientes interesados.
     *
     * Esta versión del método es usada si no se desean enviar datos adicionales con la respuesta.
     * Si se busca enviar datos adicionales, considere usar responder(String, int, Serializable)
     *
     * Ya que la función llama responder(String, int, Serializable) internamente, a los clientes
     * será unviado un Intent con una propiedad accion igual al parámetro "accion". Los componentes
     * clientes pueden usar esta propiedad junto con tipoRespuesta para determinar un curso de
     * acción.
     *
     * Ver: responder(String, int, Serializable)
     *
     * @param accion La acción a la que los clientes deben responder. Ver: SERVICIO_ACCION_*
     * @param tipoRespuesta El código de respuesta asociado a la acción. Ver: SERVICIO_RESPUESTA_*
     */
    private void responder(String accion, int tipoRespuesta) {
        responder(accion, tipoRespuesta, null);
    }

    /**
     * Intenta iniciar una nueva sesión si no hay una sesión en progreso, actualizando
     * sesionIDActual y otras propiedades relacionadas en "preferencias."
     *
     * No confundir con accionIniciarSesion().
     *
     * @return true si la operación fue exitosa. false en otros casos.
     */
    private boolean iniciarSesion(String nombre) {
        Cursor sesion;

        if (sesionIDActual > 0) {
            return false; // No se puede crear una nueva sesión si ya existe una.
        }

        if ((sesion = SesionSQLite.iniciarSesion(db, nombre)) != null) {
            preferencias.edit()
                .putLong(SERVICIO_PREFERENCIA_ID_SESION_EN_PROGRESO, sesionIDActual = sesion.getLong(Sesion.TABLA_SESION_ID_INDICE))
                .putLong(SERVICIO_PREFERENCIA_ID_SESION_A_MOSTRAR, sesionIDAMostrar = sesionIDActual)
                .putString(SERVICIO_PREFERENCIA_NOMBRE_SESION, sesionNombre = sesion.getString(Sesion.TABLA_SESION_NOMBRE_INDICE))
                .putString(SERVICIO_PREFERENCIA_FECHA_INICIO_SESION, sesionFechaInicio = sesion.getString(Sesion.TABLA_SESION_FECHA_INICIO_INDICE))
                .putString(SERVICIO_PREFERENCIA_FECHA_FIN_SESION, sesionFechaFin = SERVICIO_PREFERENCIA_FECHA_DEFAULT)
            .commit();

            sesion.close();
            return true;
        }

        return false;
    }

    /**
     * Intenta terminar la sesión abierta, si existe, modificando sesionIDActual y otras propiedades
     * relacionadas en "preferencias."
     *
     * No confundir con accionTerminarSesion().
     *
     * @return true si la operación fue exitosa. false en otros casos.
     */
    private boolean terminarSesion() {
        Cursor resultado = SesionSQLite.terminarSesion(db, sesionIDActual);

        if (resultado != null) {
            preferencias.edit()
                    .putLong(SERVICIO_PREFERENCIA_ID_SESION_EN_PROGRESO, sesionIDActual = SERVICIO_PREFERENCIA_ID_DEFAULT)
                    .putString(SERVICIO_PREFERENCIA_FECHA_FIN_SESION, sesionFechaFin = resultado.getString(Sesion.TABLA_SESION_FECHA_FIN_INDICE))
            .commit();

            resultado.close();
            return true;
        }

        return false;
    }

    /**
     * Si el dispositivo usa un sensor GPS y está activado en modo GPS o de alta precisión, se
     * enciende el GPS y se inicia la captura regular, contínua de coordenadas del dispositivo.
     *
     * Llamar esta función tiene como efecto secundario la actualización de los valores
     * permisosGPSSuficientes y proveedorGPSActivado. Si la operación es exitosa, entonces este
     * servicio se registra a si mismo como LocationListener, sobreescribiendo los métodos
     * onLocationChanged(Location location), onStatusChanged(String, int, Bundle),
     * onProviderDisabled(String) y onProviderEnabled(String).
     *
     * La frecuencia de peticiones de coordenadas es controlada
     * SERVICIO_INTERVALO_TIEMPO_GEOLOCALIZACION_MINIMO y SERVICIO_DISTANCIA_GEOLOCALIZACION_MINIMO
     *
     * @return true si la operación es exitosa. false en otros casos.
     */
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

    /**
     * Si se cuentan con suficientes permisos, el GPS está activado en modo "GPS" o de "alta
     * precisión" y se están recibiendo actualizaciones de geolocalización (con una sesión abierta),
     * entonces se detiene la captura de ubicación.
     *
     * Si no se cumple lo anterior, la operación falla silenciosamente sin emitir ningún tipo de
     * mensaje y sin afectar el funcionamiento del servicio.
     */
    private void terminarGPS() {
        permisosGPSSuficientes = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        proveedorGPSActivado = geolocalizador.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!permisosGPSSuficientes) {
            return;
        }

        geolocalizador.removeUpdates(this);
    }

    /**
     * Método ejecutado como respuesta a solicitudes SERVICIO_ACCION_INICIAR_SESION.
     *
     * El método opera de esta manera:
     *
     *      * Se intenta iniciar el GPS. Si no se pudo iniciar el GPS y solicitar capturas de
     *        coordenadas, el servicio actualiza su estado a "no operando".
     *          * Si el GPS no está activado, se emite una respuesta de
     *            tipo SERVICIO_ACCION_GPS_INICIADO y respuesta SERVICIO_RESPUESTA_ERROR y mensaje
     *            R.string.error_geolocalizacion_no_gps.
     *          * Si el usuario no tiene suficientes permisos de ubicación, entonces ocurre lo
     *            anterior, pero con mensaje de error R.string.error_geolocalizacion_no_permisos.
     *        En tdo caso, se emite un mensaje SERVICIO_ACCION_INICIAR_SESION con código
     *        de respuesta SERVICIO_RESPUESTA_ERROR.
     *      * Si GPS fue activado exitosamente, así que se emite mensaje
     *        SERVICIO_ACCION_GPS_INICIADO con respuesta SERVICIO_RESPUESTA_VACIA.
     *      * Se intenta iniciar una sesión. Si la operación falla, el GPS es terminado y se
     *        envía SERVICIO_ACCION_GPS_TERMINADO con código respuesta SERVICIO_RESPUESTA_VACIA.
     *        Se envía un mensaje SERVICIO_ACCION_INICIAR_SESION con SERVICIO_RESPUESTA_ERROR y
     *        mensaje R.string.error_creando_sesion_formato (con ID de sesión que no se pudo crear).
     *        Finalmente, el estado del servicio cambia a "no operando" y "no acción."
     *      * Si la sesión fue iniciada exitosamente, el estado del servicio cambia a "operando"
     *        con acción "ninguna" y la captura de coordenadas continuará indefinidamente,
     *        almacenando coordenadas en la base de datos local. Se emite un mensaje
     *        SERVICIO_ACCION_INICIAR_SESION con respuesta SERVICIO_RESPUESTA_OK.
     *
     *  @param datos Bundle con datos adicionales enviados desde onStartCommand. Su propiedad SERVICIO_PARAMETRO_ACCION será tratada como un String que contiene el nombre de la sesión a crear. Si no hay un nombre, el nombre será tratado como null (o una String vacía en la base de datos.)
     */
    private void accionIniciarSesion(Bundle datos) {
        String nombre = null;

        if (datos != null) {
            nombre = datos.getString(SERVICIO_PARAMETRO_ACCION, null);
        }

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

        responder(SERVICIO_ACCION_GPS_INICIADO, SERVICIO_RESPUESTA_VACIA);

        if (!iniciarSesion(nombre)) {
            terminarGPS();
            responder(SERVICIO_ACCION_GPS_TERMINADO, SERVICIO_RESPUESTA_VACIA);

            responder(SERVICIO_ACCION_INICIAR_SESION, SERVICIO_RESPUESTA_ERROR, String.format(
                getString(R.string.error_creando_sesion_formato),
                    sesionIDActual
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

        responder(SERVICIO_ACCION_INICIAR_SESION, SERVICIO_RESPUESTA_OK);
    }

    /**
     * Método ejecutado como respuesta a solicitudes SERVICIO_ACCION_TERMINAR_SESION.
     *
     * El método opera de esta manera:
     *
     *      * Se termina el GPS si no ha sido terminado. Se envía un mensaje de tipo
     *        SERVICIO_ACCION_GPS_TERMINADO con respuesta tipo SERVICIO_RESPUESTA_VACIA.
     *      * Se intenta terminar la sesión. Si la operación falló, entonces se envía un mensaje
     *        SERVICIO_ACCION_TERMINAR_SESION con código respuesta SERVICIO_RESPUESTA_ERROR y
     *        mensaje R.string.error_terminando_sesion_formato con el ID de la sesión que no pudo
     *        ser cerrada.
     *      * Si la operación fue exitosa, se cambia el estado del servicio a "no operando" y
     *        y la acción a "ninguna". Se envía como respuesta un mensaje tipo
     *        SERVICIO_ACCION_TERMINAR_SESION con código de respuesta SERVICIO_RESPUESTA_OK.
     *
     *  @param datos Bundle con datos adicionales enviados desde onStartCommand. Ignorado.
     */
    private void accionTerminarSesion(Bundle datos) {
        long sesionActual = sesionIDActual;

        terminarGPS();
        responder(SERVICIO_ACCION_GPS_TERMINADO, SERVICIO_RESPUESTA_VACIA);

        if (!terminarSesion()) {
            responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_ERROR, String.format(
                    getString(R.string.error_terminando_sesion_formato),
                    sesionActual
            ));
        } else {
            responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_OK);
        }

        preferencias.edit()
            .putBoolean(SERVICIO_PREFERENCIA_OPERANDO, operando = false)
            .putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_NINGUNA)
        .commit();
    }

    /**
     * Método ejecutado como respuesta a solicitudes SERVICIO_ACCION_INICIAR_SERVICIO.
     *
     * Prácticamente no hace nada, ya que cualquier llamado a startServic causa que el servicio
     * inicie si no está iniciado. Sin embargo, para mantener un estado consistente anticipandose
     * a cambios futuros, este método debe ser llamado explícitamente como primera solicitud
     * en los componentes clientes para asegurarse que el servicio mantenga un estado consistente
     * y que sea inicializado de manera correcta.
     *
     * El método opera de esta manera:
     *
     *      * Se cambia el tipo de acción a "ninguna".
     *      * Si el servicio ya estaba iniciado, se envía una respuesta tipo
     *        SERVICIO_ACCION_INICIAR_SERVICIO con código respuesta SERVICIO_RESPUESTA_NO_CAMBIOS
     *        para indicar que no se ha hecho nada.
     *      * Si el servicio no estaba funcionando y acaba de ser iniciado, se marca como "iniciado"
     *        y se envía una respuesta tipo SERVICIO_ACCION_INICIAR_SERVICIO con código de respuesta
     *        SERVICIO_RESPUESTA_OK.
     *
     *  @param datos Bundle con datos adicionales enviados desde onStartCommand. Ignorado.
     */
    private void accionIniciarServicio(Bundle datos) {
        // Prácticamente no hace nada, ya que cualquier startService causa que el servicio inicie si no está iniciado.
        // A pesar de esto, es la manera recomendada de iniciar el servicio porque causa una respuesta a clientes.
        preferencias.edit().putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_NINGUNA).commit();

        if (iniciado) {
            responder(SERVICIO_ACCION_INICIAR_SERVICIO, SERVICIO_RESPUESTA_NO_CAMBIOS);
        } else {
            iniciado = true;
            responder(SERVICIO_ACCION_INICIAR_SERVICIO, SERVICIO_RESPUESTA_OK);
        }
    }

    /**
     * Método ejecutado como respuesta a solicitudes SERVICIO_ACCION_TERMINAR_SESION.
     *
     * Debe ser usado con cuidado y es usado internamente. Se debe evitar no hacer este tipo de
     * solicitud a menos que sea verdaderamente necesario (para reestablecer configuración y
     * funcionamiento debido del servicio).
     *
     * El método simplemente cambia la "accion" a realizar a SERVICIO_ACCION_TERMINAR_SERVICIO y
     * se intenta detener el servicio. Esto tiene como consecuencia que se ejecute su método
     * onDestroy(), el cual se encarga de terminar el servicio ordenadamente.
     *
     *  @param datos Bundle con datos adicionales enviados desde onStartCommand. Ignorado.
     */
    private void accionTerminarServicio(Bundle datos) {
        preferencias.edit().putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_TERMINAR_SERVICIO).commit();
        stopSelf(); // onDestroy se encarga del resto. Cierra sesión si no está cerrada. Termina GPS.
    }

    /**
     * Método ejecutado com orespuesta a solicitudes SERVICIO_ACCION_GPS_DESACTIVADO.
     *
     * Detiene la captura de coordenadas y termina la sesión actual, si existe. Tiene un efecto
     * similar a SERVICIO_ACCION_TERMINAR_SERVICIO y lo usa internamente. Adicionalmente,
     * envía un mensaje tipo SERVICIO_ACCION_GPS_DESACTIVADO con un código de respuesta
     * tipo SERVICIO_RESPUESTA_VACIA.
     *
     * No se debe confundir con SERVICIO_ACCION_GPS_TERMINADO. GPS terminado símplemente ocurre
     * cuando se deteine la captura de coordenadas, meintras que GPS desactivado ocurre cuando
     * el usuario manualmente desactiva el GPS, el proveedor de GPS "GPS" o remueve los permisos
     * de geolocalización a la aplicación en Android 6 o superior.
     *
     * No se deben hacer solicitudes de este tipo directamente por clientes y toda solicitud de
     * clientes a esta acción debe ser ignorada por el Servicio. Los clientes son libres de
     * responder a esta acción para mostrar mensajes relevantes a lo ocurrido.
     *
     *  @param datos Bundle con datos adicionales enviados desde onStartCommand. Ignorado.
     */
    private void accionGPSDesactivado(Bundle datos) {
        accion = SERVICIO_ACCION_GPS_DESACTIVADO;
        responder(SERVICIO_ACCION_GPS_DESACTIVADO, SERVICIO_RESPUESTA_VACIA);
        accionTerminarServicio(datos);
    }

    /**
     * Método ejecutado como respuesta a solicitudes SERVICIO_ACCION_DISPOSITIVO_APAGADO.
     *
     * La acción tiene el mismo efecto que ACCION_TERMINAR_SERVICIO, ya que en este caso se busca
     * terminar el servicio (y cualquier recurso y sesión abierta) antes de apagar el sistema. Es
     * crucial terminar toda sesión abierta al apagar el dispositivo para no afectar la información
     * extraida del análisis de los datos; promedios de velocidad, por ejemplo. Adicionalmente,
     * envía una respuesta tipo SERVICIO_ACCION_DISPOSITIVO_APAGADO con un código de respuesta
     * tipo SERVICIO_RESPUESTA_VACIA.
     *
     * No se deben hacer solicitudes de este tipo directamente por clientes, y toda solicitud de
     * clientes a este tipo de acción debe ser rechazada; es una acción de uso interno. Sin
     * embargo, los clientes pueden optar por responder a respuestas de esta acción para actualizar
     * los componentes de su UI, guardar información, o símplemente mostrar mensajes de depuración.
     *
     * TODO: arreglar, no funciona. Desactivado (comentado) en onCreate.
     *
     * @param datos Bundle con datos adicionales enviados desde onStartCommand. Ignorado.
     */
    private void accionDispositivoApagado(Bundle datos) {
        accion = SERVICIO_ACCION_DISPOSITIVO_APAGADO;
        responder(SERVICIO_ACCION_DISPOSITIVO_APAGADO, SERVICIO_RESPUESTA_VACIA);
        accionTerminarServicio(datos);
    }

    /**
     * Método ejecutado cuando se recibe una acción broadcast (Intent) del sistema listado en el
     * objeto filtroAccionesSistema.
     *
     * Actualmente sólo responde al evento "dispositivo apagándose".
     *
     * TODO: arreglar, no funciona. Desactivado (comentado) en onCreate.
     *
     * @param intent Intent de acción en filtroAccionesSistema, enviado por Android.
     */
    private void intentSistemaRecibido(Intent intent) {
        String accion;

        // Se ignora tdo Intent que parezca no estar bien formado

        if (intent == null || (accion = intent.getAction()) == null || accion.trim().compareTo("") == 0) {
            return;
        }

        // Se responde por ahora sólo al evento ACTION_SHUTDOWN. Termina servicio y sesion.
        if (accion.compareTo(Intent.ACTION_SHUTDOWN) == 0) {
            accionTerminarServicio(null);
        }
    }

    /**
     * Si el servicio es iniciado con startService por Android, no por la aplicación en si,
     * onStartCommand es llamado automáticamente con un Intent null si el servicio está configurado
     * para que sea reiniciado automáticamente, tan pronto como sea posible si Android mata su
     * proceso (START_STICKY).
     *
     * Si el Intent es null, entonces la implementación de onStartCommand despacha el flujo de
     * ejecución del servicio a este método, el cual se encarga de continuar la operación que
     * el servicio estaba llevando a cabo antes de ser matado. Si no se estaba llevando a cabo
     * alguna acción, el método no hace nada; simplemente restaura propiedades del estado anterior
     * del servicio/proceso.
     *
     * El método funciona de esta manera:
     *
     *      * Si existía una sesión abierta con id inferior a 1 y "operando" era false, entonces el
     *        servicio mantenía un estado inconsistente. En este caso, se emite un mensaje de tipo
     *        SERVICIO_ACCION_SERVICIO_REINICIADO con un código de respuesta
     *        SERVICIO_RESPUESTA_ERROR y un mensaje R.string.error_geolocalizacion_interno.
     *      * Si no se estaba llevando a cabo alguna operación, entonces se responde con un mensaje
     *        tipo SERVICIO_ACCION_SERVICIO_REINICIADO y código SERVICIO_RESPUESTA_VACIA.
     *      * Si existía una sesión válida antes de reiniciar el servicio, entonces se intenta
     *        restaurar el estado anterior.
     *        * Se intenta iniciar el GPS. Si la operación es exitosa, se envía una respuesta tipo
     *          SERVICIO_ACCION_SERVICIO_REINICIADO con código SERVICIO_RESPUESTA_VACIA.
     *        * Si no se pudo iniciar el GPS, se encía una respuesta de error con tipo
     *          SERVICIO_ACCION_SERVICIO_REINICIADO y código SERVICIO_RESPUESTA_ERROR con texto
     *          R.string.error_geolocalizacion_no_gps. El estado del servicio cambia a "no operando"
     *          y se intenta terminar la sesiión actual.
     *          * Si no se pudo terminar la sesión, se responde con mensaje tipo
     *            SERVICIO_ACCION_TERMINAR_SESION, código SERVICIO_RESPUESTA_ERROR y texto
     *            R.string.error_terminando_sesion_formato con el ID de la sesión no terminada.
     *          * Si la sesión fue terminada exitosamente, se regresa mensaje de tipo
     *            SERVICIO_ACCION_TERMINAR_SESION con código respuesta SERVICIO_RESPUESTA_OK. Este
     *            caso ocurre para arreglar problemas con sesiones si el servicio fue terminado
     *            automáticamente para reiniciarse a si mismo.
     */
    private void reanudarOperacion() {
        long sesionActual = sesionIDActual;

        if (!operando) {
            responder(SERVICIO_ACCION_SERVICIO_REINICIADO, SERVICIO_RESPUESTA_VACIA);
        } else if (sesionIDActual < 1) {
            // Estado inconsistente! Se está operando sobre una sesión inválida. Terminando servicio.
            responder(SERVICIO_ACCION_SERVICIO_REINICIADO, SERVICIO_RESPUESTA_ERROR, getString(R.string.error_geolocalizacion_interno));
            accionTerminarServicio(null);
        } else if (iniciarGPS()) {
            responder(SERVICIO_ACCION_SERVICIO_REINICIADO, SERVICIO_RESPUESTA_VACIA);
        } else {
            responder(SERVICIO_ACCION_SERVICIO_REINICIADO, SERVICIO_RESPUESTA_ERROR, getString(R.string.error_geolocalizacion_no_gps));

            preferencias.edit().putBoolean(SERVICIO_PREFERENCIA_OPERANDO, operando = false).commit();
            responder(SERVICIO_ACCION_GPS_INICIADO, SERVICIO_RESPUESTA_ERROR, getString(R.string.error_geolocalizacion_no_gps));

            if (!terminarSesion()) {
                responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_ERROR, String.format(
                        getString(R.string.error_terminando_sesion_formato),
                        sesionActual
                ));
            } else {
                responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_OK);
            }
        }
    }

    /**
     * Método iniciado automáticamente después de cada llamado exitoso a startService. Toma como
     * entrada el Intent enviado con startService; o toma null si el servicio está configurado
     * con START_STICKY para que sea reiniciado tan pronto como sea posible después de haber sido
     * matado por Android, después de haber sido matado.
     *
     * En tdo caso, el Servicio es iniciado (si no ocurre un crash). Su acción es cambiada
     * *normalmente* a "ninguna" después de llevar a cabo cualquier operación, y su propiedad
     * iniciado es cambiada a true.
     *
     * Algunas acciones, como accionIniciarSesion (SERVICIO_ACCION_INICIAR_SESION) pueden o
     * requieren datos adicionales en el Intent pasado a onStartCommand para poder funcionar. En
     * el caso de accionIniciarSesion, se puede pero no se requiere adicionar una propiedad
     *
     * IDEA: se podría usar startID para identificar únicamente las solicitudes por Activity y
     * por petición individual. Esto permitiría hacer dos tipos de respuestas si llega a ser
     * necesario: respuestas que son atendidas para todos los "broadcast receivers", y
     * respuestas que son atentidas para "broadcast receivers" específicos.
     *
     * @param intent Intent recibido por startService, o null si Android mató y reinió el servicio.
     * @param flags No usado por este servicio START_STICKY.
     * @param startId No usado. Solicitud única que identifica esta solicitud.
     *
     * @return SERVICIO_MODO_INICIO: START_STICKY actualmente. Modo de inicio del servicio. Es un requisito de Android para tdo Service.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String accionIntent;
        Bundle datos;

        if (intent == null) {
            /*
             * El Intentes null; el servicio fue (re)iniciado por Android. Se intenta reanudar la
             * acción que estaba ejecutando el Servicio, si había alguna.
             *
             * https://developer.android.com/reference/android/app/Service.html#START_STICKY
             */
            reanudarOperacion();
        } else {
            // La acción es implícitamente SERVICIO_ACCION_DESCONOCIDA si no hay acción en Intent.
            if ((accionIntent = intent.getAction()) == null || accionIntent.trim().compareTo("") == 0) {
                accionIntent = SERVICIO_ACCION_DESCONOCIDA;
            }

            // Algunas acciones aceptan datos; otras requieren datos. El bundle "extras" contiene estos datos.
            datos = intent.getExtras();

            // Se determina un curso de acción tomando datos del Intent.
            switch (accionIntent) {
                // ACCIONES QUE PUEDEN SER SOLICITADAS EXPLÍCITAMENTE POR CLIENTES
                // ===============================================================
                case SERVICIO_ACCION_INICIAR_SESION:
                    accionIniciarSesion(datos);
                    break;

                case SERVICIO_ACCION_TERMINAR_SESION:
                    accionTerminarSesion(datos);
                    break;

                case SERVICIO_ACCION_INICIAR_SERVICIO:
                    accionIniciarServicio(datos);
                    break;

                // ACIONES QUE NO DEBEN SER SOLICITADAS EXPLÍCITAMENTE POR CLIENTES
                // ================================================================
                case SERVICIO_ACCION_TERMINAR_SERVICIO:
                    accionTerminarServicio(datos);
                    break;

                /*
                 * El servicio ignora intencionalmente toda solicitud a las siguientes acciones,
                 * tratándolas como acción "ninguna".
                 */
                case SERVICIO_ACCION_DISPOSITIVO_APAGADO:
                case SERVICIO_ACCION_SERVICIO_REINICIADO:
                case SERVICIO_ACCION_GPS_INICIADO:
                case SERVICIO_ACCION_GPS_TERMINADO:
                case SERVICIO_ACCION_GPS_DESACTIVADO:
                    preferencias.edit().putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_NINGUNA).commit();
                    break;

                /*
                 * ACCIONES QUE NO HACE SENTIDO SOLICITAR
                 * ======================================
                 *
                 * La acción "ninguna" hace lo que su nombre implica, pero implícitamente inicia
                 * el servicio al ser solicitada. Considere usar SERVICIO_ACCION_INICIAR_SERVICIO
                 * en lugar de SERVICIO_ACCION_NINGUNA.
                 */
                case SERVICIO_ACCION_NINGUNA:
                    // Nada que hacer.
                    break;

                /*
                 * El caso de SERVICIO_ACCION_DESCONOCIDA es tratado como el caso "default," en
                 * ambos casos es un error y se trata como acción "ninguna", pero se envía una
                 * respuesta a los clientes interesados.
                 *
                 * TODO: manejar más estrictamente? Esto es prácticamente un error.
                 */
                case SERVICIO_ACCION_DESCONOCIDA:
                default:
                    responder(SERVICIO_ACCION_DESCONOCIDA, SERVICIO_RESPUESTA_ACCION_DESCONOCIDA);
                    break;
            }
        }

        // Control de estado y requisitos de Android.
        iniciado = true;
        return SERVICIO_MODO_INICIO;
    }

    /**
     * No hace nada; este no es un bound service.
     *
     * @param intent Intent envíado como "comando" a bound service.
     * @return null, para indicar que este no es un bound service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * No hace nada; este no es un bound service.
     *
     * @param intent Intent enviado al "soltar" un bound service.
     * @return false, ya que este no es un bound service y no se permite "rebind"
     */
    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    /**
     * No hace nada; este no es un bound service.
     *
     * @param intent Intent enviado al intentar hacer "rebind". No hace nada, ya que no es un bound service y no permite rebind.
     */
    @Override
    public void onRebind(Intent intent) {
        // Nada.
    }

    /**
     * Mientras que este servicio esté configurado para recibir actualizaciones de geolocalización,
     * se realizarán capturas de coordenadas regularmente mientras exista una sesión abierta. Cada
     * captura de coordenadas será insertada en una base de datos local con una fecha de captura y
     * asociada a la sesion actual, por ID.
     *
     * Sobreescritura de método de LocationListener.
     *
     * @param location Las coordenadas obtenidas en esta captura; proveido por Android.
     */
    @Override
    public void onLocationChanged(Location location) {
        android.util.Log.d(ServicioGeolocalizacion.class.getCanonicalName(), location.toString()); // TODO: remover este mensaje cuando la aplicación esté más estable

        if (operando) {
            GeolocalizacionSQLite.agregarCoordenadas(db, sesionIDActual, location.getLatitude(), location.getLongitude());
        }
    }

    /**
     * Ejecutado cada vez que cambia el estado del GPS. Sobreescritura de método de LocationListener.
     *
     * * Si el proveedor de GPS GPS_PROVIDER es desactivado, se actualia el estado interno de GPS
     *   y se interrumpe la sección actual.
     * + Si el estado es AVAILABLE, entonces el GPS se marca como disponible y listo para aceptar
     *   coordenadas entrantes. Se debe iniciar una sesión manualmente después. No responsabilidad
     *   de este método.
     * * Si es TEMPORARILY_UNAVAILABLE, actualmente sólo se muestra un mensaje de depuración; según
     *   la documentación de Android, no es muy extensa en general.
     *
     * @param provider El proveedor cuyo estado ha cambiado.
     * @param status Código de estado del tipo de cambio. Puede ser AVAILABLE, TEMPORARILY_UNAVAILABLE o OUT_OF_SERVICE.
     * @param extras Información extra proporcionada por LocationManager.
     */
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (provider.compareTo(LocationManager.GPS_PROVIDER) == 0) {
            if (status == LocationProvider.OUT_OF_SERVICE) {
                proveedorGPSActivado = false;
                accionGPSDesactivado(null);
            } else if (status == LocationProvider.AVAILABLE) {
                proveedorGPSActivado = true;
            } else if (status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                // TODO: considerar manejar como error y considerar remover mesnsaje.
                android.util.Log.d(xyz.fabianpineda.desarrollomovil.transqa.MainActivity.class.getCanonicalName(), "Estado de LocationProvider GPS_PROVIDER cambiado a TEMPORARILY_UNAVAILABLE.");
            }
        }
    }

    /**
     * De LocationListener. Ocurre cada vez que un proveedor de GPS es activado. Marca el GPS como
     * "disponible" para iniciar capturas si el proveedor es GPS_PROVIDER.
     *
     * @param provider El proveedor de GPS que fue activado.
     */
    @Override
    public void onProviderEnabled(String provider) {
        if (provider.compareTo(LocationManager.GPS_PROVIDER) == 0) {
            proveedorGPSActivado = true;
        }
    }

    /**
     * De LocationListener. Ocurre cada vez que un proveedor de GPS es activado. Si el proveedor
     * de GPS GPS_PROVIDER es desactivado, el servicio es terminado, y con el servicio, es terminada
     * cualquie sesión abierta.
     *
     * No confundir SERVICIO_ACCION_GPS_DESACTIVADO con SERVICIO_ACCION_GPS_TERMINADO. El primero
     * es emitido cuando el GPS es desactivado por el usuario (en settings, o si el proveedor de
     * GPS SERVICIO_ACCION_GPS_DESACTIVADO es desactivado por el usuario), mientras que el último
     * es emitido cuando el servicio desactiva la captura de coordenadas.
     *
     * @param provider El GPS desactivado.
     */
    @Override
    public void onProviderDisabled(String provider) {
        if (provider.compareTo(LocationManager.GPS_PROVIDER) == 0) {
            proveedorGPSActivado = false;
            accionGPSDesactivado(null);
        }
    }

    /**
     * Ejecutado como resultado de SERVICIO_ACCION_TERMINAR_SERVICIO, o cuando Android mata el
     * servicio. En tdo caso, se cierra la conexión con la base de datos local (si existe dicha
     * conexion) y el estado del servicio cambia a "no iniciado."
     *
     *      * Si existe una sesión abierta, se termina el GPS y el servicio envía una respuesta
     *        tipo SERVICIO_ACCION_GPS_TERMINADO con código de respuesta SERVICIO_RESPUESTA_VACIA
     *        antes de terminar el servicio. Una sesión es considerada abierta en este paso si
     *        "operando" es true; no si el ID de la sesión es > 0.
     *      * Independientemente de lo anterior, si la acción siendo ejecutada es "terminar
     *        servicio" o "gps _desactivado_ (no terminado, ver onProviderDisabled(String))"
     *          * Si existe una sesión abierta (independientemente del valor de "operando"; es
     *            decir, si el ID de la sesión es > 0), se intenta cerrar la sesión.
     *              * Si la sesión no fue terminada (error) resp. SERVICIO_ACCION_TERMINAR_SESION
     *                con código respuesta SERVICIO_RESPUESTA_ERROR y mensaje
     *                R.string.error_terminando_sesion_formato con ID de sesión actual.
     *              * Si la sesión si fue terminada exitosamente, se responde
     *                SERVICIO_ACCION_TERMINAR_SESION, código SERVICIO_RESPUESTA_OK.
     *              * Después de lo anterior, el servicio cambia a estado "no operando" y su acción
     *                cambia a SERVICIO_ACCION_DESCONOCIDA y se responde con
     *                SERVICIO_ACCION_TERMINAR_SERVICIO, código SERVICIO_RESPUESTA_OK.
     *                Se usa SERVICIO_ACCION_DESCONOCIDA ya que es el valor por defecto de accion en
     *                onCreate. Se busca en este caso iniciar con una configuración inicial, sin
     *                información de estado anterior.
     *      * De lo contrario, si la acción siendo ejecutada no es "terminar servicio" o "GPS
     *        desactivado", entonces se responde con SERVICIO_ACCION_TERMINAR_SERVICIO y código
     *        SERVICIO_RESPUESTA_VACIA.
     */
    @Override
    public void onDestroy() {
        long sesionActual = sesionIDActual;

        if (operando) {
            terminarGPS();
            responder(SERVICIO_ACCION_GPS_TERMINADO, SERVICIO_RESPUESTA_VACIA);
        }

        if (accion.equals(SERVICIO_ACCION_TERMINAR_SERVICIO) || accion.equals(SERVICIO_ACCION_GPS_DESACTIVADO)) {
            if (sesionIDActual > 0) {
                if (!terminarSesion()) {
                    responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_ERROR, String.format(
                            getString(R.string.error_terminando_sesion_formato),
                            sesionActual
                    ));
                } else {
                    responder(SERVICIO_ACCION_TERMINAR_SESION, SERVICIO_RESPUESTA_OK);
                }
            }

            preferencias.edit()
                    .putBoolean(SERVICIO_PREFERENCIA_OPERANDO, operando = false)
                    .putString(SERVICIO_PREFERENCIA_ACCION, accion = SERVICIO_ACCION_DESCONOCIDA)
                    .commit();

            responder(SERVICIO_ACCION_TERMINAR_SERVICIO, SERVICIO_RESPUESTA_OK);
        } else {
            responder(SERVICIO_ACCION_TERMINAR_SERVICIO, SERVICIO_RESPUESTA_VACIA);
        }

        if (db != null && db.isOpen()) {
            db.close();
        }

        iniciado = false;
    }

    /**
     * Define el estado inicial del servicio, o restaura el estado de operación anterior si está
     * siendo reiniciado.
     *
     * Las propiedades de estado semi-persistentes del estado del servicio son almacenadas y leidas
     * de un objeto SharedPreference, "preferencias" que opera a nivel de aplicación.
     *
     * Si no existe información de estado anterior, se asume que no se está operando en ninguna
     * sesión, se asume que la acción es SERVICIO_ACCION_DESCONOCIDA, se toman valores por defecto
     * para sesionIDActual y sesionIDAMostrar (SERVICIO_PREFERENCIA_ID_DEFAULT) y para sesionNombre
     * (SERVICIO_PREFERENCIA_NOMBRE_SESION_DEFAULT) y para sesionFechaInicio sesionFechaFin
     * (SERVICIO_PREFERENCIA_FECHA_DEFAULT). Se obtiene una referencia a una instancia
     * LocalBroadcastManager para enviar mensajes a componentes clientes; se obtiene una referencia
     * a LocationManager, se comprueba si el dispositivo tiene un sensor GPS configurado en modo
     * GPS o "alta precisión" y que tenga suficientes permisos en Android 6.0 o superior. Por último
     * el estado del servicio cambia a "iniciado", "operando" permanece siendo false, y se abre
     * una conexión con la base de datos "DB" del servicio con permisos de escritura.
     *
     * El servicio no es destruido y re-creado durante el funcionamiento normal del servicio. Si
     * las operaciones no fallan con errores críticos, es seguro asumir, por ejemplo, que una
     * solicitud exitosa de "iniciar sesión" seguida por una solicitud exitosa de "terminar sesión"
     * sea atendida por el mismo proceso. El servidor continúa operando normalmente sin interrupción
     *
     * TODO: arreglar manejo de reinicio de dispositivo. No funciona. Desactivado (comentado).
     */
    @Override
    public void onCreate() {
        preferencias = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        operando = preferencias.getBoolean(SERVICIO_PREFERENCIA_OPERANDO, false);
        accion = preferencias.getString(SERVICIO_PREFERENCIA_ACCION, SERVICIO_ACCION_DESCONOCIDA);

        sesionIDActual = preferencias.getLong(SERVICIO_PREFERENCIA_ID_SESION_EN_PROGRESO, SERVICIO_PREFERENCIA_ID_DEFAULT);
        sesionIDAMostrar = preferencias.getLong(SERVICIO_PREFERENCIA_ID_SESION_A_MOSTRAR, sesionIDActual);
        sesionNombre = preferencias.getString(SERVICIO_PREFERENCIA_NOMBRE_SESION, SERVICIO_PREFERENCIA_NOMBRE_SESION_DEFAULT);
        sesionFechaInicio = preferencias.getString(SERVICIO_PREFERENCIA_FECHA_INICIO_SESION, SERVICIO_PREFERENCIA_FECHA_DEFAULT);
        sesionFechaFin = preferencias.getString(SERVICIO_PREFERENCIA_FECHA_FIN_SESION, SERVICIO_PREFERENCIA_FECHA_DEFAULT);

        transmisor = LocalBroadcastManager.getInstance(this);
        // TODO: arreglar, probar ya ctivar manejo de reinicio de dispositivo. *Debe* terminar sesiones abiertas.
        /*transmisor.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                intentSistemaRecibido(intent);
            }
        }, filtroAccionesSistema);*/

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
}