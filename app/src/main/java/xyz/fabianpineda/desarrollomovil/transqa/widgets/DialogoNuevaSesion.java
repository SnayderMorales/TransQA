package xyz.fabianpineda.desarrollomovil.transqa.widgets;

import android.support.v7.app.AppCompatActivity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import xyz.fabianpineda.desarrollomovil.transqa.R;

/**
 * Define un diálogo simple y personalizable con un View EditText para colectar un dato o respuesta
 * del usuario.
 *
 * En tdo caso, como mínimo, es presentado un diálogo con un botón "OK" y un botón "cancelar", y con
 * un EditText para colectar información. Opcionalmente se puede agregar un título al diálogo,
 * un nombreSesion (TextView) que aparecerá arriba del EditText, y un texto "hint" para el EditText.
 *
 * Las características opcionales mencionadas anteriormente pueden ser incluidas si se usa como
 * parámetros objetos en lugar de null. Adicionalmente, el díalogo por defecto no es "cancelable",
 * pero si "listenerCancelado" no es null, entonces será cancelable y el objeto será el handler
 * para el evento "diálogo cancelado."
 *
 * Es obligatorio usar listeners para los _botones_ "OK" y "cancelar."
 *
 * Evite usar el método show() para mostrar el diálogo; use el método mostrar().
 */
public class DialogoNuevaSesion extends DialogFragment {
    public interface ListenerDialogoNuevaSesionOK {
        void dialogoNuevaSesionOK(String nombreSesion, int idVehiculo);
    }

    public interface ListenerDialogoNuevaSesionCancelar {
        void dialogoNuevaSesionCancelar(String nombreSesion, int idVehiculo);
    }

    public interface ListenerDialogoNuevaSesionCancelado {
        void dialogoNuevaSesionCancelado(String nombreSesion, int idVehiculo);
    }

    private AppCompatActivity contexto;
    private String etiquetaFragment;

    private Resources recursos;

    private String titulo;
    private String nombreSesion;
    private String hintTextInput;

    // TODO: probando; actualmente es la posición en ListView de vehiculos, NO ES id de vehiculo.
    private int idVehiculoSeleccionado;

    private ListenerDialogoNuevaSesionOK listenerOK;
    private ListenerDialogoNuevaSesionCancelar listenerCancelar;
    private ListenerDialogoNuevaSesionCancelado listenerCancelado;

    private TextView viewTitulo;
    private EditText viewNombreSesion;
    private ListView viewVehiculo;

    /**
     * Muestra un nuevo diálogo personalizado con un EditText, un botón "OK" y un botón "cancelar"
     *
     * Es obligatorio proveed un OnClickListener para el botón "OK" y otro para el botón "cancelar".
     * Estos datos serán enviados al método show() para mostrar el diálogo, y a su vez, show()
     * usará estos datos para crear el diálogo por medio de onCreateDialog().
     *
     * Evite usar el método show() para mostrar este diálogo.
     *
     * Esta versión del método acepta Strings para valores textuales. Use mostrar(String, int, int, int, ...)
     * si prefiere usar IDs de recursos String definidos en R.string.*
     *
     * @param contexto Activity "padre" de este Fragment.
     * @param etiquetaFragment Etiqueta usada para identificar este DialogFragment. Un requisito del método show(); obligatorio.
     * @param titulo Título a mostrar. Opcional. No es mostrado si no está presente.
     * @param nombreSesion Mensaje a mostrar encima del campo de entrada de texto. Opcional. No es mostrado si no está presente.
     * @param hintTextInput Mensaje a mostrar como "pista" cuando el campo de entrada de texto está vacío. Opcional.
     * @param listenerOK Callback que será invocado cuando se presione el boton OK. Obligatorio. El objeto que implementa este método recibirá el texto actual del campo de entrada.
     * @param listenerCancelar Callback que será invocado cuando se presione el boton Cancelar. Obligatorio. El objeto que implementa este método recibirá el texto actual del campo de entrada.
     * @param listenerCancelado Callback que será invocado cuando se cancela el diálogo presionando el botón atrás. Opcional. Si está presente, el diálogo será cancelable y si se cancela, será enviado el valor actual del campo de texto al usuario. Si no está presente, entonces el diálogo no será "cancelable" y las únicas opciones para hacerlo desaparecer son presionar el botón OK o el botón Cancelar.
     */
    public void mostrar(AppCompatActivity contexto, String etiquetaFragment, String titulo, String nombreSesion, String hintTextInput, ListenerDialogoNuevaSesionOK listenerOK, ListenerDialogoNuevaSesionCancelar listenerCancelar, ListenerDialogoNuevaSesionCancelado listenerCancelado) {
        if (listenerOK == null || listenerCancelar == null) {
            throw new RuntimeException("DialogoNuevaSesion.mostrar requiere un OnClickListener no nulo para el botón 'OK' y para el botón 'cancelar'.");
        }

        this.contexto = contexto;

        if (this.recursos == null) {
            this.recursos = contexto.getResources();
        }

        this.etiquetaFragment = etiquetaFragment;
        this.titulo = titulo;
        this.nombreSesion = nombreSesion;
        this.hintTextInput = hintTextInput;
        this.listenerOK = listenerOK;
        this.listenerCancelar = listenerCancelar;
        this.listenerCancelado = listenerCancelado;

        show(contexto.getSupportFragmentManager(), this.etiquetaFragment);
    }

