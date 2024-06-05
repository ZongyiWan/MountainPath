package PBAS;

import com.google.common.collect.ImmutableList;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

public class BAS_Server {
    static final boolean DEBUG = false;
    private static void debugPrint(String message) {
        if (DEBUG) {
            System.out.println(message);
        }
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(5001)) {
            debugPrint("Java server started, waiting for connection...");
            Socket socket = serverSocket.accept();
            debugPrint("Python client connected");
            DataInputStream in = new DataInputStream(socket.getInputStream());
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            while (true) {
                try {
                    int length = in.readInt(); // Read the length of the data
                    if (length == -1) break;
                    byte[] dataBytes = new byte[length];
                    in.readFully(dataBytes); // Read the data

                    String data = new String(dataBytes);
                    String[] inputArray = data.split("\n");

                    int rows = Integer.parseInt(inputArray[0]);
                    int cols = Integer.parseInt(inputArray[1]);
                    int startX = Integer.parseInt(inputArray[2]);
                    int startY = Integer.parseInt(inputArray[3]);
                    int goalX = Integer.parseInt(inputArray[4]);
                    int goalY = Integer.parseInt(inputArray[5]);

                    debugPrint("Dimensions: " + rows + "by " + cols + "\n"
                                        + "start: " + new Node(startX, startY) + "\n"
                                        + "goal: " + new Node(goalX, goalY));

                    int[][] elevationMap = new int[rows][cols];
                    int index = 6;
                    for (int i = 0; i < rows; i++) {
                        for (int j = 0; j < cols; j++) {
                            elevationMap[i][j] = Integer.parseInt(inputArray[index++]);
                        }
                    }
                    ToIntBiFunction<Node, Node> knownDistance = (a, b) ->
                            Math.abs(elevationMap[a.getX()][a.getY()] - elevationMap[b.getX()][b.getY()]);

                    ToIntBiFunction<Node, Node> estimatedDistance = (a, b) ->
                            (Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) ) + knownDistance.applyAsInt(a, b);

                    Function<Node, Iterable<Node>> connectedNodes = a -> {
                        var neighbors = Arrays.asList(
                                new Node(a.getX() - 1, a.getY()),
                                new Node(a.getX() + 1, a.getY()),
                                new Node(a.getX(), a.getY() - 1),
                                new Node(a.getX(), a.getY() + 1));
                        return neighbors.stream()
                                .filter(n -> n.getX() >= 0 && n.getX() < elevationMap.length && n.getY() >= 0 && n.getY() < elevationMap[0].length)
                                .toList();
                    };

                    // Run parallel A* algorithm
                    debugPrint("Calculating path...");
                    ParallelAStar pathFinder = new ParallelAStar(knownDistance, estimatedDistance, connectedNodes);
                    ImmutableList<Node> path = pathFinder.search(new Node(startX, startY), new Node(goalX, goalY));
                    debugPrint("Calculation finished");
                    // Send results back to Python
                    if (path != null && !path.isEmpty()) {
                        for (Node point : path) {
                            out.println(point.x + "," + point.y);
                        }
                    } else {
                        out.println("No path found\n");
                    }
                    out.println("END\n");
                    out.flush();
                } catch (EOFException e) {
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
            socket.shutdownOutput();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}