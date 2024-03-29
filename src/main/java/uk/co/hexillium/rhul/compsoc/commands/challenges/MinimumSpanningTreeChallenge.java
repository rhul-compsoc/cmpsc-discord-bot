package uk.co.hexillium.rhul.compsoc.commands.challenges;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Collectors;

public class MinimumSpanningTreeChallenge extends GraphChallenge {

    PrimsSolution solution;

    public MinimumSpanningTreeChallenge(int points) {
        super(new PrimsDelaunayGraph(generateNodeDistribution(points)));
        solution = new PrimsSolution((PrimsDelaunayGraph) this.graph);
        ((PrimsDelaunayGraph) this.graph).setSolution(solution);
    }

    @Override
    public String getQuestion() {
        return "What is the sum of the edge weights of this graph's Minimum Spanning Tree?";
    }

    @Override
    public int getPoints(boolean correct) {
        int max = (int) Math.ceil(this.graph.nodes.size() / 3f);
        return correct ? max : -max/2;
    }

    @Override
    public boolean isValidAnswer(String answer) {
        try {
            return Integer.parseInt(answer) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    @Override
    public boolean isCorrectAnswer(String answer) {
        return Integer.parseInt(answer) == this.solution.score;
    }

    @Override
    public String getSolution() {
        return this.solution.score + "";
    }

    @Override
    public BufferedImage generateSolutionImage() {
        return this.graph.genImage(true, true);
    }

    @Override
    public int minimumSolveTimeSeconds() {
        return 25 + 5 * this.graph.nodes.size();
    }

    @Override
    public int getSolveOperationCount() {
        return this.solution.operationCount;
    }
}
class PrimsDelaunayGraph extends DelaunayGraph {

    PrimsSolution solution;

    PrimsDelaunayGraph(Collection<Node> points) {
        super(points);
    }

    public void setSolution(PrimsSolution solution) {
        this.solution = solution;
    }

    @Override
    protected void drawConnections(ArrayList<Node> nodes, Graphics2D g2, boolean solution) {
        for (Node node : nodes) {
            for (Node other : node.neighbours.keySet()) {
                if (solution && this.solution.containsEdge(node, other)){
                    g2.setColor(Color.MAGENTA);
                } else {
                    g2.setColor(Color.WHITE);
                }
                drawLine(node.x, node.y, other.x, other.y, g2);
            }
        }
    }
}

class PrimsSolution {
    List<Node> pairwiseNodes;
    int score = 0;
    int operationCount = 0;

    PrimsSolution (PrimsDelaunayGraph graph){
        solve(graph);
    }

    private void solve(PrimsDelaunayGraph graph){
        pairwiseNodes = new ArrayList<>();

        HashSet<Node> visited = new HashSet<>();
        visited.add(graph.nodes.get(0));

        while (visited.size() < graph.nodes.size()){
            //get the smallest edge from a visited node to an unvisited node
            Node from = null;
            Node smallest = null;
            int smallestWeight = Integer.MAX_VALUE;
            for (Node node : visited){
                for (Node neighbour : node.neighbours.keySet()){
                    if (visited.contains(neighbour)){
                        continue;
                    }
                    int weight = node.neighbours.get(neighbour);
                    if (weight < smallestWeight){
                        from = node;
                        smallest = neighbour;
                        smallestWeight = weight;
                    }
                }
            }
            //add the smallest edge to the solution
            pairwiseNodes.add(from);
            pairwiseNodes.add(smallest);
            visited.add(smallest);
            score += smallestWeight;
        }
    }

    public boolean containsEdge(Node n1, Node n2){
        for (int i = 0; i < pairwiseNodes.size(); i+=2){
            Node t1 = pairwiseNodes.get(i);
            Node t2 = pairwiseNodes.get(i+1);
            if ((n1.equals(t1) && n2.equals(t2))
                   || (n1.equals(t2) && n2.equals(t1))){
                return true;
            }
        }
        return false;
    }




}