    /**
     * Muestra un nuevo diálogo personalizado con un EditText, un botón "OK" y un botón "cancelar"
     *
     * Es obligatorio proveed un OnClickListener para el botón "OK" y otro para el botón "cancelar".
     * Estos datos serán enviados al método show() para mostrar el diálogo, y a su vez, show()
     * usará estos datos para crear el diálogo por medio de onCreateDialog().
     *
     * Evite usar el método show() para mostrar este diálogo.
     *
     * Esta versión del método acepta ints de recursos en R.string.* para valores textuales. Use
     * mostrar(String, String, String, String, ...) si prefiere usar IDs de recursos String y no "recursos."
     *
     * @param contexto Activity "padre" de este Fragment.
     * @param etiquetaFragment Etiqueta usada para identificar este DialogFragment. Un requisito del método show(); obligatorio.
     * @param titulo Título a mostrar. Opcional. No es mostrado si no está presente.
     * @param nombreSesion Mensaje a mostrar encima del campo de entrada de texto. Opcional. No es mostrado si no está presente.
     * @param hintTextInput Mensaje a mostrar como "pista" cuando el campo de entrada de texto está vacío. Opcional.
     * @param listenerOK Callback que será invocado cuando se presione el boton OK. Obligatorio. El objeto que implementa este método recibirá el texto actual del campo de entrada.
     * @param listenerCancelar Callback que será invocado cuando se presione el boton Cancelar. Obligatorio. El objeto que implementa este método recibirá el texto actual del campo de entrada.
     * @param listenerCancelado Callback que será invocado cuando se cancela el diálogo presionando el botón atrás. Opcional. Si está presente, el diálogo será cancelable y si se cancela, será enviado el valor actual del campo de texto al usuario. Si no está presente, entonces el diálogo no será "cancelable" y las únicas opciones para hacerlo desaparecer son presionar el botón OK o el botón Cancelar.
     */
    public void mostrar(AppCompatActivity contexto, String etiquetaFragment, int titulo, int nombreSesion, int hintTextInput, ListenerDialogoNuevaSesionOK listenerOK, ListenerDialogoNuevaSesionCancelar listenerCancelar, ListenerDialogoNuevaSesionCancelado listenerCancelado) {
        this.recursos = contexto.getResources();

        mostrar(contexto, etiquetaFragment, recursos.getString(titulo), recursos.getString(nombreSesion), recursos.getString(hintTextInput), listenerOK, listenerCancelar, listenerCancelado);
    }

