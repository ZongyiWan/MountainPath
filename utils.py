import numpy as np
import random


def normalize_data(elevation_data):
    min_elev = np.min(elevation_data)
    max_elev = np.max(elevation_data)
    normalized_data = 255 * (elevation_data - min_elev) / (max_elev - min_elev)
    return normalized_data.astype(np.uint8)


def read_dat(file_path):
    with open(file_path, 'r') as f:
        data = []
        for line in f:
            row = list(map(int, line.strip().split()))
            data.append(row)
    return np.array(data)


def generate_random_elevation_map(rows, cols, low=0, high=255):
    return np.random.randint(low, high, size=(rows, cols), dtype=np.uint8)


def generate_snake_like_path(rows, cols, valley_width=3):
    terrain = np.random.randint(1500, 2500, size=(rows, cols))

    direction = 1
    for x in range(0, rows, valley_width * 2):
        y = 0 if direction == 1 else cols - 1
        while 0 <= y < cols:
            for w in range(valley_width):
                if 0 <= x + w < rows and 0 <= y < cols:
                    terrain[x + w, y] = random.randint(100, 250)
            y += direction
        direction *= -1

        if x + valley_width * 2 < rows:
            next_y = 0 if direction == 1 else cols - 1
            for w in range(valley_width):
                if 0 <= x + valley_width + w < rows:
                    for i in range(valley_width):
                        currentY = next_y + i if direction == 1 else next_y - i
                        terrain[x + valley_width + w, currentY] = random.randint(100, 250)

    return terrain
