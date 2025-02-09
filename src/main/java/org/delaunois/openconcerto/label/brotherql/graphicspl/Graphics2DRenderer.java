package org.delaunois.openconcerto.label.brotherql.graphicspl;

import uk.org.okapibarcode.backend.Code128;
import uk.org.okapibarcode.backend.DataMatrix;
import uk.org.okapibarcode.backend.Ean;
import uk.org.okapibarcode.backend.QrCode;
import uk.org.okapibarcode.backend.Symbol;
import uk.org.okapibarcode.output.Java2DRenderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class Graphics2DRenderer extends GPLRenderer {

    private final Graphics2D g;

    public Graphics2DRenderer(Graphics2D g, float ratio) {
        super(ratio);
        this.g = g;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    @Override
    public void drawText(int x, int y, String text, int align, String fontName, int fontSize, Color color, 
                         int maxWidth, boolean wrap, int rotate) {
        Font font = new Font(fontName, Font.PLAIN, fontSize);
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        final int height = fm.getAscent() - fm.getDescent();
        y += height;

        int lineHeight = 0;
        List<String> wrapText = new ArrayList<>();
        if (wrap) {
            lineHeight = fm.getAscent() + fm.getDescent() + fm.getLeading();
            wrapText = GPLRenderer.wrapString(text, fm, maxWidth);
        } else {
            wrapText.add(text);
        }

        AffineTransform affineTransform = new AffineTransform();
        affineTransform.rotate(Math.toRadians(rotate), x, y);
        g.setTransform(affineTransform);
        
        for (String s : wrapText) {
            if (align == ALIGN_RIGHT) {
                int w = (int) g.getFontMetrics().getStringBounds(s, g).getWidth();
                g.drawString(s, x - w, y);
            } else if (align == ALIGN_CENTER) {
                int w = (int) (g.getFontMetrics().getStringBounds(s, g).getWidth() / 2D);
                g.drawString(s, x - w, y);
            } else {
                g.drawString(s, x, y);
            }

            y = y + lineHeight;
        }

    }

    @Override
    public void drawImage(int x, int y, int w, int h, BufferedImage img, int rotate) {
        g.drawImage(rotateImage(img, rotate), x, y, x + w, y + h, 0, 0, img.getWidth(), img.getHeight(), null);
    }

    @Override
    public void fillRectangle(int x, int y, int w, int h, Color color) {
        g.setColor(color);
        g.fillRect(x, y, w, h);
    }

    @Override
    public void drawRectangle(int x, int y, int w, int h, Color color, int lineWidth) {
        Stroke s = g.getStroke();
        g.setColor(color);
        if (lineWidth != 1) {
            g.setStroke(new BasicStroke(lineWidth));
        }
        g.drawRect(x, y, w, h);
        g.setStroke(s);
    }

    @Override
    public void drawBarcode(int x, int y, int h, int type, String code, int moduleWidth, int fontSize, int rotate) {
        if (code.isEmpty()) {
            return;
        }
        
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Symbol symbol = null;
        if (type == BARCODE_EAN8) {
            symbol = new Ean();
            ((Ean) symbol).setMode(Ean.Mode.EAN8);
            symbol.setBarHeight(h);
        } else if (type == BARCODE_EAN13) {
            symbol = new Ean();
            ((Ean) symbol).setMode(Ean.Mode.EAN13);
            symbol.setBarHeight(h);
            if (code.length() != 13) {
                return;
            }
            code = code.substring(0, 12);
        } else if (type == BARCODE_CODE128) {
            symbol = new Code128();
            symbol.setDataType(Symbol.DataType.GS1);
            symbol.setBarHeight(h);
        } else if (type == BARCODE_CODE128_GS1) {
            symbol = new Code128();
            symbol.setBarHeight(h);
        } else if (type == BARCODE_DATAMATRIX) {
            symbol = new DataMatrix();
        } else if (type == BARCODE_QRCODE) {
            symbol = new QrCode();
            symbol.setModuleWidth(1);
            symbol.setContent(code.trim());
            AffineTransform aT = g.getTransform();
            g.setTransform(AffineTransform.getTranslateInstance(x, y));
            Java2DRenderer renderer = new Java2DRenderer(g, Math.max(1, moduleWidth), 
                    uk.org.okapibarcode.graphics.Color.WHITE, uk.org.okapibarcode.graphics.Color.BLACK);
            renderer.render(symbol);
            g.setTransform(aT);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            return;
        }

        if (symbol == null) {
            return;
        }
        symbol.setModuleWidth(moduleWidth);
        symbol.setFontSize(fontSize);
        symbol.setContent(code.trim());

        AffineTransform aT = g.getTransform();
        AffineTransform affineTransform = new AffineTransform();
        affineTransform.rotate(Math.toRadians(rotate), x, y);
        affineTransform.translate(x, y);
        g.setTransform(affineTransform);

        Java2DRenderer renderer = new Java2DRenderer(g, 1, 
                uk.org.okapibarcode.graphics.Color.WHITE, uk.org.okapibarcode.graphics.Color.BLACK);
        renderer.render(symbol);

        g.setTransform(aT);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

    }

    private static BufferedImage rotateImage(BufferedImage buffImage, double angle) {
        double radian = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(radian));
        double cos = Math.abs(Math.cos(radian));
    
        int width = buffImage.getWidth();
        int height = buffImage.getHeight();
    
        int nWidth = (int) Math.floor((double) width * cos + (double) height * sin);
        int nHeight = (int) Math.floor((double) height * cos + (double) width * sin);
    
        BufferedImage rotatedImage = new BufferedImage(
                nWidth, nHeight, BufferedImage.TYPE_INT_ARGB);
    
        Graphics2D graphics = rotatedImage.createGraphics();
    
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    
        graphics.translate((nWidth - width) / 2, (nHeight - height) / 2);
        // rotation around the center point
        graphics.rotate(radian, width / 2.0, height / 2.0);
        graphics.drawImage(buffImage, 0, 0, null);
        graphics.dispose();
    
        return rotatedImage;
    }
}
