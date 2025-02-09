package org.delaunois.openconcerto.label.brotherql.graphicspl;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public abstract class GPLRenderer {

	private static final Logger LOGGER = Logger.getLogger(GPLRenderer.class.getName());

	public static final int ALIGN_LEFT = 0;
	public static final int ALIGN_RIGHT = 1;
	public static final int ALIGN_CENTER = 2;

	public static final int BARCODE_EAN8 = 0;
	public static final int BARCODE_EAN13 = 1;
	public static final int BARCODE_CODE128 = 2;
	public static final int BARCODE_CODE128_GS1 = 3;
	public static final int BARCODE_DATAMATRIX = 4;
	public static final int BARCODE_QRCODE = 5;
	private final float ratio;

	public GPLRenderer(float ratio) {
		this.ratio = ratio;
	}

	@SuppressWarnings("unused")
	public GPLRenderer() {
		this.ratio = 1.0f;
	}

	@SuppressWarnings("unused")
	public float getRatio() {
		return this.ratio;
	}

	public void render(GraphicsPL graphicsPL) throws IOException {
		Document doc = graphicsPL.getDocument();
		final int width = Integer.parseInt(doc.getDocumentElement().getAttribute("width"));
		NodeList nodeList = doc.getFirstChild().getChildNodes();
		int size = nodeList.getLength();
		for (int i = 0; i < size; i++) {
			if (nodeList.item(i).getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) nodeList.item(i);
				renderNode(graphicsPL, element, width);
			}
		}
	}

	private void renderNode(GraphicsPL graphicsPL, Element element, int width) throws IOException {
		String name = element.getNodeName();
        switch (name) {
            case "text":
				renderNodeText(element, width);
				break;
			case "rectangle":
				renderNodeRectangle(element);
				break;
			case "image":
				renderNodeImage(graphicsPL, element);
				break;
			case "barcode":
				RenderNodeBarcode(element);
				break;
			default:
                throw new IllegalStateException("unsupported primitive : " + name);
        }
	}

	private void RenderNodeBarcode(Element element) {
		int x = Math.round(this.ratio * Integer.parseInt(element.getAttribute("x")));
		int y = Math.round(this.ratio * Integer.parseInt(element.getAttribute("y")));
		int h = 0;

		if (element.hasAttribute("height")) {
			h = Math.round(this.ratio * Integer.parseInt(element.getAttribute("height")));
		}
		String type = element.getAttribute("type");
		String code = element.getTextContent();
		int t;
        switch (type) {
            case "ean8":
                t = BARCODE_EAN8;
                break;
            case "ean13":
                t = BARCODE_EAN13;
                break;
            case "ean128":
                t = BARCODE_CODE128;
                break;
            case "gs1":
                t = BARCODE_CODE128_GS1;
                break;
            case "datamatrix":
                t = BARCODE_DATAMATRIX;
                if (h != 0) {
					LOGGER.info("ignoring datamatrix height attribute");
                }
                break;
            case "qrcode":
                t = BARCODE_QRCODE;
                break;
            default:
                throw new IllegalArgumentException("unsupported barcode type : " + type);
        }

		int fontSize = Math.round(this.ratio * 8);
		if (element.hasAttribute("fontsize")) {
			fontSize = Math.round(this.ratio * Integer.parseInt(element.getAttribute("fontsize")));
		}
		int moduleWidth = Math.round(this.ratio);
		if (element.hasAttribute("modulewidth")) {
			moduleWidth = Math.round(this.ratio * Integer.parseInt(element.getAttribute("modulewidth")));
		}
		
		int rotate = element.getAttribute("rotate").isEmpty() ? 0 
				: Integer.parseInt(element.getAttribute("rotate"));
		
		drawBarcode(x, y, h, t, code, moduleWidth, fontSize, rotate);
	}

	private void renderNodeImage(GraphicsPL graphicsPL, Element element) throws IOException {
		String fileName = element.getAttribute("file");
		int x = Math.round(this.ratio * Integer.parseInt(element.getAttribute("x")));
		int y = Math.round(this.ratio * Integer.parseInt(element.getAttribute("y")));
		final File input = new File(graphicsPL.getImageDir(), fileName);
		if (input.exists()) {
			BufferedImage img = ImageIO.read(input);
			int w = Math.round(this.ratio * img.getWidth());
			int h = Math.round(this.ratio * img.getHeight());
			if (element.hasAttribute("width")) {
				w = Math.round(this.ratio * Integer.parseInt(element.getAttribute("width")));
			}
			if (element.hasAttribute("height")) {
				h = Math.round(this.ratio * Integer.parseInt(element.getAttribute("height")));
			}
			
			int rotate = element.getAttribute("rotate").isEmpty() ? 0 
					: Integer.parseInt(element.getAttribute("rotate"));
			
			drawImage(x, y, w, h, img, rotate);
		} else {
			throw new IllegalStateException(input.getAbsolutePath() + " not found");
		}
	}

	private void renderNodeRectangle(Element element) {
		int x = Math.round(this.ratio * Integer.parseInt(element.getAttribute("x")));
		int y = Math.round(this.ratio * Integer.parseInt(element.getAttribute("y")));
		int w = Math.round(this.ratio * Integer.parseInt(element.getAttribute("width")));
		int h = Math.round(this.ratio * Integer.parseInt(element.getAttribute("height")));
		Color color = Color.BLACK;
		if (element.hasAttribute("color")) {
			color = Color.decode(element.getAttribute("color"));
		}

		if (element.hasAttribute("fill") && element.getAttribute("fill").equals("true")) {
			fillRectangle(x, y, w, h, color);
		} else {
			int lineWidth = Math.round(this.ratio);
			if (element.hasAttribute("linewidth")) {
				lineWidth = Math.round(this.ratio * Integer.parseInt(element.getAttribute("linewidth")));
			}
			drawRectangle(x, y, w, h, color, lineWidth);
		}
	}

	private void renderNodeText(Element element, int width) {
		int x = Math.round(this.ratio * Integer.parseInt(element.getAttribute("x")));
		int y = Math.round(this.ratio * Integer.parseInt(element.getAttribute("y")));
		String txt = element.getTextContent();
		Color color = Color.BLACK;
		if (element.hasAttribute("color")) {
			color = Color.decode(element.getAttribute("color"));
		}
		int fontSize = Math.round(this.ratio * Integer.parseInt(element.getAttribute("fontsize")));
		String fontName = element.getAttribute("font");
		int maxWidth = element.getAttribute("maxwidth").isEmpty() ? width
				: Integer.parseInt(element.getAttribute("maxwidth"));
		boolean wrap = Boolean.parseBoolean(element.getAttribute("wrap"));

		int align = ALIGN_LEFT;
		if (element.hasAttribute("align")) {
			if (element.getAttribute("align").equals("right")) {
				align = ALIGN_RIGHT;
			} else if (element.getAttribute("align").equals("center")) {
				align = ALIGN_CENTER;
			}
		}
		
		int rotate = element.getAttribute("rotate").isEmpty() ? 0 
				: Integer.parseInt(element.getAttribute("rotate"));

		if (!element.hasAttribute("visible") || element.getAttribute("visible").equals("true")) {
			drawText(x, y, txt, align, fontName, fontSize, color, (int) (this.ratio * maxWidth), wrap, rotate);
		}
	}

	public abstract void drawText(int x, int y, String text, int align, String fontName, int fontSize, Color color,
			int maxWidth, boolean wrap, int rotate);

	public abstract void drawImage(int x, int y, int w, int h, BufferedImage img, int rotate);

	public abstract void fillRectangle(int x, int y, int w, int h, Color color);

	public abstract void drawRectangle(int x, int y, int w, int h, Color color, int lineWidth);

	public abstract void drawBarcode(int x, int y, int h, int type, String code, int moduleWidth, int fontSize, int rotate);

	public static List<String> wrapString(String toCut, FontMetrics fm, int width) {
		String[] tokens = toCut.split("(?<=\\s|-)");
		List<String> cutLines = new ArrayList<>(2);

		for (String token : tokens) {
			if (cutLines.isEmpty()) {
				cutLines.add(token);
			} else {
				String lastLine = cutLines.get(cutLines.size() - 1);

				if (fm.stringWidth(lastLine) + fm.stringWidth(token.trim()) >= width) {
					cutLines.add(token);
				} else {
					cutLines.set(cutLines.size() - 1, lastLine + token);
				}
			}
		}

		List<String> result = new ArrayList<>(cutLines.size());

		for (String l : cutLines) {
			result.add(l.trim());
		}

		return result;
	}
}
