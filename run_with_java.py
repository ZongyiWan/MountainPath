import math
import socket

import cv2
import matplotlib.pyplot as plt
import numpy as np
import time
from visualization import color_path_on_map, display_map, calculate_total_elevation_change
from utils import normalize_data, read_dat


def compute_with_java(elevation_maps, starts, goals, debug=True):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect(('localhost', 5001))
        paths = []
        with s.makefile('rb') as br, s.makefile('wb') as bw:
            for elevation_map, start, goal in zip(elevation_maps, starts, goals):
                start_time = time.time()
                rows, cols = elevation_map.shape
                data = f"{rows}\n{cols}\n{start[0]}\n{start[1]}\n{goal[0]}\n{goal[1]}\n"
                data += '\n'.join(map(str, elevation_map.flatten())) + '\n'

                data_bytes = data.encode()
                length = len(data_bytes)

                bw.write(length.to_bytes(4, byteorder='big'))
                bw.write(data_bytes)
                bw.flush()

                if debug:
                    print("data sent")
                received_data = []
                while True:
                    line = br.readline().decode().strip()
                    if line == "END":
                        break
                    received_data.append(line)

                if received_data[0] == "No path found":
                    continue
                else:
                    path = [tuple(map(int, point.split(','))) for point in received_data if point]
                    paths.append(path)

                end_time = time.time()
                if debug:
                    print(f"Bidirectional A* execution time: {end_time - start_time:.2f} seconds")

        return paths


def greedy_search(elevation_map, start):
    def get_neighbors(x, y):
        neighborNodes = []
        rows, cols = len(elevation_map), len(elevation_map[0])
        if y + 1 < cols:
            neighborNodes.append((x, y + 1))
        if x - 1 >= 0 and y + 1 < cols:
            neighborNodes.append((x - 1, y + 1))
        if x + 1 < rows and y + 1 < cols:
            neighborNodes.append((x + 1, y + 1))
        return neighborNodes

    path = [start]
    current = start

    while current[1] < len(elevation_map[0]) - 1:
        neighbors = get_neighbors(current[0], current[1])
        if not neighbors:
            break

        next_node = min(neighbors, key=lambda n: abs(elevation_map[n[0]][n[1]] - elevation_map[current[0]][current[1]]))
        path.append(next_node)
        current = next_node

    return path


def main():
    file_path = 'Colorado_480x480.dat'

    elevation_data = read_dat(file_path)

    # For finding the least elevation path from one border to another:
    '''
    first_col_insert = np.full((elevation_data.shape[0], 1), int(np.mean(elevation_data[:, 0])))
    last_col_insert = np.full((elevation_data.shape[0], 1), int(np.mean(elevation_data[:, -1])))
    elevation_data = np.hstack((first_col_insert, elevation_data, last_col_insert))
    '''
    last_col = np.full((elevation_data.shape[0], 1), int(np.mean(elevation_data[:, -1])))
    elevation_data = np.hstack((elevation_data, last_col))

    normalized_elevation_data = normalize_data(elevation_data)
    steps = 10
    step = math.floor((normalized_elevation_data.shape[0] - 1) / steps)
    # goal = (0, 0)
    # start = (normalized_elevation_data.shape[0] - 1, normalized_elevation_data.shape[1] - 1)

    starts = [(i * step, 0) for i in range(steps)]
    goals = [(i * step, elevation_data.shape[1] - 1) for i in range(steps)]

    paths_A = compute_with_java([elevation_data for _ in range(steps)], starts, goals, debug=False)
    t1 = time.time()
    paths_G = [greedy_search(elevation_data, start) for start in starts]
    print(time.time()-t1)
    AS_score = []
    GS_score = []

    map_with_path = cv2.cvtColor(normalized_elevation_data, cv2.COLOR_GRAY2BGR)
    if paths_A and paths_G:
        for path_A, path_G in zip(paths_A, paths_G):
            total_elevation_change_A = calculate_total_elevation_change(elevation_data, path_A)
            total_elevation_change_G = calculate_total_elevation_change(elevation_data, path_G)
            print("Total Elevation Change for A*:", total_elevation_change_A)
            print("Total Elevation Change for Greedy Search:", total_elevation_change_G)
            AS_score.append(total_elevation_change_A)
            GS_score.append(total_elevation_change_G)
            map_with_path = color_path_on_map(map_with_path, path_A, [0, 0, 255])
            map_with_path = color_path_on_map(map_with_path, path_G, [255, 0, 0])

    display_map(map_with_path[:, :-1])

    x_coords, y_coords = zip(*starts)

    # Plotting the data
    plt.figure(figsize=(14, 8))
    plt.plot(x_coords, AS_score, marker='o', color='blue', label='Algorithm 1')
    plt.plot(x_coords, GS_score, marker='s', color='red', label='Algorithm 2')
    
    # Adding titles and labels
    plt.title('Elevation Change vs Coordinates')
    plt.xlabel('X Coordinate of Starting Coordinates')
    plt.ylabel('Total Elevation Change')
    plt.legend()
    
    # Adding data labels
    for i, txt in enumerate(AS_score):
        plt.annotate(f"{txt}", (x_coords[i], AS_score[i]), textcoords="offset points",
                     xytext=(0, 5), ha='center', color='blue', fontsize=5)
    for i, txt in enumerate(GS_score):
        plt.annotate(f"{txt}", (x_coords[i], GS_score[i]), textcoords="offset points",
                     xytext=(0, 3), ha='center', color='red', fontsize=5)

    plt.ylim(0, max(max(AS_score), max(GS_score)) * 1.1)
    plt.grid(True)
    plt.show()


if __name__ == "__main__":
    main()
