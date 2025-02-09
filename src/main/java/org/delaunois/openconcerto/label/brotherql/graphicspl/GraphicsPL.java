package org.delaunois.openconcerto.label.brotherql.graphicspl;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GraphicsPL {
    
    private static final Logger LOGGER = Logger.getLogger(GraphicsPL.class.getName());

    private Document doc;
    private File imgDir;
    private boolean showSetupInfo;

    @SuppressWarnings("unused")
    public Printable createPrintable() {
        return createPrintable(1, 1, 0, 0, false);
    }

    public Printable createPrintable(int rows, int columns, int lMargin, int tMargin, boolean ignorePrinterMargins) {
        final Element root = this.doc.getDocumentElement();
        int dpi = 300;
        if (root.hasAttribute("dpi")) {
            dpi = Integer.parseInt(root.getAttribute("dpi"));
        }
        float printRatio = 1f;
        if (root.hasAttribute("printratio")) {
            printRatio = Float.parseFloat(root.getAttribute("printratio"));
        }
        return createPrintable(dpi, printRatio, rows, columns, lMargin, tMargin, ignorePrinterMargins);
    }

    public Printable createPrintable(int dpi, float printRatio, int rows, int columns, int lMargin, int tMargin, boolean ignorePrinterMargins) {
        return (graphics, pf, pageIndex) -> {
            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            final Element root = GraphicsPL.this.doc.getDocumentElement();
            final int width = Math.round(printRatio * Integer.parseInt(root.getAttribute("width")));
            final int height = Math.round(printRatio * Integer.parseInt(root.getAttribute("height")));
            final Graphics2D g2d = (Graphics2D) graphics;
            float ratio = (printRatio * dpi) / 72f;
            try {
                int centerX = (int) pf.getWidth() / 3;
                int centerY = (int) pf.getHeight() / 3;
                final BufferedImage img = createImage(ratio);
                int x = lMargin;
                int y = tMargin;
                if (!ignorePrinterMargins) {
                    x += (int) Math.round(pf.getImageableX());
                    y += (int) Math.round(pf.getImageableY());
                }
                String str = "";
                if (rows == 1 && columns == 1) {
                    g2d.drawImage(img, x, y, width, height, null);
                    if (GraphicsPL.this.showSetupInfo) {
                        str = "Drawing image : " + x + "," + y + " " + width + "x" + height;
                    }
                } else {

                    int cellWidth;
                    int cellHeight;
                    int dx = lMargin;
                    int dy = tMargin;
                    if (ignorePrinterMargins) {
                        cellWidth = (int) Math.round((pf.getWidth() - 2f * lMargin) / columns);
                        cellHeight = (int) Math.round((pf.getHeight() - 2f * tMargin) / rows);
                    } else {
                        cellWidth = (int) Math.round((pf.getImageableWidth() - 2f * lMargin) / columns);
                        cellHeight = (int) Math.round((pf.getImageableHeight() - 2f * tMargin) / rows);
                        dx += (int) pf.getImageableX();
                        dy += (int) pf.getImageableY();
                    }

                    float imgRatio = ((float) img.getWidth()) / img.getHeight();
                    float cellRatio = ((float) cellWidth) / cellHeight;
                    int imgWidth;
                    int imgHeight;
                    if (imgRatio > cellRatio) {
                        imgWidth = cellWidth;
                        imgHeight = (img.getHeight() * cellWidth) / img.getWidth();
                    } else {
                        imgWidth = (img.getWidth() * cellHeight) / img.getHeight();
                        imgHeight = cellHeight;
                    }
                    if (GraphicsPL.this.showSetupInfo) {
                        str = "Drawing image on cell [0,0] : " + dx + "," + dy + " " + imgWidth + "x" + imgHeight;
                        g2d.drawImage(img, dx, dy, imgWidth, imgHeight, null);
                        float[] dash = {5F, 5F};
                        Stroke dashedStroke = new BasicStroke(1.5F, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 3F, dash, 0F);
                        g2d.setStroke(dashedStroke);
                        for (int i = 0; i < rows; i++) {
                            for (int j = 0; j < columns; j++) {
                                g2d.drawRect(j * cellWidth + dx, i * cellHeight + dy, imgWidth, imgHeight);
                            }
                        }

                    } else {

                        for (int i = 0; i < rows; i++) {
                            for (int j = 0; j < columns; j++) {
                                g2d.drawImage(img, j * cellWidth + dx, i * cellHeight + dy, imgWidth, imgHeight, null);
                            }
                        }

                    }
                }
                if (GraphicsPL.this.showSetupInfo) {

                    g2d.setFont(g2d.getFont().deriveFont(16f).deriveFont(Font.BOLD));

                    for (int i = 1; i < 10; i++) {
                        final int s = i * 2;
                        g2d.setColor(Color.WHITE);
                        g2d.fillRect(centerX - 2, centerY - s - 2, s + 4, s + 4);
                        g2d.setColor(Color.BLACK);
                        g2d.fillRect(centerX, centerY - s, s, s);
                        drawString(g2d, String.valueOf(s), centerX + 30, centerY);
                        centerY += 24 + s;
                    }

                    drawString(g2d, str, centerX, centerY);
                    centerY += 24;
                    drawString(g2d, "DPI : " + dpi, centerX, centerY);
                    centerY += 24;
                    drawString(g2d, "Print ratio : " + printRatio, centerX, centerY);
                    centerY += 24;
                    drawString(g2d, "Number of rows : " + rows, centerX, centerY);
                    centerY += 24;
                    drawString(g2d, "Number of columns : " + columns, centerX, centerY);
                    centerY += 24;
                    drawString(g2d, "Margin left : " + lMargin, centerX, centerY);
                    centerY += 24;
                    drawString(g2d, "Margin top : " + tMargin, centerX, centerY);
                    centerY += 24;
                    drawString(g2d, "Ignore printer margin : " + ignorePrinterMargins, centerX, centerY);
                    centerY += 24;
                    drawString(g2d, "Image size : " + img.getWidth() + "x" + img.getHeight(), centerX, centerY);

                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "IO exception: ", e);                
                throw new PrinterException(e.getMessage());
            }
            return Printable.PAGE_EXISTS;
        };
    }

    protected void drawString(Graphics2D g2d, String string, int x, int y) {
        g2d.setColor(Color.WHITE);
        Rectangle2D r = g2d.getFontMetrics().getStringBounds(string, g2d);

        g2d.fillRect(x - 2, y + 2 - (int) r.getHeight(), (int) r.getWidth() + 4, (int) r.getHeight() + 2);
        g2d.setColor(Color.BLACK);
        g2d.drawString(string, x, y);
    }

    public BufferedImage createImage(float ratio) throws IOException {
        final Element root = this.doc.getDocumentElement();
        final int width = Math.round(ratio * Integer.parseInt(root.getAttribute("width")));
        final int height = Math.round(ratio * Integer.parseInt(root.getAttribute("height")));
        final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = (Graphics2D) img.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        final Graphics2DRenderer renderer = new Graphics2DRenderer(graphics, ratio);
        renderer.render(this);
        graphics.dispose();
        return img;
    }

    public Document getDocument() {
        return this.doc;
    }

    public void load(File file) throws ParserConfigurationException, SAXException, IOException {
        load(new String(Files.readAllBytes(file.toPath())), file.getParentFile());
    }

    public void load(String str, File imageDir) throws ParserConfigurationException, SAXException, IOException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (Exception e) {
            // nothing
        }
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (Exception e) {
            // nothing
        }
        final DocumentBuilder builder = factory.newDocumentBuilder();
        final ByteArrayInputStream input = new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
        this.doc = builder.parse(input);
        this.doc.getDocumentElement().normalize();
        this.imgDir = imageDir;
    }

    public File getImageDir() {
        return this.imgDir;
    }

    @SuppressWarnings("unused")
    public void setImageDir(File dir) {
        this.imgDir = dir;
    }

    @SuppressWarnings("unused")
    public void setShowSetupInfo(boolean b) {
        this.showSetupInfo = b;

    }
    
}
