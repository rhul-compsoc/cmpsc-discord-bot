package uk.co.hexillium.rhul.compsoc.commands.challenges;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class GraphChallenge extends Challenge {


    /*

        Challenges:
          [x]  - Search:
          [x]    - DFS => in which order would these nodes be visited if a Depth-first search was conducted from node X
          [x]    - BFS => in which order would these nodes be visited if a Breadth-first search was conducted from node X
          [x]  - Pathfinding:
          [x]     - Djikstra => what is the shortest path from X to Y and/or what is the distance of the shortest path from X to Y
 need to  [-]  - Max Flow:
 create a [-]     - Ford-Fulkerson => what is the maximum flow of the given symmetrical network
 digraph  [-]
 for this [-]
          [x]  - Minimum Spanning Tree
          [x]     - Total Cost => what is the sum of all weights of this graph's minimum spanning tree? (Prim's)


     */


    static List<String> letters = new ArrayList<>();
    protected DelaunayGraph graph;

    static {
        //S and T omitted for their usefulness as source and drain labels
        letters = Stream.of(("ABCDEFGH" + /* IJ + */ "K" + /* L + */ "MNOPQR" + /* "ST" */ "UVWXYZ").split("")).collect(Collectors.toList());
    }

    public GraphChallenge(int points){
        Collection<Node> nodes = generateNodeDistribution(points);
        this.graph = new DelaunayGraph(nodes);
    }

    public GraphChallenge(DelaunayGraph graph){
        this.graph = graph;
    }

    static synchronized Collection<Node> generateNodeDistribution(int points){
        Random random = ThreadLocalRandom.current();
        Collection<Node> nodes = new ArrayList<>();
        int max_attempts = 100_000;
        while (nodes.size() < points && max_attempts-- > 0){
            Node newnode = new Node(random.nextDouble() * 90 + 5, random.nextDouble() * 90 + 5, getLetter(nodes.size(), points));
            if (nodes.stream().anyMatch(node -> node.sqDistTo(newnode) < 175)) continue;

            nodes.add(newnode);
        }
        return nodes;
    }

    static private String getLetter(int i, int total){
        if (total < letters.size()) return letters.get(i);
        else return Integer.toString(i, 32);
    }

    @Override
    public String getDebugInformation() {
        return "Graph: {" + graph.getDebugInfo() + "}";
    }

    @Override
    public BufferedImage getImage() {
        return graph.genImage(true, false);
    }
}

abstract class Graph {
    protected ArrayList<Node> nodes = new ArrayList<>();

    protected void drawLine(double x1, double y1, double x2, double y2, Graphics2D g2){
        g2.drawLine((int) x1 * 10, (int) y1 * 10, (int) x2 * 10, (int) y2 * 10);
    }

    protected void drawConnections(ArrayList<Node> nodes, Graphics2D g2, boolean solution){
        for (Node node : nodes) {
            for (Node other : node.neighbours.keySet()) {
                drawLine(node.x, node.y, other.x, other.y, g2);
            }
        }
    }

    protected void drawNodes(Graphics2D g2, float fontSize, int nodesize, int bordersize, Iterable<Node> nodes) {
        for (Node node : nodes) {
            g2.setColor(Color.WHITE);
            g2.fillOval((int) (node.x * 10) - ((nodesize + bordersize)/2), (int) (node.y * 10) - ((nodesize + bordersize)/2), nodesize + bordersize, nodesize + bordersize);
            g2.setColor(Color.BLACK);
            g2.fillOval((int) (node.x * 10) - (nodesize /2), (int) (node.y * 10) - (nodesize /2), nodesize, nodesize);
            g2.setColor(Color.RED);
            g2.drawString(node.label, (int) (node.x * 10)- fontSize /3f, (int) (node.y * 10)+ fontSize /3f);
        }
    }

    static double bound(double min, double input, double max){
        return Math.min(Math.max(min, input), max);
    }

    public String getDebugInfo() {
        return nodes.toString();
    }

}

class VoronoiGraph extends Graph {

    private List<Triangle> triangles;
    private ArrayList<Node> originalNodes;
    private Map<Node, List<Triangle>> bounds;

