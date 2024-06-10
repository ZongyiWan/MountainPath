package PBAS;

import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

import static java.lang.Math.addExact;

public class Search implements Runnable {
    private final Node start;
    private final Node end;
    private final AtomicBoolean finished;
    private final Set<Node> visited;
    private final AtomicStampedReference<Node> bestCompletePath;
    private final PriorityQueue<Node> nodesToVisit;
    private final Map<Node, Path> paths1;
    private final Map<Node, Path> paths2;
    private final Function<Node, Iterable<Node>> connectedNodes;
    private final ToIntBiFunction<Node, Node> knownDistanceBetweenAdjacentNodes;
    private final ToIntBiFunction<Node, Node> estimatedDistance;
    private final PrintWriter out;
    private final boolean forward;

    public Search(Node start,
                  Node end,
                  AtomicBoolean finished,
                  Set<Node> visited,
                  AtomicStampedReference<Node> bestCompletePath,
                  Map<Node, Path> paths1,
                  Map<Node, Path> paths2,
                  Function<Node, Iterable<Node>> connectedNodes,
                  ToIntBiFunction<Node, Node> knownDistanceBetweenAdjacentNodes,
                  ToIntBiFunction<Node, Node> estimatedDistance,
                  PrintWriter out,
                  boolean forward) {
        this.start = start;
        this.end = end;
        this.finished = finished;
        this.visited = visited;
        this.bestCompletePath = bestCompletePath;
        this.nodesToVisit = new PriorityQueue<>(Comparator.comparingInt(x -> paths1.get(x).distanceTravelled));
        this.paths1 = paths1;
        this.paths2 = paths2;
        this.connectedNodes = connectedNodes;
        this.knownDistanceBetweenAdjacentNodes = knownDistanceBetweenAdjacentNodes;
        this.estimatedDistance = estimatedDistance;
        this.out = out;
        this.forward = forward;
    }

    @Override
    public void run() {
        var x = start;
        while (!finished.get()) {
            if (!visited.contains(x)) {
                visit(x);
            }
            if (nodesToVisit.isEmpty()) {
                finished.set(true);
            } else {
                x = nodesToVisit.poll();
                sendUpdateToPython("Expanding node", x);
            }
        }
        sendUpdateToPython("Path complete", new Node(0, 0));
    }

    private void visit(Node x) {
        sendUpdateToPython("Visiting node", x);
        var xPath = paths1.get(x);
        if (shouldExpand(xPath)) {
            for (var y : connectedNodes(x)) {
                if (!visited.contains(x)) {
                    var yPathOld = paths1.get(y);
                    var newTotalDistanceToY = addExact(xPath.distanceTravelled, knownDistanceBetweenAdjacentNodes(x, y));
                    if (yPathOld == null || newTotalDistanceToY < yPathOld.distanceTravelled) {
                        Path betterPathToY = xPath.prepend(
                                y,
                                yPathOld == null ? estimatedDistanceToGoal(y) : yPathOld.estimatedDistanceToGoal,
                                newTotalDistanceToY,
                                yPathOld == null ? estimateOfActualTravel(y) : yPathOld.estimateOfActualTravel);
                        paths1.put(y, betterPathToY);
                        nodesToVisit.remove(y);
                        nodesToVisit.add(y);
                        updateBestCompletePath(betterPathToY);
                        if(forward) sendUpdateToPython("Best path forward", betterPathToY);
                        else sendUpdateToPython("Best path backward", betterPathToY);
                    }
                }
            }
        }
        visited.add(x);
        sendUpdateToPython("Expanded node", x);
    }

    private boolean shouldExpand(Path xPath) {
        return bestCompletePath.getReference() == null
                || addExact(xPath.distanceTravelled, xPath.estimatedDistanceToGoal) < bestCompleteDistance();
    }

    private void updateBestCompletePath(Path path) {
        var success = false;
        Node newCommonNode = path.value;
        while (!success) {
            var oldCommonNode = bestCompletePath.getReference();
            var oldDistance = bestCompleteDistance();
            var connectingPath = paths2.get(newCommonNode);
            if (connectingPath == null) {
                return;
            }
            var newDistance = addExact(path.distanceTravelled, connectingPath.distanceTravelled);
            if (newDistance >= oldDistance) {
                return;
            }
            success = bestCompletePath.compareAndSet(oldCommonNode, newCommonNode, oldDistance, newDistance);
            sendUpdateToPython("Updated best complete path", bestCompletePath.getReference());
        }
    }

    private void sendUpdateToPython(String action, Node node) {
        if(BAS_Server.ANIMATE){
            out.println(action + ": " + node.x + "," + node.y);
        }
    }

    private void sendUpdateToPython(String action, Path path) {
        if(BAS_Server.ANIMATE){
            List<String> pathNodes = new ArrayList<>();
            while (path != null) {
                pathNodes.add(path.value.x + "," + path.value.y);
                path = path.previous;
            }
            Collections.reverse(pathNodes);
            out.println(action + ": " + String.join(" -> ", pathNodes));
        }
    }


    private Iterable<Node> connectedNodes(Node x) {
        return connectedNodes.apply(x);
    }

    private int knownDistanceBetweenAdjacentNodes(Node x, Node y) {
        return knownDistanceBetweenAdjacentNodes.applyAsInt(x, y);
    }

    private int estimateOfActualTravel(Node y) {
        return estimatedDistance.applyAsInt(start, y);
    }

    private int estimatedDistanceToGoal(Node y) {
        return estimatedDistance.applyAsInt(y, end);
    }

    private int bestCompleteDistance() {
        return bestCompletePath.getStamp();
    }
}