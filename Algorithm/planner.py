import heapq
import math

# 40x40 grid. For a 2x2 meter square, each cell is 5cm x 5cm
GRID_SIZE = 40

# Safety padding around obstacles
SAFE_PADDING = 3

# Safety distance when turning
TURN_SAFETY = 7

# Movement costs
TURN_COST = 16
MOVE_COST = 1
CHANGE_PENALTY = 1

# Our car is not very good. This is software compensating for hardware
# The right and left turns result in different displacements.
FR_X = 8
FR_Y = 4

FL_X = 6
FL_Y = 2

BR_X = 4
BR_Y = 8

BL_X = 2
BL_Y = 6

# These commands are for the RPi/STM32
COMMANDS = ["FC", "FR", "FL", "BC", "BR", "BL", "IMG"]

# Represents the direction the car is facing
NORTH = 0
SOUTH = 1
EAST = 2
WEST = 3

# Represents the commands the car can execute
# The commands are FC (Forward Centre), FR (Forward Right), FL (Forward Left), BC (Back Centre), BR (Back Right), BL (Back Left)
FC = 0
FR = 1
FL = 2
BC = 3
BR = 4
BL = 5

# Each movement is a tuple of (dx, dy, new_direction)
# The new direction is what the car will end up facing and also the index of the new direction in the MOVEMENTS array
# This makes a 4 (directions) x 6 (commands) array.
# Accessed as MOVEMENTS[DIRECTION][COMMAND].
# Example - MOVEMENTS[0][1] is the movement when the car is facing NORTH and does a FR (Forward Right) turn
MOVEMENTS = (
    (
        (-1, 0, NORTH),
        (-FR_Y, FR_X, EAST),
        (-FL_Y, -FL_X, WEST),
        (1, 0, NORTH),
        (BR_Y, BR_X, WEST),
        (BL_Y, -BL_X, EAST),
    ),
    (
        (1, 0, SOUTH),
        (FR_Y, -FR_X, WEST),
        (FL_Y, FL_X, EAST),
        (-1, 0, SOUTH),
        (-BR_Y, -BR_X, EAST),
        (-BL_Y, BL_X, WEST),
    ),
    (
        (0, 1, EAST),
        (FR_X, FR_Y, SOUTH),
        (-FL_X, FL_Y, NORTH),
        (0, -1, EAST),
        (BR_X, -BR_Y, NORTH),
        (-BL_X, -BL_Y, SOUTH),
    ),
    (
        (0, -1, WEST),
        (-FR_X, -FR_Y, NORTH),
        (FL_X, -FL_Y, SOUTH),
        (0, 1, WEST),
        (-BR_X, BR_Y, SOUTH),
        (BL_X, BL_Y, NORTH),
    ),
)

# Each reverse movement is a tuple of (dx, dy, new_direction, command)
# The command is FC, FR, FL, BC, BR, BL
# This makes a 4 (directions) x 6 (commands) array.

# Essentially to answer the question "How did we get here?". In what ways could we have ended up in a cell facing a certain direction?
# Accessed as REVERSE_MOVEMENTS[DIRECTION][i].
# Example - REVERSE_MOVEMENTS[0][1] is the reverse movement when the car is facing NORTH and does a BC (Back Centre) move
# (-1, 0, NORTH, BC) means the car was one row above, and landed in the current cell by moving back

# The movements could be reordered to use COMMAND as the index instead and remove it from the tuple.
# The reason it was done this way is that its easier to think this way (and there was not much time).
REVERSE_MOVEMENTS = (
    (
        (-1, 0, NORTH, BC),
        (1, 0, NORTH, FC),
        (-BR_X, BR_Y, EAST, BR),
        (FL_X, -FL_Y, EAST, FL),
        (-BL_X, -BL_Y, WEST, BL),
        (FR_X, FR_Y, WEST, FR),
    ),
    (
        (1, 0, SOUTH, BC),
        (-1, 0, SOUTH, FC),
        (BL_X, BL_Y, EAST, BL),
        (-FR_X, -FR_Y, EAST, FR),
        (BR_X, -BR_Y, WEST, BR),
        (-FL_X, FL_Y, WEST, FL),
    ),
    (
        (0, 1, EAST, BC),
        (0, -1, EAST, FC),
        (-BL_Y, BL_X, NORTH, BL),
        (FR_Y, -FR_X, NORTH, FR),
        (BR_Y, BR_X, SOUTH, BR),
        (-FL_Y, -FL_X, SOUTH, FL),
    ),
    (
        (0, -1, WEST, BC),
        (0, 1, WEST, FC),
        (-BR_Y, -BR_X, NORTH, BR),
        (FL_Y, FL_X, NORTH, FL),
        (BL_Y, -BL_X, SOUTH, BL),
        (-FR_Y, FR_X, SOUTH, FR),
    ),
)