    public VoronoiGraph(List<Triangle> triangles, ArrayList<Node> originalNodes) {

        Map<Node, ArrayList<Node>> regionBounds = new HashMap<>(); // voroni node -> the surrounding delaunay nodes

        Triangle root = triangles.get(0);
        nodes = new ArrayList<>();
        for (int i = 0; i < triangles.size(); i++) {
            Triangle triangle = triangles.get(i);
            Node newNode = triangle.findCircle().toNode("t" + i);
            nodes.add(newNode);
            updateMapping(regionBounds, triangle.n1, newNode);
            updateMapping(regionBounds, triangle.n2, newNode);
            updateMapping(regionBounds, triangle.n3, newNode);

        }

        this.triangles = triangles;
        this.originalNodes = originalNodes;

        for (int i = 0; i < nodes.size(); i++) {
            findNeighbours(i, triangles.get(i));
        }

        bounds = new HashMap<>();
        for (Node node : originalNodes) {
            List<Triangle> poly = new ArrayList<>();
            ArrayList<Node> neighbours = regionBounds.get(node);
            for (int i = 0; i < neighbours.size(); i++) {
                Node neighbour = neighbours.get(i);
                for (int j = i+1; j < neighbours.size(); j++) {
                    Node neighbour2 = neighbours.get(j);
                    if (neighbour.neighbours.containsKey(neighbour2)) {
                        poly.add(new Triangle(node, neighbour, neighbour2));
                    }
                }
            }
            bounds.put(node, poly);
        }
    }

    public void drawDebugImage(){
        BufferedImage image = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.RED);
        float h = (float) Math.random() + (1f/ triangles.size());
        for (int i = 0; i < triangles.size(); i++) {
//            g2.setColor(Color.getHSBColor(h*(i+1), 0.5f, 0.5f));
            g2.setColor(Color.getHSBColor((float) Math.random(), 0.7f, 0.7f));
            Triangle triangle = triangles.get(i);
            drawTriangle(g2, triangle);
        }

        BufferedImage image4 = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB);
        g2 = image4.createGraphics();
        g2.setColor(Color.RED);
        for (Map.Entry<Node, List<Triangle>> entry : bounds.entrySet()) {
            h = (float) (Math.random() + (1f/ entry.getValue().size()));
            for (int i = 0; i < entry.getValue().size(); i++) {
//                g2.setColor(Color.getHSBColor(h*(i+1), 0.5f, 0.5f));
                g2.setColor(Color.getHSBColor((float) Math.random(), 0.8f, 0.8f));
                Triangle triangle = entry.getValue().get(i);
                drawTriangle(g2, triangle);
            }
        }
//        for (Map.Entry<Node, List<Triangle>> entry : bounds.entrySet()) {
//            g2.setColor(Color.WHITE);
//            g2.fillRect((int) (entry.getKey().x - 1) * 10, (int) (entry.getKey().y - 1) * 10, 20, 20);
//        }

        BufferedImage image2 = new BufferedImage(1000, 1000, BufferedImage.TYPE_4BYTE_ABGR);
        g2 = (Graphics2D) image2.getGraphics();
        g2.drawImage(image, 0, 0, null);
        g2.setStroke(new BasicStroke(5));
        float fontSize = 35;
        g2.setFont(g2.getFont().deriveFont(fontSize));
        g2.setColor(Color.WHITE);
        drawConnections(nodes, g2, false);
        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(1));
        int nodesize = 40;
        int bordersize = 4;
        drawNodes(g2, fontSize, nodesize, bordersize, nodes);


        BufferedImage image3 = new BufferedImage(1000, 1000, BufferedImage.TYPE_4BYTE_ABGR);
        g2 = (Graphics2D) image3.getGraphics();

        g2.setColor(new Color(255,255,255,90));
        g2.setStroke(new BasicStroke(5));

        g2.setFont(g2.getFont().deriveFont(fontSize));
        g2.setColor(new Color(30, 140, 200));
        drawConnections(originalNodes, g2, false);
        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(1));
        drawNodes(g2, fontSize, nodesize, bordersize, originalNodes);

        BufferedImage image5 = new BufferedImage(1000, 1000, BufferedImage.TYPE_4BYTE_ABGR);
        g2 = (Graphics2D) image5.getGraphics();
        g2.drawImage(image4, 0, 0, null);
        g2.drawImage(image3, 0, 0, null);



        BufferedImage image6 = new BufferedImage(1000, 1000, BufferedImage.TYPE_4BYTE_ABGR);
        g2 = (Graphics2D) image6.getGraphics();
        g2.drawImage(image5, 0, 0, null);
        g2.setStroke(new BasicStroke(5));

        g2.setFont(g2.getFont().deriveFont(fontSize));
        g2.setColor(Color.GREEN);
        drawConnections(nodes, g2, false);
        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(1));
        g2.setColor(Color.WHITE);
