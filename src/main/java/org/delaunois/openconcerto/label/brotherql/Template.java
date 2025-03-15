package org.delaunois.openconcerto.label.brotherql;

import org.delaunois.openconcerto.label.brotherql.graphicspl.GraphicsPL;

import java.util.HashSet;
import java.util.Set;

/**
 * A dto holding template properties
 *
 * @author Cedric de Launois
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Template {
    
    private final String name;
    private final String code;
    private final GraphicsPL graphicsPL;
    private final int width;
    private final int height;
    
    private Set<Integer> rotations = new HashSet<>();
    private boolean highdpi = false;
    
    public Template(String name, String code, GraphicsPL graphicsPL, int width, int height) {
        this.name = name;
        this.code = code;
        this.width = width;
        this.height = height;
        this.graphicsPL = graphicsPL;
        rotations.add(0);
        rotations.add(90);
        rotations.add(180);
        rotations.add(270);
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
    
    public GraphicsPL getGraphicsPL() {
        return graphicsPL;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Template template = (Template) o;
        return name.equals(template.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public Set<Integer> getRotations() {
        return rotations;
    }

    public Template addRotation(int rotation) {
        rotations.add(rotation);
        return this;
    }

    public boolean isHighdpi() {
        return highdpi;
    }

    public Template setHighdpi(boolean highdpi) {
        this.highdpi = highdpi;
        return this;
    }
}
