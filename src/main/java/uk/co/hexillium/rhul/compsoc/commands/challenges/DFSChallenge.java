package uk.co.hexillium.rhul.compsoc.commands.challenges;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class DFSChallenge extends GraphChallenge{

    Node from;
    DFSSolution solution;
    Set<String> nodeLabels;

    public DFSChallenge(int points) {
        super(new DFSDelaunayGraph(generateNodeDistribution(points)));
        int max = this.graph.nodes.size();
        from = this.graph.nodes.get(ThreadLocalRandom.current().nextInt(max));
        solution = new DFSSolution(graph, from);
        nodeLabels = this.graph.nodes.stream().map(Node::getLabel).collect(Collectors.toSet());
        ((DFSDelaunayGraph) this.graph).setSolution(this.solution);
    }

    @Override
    public BufferedImage getImage(){
        return this.graph.genImage(false, false);
    }

    @Override
    public String getQuestion() {
        return "Using a depth-first-search starting from node " + from.label + ", in which order would the nodes of this graph be explored?\n\n" +
                "Please give your answer as each of the nodes' letters in the correct order.  A node's children should be explored alphabetically.\n" +
                "Example: `!t ABCDEFG`";
    }

    @Override
    public int getPoints(boolean correct) {
        int raw = (int) (1 + Math.floor(Math.pow(this.graph.nodes.size(), 0.6)));
        if (correct){
            return raw;
        } else {
            return -2;
        }
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
        return this.solution.solution.equals(labels);
    }

    @Override
    public String getSolution() {
        return String.join("", this.solution.solution);
    }

    @Override
    public BufferedImage generateSolutionImage() {
        return getImage(); //I don't really know how I can make a solution image for this...
    }

    @Override
    public int minimumSolveTimeSeconds() {
        return this.graph.nodes.size() * 10; //>= 10 seconds per node
    }

    @Override
    public int getSolveOperationCount() {
        return this.graph.nodes.size() - 1; //the number of nodes to visit
    }
}

class DFSSolution {
    List<String> solution = new ArrayList<>();

    DFSSolution(DelaunayGraph graph, Node node){
        solve(graph, node);
    }

    private void solve(DelaunayGraph graph, Node node){
        Stack<Node> stack = new Stack<>();
        HashSet<Node> visited = new HashSet<>();
        stack.push(node);
        while (!stack.isEmpty()){
            Node current = stack.pop();
            if (visited.contains(current)){
                continue;
            }
            solution.add(current.getLabel());
            visited.add(current);
            current.getNeighbours()
                    .keySet()
                    .stream()
                    .sorted(Comparator.comparing(Node::getLabel).reversed())
                    .filter(n -> !visited.contains(n))
                    .forEach(stack::push);
        }
    }
}


class DFSDelaunayGraph extends DelaunayGraph{

    DFSSolution solution;

    DFSDelaunayGraph(Collection<Node> points) {
        super(points);
    }

    public void setSolution(DFSSolution solution) {
        this.solution = solution;
    }
//
//    @Override
//    protected void drawConnections(ArrayList<Node> nodes, Graphics2D g2) {
//        HashSet<String> combos = new HashSet<>();
//        for (int i = 0; i < solution.solution.size() - 1; i++){
//            combos.add(solution.solution.get(i) + solution.solution.get(i + 1));
//            combos.add(solution.solution.get(i + 1) + solution.solution.get(i));
//        }
//        for (Node node : nodes) {
//            for (Node other : node.neighbours.keySet()) {
//                drawLine(node.x, node.y, other.x, other.y, g2);
//            }
//        }
//    }
}

