package com.diamon.civil.structural.engine;

import java.util.ArrayList;
import java.util.List;

public class StructuralModel {
    public enum SupportType {
        FREE,       // Libre
        FIXED,      // Empotrado (Ux=0, Uy=0, Uz=0, Rx=0, Ry=0, Rz=0)
        PINNED,     // Articulado (Ux=0, Uy=0, Uz=0)
        ROLLER      // Apoyo Simple / Rodillo (Uy=0)
    }

    public static class Node {
        public int id;
        public double x, y, z;
        public SupportType supportType = SupportType.FREE;

        public Node(int id, double x, double y, double z) {
            this.id = id; this.x = x; this.y = y; this.z = z;
            this.supportType = SupportType.FREE;
        }

        public Node(int id, double x, double y, double z, SupportType supportType) {
            this.id = id; this.x = x; this.y = y; this.z = z;
            this.supportType = supportType != null ? supportType : SupportType.FREE;
        }

        public Node copy() {
            return new Node(id, x, y, z, supportType);
        }
    }

    public static class Element {
        public int id;
        public int node1Id, node2Id;
        public String sectionName;
        public String materialName;
        public Element(int id, int n1, int n2, String section, String material) {
            this.id = id; this.node1Id = n1; this.node2Id = n2;
            this.sectionName = section; this.materialName = material;
        }

        public Element copy() {
            return new Element(id, node1Id, node2Id, sectionName, materialName);
        }
    }

    public static class Load {
        public int nodeId;
        public double fx, fy, fz;
        public Load(int nodeId, double fx, double fy, double fz) {
            this.nodeId = nodeId; this.fx = fx; this.fy = fy; this.fz = fz;
        }

        public Load copy() {
            return new Load(nodeId, fx, fy, fz);
        }
    }

    public static class Panel {
        public int id;
        public List<Integer> nodeIds = new ArrayList<>();
        public double thickness = 0.15; // in meters (e.g. 15 cm)
        public String materialName = "Concrete";
        public String elementType = "S4R"; // "S4R" (Shell / Slab), "CPS4" (Shear Wall / Plane Stress), "CPE4" (Plane Strain)

        public Panel(int id, List<Integer> nodeIds, double thickness, String material, String elementType) {
            this.id = id;
            if (nodeIds != null) this.nodeIds.addAll(nodeIds);
            this.thickness = thickness > 0 ? thickness : 0.15;
            this.materialName = material != null ? material : "Concrete";
            this.elementType = elementType != null ? elementType : "S4R";
        }

        public Panel copy() {
            return new Panel(id, new ArrayList<>(nodeIds), thickness, materialName, elementType);
        }
    }

    public List<Node> nodes = new ArrayList<>();
    public List<Element> elements = new ArrayList<>();
    public List<Panel> panels = new ArrayList<>();
    public List<Load> loads = new ArrayList<>();

    public StructuralModel copy() {
        StructuralModel clone = new StructuralModel();
        for (Node n : nodes) clone.nodes.add(n.copy());
        for (Element e : elements) clone.elements.add(e.copy());
        for (Panel p : panels) clone.panels.add(p.copy());
        for (Load l : loads) clone.loads.add(l.copy());
        return clone;
    }
}

