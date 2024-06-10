package PBAS;

import java.util.Collection;

final class Path {
    final Node value;
    final Path previous;
    final int distanceTravelled;
    final int estimateOfActualTravel;
    final int estimatedDistanceToGoal;

    Path(Node value, int estimatedDistanceToGoal) {
        this(value, estimatedDistanceToGoal, null, 0, 0);
    }

    private Path(Node value, int estimatedDistanceToGoal, Path previous, int distanceTravelled, int estimateOfActualTravel) {
        this.value = value;
        this.previous = previous;
        this.distanceTravelled = distanceTravelled;
        this.estimateOfActualTravel = estimateOfActualTravel;
        this.estimatedDistanceToGoal = estimatedDistanceToGoal;
    }

    Path prepend(Node value, int estimatedDistanceToGoal, int newTotalDistanceTravelled, int estimateOfActualTravel) {
        return new Path(value, estimatedDistanceToGoal, this, newTotalDistanceTravelled, estimateOfActualTravel);
    }

    void collectInto(Collection<Node> accumulator) {
        var current = this;
        while (current != null) {
            accumulator.add(current.value);
            current = current.previous;
        }
    }

    @Override
    public String toString() {
        var builder = new StringBuilder(value.toString());
        var previous = this.previous;
        while (previous != null) {
            builder.append(" ← ");
            builder.append(previous.value);
            previous = previous.previous;
        }
        return builder.toString();
    }
}