//        for (Triangle t : triangles){
//            drawCircle(g2, t.findCircle());
//        }

        drawNodes(g2, fontSize, nodesize, bordersize, nodes);

        try {
            saveAndOpen(image, "triangles");
            saveAndOpen(image2, "voronoi");
            saveAndOpen(image3, "mixed");
            saveAndOpen(image4, "vortess");
            saveAndOpen(image5, "complete");
            saveAndOpen(image6, "total");
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void saveAndOpen(BufferedImage im, String name) throws IOException {
        File f = new File("images/" + name + ".png");
        ImageIO.write(im, "png", f);
//        Desktop.getDesktop().open(f);

    }


    private void updateMapping(Map<Node, ArrayList<Node>> regionBounds, Node key, Node val){
        ArrayList<Node> current = regionBounds.getOrDefault(key, new ArrayList<Node>());
        current.add(val);
        regionBounds.put(key, current);
    }

    private void findNeighbours(int nodeIndex, Triangle triangle){
        for (int i = 0; i < triangles.size(); i++) {
            Triangle t = triangles.get(i);
            if (t == triangle || i == nodeIndex) continue;
            int score = (t.containsNode(triangle.n1) ? 1 : 0) + (t.containsNode(triangle.n2) ? 1 : 0) + (t.containsNode(triangle.n3) ? 1 : 0);
            if (score == 2) {
                // our triangles share two points
                nodes.get(nodeIndex).addNeighbour(nodes.get(i), 1);
                nodes.get(i).addNeighbour(nodes.get(nodeIndex), 1);
            }
        }
    }

    public void paintBackground(BufferedImage bufIm, List<Set<String>> groups, List<Color> colorStages){
        HashMap<String, Node> nodes = new HashMap<>();
        for (Node node : this.originalNodes) {
            nodes.put(node.label, node);
        }
        Graphics2D g2 = bufIm.createGraphics();
        for (int i = 0; i < groups.size(); i++) {
            Color color = colorStages.get(i);
            g2.setColor(color);
            for (String currLabel : groups.get(i)) {
                Node current = nodes.get(currLabel);
                for (Triangle t : bounds.get(current)){
                    drawTriangle(g2, t);
                }
            }
        }
    }

    private void drawTriangle(Graphics2D g2, Triangle t){
        int[] x = {(int) (t.n1.x * 10), (int) (t.n2.x * 10), (int) (t.n3.x * 10)};
        int[] y = {(int) (t.n1.y * 10), (int) (t.n2.y * 10), (int) (t.n3.y * 10)};
        g2.fillPolygon(x, y, 3);
    }

    private void drawCircle(Graphics2D g2, Circle c){
        g2.drawOval((int) (c.x - c.radius)*10, (int) (c.y - c.radius)*10, (int) c.radius*10, (int) c.radius*10);
    }
}

class DelaunayGraph extends Graph {

    private final ArrayList<Triangle> triangles;

