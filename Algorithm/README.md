# Pathfinding Algorithm

- Uses a 40x40 grid to represent the 2mx2m arena for finer precision.
- The individual turn displacements are configurable. 
- Each obstacle has 4 "goal states" or cells where the car can stop to take an image. It leverages the reliability of image recognition to work even if the image is taken from the sides.

The algorithm works by building one cost grid for every obstacle. Each cell in this cost grid has the next best move to make from that cell to reach the stopping point for an obstacle, as well as its cost. This is done using UCS. Starting from each "goal state" we add all the possible "reverse" movements that the car could have made to land there along with the costs. 

Once we have all the cost grids, the algorithm permutes all possible orders in which the obstacles can be visited and takes the one with the lowest total cost. 