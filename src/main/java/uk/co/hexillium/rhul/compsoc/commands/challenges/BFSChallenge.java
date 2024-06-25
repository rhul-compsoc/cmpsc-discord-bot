package uk.co.hexillium.rhul.compsoc.commands.challenges;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
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
                "Example: `!t ABCDEFG` \nThe answer shows the groups of nodes with the same distance.  As this is distance may be the same for many nodes, these are shown in `[]`, and the order inside the `[]` may be changed.";
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
        return this.from.label + this.solution.stages.stream().map(stage -> "[" + String.join("", stage) + "]").collect(Collectors.joining(" "));
    }

    @Override
    public BufferedImage generateSolutionImage() {
        BufferedImage baseImage = getImage();
        BufferedImage colourMap = new BufferedImage(baseImage.getWidth(), baseImage.getHeight(), BufferedImage.TYPE_INT_ARGB);

        int stages = this.solution.stages.size();

        float startHue = 240 / 360f;
        float endHue = (360 + 60) / 360f;

        float hueGap = (endHue - startHue) / stages;

        List<Color> colors = new ArrayList<>();
        for (int i = 0; i < stages; i++){
            colors.add(Color.getHSBColor(startHue + hueGap * (i + 1), 0.8f, 0.8f));
        }

        VoronoiGraph voronoiGraph = graph.createDual();
//        voronoiGraph.drawDebugImage();
        voronoiGraph.paintBackground(colourMap, solution.stages, colors);

        colourMap.getGraphics().drawImage(baseImage, 0, 0, baseImage.getWidth(), baseImage.getHeight(), null);

        return colourMap;
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
