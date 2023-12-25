import pygame
import random

# Constants
WIDTH, HEIGHT = 300, 600
GRID_SIZE = 30
GRID_WIDTH, GRID_HEIGHT = WIDTH // GRID_SIZE, HEIGHT // GRID_SIZE
WHITE = (255, 255, 255)
BLACK = (0, 0, 0)
color_grid = [[None] * GRID_WIDTH for _ in range(GRID_HEIGHT)]
# Add a counter for Tetromino movement
fall_speed = 5  # Adjust this number to control falling speed
fall_counter = 0



# Grid visibility toggle
show_grid = True  # Set to False to hide the grid

# Tetromino shapes and colors
SHAPES_COLORS = {
    'I': ([[1, 1, 1, 1]], (0, 255, 255)),
    'O': ([[1, 1], [1, 1]], (255, 255, 0)),
    'T': ([[1, 1, 1], [0, 1, 0]], (128, 0, 128)),
    'L': ([[1, 1, 1], [1, 0, 0]], (255, 165, 0)),
    'J': ([[1, 1, 1], [0, 0, 1]], (0, 0, 255)),
    'S': ([[0, 1, 1], [1, 1, 0]], (0, 255, 0)),
    'Z': ([[1, 1, 0], [0, 1, 1]], (255, 0, 0))
}

# Initialize Pygame
pygame.init()
screen = pygame.display.set_mode((WIDTH, HEIGHT))
pygame.display.set_caption("Tetris")

# Functions

def draw_grid():
    if show_grid:
        for x in range(0, WIDTH, GRID_SIZE):
            pygame.draw.line(screen, WHITE, (x, 0), (x, HEIGHT))
        for y in range(0, HEIGHT, GRID_SIZE):
            pygame.draw.line(screen, WHITE, (0, y), (WIDTH, y))
            
def draw_block(x, y, shape, color):
    for row in range(len(shape)):
        for col in range(len(shape[row])):
            if shape[row][col]:
                pygame.draw.rect(
                    screen, color, (x + col * GRID_SIZE, y + row * GRID_SIZE, GRID_SIZE, GRID_SIZE))

def collide(x, y, shape, grid):
    for row in range(len(shape)):
        for col in range(len(shape[row])):
            if shape[row][col]:
                if x + col < 0 or x + col >= GRID_WIDTH or y + row >= GRID_HEIGHT or grid[y + row][x + col]:
                    return True
    return False


def rotate(shape):
    return [list(row) for row in zip(*shape[::-1])]

# Initialize game variables
grid = [[0] * GRID_WIDTH for _ in range(GRID_HEIGHT)]
current_x, current_y = GRID_WIDTH // 2 - 1, 0
hard_dropped = False
current_shape_name = random.choice(list(SHAPES_COLORS.keys()))
current_shape, current_color = SHAPES_COLORS[current_shape_name]
score = 0
hard_dropped = False

clock = pygame.time.Clock()


def hard_drop(x, y, shape, grid):
    while not collide(x, y + 1, shape, grid):
        y += 1
    return y

def clear_lines(grid, color_grid):
    cleared_lines = 0
    i = len(grid) - 1
    while i >= 0:
        if all(grid[i]):
            del grid[i]
            grid.insert(0, [0] * GRID_WIDTH)
            del color_grid[i]
            color_grid.insert(0, [None] * GRID_WIDTH)
            cleared_lines += 1
            # Do not decrement i, as we want to recheck the new row that has moved into this position
        else:
            i -= 1  # Only move to the next row if the current row was not cleared
    return cleared_lines


def calculate_ghost_piece(x, y, shape, grid):
    ghost_y = y
    while not collide(x, ghost_y + 1, shape, grid):
        ghost_y += 1
    return ghost_y