    DelaunayGraph(Collection<Node> points) {
        Random random = ThreadLocalRandom.current();

        nodes = new ArrayList<>();
        nodes.addAll(points);

//        double explosionFactor = 1500;
//        HashMap<Node, Double> xForceMap = new HashMap<>();
//        HashMap<Node, Double> yForceMap = new HashMap<>();
//        for (Node node : nodes){
//            double xForce = 0;
//            double yForce = 0;
//            for (Node other : nodes){
//                if (node == other){
//                    continue;
//                }
//                double yDiff = node.y - other.y, xDiff = node.x - other.x;
//                double alpha = Math.atan2(yDiff, xDiff);
//                double distSq = Math.pow(xDiff, 2) + Math.pow(yDiff, 2);
//                double force = explosionFactor / distSq;
//                xForce += force * Math.sin(alpha);
//                yForce += force * Math.cos(alpha);
//            }
//            xForceMap.put(node, xForce);
//            yForceMap.put(node, yForce);
//        }
//        for (Node node : nodes){
//            node.x = bound(5, node.x + xForceMap.get(node), 95);
//            node.y = bound(5, node.y + yForceMap.get(node), 95);
//        }
        ArrayList<Node> falseNodes = new ArrayList<>();
        falseNodes.add(new Node(-100, -100, ""));
        falseNodes.add(new Node(50, 200, ""));
        falseNodes.add(new Node(200, -100, ""));

        triangles = new ArrayList<>();
        triangles.add(new Triangle(falseNodes.get(0), falseNodes.get(1), falseNodes.get(2)));

        ArrayList<Triangle> badTriangles = new ArrayList<>();

        for (Node node : nodes) {
            badTriangles.clear();
            for (Triangle triangle : triangles) {
                if (triangle.findCircle().contains(node)) {
                    badTriangles.add(triangle);
                }
            }

            triangles.removeAll(badTriangles);
            ArrayList<Node> orphanedEdges = new ArrayList<>();
            for (Triangle triangle : badTriangles) {
                //find which edges are now stranded, and join those edges up in a new triangle with the current node
//                triangles.add(new Triangle(triangle.n1, triangle.n2, node))
//                triangles.add(new Triangle(triangle.n2, triangle.n3, node))
//                triangles.add(new Triangle(triangle.n1, triangle.n3, node))
                //we can treat points as edges joined up to their next (ie, n1 is n1-n2 and n3 is n3-n1
                //oh god this will be O(n^3)
                int orphans = 1 << 0 | 1 << 1 | 1 << 2;
                for (Triangle valid : badTriangles) {
                    if (triangle == valid) continue;
                    if (valid.containsNode(triangle.n1) && valid.containsNode(triangle.n2)) {
                        orphans &= ~(1 << 0);
                    }
                    if (valid.containsNode(triangle.n2) && valid.containsNode(triangle.n3)) {
                        orphans &= ~(1 << 1);
                    }
                    if (valid.containsNode(triangle.n3) && valid.containsNode(triangle.n1)) {
                        orphans &= ~(1 << 2);
                    }
                }
                if ((orphans & 1 << 0) == 1 << 0) {
                    orphanedEdges.add(triangle.n1);
                    orphanedEdges.add(triangle.n2);
                }
                if ((orphans & 1 << 1) == 1 << 1) {
                    orphanedEdges.add(triangle.n2);
                    orphanedEdges.add(triangle.n3);
                }
                if ((orphans & 1 << 2) == 1 << 2) {
                    orphanedEdges.add(triangle.n3);
                    orphanedEdges.add(triangle.n1);
                }
            }
            for (int i = 0; i < orphanedEdges.size(); i += 2) {
                triangles.add(new Triangle(orphanedEdges.get(i), orphanedEdges.get(i + 1), node));
            }
        }
        for (Triangle t : triangles) {

            if (falseNodes.contains(t.n1) || falseNodes.contains(t.n2) || falseNodes.contains(t.n3)) continue;

            int randWeight = random.nextInt(10) + 1;
            t.n1.addNeighbour(t.n2, randWeight);
            t.n2.addNeighbour(t.n1, randWeight);

            randWeight = random.nextInt(10) + 1;
            t.n1.addNeighbour(t.n3, randWeight);
            t.n3.addNeighbour(t.n1, randWeight);

            randWeight = random.nextInt(10) + 1;
            t.n2.addNeighbour(t.n3, randWeight);
            t.n3.addNeighbour(t.n2, randWeight);
        }
    }

    public VoronoiGraph createDual(){
        return new VoronoiGraph(triangles, this.nodes);
    }

    protected void drawWeights(Graphics2D g2, float fontSize, boolean solution) {
        for (Node node : this.nodes) {
            for (Node other : node.neighbours.keySet()) {
                int halfX = (int) ((node.x * 10 + other.x * 10) / 2);
                int halfY = (int) ((node.y * 10 + other.y * 10) / 2);
                g2.drawLine(halfX - 5, halfY, halfX + 5, halfY);
                g2.drawLine(halfX, halfY - 5, halfX, halfY + 5);
                g2.setColor(new Color(0, 0, 0, 100));
                g2.fillOval((int) (halfX - fontSize / 2),
                        (int) (halfY - fontSize / 2),
                        (int) fontSize,
                        (int) fontSize);
                g2.setColor(Color.RED);
                g2.drawString("" + node.neighbours.get(other),
                        (int) (halfX - fontSize / 3f),
                        (int) (halfY + fontSize / 3f));
            }
        }
    }


    public BufferedImage genImage(boolean drawWeights, boolean solution) {
        BufferedImage image = new BufferedImage(1000, 1000, BufferedImage.TYPE_4BYTE_ABGR);

        Graphics2D g2 = (Graphics2D) image.getGraphics();

        g2.setStroke(new BasicStroke(5));

        float fontSize = 35;
        g2.setFont(g2.getFont().deriveFont(fontSize));


        g2.setColor(Color.WHITE);
        drawConnections(nodes, g2, solution);

        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(1));

