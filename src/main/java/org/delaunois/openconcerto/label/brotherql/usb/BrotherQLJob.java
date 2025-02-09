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

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * A job for a Brother QL printer.
 * The job must be completed with the printerId, the media and the images.
 *
 * @author Cedric de Launois
 */
public class BrotherQLJob {
    
    private boolean autocut;
    private int cutEach = 1;
    private BrotherQLPrinterId printerId;
    private BrotherQLMedia media;
    private List<BufferedImage> images;
    private int feedAmount;
    private int delay = 0;

    public BrotherQLPrinterId getPrinterId() {
        return printerId;
    }

    public BrotherQLJob setPrinterId(BrotherQLPrinterId printerId) {
        this.printerId = printerId;
        return this;
    }

    public boolean isAutocut() {
        return autocut;
    }

    public BrotherQLJob setAutocut(boolean autocut) {
        this.autocut = autocut;
        return this;
    }

    public int getCutEach() {
        return cutEach;
    }

    public BrotherQLJob setCutEach(int cutEach) {
        this.cutEach = cutEach;
        return this;
    }

    public BrotherQLMedia getMedia() {
        return media;
    }

    public BrotherQLJob setMedia(BrotherQLMedia media) {
        this.media = media;
        return this;
    }
    
    @SuppressWarnings("unused")
    public BrotherQLJob setFeedAmount(int feedAmount) {
        if (BrotherQLPrinterId.QL_700_P.allowsFeedMargin) {
            this.feedAmount = feedAmount & 0xFFFF;
        }
        return this;
    }

    /** 
     * Margin in dots. 
     * In case of using QL-550/560/570/580N/700, specify 35dots. 0 for DC
     */
    public int getFeedAmount() {
        if (BrotherQLPrinterId.QL_700_P.allowsFeedMargin) {
            return this.feedAmount;
        } else if (this.media.mediaType.equals(BrotherQLMediaType.DIE_CUT_LABEL) ) {
            return 0;
        } else {
            return 35;
        }
    }

    public List<BufferedImage> getImages() {
        return images;
    }

    public BrotherQLJob setImages(List<BufferedImage> images) {
        this.images = images;
        return this;
    }

    public int getDelay() {
        return delay;
    }

    public BrotherQLJob setDelay(int delay) {
        this.delay = delay;
        return this;
    }
}
