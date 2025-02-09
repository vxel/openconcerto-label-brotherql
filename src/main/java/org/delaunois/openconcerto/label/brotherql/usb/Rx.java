package org.delaunois.openconcerto.label.brotherql.usb;

import java.util.ResourceBundle;

/**
 * Utility class for accessing message resource bundle.
 *
 * @author Cedric de Launois
 */
public class Rx {
    
    private static final ResourceBundle RX = ResourceBundle
            .getBundle("org.delaunois.openconcerto.label.brotherql.usb.BrotherQLResource");

    public static String msg(String key) {
        return RX.getString(key);
    }
}
