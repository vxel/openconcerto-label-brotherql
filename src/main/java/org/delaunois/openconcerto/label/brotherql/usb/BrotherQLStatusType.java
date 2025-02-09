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
 * Brother QL status types
 * 
 * @author Cedric de Launois
 */
public enum BrotherQLStatusType {
    
    READY((byte) 0x00, Rx.msg("statustype.ready")),
    PRINTING_COMPLETED((byte) 0x01, Rx.msg("statustype.completed")),
    ERROR_OCCURRED((byte) 0x02, Rx.msg("statustype.error")),
    NOTIFICATION((byte) 0x05, Rx.msg("statustype.notification")),
    PHASE_CHANGE((byte) 0x06, Rx.msg("statustype.phasechange")),

    // Custom status
    PRINTER_UNAVAILABLE((byte)0xF0, Rx.msg("statustype.unavailable")),
    PRINTER_NOT_CONNECTED((byte)0xF1, Rx.msg("statustype.notconnected"));
    
    private static final Map<Byte, BrotherQLStatusType> CODE_MAP = new HashMap<>();

    static {
        for (BrotherQLStatusType mt : BrotherQLStatusType.values()) {
            CODE_MAP.put(mt.code, mt);
        }
    }

    public final byte code;
    public final String message;

    BrotherQLStatusType(byte code, String message) {
        this.code = code;
        this.message = message;
    }

    public static BrotherQLStatusType fromCode(byte code) {
        return CODE_MAP.get(code);
    }

    @SuppressWarnings("unused")
    public String getMessage() {
        return message;
    }
}
