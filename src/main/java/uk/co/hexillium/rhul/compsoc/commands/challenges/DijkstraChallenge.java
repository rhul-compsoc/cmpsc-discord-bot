package uk.co.hexillium.rhul.compsoc.commands.challenges;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class DijkstraChallenge extends GraphChallenge{

    private Node from;
    private Node to;
    private Set<String> nodeLabels;
    private Path[] solutions;
    private int score;
    private int totalOperations;

    private static final Logger logger = LogManager.getLogger(DijkstraChallenge.class);

    public DijkstraChallenge(int points) {
        super(points);
        int max = this.graph.nodes.size();
        from = this.graph.nodes.get(ThreadLocalRandom.current().nextInt(max));
        to = from;

        int tries = this.graph.nodes.size() * 100;
        //have an attempt to maximise the number of hops between them
        int maxScore = hopsBetween(from, to);
        for (int i = 0; i < tries; i++){
            Node tentativeFrom = this.graph.nodes.get(ThreadLocalRandom.current().nextInt(max));
            Node tentativeTo = this.graph.nodes.get(ThreadLocalRandom.current().nextInt(max));

            int score = hopsBetween(tentativeFrom, tentativeTo);
            if (score > maxScore){
                from = tentativeFrom;
                to = tentativeTo;
            }

        }
        //worst case, at least make sure they're not the same node
        while (to == from)
            to = this.graph.nodes.get(ThreadLocalRandom.current().nextInt(max));
        nodeLabels = this.graph.nodes.stream().map(Node::getLabel).collect(Collectors.toSet());
        this.solutions = findPath(from, to);

        // some function of the total nodes, the total number of steps in the path and a random variable to make this process less reversible
        this.score = (int) (Math.sqrt(this.graph.nodes.size()) * 0.6    +    this.solutions[0].nodes.size()    +    ThreadLocalRandom.current().nextInt(3));
    }

    private int hopsBetween(Node from, Node to){
        HashSet<Node> visited = new HashSet<>();
        HashSet<Node> newVisited = new HashSet<>();
        visited.add(from);
        int level = 0;
        while (visited.size() < this.graph.nodes.size()){
            newVisited.clear();
            for (Node n : visited){
                for (Node neighbour : n.neighbours.keySet()){
                    if (!visited.contains(neighbour)){
                        newVisited.add(neighbour);
                    }
                }
            }
            visited.addAll(newVisited);
            level++;
            if (visited.contains(to)){
                return level;
            }
            if (newVisited.isEmpty()){
                throw new IllegalStateException("Failed to explore graph.");
            }
        }
        return level;
    }

    @Override
    public BufferedImage getImage() {
        return this.graph.genImage(true, false);
    }

    @Override
    public String getQuestion() {
        return "What is the shortest (ie, lowest-cost) path from node " + this.from.label + " to " + this.to.label + "?  " +
                "If two edges from a node have equal weight, the alphabetical ordering is used to determine which to select - that is to say if nodes A and B both have equal cost from node C, node A will be considered first.\n\n" +
                "Example: `!t ABCDEF`.  Please include the source and destination node labels in your answer.";
    }

    @Override
    public int getPoints(boolean correct) {
        return correct ? score : -1 * (int) Math.ceil(score/3d);
    }

    @Override
    public boolean isValidAnswer(String answer) {
        ArrayList<String> labels = Arrays.stream(answer.toUpperCase(Locale.ROOT).split("")).collect(Collectors.toCollection(ArrayList::new));
        labels.removeAll(nodeLabels);
        return labels.isEmpty();
    }

    @Override
    public boolean isCorrectAnswer(String answer) {
        return Arrays.stream(this.solutions)
                .anyMatch(solution ->
                                          answer .equalsIgnoreCase(solution.getRepr()) ||
                       (this.from.label + answer).equalsIgnoreCase(solution.getRepr())
                );
    }

    @Override
    public String getSolution() {
        return this.solutions[0].getRepr();
    }

    @Override
    public BufferedImage generateSolutionImage() {
        Path solution = this.solutions[0];
        BufferedImage image = new BufferedImage(1000, 1000, BufferedImage.TYPE_4BYTE_ABGR);

        Graphics2D g2 = (Graphics2D) image.getGraphics();

        g2.setStroke(new BasicStroke(5));

        float fontSize = 35;
        g2.setFont(g2.getFont().deriveFont(fontSize));

        g2.setStroke(new BasicStroke(5));

        g2.setColor(Color.WHITE);
        this.graph.drawConnections(this.graph.nodes, g2, true);

        g2.setColor(Color.RED);
        Node prev = solution.nodes.get(0);
        for (int i = 1; i < solution.nodes.size(); i++){
            Node next = solution.nodes.get(i);
            this.graph.drawLine(prev.x, prev.y, next.x, next.y, g2);
            prev = next;
        }

        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(1));

        graph.drawWeights(g2, fontSize, true);

        int nodesize = 40;
        int bordersize = 4;
        graph.drawNodes(g2, fontSize, nodesize, bordersize, graph.nodes);
        return image;
    }

    @Override
    public int minimumSolveTimeSeconds() {
        return (int) (20 + totalOperations * 0.4);
    }

    @Override
    public int getSolveOperationCount() {
        return totalOperations;
    }

    @Override
    public String getDebugInformation() {
        String graph = super.getDebugInformation();
        return String.format("%s, paths={%s}, from={%s}, to={%s}",
                graph,
                Arrays.toString(this.solutions),
                from,
                to
        );
    }

    Path[] findPath(Node source, Node destination){
        //Paths are sorted on their length, so the first path is the shortest
        PriorityQueue<Path> paths = new PriorityQueue<>(Comparator.comparing(Path::getCost));
        HashSet<Node> explored = new HashSet<>();
        HashMap<Node, Integer> distances = new HashMap<>();
        totalOperations = 0;
        explored.add(source);
        paths.add(new Path(Collections.singletonList(source), 0));

        int minimumCost = Integer.MAX_VALUE;
        ArrayList<Path> validPaths = new ArrayList<>();

        while (!paths.isEmpty()){
            Path path = paths.poll();

            //if the path is longer than the shortest path, we can stop
            if (path.getCost() > minimumCost){
                break;
            }

            //if the path meets the destination, return it
            if (path.getHead().equals(destination)){
                minimumCost = path.getCost();
                validPaths.add(path);
                continue;
            }
            //index all neighbors of the head of the path
            for (Node child : path.getHead().getNeighbours().keySet()){
                int newCost = path.getCost() + path.getHead().getNeighbours().get(child);
                totalOperations++;
                if (distances.containsKey(child)){
                    //if we have already found a shorter path to this node, then don't create a new path to it.
                    //we need to consider equal paths too, so that multiple correct solutions for this puzzle are considered.
                    if (newCost > distances.get(child)){
                        continue;
                    }
                }
                distances.put(child, newCost);
                explored.add(child);
                paths.add(path.addNode(child, path.getHead().getNeighbours().get(child)));

            }
        }
        return validPaths.toArray(new Path[0]);
    }

    static class Path implements Comparable<Path>{
        String repr;
        List<Node> nodes;
        int cost;

        public Path(List<Node> nodes, int cost) {
            this.nodes = nodes;
            this.repr = nodes.stream().map(Node::getLabel).collect(Collectors.joining(""));
            this.cost = cost;
        }

        public boolean containsNode(String str){
            return repr.contains(str);
        }

        public Path addNode(Node node, int extraCost){
            List<Node> newNodes = new ArrayList<>(this.nodes);
            newNodes.add(node);
            return new Path(newNodes, this.cost + extraCost);
        }

        public String getRepr(){
            return repr;
        }

        public Node getHead(){
            return nodes.get(nodes.size() - 1);
        }

        public int getCost() {
            return cost;
        }

        @Override
        public int compareTo(@NotNull DijkstraChallenge.Path o) {
            return this.cost - o.cost;
        }

        @Override
        public String toString() {
            return this.repr + "~@" + this.cost + nodes.stream().map(Node::getLabel).collect(Collectors.joining(""));
        }

        @Override
        public int hashCode() {
            return nodes.hashCode();
        }
    }
}


