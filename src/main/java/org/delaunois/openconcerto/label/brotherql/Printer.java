package org.delaunois.openconcerto.label.brotherql;

import java.net.URI;

/**
 * Date: 14/03/25
 *
 * @author Cedric de Launois
 */
@SuppressWarnings("unused")
public class Printer {
    
    private final String brand;
    private final String name;
    private final String serial;
    private final String uri;
    
    public Printer(String uri) {
        this.uri = uri;
        URI deviceUri = URI.create(uri);
        this.brand = deviceUri.getHost();
        this.name = deviceUri.getPath().substring(1);
        this.serial = deviceUri.getQuery().split("=")[1];
    }

    public String getBrand() {
        return brand;
    }

    public String getName() {
        return name;
    }

    public String getSerial() {
        return serial;
    }

    public String getUri() {
        return uri;
    }
    
    @Override
    public String toString() {
        return String.format("%s %s (SN:%s)", brand, name, serial);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Printer printer = (Printer) o;
        return uri.equals(printer.uri);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }
}
