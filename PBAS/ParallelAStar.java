package PBAS;

import com.google.common.collect.ImmutableList;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.reverse;

final class ParallelAStar {
    private final ToIntBiFunction<Node, Node> knownDistance;
    private final ToIntBiFunction<Node, Node> estimatedDistance;
    private final Function<Node, Iterable<Node>> connectedNodes;
    private final Function<Runnable, Future<?>> executor;

    public ParallelAStar(
            ToIntBiFunction<Node, Node> knownDistance,
            ToIntBiFunction<Node, Node> estimatedDistance,
            Function<Node, Iterable<Node>> connectedNodes) {
        this(knownDistance, estimatedDistance, connectedNodes,
                a -> ForkJoinPool.commonPool().submit(a));
    }

    public ParallelAStar(
            ToIntBiFunction<Node, Node> knownDistance,
            ToIntBiFunction<Node, Node> estimatedDistance,
            Function<Node, Iterable<Node>> connectedNodes,
            Function<Runnable, Future<?>> executor) {
        this.knownDistance = checkNotNull(knownDistance);
        this.estimatedDistance = checkNotNull(estimatedDistance);
        this.connectedNodes = checkNotNull(connectedNodes);
        this.executor = checkNotNull(executor);
    }

    public ImmutableList<Node> search(Node start, Node end) {
        int d = estimatedDistance.applyAsInt(start, end);
        boolean startAndEndEqual = d == 0;
        if (startAndEndEqual) {
            return ImmutableList.of(start);
        }

        var finished = new AtomicBoolean(false);
        var visited = ConcurrentHashMap.<Node>newKeySet();
        var bestCompletePath = new AtomicStampedReference<Node>(null, Integer.MAX_VALUE);

        var paths1 = new ConcurrentHashMap<Node, Path>();
        var paths2 = new ConcurrentHashMap<Node, Path>();

        paths1.put(start, new Path(start, d));
        paths2.put(end, new Path(end, d));

        var result1 = new ArrayList<Node>();
        Future<?> future = executor.apply(
                () -> {
                    new Search(start, end, finished, visited, bestCompletePath, paths1, paths2,
                            connectedNodes, knownDistance, estimatedDistance)
                            .run();
                    var commonNode = bestCompletePath.getReference();
                    if (commonNode != null) {
                        Path previous = paths1.get(commonNode).previous;
                        if (previous != null) {
                            previous.collectInto(result1);
                        }
                        reverse(result1);
                    }
                });

        var result2 = new ArrayList<Node>();
        new Search(end, start, finished, visited, bestCompletePath, paths2, paths1,
                connectedNodes, knownDistance, estimatedDistance)
                .run();
        var commonNode = bestCompletePath.getReference();
        if (commonNode == null) {
            future.cancel(true);
            debugPrint("No common node found");

            collectPartialPaths(paths1, result1);
            collectPartialPaths(paths2, result2);

            debugPrint("Partial Path1: " + paths1.size() + "," + result1.size());
            debugPrint("Partial Path2: " + paths2.size() + "," + result2.size());

            return ImmutableList.<Node>builder()
                    .addAll(result1)
                    .addAll(result2)
                    .build();
        }

        Path p = paths2.get(commonNode);
        p.collectInto(result2);
        boolean thisThreadFoundFullPath = p.value.equals(start);
        if (thisThreadFoundFullPath) {
            future.cancel(true);
        } else {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        debugPrint("Final path collected");
        return ImmutableList.<Node>builder()
                .addAll(result1)
                .addAll(result2)
                .build();
    }

    private void collectPartialPaths(Map<Node, Path> paths, ArrayList<Node> result) {
        Set<Node> seen = new HashSet<>();
        for (Path path : paths.values()) {
            while (path != null && !seen.contains(path.value)) {
                seen.add(path.value);
                result.add(path.value);
                path = path.previous;
            }
        }
    }

    private void debugPrint(String message) {
        if (BAS_Server.DEBUG) {
            System.out.println(message);
        }
    }

}
