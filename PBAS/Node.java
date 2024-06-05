package PBAS;

import java.util.Objects;

public class Node{
    int x, y;
    int fScore;

    public Node(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Node(int x, int y, int fScore) {
        this.x = x;
        this.y = y;
        this.fScore = fScore;
    }

    public int compareTo(Node other) {
        if (other == null) {
            return -1;
        }
        return Integer.compare(this.fScore, other.fScore);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return x == node.x && y == node.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String toString() {
        return "[" + x + ", " + y + "]";
    }
}