        if (drawWeights) {
            drawWeights(g2, fontSize, solution);
        }
        int nodesize = 40;
        int bordersize = 4;
        drawNodes(g2, fontSize, nodesize, bordersize, nodes);
        return image;
    }
}

class Node {

    Map<Node, Integer> neighbours;
    double x, y;
    String label;

    Node(double x, double y, String label) {
        neighbours = new HashMap<Node, Integer>();
        this.x = x;
        this.y = y;
        this.label = label;
    }

    public void addNeighbour(Node neighbour, int distance) {
        this.neighbours.put(neighbour, distance);
    }

    public void removeNeighbour() {}

    public Map<Node, Integer> getNeighbours() {
        return neighbours;
    }

    double getX() {
        return x;
    }

    double getY() {
        return y;
    }

    String getLabel() {
        return label;
    }

    double sqDistTo(Node other){
        return (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y);
    }


    @Override
    public String toString() {
        String connections = neighbours.entrySet().stream()
                .map(entry -> entry.getKey().getLabel() + "@" + entry.getValue())
                .collect(Collectors.joining(","));
        return "Node{" +
                "x=" + x +
                ", y=" + y +
                ", label='" + label + '\'' + ", connections=[" + connections + "]}";
    }
}

class Circle {
    double x, y;
    double radius;

    public Circle(double x, double y, double radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    public boolean liesWithin(double x, double y) {
        return Math.hypot(this.x - x, this.y - y) < radius;
    }

    boolean contains(Node node) {
        return liesWithin(node.x, node.y);
    }

    Node toNode(String label){
        return new Node(x, y, label);
    }

    @Override
    public String toString() {
        return "Circle{" +
                "x=" + x +
                ", y=" + y +
                ", radius=" + radius +
                '}';
    }
}

class Triangle {

    Node n1, n2, n3;
    Circle circle = null;

    public Triangle(Node n1, Node n2, Node n3) {
        if (n1 == n2 || n1 == n3 || n2 == n3) {
            throw new IllegalArgumentException("Duplicate points not permitted.");
        }
        this.n1 = n1;
        this.n2 = n2;
        this.n3 = n3;
    }

    boolean containsNode(Node node) {
        return node == n1 || node == n2 || node == n3;
    }

    public Circle findCircle() {
        if (circle != null) return circle;
        double x1 = n1.x;
        double y1 = n1.y;

        double x2 = n2.x;
        double y2 = n2.y;

        double x3 = n3.x;
        double y3 = n3.y;

        //check if all points are on the same line
        var tan1 = (y3 - y2) / (x3 - x2);
        var tan2 = (y2 - y1) / (x2 - x1);

        //two points are the same
        if (((x1 == x2) && (y1 == y2)) || ((x1 == x3) && (y1 == y3)) || ((x2 == x3) && (y2 == y3))) {
            return null;
        }

        if (tan1 == tan2) {
            return null;
        }

        double a = x1 * (y2 - y3) - y1 * (x2 - x3) + x2 * y3 - x3 * y2;
        double b = (x1 * x1 + y1 * y1) * (y3 - y2) + (x2 * x2 + y2 * y2) * (y1 - y3) + (x3 * x3 + y3 * y3) * (y2 - y1);
        double c = (x1 * x1 + y1 * y1) * (x2 - x3) + (x2 * x2 + y2 * y2) * (x3 - x1) + (x3 * x3 + y3 * y3) * (x1 - x2);
        double d = (x1 * x1 + y1 * y1) * (x3 * y2 - x2 * y3) + (x2 * x2 + y2 * y2) * (x1 * y3 - x3 * y1) + (x3 * x3 + y3 * y3) * (x2 * y1 - x1 * y2);

        double radius = Math.sqrt((b * b + c * c - 4 * a * d) / (4 * a * a));

        double centerX = -b / (2 * a);
        double centerY = -c / (2 * a);

        this.circle = new Circle(centerX, centerY, radius);
        return this.circle;

    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Triangle triangle)) return false;
        return Set.of(n1, n2, n3).equals(Set.of(triangle.n1, triangle.n2, triangle.n3));
    }

    public int hashCode() {
        int result;
        result = n1.hashCode();
        result = 31 * result + n2.hashCode();
        result = 31 * result + n3.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Triangle{" +
                "n1=" + n1 +
                ", n2=" + n2 +
                ", n3=" + n3 +
                '}';
    }
}