    /**
     * Ejecutado cuando el diálogo es cancelado. No hace nada si no hay listener de cancelación. No
     * ocurre si DialogoNuevaSesion está configurado para no ser cancelado.
     *
     * TODO: este método, en conjunto con onCreateDialog, producen un warning que no he logrado
     * TODO: solucionar de ninguna manera. Ocurre sólo si hay un listener de evento "cancelado"
     * TODO: W/InputEventReceiver: Attempted to finish an input event but the input event receiver has already been disposed.
     * TODO: por ahora parece no afectar la funcionalidad de la aplicación, pero *debe* ser investigado
     *
     * @param dialog Referencia al diálogo siendo cancelado. Debe ser this.getDialog().
     */
    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);

        if (listenerCancelado != null) {
            listenerCancelado.dialogoNuevaSesionCancelado(viewNombreSesion.getText().toString().trim(), idVehiculoSeleccionado);
        }
    }

    /**
     * Método ejecutado automáticamente cuando se intenta mostrar este diálogo. Llamado por show(),
     * inicializa el estado interno de la UI del diálogo.
     *
     * Use el método mostrar() para mostrar el diálogo. Evite usar show().
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(contexto);
        Dialog dialogo;

        LayoutInflater inflater = contexto.getLayoutInflater();
        View layout = inflater.inflate(recursos.getLayout(R.layout.dialogo_nueva_sesion), null);
        builder.setView(layout);

        idVehiculoSeleccionado = -1;

        viewTitulo = (TextView) layout.findViewById(R.id.dialogoNuevaSesionTitulo);
        viewNombreSesion = (EditText) layout.findViewById(R.id.dialogoNuevaSesionNombre);
        viewVehiculo = (ListView) layout.findViewById(R.id.dialogoNuevaSesionVehiculos);

        // El campo de contenido siempre debe iniciar en blanco.
        viewNombreSesion.setText("");

        // No se usa un título si no lo hay.
        if (titulo != null && (titulo = titulo.trim()).compareTo("") != 0) {
            builder.setTitle(titulo);
        }

        // Se muestra el View del nombreSesion sólo si hay un nombreSesion.
        if (nombreSesion != null && (nombreSesion = nombreSesion.trim()).compareTo("") != 0) {
            viewTitulo.setText(nombreSesion);
            viewTitulo.setVisibility(View.VISIBLE);
        }

        // Se usa un hint para el View del contenido sólo si tenemos dicho hint.
        if (hintTextInput != null && (hintTextInput = hintTextInput.trim()).compareTo("") != 0) {
            viewNombreSesion.setHint(hintTextInput);
        }

        // Se asignan los listeners de botones "OK" y "Cancelar", y sus textos (en R)
        builder.setPositiveButton(R.string.dialogo_nombre_sesion_boton_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                listenerOK.dialogoNuevaSesionOK(viewNombreSesion.getText().toString().trim(), idVehiculoSeleccionado);
                dismiss(); // https://developer.android.com/guide/topics/ui/dialogs.html#DismissingADialog
            }
        });

        // Botón cancelar.
        builder.setNegativeButton(R.string.dialogo_nombre_sesion_boton_cancelar, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                listenerCancelar.dialogoNuevaSesionCancelar(viewNombreSesion.getText().toString().trim(), idVehiculoSeleccionado);
            }
        });

        /*
         * Si existe un listener para evento "cancelado", entonces se hace cancelable el diálogo.
         *
         * Nota: de acuerdo a la documentación de Android, en DialogFragment no se debe usar
         * directamente setOnCancelListener o setOnDismissListener en el diálogo. Se deben
         * sobreescribir los métodos onCancel y onDismiss de DialogFragment. Los listeners siempre
         * están habilitados, pero no harán nada si listenerCancelado es null.
         *
         * https://developer.android.com/reference/android/app/DialogFragment.html#onCreateDialog(android.os.Bundle)
         *
         * Adicionalmente, es necesario hacer que el diálogo sea cancelable si se toca una región
         * de la pantalla externa al diálogo. Esto se debe hacer después de llamar builder.create(),
         * ya que es una función disponible en Dialog y no de builders.
         */
        if (listenerCancelado != null) {
            /*
             * No se debe usar el método setCancelable del diálogo (en el builder o no) directamente.
             * https://developer.android.com/reference/android/app/DialogFragment.html#setCancelable(boolean)
             */
            setCancelable(true);
        } else {
            setCancelable(false);
        }

        // El diálogo que será regresado.
        dialogo = builder.create();

        // Método no hace parte de builder pero si de Dialog. No disponible en DialogFragment.
        if (listenerCancelado != null) {
            dialogo.setCanceledOnTouchOutside(true);
        } else {
            dialogo.setCanceledOnTouchOutside(false);
        }

        viewVehiculo.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                idVehiculoSeleccionado = position;
            }
        });

        return dialogo;
    }
}
