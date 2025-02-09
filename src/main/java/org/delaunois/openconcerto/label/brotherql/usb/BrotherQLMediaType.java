/* 
 * USB Driver for printing with Brother QL printers.
 * 
 * Copyright (C) 2024 Cédric de Launois
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.delaunois.openconcerto.label.brotherql.usb;

import java.util.HashMap;
import java.util.Map;

/**
 * Brother QL media types
 * 
 * @author Cedric de Launois
 */
public enum BrotherQLMediaType {
    
    NO_MEDIA(Rx.msg("mediatype.nomedia"), (byte) 0x00),
    CONTINUOUS_LENGTH_TAPE(Rx.msg("mediatype.continuous"), (byte) 0x0A),
    DIE_CUT_LABEL(Rx.msg("mediatype.diecut"), (byte) 0x0B),
    UNKNOWN(Rx.msg("mediatype.unknown"), (byte) 0xFF);

    private static final Map<Byte, BrotherQLMediaType> CODE_MAP = new HashMap<>();

    static {
        for (BrotherQLMediaType mt : BrotherQLMediaType.values()) {
            CODE_MAP.put(mt.code, mt);
        }
    }

    public final String name;
    public final byte code;

    BrotherQLMediaType(String name, byte code) {
        this.name = name;
        this.code = code;
    }

    public static BrotherQLMediaType fromCode(byte code) {
        return CODE_MAP.get(code);
    }

    public String toString() {
        return name;
    }
}
