package PBAS;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

import static java.lang.Math.addExact;
import static java.util.Comparator.comparingInt;

final class Search implements Runnable {
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

    Search(Node start,
           Node end,
           AtomicBoolean finished,
           Set<Node> visited,
           AtomicStampedReference<Node> bestCompletePath,
           Map<Node, Path> paths1,
           Map<Node, Path> paths2,
           Function<Node, Iterable<Node>> connectedNodes,
           ToIntBiFunction<Node, Node> knownDistanceBetweenAdjacentNodes,
           ToIntBiFunction<Node, Node> estimatedDistance) {
        this.start = start;
        this.end = end;
        this.finished = finished;
        this.visited = visited;
        this.bestCompletePath = bestCompletePath;
        this.nodesToVisit = new PriorityQueue<>(comparingInt(x -> paths1.get(x).distanceTravelled));
        this.paths1 = paths1;
        this.paths2 = paths2;
        this.connectedNodes = connectedNodes;
        this.knownDistanceBetweenAdjacentNodes = knownDistanceBetweenAdjacentNodes;
        this.estimatedDistance = estimatedDistance;
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
                debugPrint("Polled node: " + x);
            }
        }
    }

    private void visit(Node x) {
        debugPrint("Visiting node: " + x);
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
                    }
                }
            }
        }
        visited.add(x);
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
            debugPrint("Updated best complete path: " + bestCompletePath.getReference() + " with distance: " + newDistance);
        }
    }

    private void debugPrint(String message) {
        if (BAS_Server.DEBUG) {
            System.out.println(message);
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