def draw_ghost_piece(x, y, shape, grid, screen):
    ghost_y = calculate_ghost_piece(x, y, shape, grid)
    ghost_color = (200, 200, 200)  # Light grey color for the ghost piece
    for row in range(len(shape)):
        for col in range(len(shape[row])):
            if shape[row][col]:
                pygame.draw.rect(
                    screen, ghost_color, (x * GRID_SIZE + col * GRID_SIZE, ghost_y * GRID_SIZE + row * GRID_SIZE, GRID_SIZE, GRID_SIZE))  # Filled rectangle

    ghost_y = calculate_ghost_piece(x, y, shape, grid)
    ghost_color = (200, 200, 200)  # Light grey color for the ghost piece
    for row in range(len(shape)):
        for col in range(len(shape[row])):
            if shape[row][col]:
                pygame.draw.rect(
                    screen, ghost_color, (x * GRID_SIZE + col * GRID_SIZE, ghost_y * GRID_SIZE + row * GRID_SIZE, GRID_SIZE, GRID_SIZE), 1)  # 1 is for border width


running = True
while running:
    for event in pygame.event.get():
        if event.type == pygame.QUIT:
            running = False
        elif event.type == pygame.KEYDOWN:
            if event.key == pygame.K_SPACE:
                current_y = hard_drop(current_x, current_y, current_shape, grid)
                hard_dropped = True
            elif event.key == pygame.K_UP and not hard_dropped:
                rotated_shape = rotate(current_shape)
                if not collide(current_x, current_y, rotated_shape, grid):
                    current_shape = rotated_shape

    if hard_dropped == False:
        # Check the state of all keys for continuous movement
        keys = pygame.key.get_pressed()
        if keys[pygame.K_LEFT]:
            if not collide(current_x - 1, current_y, current_shape, grid):
                current_x -= 1
        if keys[pygame.K_RIGHT]:
            if not collide(current_x + 1, current_y, current_shape, grid):
                current_x += 1
        if keys[pygame.K_DOWN]:
            if not collide(current_x, current_y + 1, current_shape, grid):
                current_y += 1


    fall_counter += 1
    if fall_counter >= fall_speed:
        # Move the current shape down
        if not collide(current_x, current_y + 1, current_shape, grid):
            current_y += 1
        else:
            # Clear lines if any and update the score
            print('Grid before clearing:', grid)
            lines_cleared = clear_lines(grid, color_grid)
            print('Grid after clearing:', grid)
            print('Lines cleared:', lines_cleared)
            score += lines_cleared * 100

            # Lock the current shape in place and update the color grid
            for row in range(len(current_shape)):
                for col in range(len(current_shape[row])):
                    if current_shape[row][col]:
                        grid[current_y + row][current_x + col] = 1
                        color_grid[current_y + row][current_x + col] = current_color

            # Clear lines if any and update the score
            print('Grid before clearing:', grid)
            lines_cleared = clear_lines(grid, color_grid)
            print('Grid after clearing:', grid)
            print('Lines cleared:', lines_cleared)
            score += lines_cleared * 100

            # Generate a new random shape
            current_x, current_y = GRID_WIDTH // 2 - 1, 0
            hard_dropped = False
            current_shape_name = random.choice(list(SHAPES_COLORS.keys()))
            current_shape, current_color = SHAPES_COLORS[current_shape_name]

            # Generate a new random shape
            current_x, current_y = GRID_WIDTH // 2 - 1, 0
            hard_dropped = False
            current_shape_name = random.choice(list(SHAPES_COLORS.keys()))
            current_shape, current_color = SHAPES_COLORS[current_shape_name]

            # Check for game over
            if collide(current_x, current_y, current_shape, grid):
                running = False
        fall_counter = 0

    # Clear the screen
    screen.fill(BLACK)

    # Draw the grid
    draw_grid()

    # Draw the current shape
    draw_ghost_piece(current_x, current_y, current_shape, grid, screen)
    draw_block(current_x * GRID_SIZE, current_y * GRID_SIZE, current_shape, current_color)

    # Draw the locked blocks with their respective colors
    for row in range(GRID_HEIGHT):
        for col in range(GRID_WIDTH):
            if color_grid[row][col]:
                pygame.draw.rect(
                    screen, color_grid[row][col], (col * GRID_SIZE, row * GRID_SIZE, GRID_SIZE, GRID_SIZE))

    # Display score
    font = pygame.font.Font(None, 36)
    score_text = font.render(f"Score: {score}", True, WHITE)
    screen.blit(score_text, (10, 10))

    pygame.display.flip()
    clock.tick(10)

pygame.quit()
