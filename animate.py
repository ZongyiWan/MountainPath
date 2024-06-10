import socket

import matplotlib.pyplot as plt
import matplotlib.animation as animation
import numpy as np
from utils import generate_snake_like_path


def send_data_to_java_server(elevation_map, start, goal):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect(('localhost', 5001))
        updates = []

        with s.makefile('rb') as br, s.makefile('wb') as bw:
            rows, cols = elevation_map.shape
            data = f"{rows}\n{cols}\n{start[0]}\n{start[1]}\n{goal[0]}\n{goal[1]}\n"
            data += '\n'.join(map(str, elevation_map.flatten())) + '\n'

            data_bytes = data.encode()
            length = len(data_bytes)

            bw.write(length.to_bytes(4, byteorder='big'))
            bw.write(data_bytes)
            bw.flush()

            print("Data sent")
            while True:
                line = br.readline().decode().strip()
                if line == "END":
                    break
                updates.append(line)

        return updates


def visualize_updates(updates, elevation_map):
    fig, ax = plt.subplots()
    ax.imshow(elevation_map, cmap='terrain')
    expanded_x, expanded_y = [], []
    expanding_x, expanding_y = [], []
    path_f_x, path_f_y = [], []
    path_b_x, path_b_y = [], []
    meeting_point = None
    full_path_x, full_path_y = [], []

    def update(frame):
        if frame == 1:
            print("paused")
            plt.pause(1)
        nonlocal path_f_x, path_f_y, path_b_x, path_b_y, full_path_x, full_path_y, meeting_point
        meeting_point = None
        try:
            action, coord = updates[frame].split(': ')
            if action == "Full path":
                path_nodes = coord.split(" -> ")
                full_path_x = [int(node.split(',')[1]) for node in path_nodes]
                full_path_y = [int(node.split(',')[0]) for node in path_nodes]
            elif action == "Meeting point":
                x, y = map(int, coord.split(','))
                meeting_point = (y, x)
            elif action == "Best path forward":
                path_nodes = coord.split(" -> ")
                path_f_x = [int(node.split(',')[1]) for node in path_nodes]
                path_f_y = [int(node.split(',')[0]) for node in path_nodes]
            elif action == "Best path backward":
                path_nodes = coord.split(" -> ")
                path_b_x = [int(node.split(',')[1]) for node in path_nodes]
                path_b_y = [int(node.split(',')[0]) for node in path_nodes]
            elif not full_path_x:
                x, y = map(int, coord.split(','))
                if action == "Expanding node":
                    expanding_x.append(y)
                    expanding_y.append(x)
                elif action == "Visiting node":
                    expanded_x.append(y)
                    expanded_y.append(x)
                elif action == "Expanded node":
                    expanded_x.append(y)
                    expanded_y.append(x)

            ax.clear()
            ax.imshow(elevation_map, cmap='terrain')
            ax.scatter(expanding_x, expanding_y, color='orange', label='Expanding')
            ax.scatter(expanded_x, expanded_y, color='red', label='Expanded')
            if len(full_path_x) > 0 and meeting_point is not None:
                ax.scatter(*meeting_point, color='darkgreen', label='Meeting Point')
                ax.plot(full_path_x, full_path_y, color='lightgreen', label='Full Path')
            else:
                ax.plot(path_f_x, path_f_y, color='cyan', label='Best Path Forward')
                ax.plot(path_b_x, path_b_y, color='lightblue', label='Best Path Backward')
            ax.legend()
            ax.legend(loc='center left', bbox_to_anchor=(1, 0.5))
            ax.set_title(f"{action}")

        except ValueError:
            # Skip invalid lines
            return

    ani = animation.FuncAnimation(fig, update, frames=len(updates), repeat=False, interval=0)
    plt.show()  # Display the plot with animation


def read_dat(file_path):
    with open(file_path, 'r') as f:
        data = []
        for line in f:
            row = list(map(int, line.strip().split()))
            data.append(row)
    return np.array(data)


def normalize_elevation_data(elevation_data):
    min_elev = np.min(elevation_data)
    max_elev = np.max(elevation_data)
    normalized_data = 255 * (elevation_data - min_elev) / (max_elev - min_elev)
    return normalized_data.astype(np.uint8)


def main():
    file_path = 'Colorado_480x480.dat'

    elevation_data = np.transpose(generate_snake_like_path(21, 21))

    # For finding the least elevation path from one border to another:
    '''
    first_col_insert = np.full((elevation_data.shape[0], 1), int(np.mean(elevation_data[:, 0])))
    last_col_insert = np.full((elevation_data.shape[0], 1), int(np.mean(elevation_data[:, -1])))
    elevation_data = np.hstack((first_col_insert, elevation_data, last_col_insert))
    
    last_col = np.full((elevation_data.shape[0], 1), int(np.mean(elevation_data[:, -1])))
    elevation_data = np.hstack((elevation_data, last_col))
    '''

    normalized_elevation_data = normalize_elevation_data(elevation_data)
    start = (0, 0)
    goal = (0, normalized_elevation_data.shape[1]-1)
    updates = send_data_to_java_server(normalized_elevation_data, start, goal)
    visualize_updates(updates, normalized_elevation_data)


if __name__ == "__main__":
    main()