# Each goal state is a tuple of (line_start, change, stop_dir)
# line_start is the starting point of the line from the obstacle position
# change is the change in the line direction
# stop_dir is the direction the car should be facing when it stops
# Accessed as GOALSTATES[DIRECTION]
GOALSTATES = (
    ((-9, -1), (0, 1), 1),
    ((8, -1), (0, 1), 0),
    ((1, 9), (-1, 0), 3),
    ((1, -8), (-1, 0), 2),
)

# basically how many times we'll apply the change value above to make a line
# allows the car to stop at any one of different points along the line
# flexibility in stopping points for the car
NUM_GOALSTATES = 4

# Cell Types (the numerical values make no sense)
C_EMPTY = 0
C_GOALSTATE = -1
C_STOP = 3
C_OBSTACLE_ZONE = 1
C_OBSTACLE_BOUNDARY = 2
C_OBSTACLE = 6
C_CAR_CENTRE = 5

# For printing out the grid
PRINT_MAP = {
    C_GOALSTATE: "*",
    C_OBSTACLE_BOUNDARY: "O",
    C_OBSTACLE_ZONE: "|",
    C_STOP: "S",
    C_CAR_CENTRE: "C",
    C_OBSTACLE: "X",
    C_EMPTY: ".",
}


def gen_permutations(n, visited, permutated):
    """
    Generate all permutations of length n using backtracking.
    Represents all possible orders in which we can visit the obstacles.
    Input of length n will generate n! permutations.
    4 <= n <= 8 so not too many permutations.

    Args:
        n (int): The length of the permutations.
        visited (list): The list of visited elements.
        permutated (list): The list of generated permutations.

    Returns:
        list: The list of generated permutations.
    """
    if len(visited) == n:
        return permutated.append(visited.copy())

    for j in range(n):
        if j not in visited:
            visited.append(j)
            gen_permutations(n, visited, permutated)
            visited.remove(j)

    return permutated


