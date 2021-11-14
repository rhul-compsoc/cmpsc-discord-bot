package uk.co.hexillium.rhul.compsoc.commands.challenges;

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
    private Path solution;
    private int score;
    private int totalOperations;

    public DijkstraChallenge(int points) {
        super(points);
        int max = this.graph.nodes.size();
        from = this.graph.nodes.get(ThreadLocalRandom.current().nextInt(max));
        to = from;

        int tries = this.graph.nodes.size() * 2;
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
        this.solution = findPath(from, to);

        // some function of the total nodes, the total number of steps in the path and a random variable to make this process less reversible
        this.score = (int) (Math.sqrt(this.graph.nodes.size()) * 0.6    +    this.solution.nodes.size()    +    ThreadLocalRandom.current().nextInt(3));
    }

    private int hopsBetween(Node from, Node to){
        ArrayDeque<Node> queue = new ArrayDeque<>();
        ArrayDeque<Integer> scores = new ArrayDeque<>();
        HashSet<Node> visited = new HashSet<>();
        queue.add(from);
        visited.add(from);
        int score = 0;
        while (!queue.isEmpty()){
            Node current = queue.poll();
            if (current.equals(to)){
                return score;
            }
            int entries = 0;
            for (Node child : current.neighbours.keySet()){
                if (!visited.contains(child)){
                    entries++;
                    queue.add(child);
                }
            }
            scores.add(entries);

            int currentScore = scores.poll();
            if (currentScore == 0){
                score++;
                scores.poll();
            } else {
                scores.addFirst(currentScore-1);
            }
        }
        return score;
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
        List<String> labels = Arrays.stream(answer.toUpperCase(Locale.ROOT).split("")).collect(Collectors.toList());
        if (labels.size() > this.graph.nodes.size()              //too many nodes - each node should only be explored once
                || labels.size() < this.graph.nodes.size() - 1 ){    //too few nodes - each node must be explored once (I will permit omission of the starting node)
            return false;
        }
        if (!labels.get(0).equals(from.getLabel())){
            labels.add(0, from.getLabel());
        }
        return this.solution.nodes.stream().map(n -> n.label).collect(Collectors.toList()).equals(labels);
    }

    @Override
    public String getSolution() {
        return this.solution.nodes.stream().map(n -> n.label).collect(Collectors.joining(""));
    }

    @Override
    public BufferedImage generateSolutionImage() {
        BufferedImage image = new BufferedImage(1000, 1000, BufferedImage.TYPE_4BYTE_ABGR);

        Graphics2D g2 = (Graphics2D) image.getGraphics();

        g2.setStroke(new BasicStroke(5));

        float fontSize = 35;
        g2.setFont(g2.getFont().deriveFont(fontSize));

        g2.setStroke(new BasicStroke(5));

        g2.setColor(Color.WHITE);
        this.graph.drawConnections(this.graph.nodes, g2, true);

        g2.setColor(Color.RED);
        Node prev = this.solution.nodes.get(0);
        for (int i = 1; i < this.solution.nodes.size(); i++){
            Node next = this.solution.nodes.get(i);
            this.graph.drawLine(prev.x, prev.y, next.x, next.y, g2);
            prev = next;
        }

        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(1));

        graph.drawWeights(g2, fontSize, true);

        int nodesize = 40;
        int bordersize = 4;
        graph.drawNodes(g2, fontSize, nodesize, bordersize, true);
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

    public Path findPath(Node source, Node destination){
        PriorityQueue<Path> paths = new PriorityQueue<>();
        HashSet<Node> explored = new HashSet<>();
        int totalOperations = 0;
        paths.add(new Path(Collections.singletonList(source), 0));
        while (!paths.isEmpty()){
            Path path = paths.poll();
            if (path.getHead().equals(destination)){
                return path;
            }
            for (Node child : path.getHead().getNeighbours().keySet()){
                if (explored.contains(child)){
                    continue;
                }
                totalOperations++;
                explored.add(child);
                paths.add(path.addNode(child, path.getHead().getNeighbours().get(child)));
            }
        }
        this.totalOperations = totalOperations;
        return null;
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

        public Node getHead(){
            return nodes.get(nodes.size() - 1);
        }

        @Override
        public int compareTo(@NotNull DijkstraChallenge.Path o) {
            return this.cost - o.cost;
        }
    }
}


