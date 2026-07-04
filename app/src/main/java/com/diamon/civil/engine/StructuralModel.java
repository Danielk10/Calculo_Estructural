package com.diamon.civil.engine;

import java.util.ArrayList;
import java.util.List;

public class StructuralModel {
    public static class Node {
        public int id;
        public double x, y, z;
        public Node(int id, double x, double y, double z) {
            this.id = id; this.x = x; this.y = y; this.z = z;
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
    }

    public List<Node> nodes = new ArrayList<>();
    public List<Element> elements = new ArrayList<>();
}