class PathPlanner:
    def valid(self, cell):
        """
        Check if a given cell is valid to land on.
        Must not be an obstacle (including its boundary or zone) or out of bounds.

        Args:
            cell (tuple): The cell coordinates (x, y, dir).

        Returns:
            bool: True if the cell is valid, False otherwise.
        """
        x, y, dir = cell
        min_x = 2
        min_y = 2
        return (
            min_x <= x < GRID_SIZE - min_x
            and min_y <= y < GRID_SIZE - min_y
            and not (
                self.grid[x][y] == C_OBSTACLE_ZONE
                or self.grid[x][y] == C_OBSTACLE_BOUNDARY
                or self.grid[x][y] == C_OBSTACLE
            )
        )

    def safe_turn(self, current, turn):
        """
        Checks if it is safe to turn in the given direction from the current position.
        We use turn safety to see if there is enough space to turn without hitting an obstacle.

        Parameters:
        - current: Tuple representing the current position (x, y, dir).
        - turn: Integer representing the turn direction (FR, FL, BR, BL).

        Returns:
        - Boolean value indicating whether it is safe to turn or not.
        """
        x, y, dir = current
        forward = turn == FR or turn == FL
        dx, dy, _ = MOVEMENTS[dir][FC if forward else BC]
        x, y = x + (dx * TURN_SAFETY), y + (dy * TURN_SAFETY)
        return (
            0 <= x < GRID_SIZE
            and 0 <= y < GRID_SIZE
            and not (
                self.grid[x][y] == C_OBSTACLE_BOUNDARY or self.grid[x][y] == C_OBSTACLE
            )
        )

    def safe_pad(self, x, y):
        """
        Adds a safe padding zone around a given obstacle coordinate (x, y) in the grid.

        Parameters:
        - x (int): The x-coordinate of an obstacle.
        - y (int): The y-coordinate of an obstacle.

        Returns:
        None
        """
        for i in range(x - SAFE_PADDING - 1, x + SAFE_PADDING + 1):
            for j in range(y - SAFE_PADDING, y + SAFE_PADDING + 2):
                if 0 <= i < GRID_SIZE and 0 <= j < GRID_SIZE:
                    self.grid[i][j] = C_OBSTACLE_ZONE

    def add_states(self, x, y, dir, obstacle_index):
        """
        Adds goal states to the planner.
        We draw a line of NUM_GOALSTATES cells in the direction the obstacle is facing.

        Args:
            x (int): The x-coordinate of the obstacle.
            y (int): The y-coordinate of the obstacle.
            dir (int): The direction of the obstacle is facing.
            obstacle_index (int): The index of the obstacle.

        Returns:
            None
        """
        self.goal_states[obstacle_index] = []
        line_start, change, stop_dir = GOALSTATES[dir]

        dx, dy = line_start
        dx += x
        dy += y
        for _ in range(NUM_GOALSTATES):
            if 0 <= dx < GRID_SIZE and 0 <= dy < GRID_SIZE:
                if (
                    self.grid[dx][dy] == C_OBSTACLE_BOUNDARY
                    or self.grid[dx][dy] == C_OBSTACLE_ZONE
                ):
                    continue
                self.goal_states[obstacle_index].append((dx, dy, stop_dir))
                self.grid[dx][dy] = C_GOALSTATE
                dx += change[0]
                dy += change[1]

    def set_strict(self, x, y):
        """
        Adds an obstacle and its boundary to the grid. (the strict areas on the grid)

        Parameters:
        - x (int): The x-coordinate of an obstacle.
        - y (int): The y-coordinate of an obstacle.

        Returns:
        None
        """
        # Basically drawing a vertical and horizontal rectangle around the obstacle as its boundary
        # The algorithm will essentially treat the obstacle larger than it is

        # Vertical Box
        for i in range(-3, 3):
            for j in range(-3, 5):
                strict_x = x + i
                strict_y = y + j
                if 0 <= strict_x < GRID_SIZE and 0 <= strict_y < GRID_SIZE:
                    self.grid[strict_x][strict_y] = C_OBSTACLE_BOUNDARY

        # Horizontal Box
        for i in range(-4, 4):
            for j in range(-2, 4):
                strict_x = x + i
                strict_y = y + j
                if 0 <= strict_x < GRID_SIZE and 0 <= strict_y < GRID_SIZE:
                    self.grid[strict_x][strict_y] = C_OBSTACLE_BOUNDARY

        # A 10x10cm obstacle will take 4 cells on the grid
        # The given coordinates are the bottom left corner of the obstacle
        for i in ((0, 0), (0, 1), (-1, 0), (-1, 1)):
            obstacle_x = x + i[0]
            obstacle_y = y + i[1]
            if 0 <= obstacle_x < GRID_SIZE and 0 <= obstacle_y < GRID_SIZE:
                self.grid[obstacle_x][obstacle_y] = C_OBSTACLE

    def to_commands(self, path, obstacle_index):
        """
        Converts a path into a list of commands.

        Args:
            path (list): The path to be converted.
            obstacle_index (int): The index of the obstacle.

        Returns:
            list: A list of commands.

        """
        command_list = []
        x = 0
        while x < len(path):
            if path[x] == FC or path[x] == BC:  # Forward Centre or Back Centre
                # Combine all continuous Forward Centre or Back Centre commands into one
                command = path[x]
                c = 5
                for i in range(x + 1, len(path)):
                    if path[i] == command:
                        c += 5
                        x += 1
                    else:
                        break
                command_list.extend([command + 1, c])
            else:  # Forward Right, Forward Left, Back Right, Back Left (Always 90 degrees)
                command_list.extend([path[x] + 1, 90])
            x += 1

        # Stop at the goal state
        command_list.extend([7, obstacle_index])
        return command_list

    def get_cost_grid(self, states):
        """
        Calculates the cost grid for the given states (all belong to one obstacle).
        We're backtracking from the goal states to everywhere else on the grid.
        Each cell in final grid will be the next best move to reach one of the obstacle's goal state.
        Enables cheap traversal from any point in the grid to the goal state.

        Args:
            states (list): A list of tuples in the form (x, y, dir) representing the goal states for an obstacle.

        Returns:
            dict: A dictionary representing the cost grid, where each cell is mapped to a tuple
                  containing the cost, the next cell to go, and the move to make.

        """

        # Similar to UCS but we're backtracking from the goal states to everywhere else on the grid
        # The key is the cell and the value is a tuple of (cost, next cell, move index)
        cost_grid = {}
        for cell in states:
            cost_grid[cell] = (0, cell, 0)
            queue = [(0, cell)]

            while queue:
                cost, current = heapq.heappop(queue)

                for i in range(6):  # 6 possible moves
                    new_cost = cost  # base cost

                    # how could we have ended up in this cell?
                    move = REVERSE_MOVEMENTS[current[2]][i]
                    new_x = current[0] + move[0]
                    new_y = current[1] + move[1]
                    new_dir = move[2]

                    # this is the cell we could have come from
                    new_cell = (new_x, new_y, new_dir)

                    # if the cell is not valid, skip it
                    if not self.valid(new_cell):
                        continue

                    # this is the move we made to get to the current cell
                    move_index = move[3]

                    # add the cost of the move
                    if move_index == FC or move_index == BC:
                        new_cost += MOVE_COST
                    else:
                        # if we're turning, check if it's safe to turn
                        if not self.safe_turn(new_cell, move_index):
                            continue
                        new_cost += TURN_COST

                    # penalize changing moves
                    # prefer the same type of movement to avoid unnecessary turns and stick to the line
                    # the car seems to be better at moving in a straight line
                    # the car also handles the same turns better than different turns
                    if cost_grid[current][2] != move_index:
                        new_cost += CHANGE_PENALTY

                    # add to our cost grid if not present or if the new cost is less than the current cost
                    # again, same as UCS
                    if new_cell not in cost_grid or new_cost <= cost_grid[new_cell][0]:
                        cost_grid[new_cell] = (new_cost, current, move_index)
                        heapq.heappush(queue, (new_cost, new_cell))

        return cost_grid

    def load_cost_grid(self):
        """
        Loads the cost grid for each obstacle.

        This method initializes the `costs` list with `None` values and then assigns the cost grid
        for each obstacle using the `get_cost_grid` method.

        Parameters:
        None

        Returns:
        None
        """
        self.costs = [None] * len(self.goal_states)
        for obstacle_index in self.goal_states:
            self.costs[obstacle_index] = self.get_cost_grid(
                self.goal_states[obstacle_index]
            )

    def convert_start_pos(self, start):
        """
        Converts the start position coordinates from the Android client's format to a new format.
        Handles scaling from 20x20 to 40x40 grid and conversion to cartesian coordinates.

        Parameters:
        start (tuple): A tuple representing the start position in the original format (x, y, direction).

        Returns:
        tuple: A tuple representing the start position in the new format (x, y, direction).
        """
        x = (GRID_SIZE - 1) - ((start[1] + 1) * 2)
        y = (start[0] + 1) * 2
        dx, dy, _ = MOVEMENTS[start[2]][0]
        x += dx
        y += dy
        return (x, y, start[2])

    def get_path(self, obstacles, start, fix_start=True):
        """
        Main method to get the optimal path for the car.
        Due to the way the grid is set up on Android, the positions are in a y, x format.

        Args:
            obstacles (list): A list of obstacles represented as tuples (y, x, dir).
            start (tuple): The starting position (y, x) of the car on the grid.
            fix_start (bool, optional): Whether to fix the starting position. Defaults to True.

        Returns:
            tuple: A tuple containing the following:
                - paths (list): A list of commands representing the optimal path.
                - unreachable (list): A list of obstacles that are unreachable.
                - point (tuple): The final stopping point on the grid.
        """

        self.goal_states = {}
        self.grid = [[0 for _ in range(GRID_SIZE)] for _ in range(GRID_SIZE)]

        if fix_start:
            start = self.convert_start_pos(start)

        # Add the obstacles to the grid
        for i in range(len(obstacles)):
            # change coordinate system
            y, x, dir = obstacles[i]
            x = (GRID_SIZE - 1) - (x * 2)
            y *= 2

            # add the safe padding zone
            self.safe_pad(x, y)

            # add the strict areas
            self.set_strict(x, y)

            # add the goal states
            self.add_states(x, y, dir, i)

        # compute and load up the cost grid for all obstacles
        self.load_cost_grid()

        # permuate the obstacles to find all possible orders to visit them
        permutations = []
        gen_permutations(len(obstacles), [], permutations)

        # find the best order to visit the obstacles
        # try all permutations and find the one with the least cost
        # traversal is cheap since the cost grid is already computed
        best_cost = math.inf
        best_order = []
        obstacles_visited = 0
        for permutation in permutations:
            total_cost = 0
            visited = []
            point = start

            for target in permutation:
                # if the target is unreachable, break
                if point not in self.costs[target]:
                    break
                visited.append(target)
                cost, stopping_point, move_index = self.costs[target][point]
                total_cost += cost

                # find the stopping point for this obstacle
                while True:
                    if cost == 0:
                        break
                    cost, stopping_point, move_index = self.costs[target][
                        stopping_point
                    ]
                point = stopping_point

            # if we visited more obstacles or if we visited the same number of obstacles but the cost is less
            if len(visited) > obstacles_visited or (
                len(visited) == obstacles_visited and total_cost < best_cost
            ):
                best_cost = total_cost
                best_order = visited
                obstacles_visited = len(visited)

        # populate the unreachable obstacles
        unreachable = []
        for x in range(len(obstacles)):
            if x not in best_order:
                unreachable.append(obstacles[x])
                print(f"{x} is unreachable")

        # build the best path
        print(f"Best Cost: {best_cost}")
        point = start
        paths = []
        stops = []
        for target in best_order:
            path = []
            while True:
                cost, stopping_point, move_index = self.costs[target][point]
                if cost == 0:  # we have reached the stopping point
                    paths.extend(self.to_commands(path, target))
                    stops.append(point)
                    break
                point = stopping_point
                path.append(move_index)

        # mark on the grid where the car will stop
        for stop in stops:
            self.grid[stop[0]][stop[1]] = C_STOP

        self.grid[start[0]][start[1]] = C_CAR_CENTRE
        return paths, unreachable, point

    def print_grid(self):
        """
        Prints the grid in a formatted manner.

        This method joins the elements of each row in the grid and prints them with a specified format.
        The elements are formatted using the PRINT_MAP dictionary.
        """
        print(
            "\n".join(
                [
                    "".join(["{:3}".format(PRINT_MAP[item]) for item in row])
                    for row in self.grid
                ]
            )
        )

    def print_path(self, path):
        """
        Prints the given path line by line for each obstacle.
        Prints the movement/command and the amount

        Args:
            path (list): A list of integers representing the path.

        Returns:
            None
        """
        print(path)
        for x in range(0, len(path), 2):
            print(
                f"{COMMANDS[path[x]-1]} {path[x+1]}; ", end="\n" if path[x] == 7 else ""
            )


