package uk.co.hexillium.rhul.compsoc.commands.challenges;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class BFSChallenge extends GraphChallenge{

    Node from;
    BFSSolution solution;
    Set<String> nodeLabels;

    public BFSChallenge(int points) {
        super(points);
        int max = this.graph.nodes.size();
        from = this.graph.nodes.get(ThreadLocalRandom.current().nextInt(max));
        solution = new BFSSolution(graph, from);
        nodeLabels = this.graph.nodes.stream().map(Node::getLabel).collect(Collectors.toSet());
    }

    @Override
    public BufferedImage getImage(){
        return this.graph.genImage(false, false);
    }

    @Override
    public String getQuestion() {
        return "Using a breadth-first-search starting from node " + from.label + ", in which order would the nodes of this tree be explored?\n\n" +
                "Please give your answer as each of the nodes' letters in the correct order.  A node's children may be explored in any order permitted by BFS.\n" +
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
        ArrayDeque<String> labels = Arrays.stream(answer.toUpperCase(Locale.ROOT).split("")).collect(Collectors.toCollection(ArrayDeque::new));
        if (labels.size() > this.graph.nodes.size()              //too many nodes - each node should only be explored once
            || labels.size() < this.graph.nodes.size() - 1 ){    //too few nodes - each node must be explored once (I will permit omission of the starting node)
            return false;
        }
        if (labels.peek().equals(from.getLabel())){
            labels.poll();
        }
        for (Set<String> stage : this.solution.stages){
            HashSet<String> set = new HashSet<>(stage);
            while (!set.isEmpty()) {
                if (!set.remove(labels.poll()))
                    return false;
            }
        }
        return true;
    }

    @Override
    public String getSolution() {
        return this.from.label + this.solution.stages.stream().flatMap(Collection::stream).collect(Collectors.joining());
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

class BFSSolution {
    List<Set<String>> stages = new ArrayList<>();

    BFSSolution(DelaunayGraph graph, Node node){
        HashSet<Node> visited = new HashSet<>();
        visited.add(node);
        ArrayDeque<Node> queue = new ArrayDeque<>(node.neighbours.keySet());
        while (visited.size() < graph.nodes.size()) {
            queue.removeAll(visited);
            visited.addAll(queue);
            HashSet<Node> stage = new HashSet<>(queue);
            queue.clear();
            stage.forEach(n -> queue.addAll(n.neighbours.keySet()));
            stages.add(stage.stream().map(Node::getLabel).collect(Collectors.toSet()));
        }
    }
}
