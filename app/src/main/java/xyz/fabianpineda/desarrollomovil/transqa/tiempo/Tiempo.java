package xyz.fabianpineda.desarrollomovil.transqa.tiempo;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class Tiempo {
    private static final Calendar calendario = GregorianCalendar.getInstance();

    public static final String ahoraLocalISO8601() {
        return (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(calendario.getTime());
    }
}