def gen_path(obstacles, start, log=False):
    """
    Generates a path using a PathPlanner object.
    Obstacles are obstacles in the arena.
    Start is the starting point of the car.
    Obstacles and start are in the format (x, y, direction).

    Args:
        obstacles (list): A list of obstacles.
        start: The starting point.

    Returns:
        list: Path list.
    """
    global TURN_SAFETY
    planner = PathPlanner()
    paths, unreachable, last_point = planner.get_path(obstacles, start)

    # This was a last minute addition
    # In grids with 7-8 obstacles, one obstacle may be unreachable due to turn safety
    # So we go to every other obstacle and try to reach the unreachable obstacle at the end
    if len(unreachable) == 1:
        myIndex = obstacles.index(unreachable[0])

        # decrease the turn safety to try and reach the unreachable obstacle
        while TURN_SAFETY > 4:
            TURN_SAFETY -= 1

            # get a path from the last_point to the unreachable obstacle
            danger_paths, unreachable, last_point = planner.get_path(
                unreachable, last_point, False
            )

            # if it's reachable, add the path to the main path
            if len(unreachable) == 0:
                danger_paths[-1] = myIndex
                paths.extend(danger_paths)
                break

    planner.print_path(paths) if log else None
    planner.print_grid() if log else None
    return paths

# For testing
# DEFAULT_OBSTACLES = [ (1, 18, 1), (6, 12, 0), (10, 7, 2), (13, 2, 2), (15, 16, 3) ]
# SAMPLE_OBSTACLES = [ (5, 9, 1), (7, 14, 3), (12, 9, 2), (15, 15, 1), (15, 4, 3) ]
# DEFAULT_START = (0, 0, 0)
# gen_path(SAMPLE_OBSTACLES,DEFAULT_START,True)