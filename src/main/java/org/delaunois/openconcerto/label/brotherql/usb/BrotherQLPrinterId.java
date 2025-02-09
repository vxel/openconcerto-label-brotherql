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
 * Brother QL printer definitions
 * 
 * @author Cedric de Launois
 */
public enum BrotherQLPrinterId {
    
    QL_500("Brother QL-500", 0x2015, true, 295, 11811, true),
    QL_550("Brother QL-550", 0x2016, false, 295, 11811, true),
    QL_560("Brother QL-560", 0x2027, false, 295, 11811, true),
    QL_570("Brother QL-570", 0x2028, false, 150, 11811, true),
    QL_580N("Brother QL-580N", 0x2029, false, 150, 11811, false),
    QL_650TD("Brother QL-650TD", 0x201B, true, 295, 11811, false),
    QL_700_P("Brother QL-700", 0x2042, false, 150, 11811, true),
    QL_700_M("Brother QL-700M", 0x2049, false, 150, 11811, true),
    QL_1050("Brother QL-1050", 0x2020, true, 295, 35433, false),
    QL_1060N("Brother QL-1060N", 0x202A, true, 295, 35433, false),
    UNKNOWN(Rx.msg("printerid.unknown"), 0x000, false, 0, 0, true);

    private static final Map<Integer, BrotherQLPrinterId> CODE_MAP = new HashMap<>();

    static {
        for (BrotherQLPrinterId mt : BrotherQLPrinterId.values()) {
            CODE_MAP.put(mt.code, mt);
        }
    }

    public final String name;
    public final int code;
    public final boolean allowsFeedMargin;
    public final int clMinLengthPx; 
    public final int clMaxLengthPx; 
    public final boolean rasterOnly;

    BrotherQLPrinterId(String name, Integer code, boolean allowsFeedMargin, int clMinLengthPx, int clMaxLengthPx, boolean rasterOnly) {
        this.code = code;
        this.allowsFeedMargin = allowsFeedMargin;
        this.clMinLengthPx = clMinLengthPx;
        this.clMaxLengthPx = clMaxLengthPx;
        this.name = name;
        this.rasterOnly = rasterOnly;
    }

    public static BrotherQLPrinterId fromCode(int code) {
        BrotherQLPrinterId printerId = CODE_MAP.get(code);
        return printerId == null ? UNKNOWN : printerId;
    }

    @Override
    public String toString() {
        return name;
    }
}
