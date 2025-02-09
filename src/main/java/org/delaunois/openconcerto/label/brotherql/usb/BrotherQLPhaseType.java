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
 * Brother QL printer phases
 * 
 * @author Cedric de Launois
 */
public enum BrotherQLPhaseType {
    
    WAITING_TO_RECEIVE((byte) 0x00),
    PHASE_PRINTING((byte) 0x01),
    UNKNOWN((byte) 0xFF);

    private static final Map<Byte, BrotherQLPhaseType> CODE_MAP = new HashMap<>();

    static {
        for (BrotherQLPhaseType mt : BrotherQLPhaseType.values()) {
            CODE_MAP.put(mt.code, mt);
        }
    }

    public final byte code;

    BrotherQLPhaseType(byte code) {
        this.code = code;
    }

    public static BrotherQLPhaseType fromCode(byte code) {
        return CODE_MAP.get(code);
    }
    
}
