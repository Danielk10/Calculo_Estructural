package com.diamon.civil.structural.engine;

import java.util.ArrayList;
import java.util.List;

public class StructuralModel {
    public enum SupportType {
        FREE,       // Free / Unconstrained
        FIXED,      // Fixed / Encastre (Ux=0, Uy=0, Uz=0, Rx=0, Ry=0, Rz=0)
        PINNED,     // Pinned / Hinged (Ux=0, Uy=0, Uz=0)
        ROLLER      // Roller Support (Uy=0)
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

    /**
     * Represents DOF releases at one end of an element with optional semi-rigid stiffness values.
     */
    public static class EndRelease {
        public boolean m11Released = false;
        public boolean m22Released = false;
        public boolean m33Released = false;
        public double m11Stiffness = -1.0;
        public double m22Stiffness = -1.0;
        public double m33Stiffness = -1.0;

        public boolean hasAnyRelease() {
            return m11Released || m22Released || m33Released;
        }

        public boolean isFullyContinuous() {
            return !hasAnyRelease();
        }

        public EndRelease copy() {
            EndRelease clone = new EndRelease();
            clone.m11Released = this.m11Released;
            clone.m22Released = this.m22Released;
            clone.m33Released = this.m33Released;
            clone.m11Stiffness = this.m11Stiffness;
            clone.m22Stiffness = this.m22Stiffness;
            clone.m33Stiffness = this.m33Stiffness;
            return clone;
        }
    }

    /**
     * Represents a concentrated force or moment applied at a specific position along an element.
     */
    public static class ElementPointLoad {
        public int elementId;
        public double position;
        public double fy;
        public double fx;
        public double mz;

        public ElementPointLoad(int elementId, double position, double fy, double fx, double mz) {
            this.elementId = elementId;
            this.position = position;
            this.fy = fy;
            this.fx = fx;
            this.mz = mz;
        }

        public ElementPointLoad copy() {
            return new ElementPointLoad(elementId, position, fy, fx, mz);
        }
    }

    /**
     * Represents a distributed load along part or all of an element.
     */
    public static class ElementDistLoad {
        public int elementId;
        public double startPos;
        public double endPos;
        public double w1;
        public double w2;
        public double wx1;
        public double wx2;

        public ElementDistLoad(int elementId, double startPos, double endPos, double w1, double w2) {
            this(elementId, startPos, endPos, w1, w2, 0.0, 0.0);
        }

        public ElementDistLoad(int elementId, double startPos, double endPos, double w1, double w2, double wx1, double wx2) {
            this.elementId = elementId;
            this.startPos = startPos;
            this.endPos = endPos;
            this.w1 = w1;
            this.w2 = w2;
            this.wx1 = wx1;
            this.wx2 = wx2;
        }

        public boolean isUniform() {
            return w1 == w2;
        }

        public boolean isFullLength() {
            return startPos <= 0.001 && endPos >= 0.999;
        }

        public ElementDistLoad copy() {
            return new ElementDistLoad(elementId, startPos, endPos, w1, w2, wx1, wx2);
        }
    }

    public static class Element {
        public int id;
        public int node1Id, node2Id;
        public String sectionName;
        public String materialName;
        
        public EndRelease releaseStart = new EndRelease();
        public EndRelease releaseEnd = new EndRelease();
        public List<ElementPointLoad> pointLoads = new ArrayList<>();
        public List<ElementDistLoad> distLoads = new ArrayList<>();

        public Element(int id, int n1, int n2, String section, String material) {
            this.id = id; this.node1Id = n1; this.node2Id = n2;
            this.sectionName = section; this.materialName = material;
        }

        public boolean hasReleases() {
            return releaseStart.hasAnyRelease() || releaseEnd.hasAnyRelease();
        }

        public Element copy() {
            Element clone = new Element(id, node1Id, node2Id, sectionName, materialName);
            clone.releaseStart = this.releaseStart.copy();
            clone.releaseEnd = this.releaseEnd.copy();
            for (ElementPointLoad pLoad : this.pointLoads) {
                clone.pointLoads.add(pLoad.copy());
            }
            for (ElementDistLoad dLoad : this.distLoads) {
                clone.distLoads.add(dLoad.copy());
            }
            return clone;
        }
    }

    public static class Load {
        public int nodeId;
        public double fx, fy, fz, mz;

        public Load(int nodeId, double fx, double fy, double fz) {
            this.nodeId = nodeId; this.fx = fx; this.fy = fy; this.fz = fz; this.mz = 0.0;
        }
        
        public Load(int nodeId, double fx, double fy, double fz, double mz) {
            this.nodeId = nodeId; this.fx = fx; this.fy = fy; this.fz = fz; this.mz = mz;
        }

        public Load copy() {
            return new Load(nodeId, fx, fy, fz, mz);
        }
    }

    public static class Panel {
        public int id;
        public List<Integer> nodeIds = new ArrayList<>();
        public double thickness = 0.15;
        public String materialName = "Concrete";
        public String elementType = "S4R";

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

    /**
     * Represents a user-defined custom material.
     */
    public static class CustomMaterial {
        public String name;
        public double E;
        public double nu;
        public double rho;
        public double yieldStrength;
        public double fc;
        
        public CustomMaterial(String name, double E, double nu, double rho, double yieldStrength, double fc) {
            this.name = name;
            this.E = E;
            this.nu = nu;
            this.rho = rho;
            this.yieldStrength = yieldStrength;
            this.fc = fc;
        }
        
        public CustomMaterial copy() {
            return new CustomMaterial(name, E, nu, rho, yieldStrength, fc);
        }
    }

    public List<Node> nodes = new ArrayList<>();
    public List<Element> elements = new ArrayList<>();
    public List<Panel> panels = new ArrayList<>();
    public List<Load> loads = new ArrayList<>();
    public List<ElementPointLoad> elementPointLoads = new ArrayList<>();
    public List<ElementDistLoad> elementDistLoads = new ArrayList<>();
    public List<CustomMaterial> customMaterials = new ArrayList<>();

    public StructuralModel copy() {
        StructuralModel clone = new StructuralModel();
        for (Node n : nodes) clone.nodes.add(n.copy());
        for (Element e : elements) clone.elements.add(e.copy());
        for (Panel p : panels) clone.panels.add(p.copy());
        for (Load l : loads) clone.loads.add(l.copy());
        for (ElementPointLoad epl : elementPointLoads) clone.elementPointLoads.add(epl.copy());
        for (ElementDistLoad edl : elementDistLoads) clone.elementDistLoads.add(edl.copy());
        for (CustomMaterial cm : customMaterials) clone.customMaterials.add(cm.copy());
        return clone;
    }
}
