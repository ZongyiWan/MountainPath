
import cv2


def color_path_on_map(elevation_map, path, color):
    color_map = elevation_map
    if path:
        for x, y in path:
            color_map[x, y] = color
    return color_map


def display_map(map_with_path):
    cv2.imshow('Elevation Map with Path', map_with_path)
    cv2.waitKey(0)
    cv2.destroyAllWindows()


def calculate_total_elevation_change(elevation_map, path):
    total_elevation_change = 0
    for i in range(1, len(path)):
        total_elevation_change += abs(elevation_map[path[i][0]][path[i][1]] - elevation_map[path[i-1][0]][path[i-1][1]])
    return total_elevation_change




