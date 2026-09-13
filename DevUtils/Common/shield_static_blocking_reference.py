"""Static blocking reference from Frozen OpenGL r669; no candidate fitting.

Frozen PNG SHA256: e51a6de96f815bd6b3860eb905b877a04eecba9541d41f587f692faf339e7c90
1280x720, scale3, vanilla resources after reload, stopped foil strength0.5.
"""
from pathlib import Path
from shield_item_reference import _compare_images

PROBES = [['gui_pattern', 394, 684, [(162, 42, 53), (162, 42, 53), (230, 192, 73), (162, 42, 53), (161, 42, 53), (161, 42, 52), (161, 42, 53), (161, 42, 52), (161, 42, 52)]], ['gui_rim', 386, 676, [(160, 154, 180), (160, 154, 180), (57, 56, 158), (160, 154, 180), (160, 154, 179), (56, 56, 157), (160, 154, 179), (56, 56, 157), (56, 56, 157)]], ['gui_bottom', 396, 704, [(230, 191, 71), (230, 191, 71), (225, 187, 70), (55, 55, 153), (230, 191, 71), (225, 187, 69), (55, 55, 153), (55, 55, 153), (55, 55, 152)]], ['top_left_rim', 530, 350, [(68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89)]], ['top_rim', 740, 328, [(95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122)]], ['top_right_rim', 1020, 314, [(72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99)]], ['right_rim', 1044, 452, [(77, 67, 105), (77, 67, 105), (77, 67, 105), (77, 67, 105), (77, 67, 105), (77, 67, 105), (77, 67, 105), (77, 67, 105), (77, 67, 105)]], ['left_rim', 540, 550, [(77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98)]], ['back_left', 610, 410, [(59, 38, 46), (59, 38, 46), (59, 38, 46), (59, 38, 46), (59, 38, 46), (59, 38, 46), (59, 38, 46), (59, 38, 46), (59, 38, 46)]], ['back_center', 700, 480, [(62, 42, 51), (62, 42, 51), (62, 42, 51), (62, 42, 51), (62, 42, 51), (62, 42, 51), (62, 42, 51), (62, 42, 51), (62, 42, 51)]], ['back_top', 820, 400, [(62, 39, 52), (62, 39, 52), (62, 39, 52), (62, 39, 52), (62, 39, 52), (62, 39, 52), (62, 39, 52), (62, 39, 52), (62, 39, 52)]], ['back_right', 940, 470, [(72, 47, 61), (72, 47, 61), (72, 47, 61), (72, 47, 61), (72, 47, 61), (72, 47, 61), (72, 47, 61), (72, 47, 61), (72, 47, 61)]], ['back_bottom', 800, 600, [(63, 42, 53), (63, 42, 53), (63, 42, 53), (63, 42, 53), (63, 42, 53), (63, 42, 53), (57, 35, 47), (63, 42, 53), (63, 42, 53)]], ['lower_rim', 1055, 660, [(81, 71, 109), (81, 71, 109), (81, 71, 109), (81, 71, 109), (81, 71, 109), (81, 71, 109), (81, 71, 109), (81, 71, 109), (81, 71, 109)]]]
REGIONS = {"gui": (370,662,424,716), "held": (490,280,1090,720)}

# Frozen r671 only, SHA256 ad423f7ca19c8032738cd1d76475d816c9f5228a17933dabfdce5c621516be7a.
OFFHAND_PROBES = [
    ['gui_pattern', 394, 684, [(162, 42, 53), (162, 42, 53), (230, 192, 73), (162, 42, 53), (161, 42, 53), (161, 42, 52), (161, 42, 53), (161, 42, 52), (161, 42, 52)]],
    ['gui_rim', 386, 676, [(160, 154, 180), (160, 154, 180), (57, 56, 158), (160, 154, 180), (160, 154, 179), (56, 56, 157), (160, 154, 179), (56, 56, 157), (56, 56, 157)]],
    ['gui_bottom', 396, 704, [(230, 191, 71), (230, 191, 71), (225, 187, 70), (55, 55, 153), (230, 191, 71), (225, 187, 69), (55, 55, 153), (55, 55, 153), (55, 55, 152)]],
    ['off_gui_pattern', 307, 684, [(162, 42, 53), (162, 42, 53), (230, 192, 73), (162, 42, 53), (161, 42, 53), (161, 42, 52), (161, 42, 53), (161, 42, 52), (161, 42, 52)]],
    ['off_gui_rim', 298, 676, [(43, 36, 15), (160, 154, 180), (160, 154, 180), (43, 36, 15), (160, 154, 180), (160, 154, 179), (43, 36, 15), (160, 154, 179), (56, 56, 157)]],
    ['off_gui_bottom', 309, 704, [(230, 191, 71), (230, 191, 71), (225, 187, 70), (55, 55, 153), (230, 191, 71), (225, 187, 69), (55, 55, 153), (55, 55, 153), (55, 55, 152)]],
    ['off_top_left', 240, 380, [(68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89)]],
    ['off_top', 500, 400, [(95, 86, 122), (95, 86, 122), (95, 86, 122), (95, 86, 122), (95, 86, 122), (95, 86, 122), (95, 86, 122), (95, 86, 122), (95, 86, 122)]],
    ['off_top_right', 750, 420, [(72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99), (72, 61, 99)]],
    ['off_left', 218, 560, [(77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98), (77, 70, 98)]],
    ['off_back_left', 290, 440, [(61, 42, 49), (61, 42, 49), (61, 42, 49), (61, 42, 49), (61, 42, 49), (61, 42, 49), (61, 42, 49), (61, 42, 49), (61, 42, 49)]],
    ['off_back_center', 400, 460, [(62, 42, 52), (62, 42, 52), (62, 42, 52), (62, 42, 52), (62, 42, 52), (62, 42, 52), (62, 42, 52), (62, 42, 52), (62, 42, 52)]],
    ['off_back_right', 560, 480, [(62, 39, 53), (62, 39, 53), (63, 39, 53), (62, 39, 53), (62, 39, 53), (63, 39, 53), (62, 39, 53), (62, 39, 53), (63, 39, 53)]],
    ['off_back_lower', 620, 600, [(63, 39, 54), (63, 39, 54), (63, 39, 54), (63, 39, 54), (63, 39, 54), (63, 39, 54), (63, 39, 54), (63, 39, 54), (63, 39, 54)]],
    ['main_top_left', 720, 490, [(68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89), (68, 60, 89)]],
    ['main_top', 980, 520, [(95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122), (95, 85, 122)]],
    ['main_top_right', 1250, 540, [(71, 61, 98), (71, 61, 98), (71, 61, 98), (71, 61, 98), (71, 61, 98), (71, 61, 98), (71, 61, 98), (71, 61, 98), (71, 61, 98)]],
    ['main_left', 718, 610, [(72, 65, 94), (73, 65, 94), (73, 65, 94), (72, 65, 94), (73, 65, 94), (73, 65, 94), (72, 65, 94), (72, 65, 94), (72, 65, 94)]],
    ['main_back_left', 800, 560, [(61, 42, 49), (61, 42, 50), (61, 42, 50), (61, 42, 49), (61, 42, 50), (61, 42, 50), (61, 42, 49), (61, 42, 50), (61, 42, 50)]],
    ['main_back_center', 980, 610, [(70, 46, 56), (70, 46, 56), (70, 46, 56), (70, 46, 56), (70, 46, 56), (70, 46, 56), (70, 46, 56), (70, 46, 56), (70, 46, 56)]],
    ['main_back_right', 1190, 660, [(72, 46, 60), (72, 46, 60), (72, 46, 60), (72, 46, 60), (72, 46, 60), (72, 46, 60), (72, 46, 60), (72, 46, 60), (72, 46, 60)]],
]
OFFHAND_REGIONS = {"gui": (370,662,424,716), "offhand_gui": (286,662,338,716),
                   "offhand": (190,345,785,720), "mainhand_control": (700,465,1280,720)}

# Frozen unenchanted PNG SHA256 b2304b1bb455305d8180d55943e2647136aa881e9e5acfc579f167ae182716c8.
BASE_PROBES = [
    ['gui_pattern', 394, 684, [[154, 40, 33], [154, 40, 33], [223, 190, 53], [154, 40, 33], [154, 40, 33], [154, 40, 33], [154, 40, 33], [154, 40, 33], [154, 40, 33]]],
    ['gui_rim', 386, 676, [[151, 152, 157], [151, 152, 157], [48, 54, 135], [151, 152, 157], [151, 152, 157], [48, 54, 135], [151, 152, 157], [48, 54, 135], [48, 54, 135]]],
    ['gui_bottom', 396, 704, [[223, 190, 53], [223, 190, 53], [218, 186, 52], [48, 54, 135], [223, 190, 53], [218, 186, 52], [48, 54, 135], [48, 54, 135], [48, 54, 135]]],
    ['top_left_rim', 530, 350, [[57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61]]],
    ['top_rim', 740, 328, [[81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87]]],
    ['top_right_rim', 1020, 314, [[57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61]]],
    ['right_rim', 1044, 452, [[62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67]]],
    ['left_rim', 540, 550, [[67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72]]],
    ['back_left', 610, 410, [[48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16]]],
    ['back_center', 700, 480, [[50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20]]],
    ['back_top', 820, 400, [[48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16]]],
    ['back_right', 940, 470, [[57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22]]],
    ['back_bottom', 800, 600, [[50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [44, 32, 14], [50, 39, 20], [50, 39, 20]]],
    ['lower_rim', 1055, 660, [[66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71]]],
]

# Frozen unenchanted PNG SHA256 bda38ddfacf64c5e85c76783bca8f09691a5f88cc59c88d1ae619d907378e97a.
BASE_OFFHAND_PROBES = [
    ['gui_pattern', 394, 684, [(154, 40, 33), (154, 40, 33), (223, 190, 53), (154, 40, 33), (154, 40, 33), (154, 40, 33), (154, 40, 33), (154, 40, 33), (154, 40, 33)]],
    ['gui_rim', 386, 676, [(151, 152, 157), (151, 152, 157), (48, 54, 135), (151, 152, 157), (151, 152, 157), (48, 54, 135), (151, 152, 157), (48, 54, 135), (48, 54, 135)]],
    ['gui_bottom', 396, 704, [(223, 190, 53), (223, 190, 53), (218, 186, 52), (48, 54, 135), (223, 190, 53), (218, 186, 52), (48, 54, 135), (48, 54, 135), (48, 54, 135)]],
    ['off_gui_pattern', 307, 684, [(154, 40, 33), (154, 40, 33), (223, 190, 53), (154, 40, 33), (154, 40, 33), (154, 40, 33), (154, 40, 33), (154, 40, 33), (154, 40, 33)]],
    ['off_gui_rim', 298, 676, [(41, 36, 7), (151, 152, 157), (151, 152, 157), (41, 36, 7), (151, 152, 157), (151, 152, 157), (41, 36, 7), (151, 152, 157), (48, 54, 135)]],
    ['off_gui_bottom', 309, 704, [(223, 190, 53), (223, 190, 53), (218, 186, 52), (48, 54, 135), (223, 190, 53), (218, 186, 52), (48, 54, 135), (48, 54, 135), (48, 54, 135)]],
    ['off_top_left', 240, 380, [(57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61)]],
    ['off_top', 500, 400, [(81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87)]],
    ['off_top_right', 750, 420, [(57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61)]],
    ['off_left', 218, 560, [(67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72)]],
    ['off_back_left', 290, 440, [(50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20)]],
    ['off_back_center', 400, 460, [(50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20)]],
    ['off_back_right', 560, 480, [(48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16)]],
    ['off_back_lower', 620, 600, [(48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16)]],
    ['main_top_left', 720, 490, [(57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61)]],
    ['main_top', 980, 520, [(81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87)]],
    ['main_top_right', 1250, 540, [(57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61)]],
    ['main_left', 718, 610, [(62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67)]],
    ['main_back_left', 800, 560, [(50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20)]],
    ['main_back_center', 980, 610, [(57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22)]],
    ['main_back_right', 1190, 660, [(56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22)]],
]

# Frozen plain unenchanted PNG SHA256 fcddd3a203615dac487593a42a34403ed3b0dd50fda02f4d42d5ce928a12d718.
PLAIN_BASE_PROBES = [
    ['gui_plate', 394, 684, [[108, 83, 44], [108, 83, 44], [94, 71, 36], [108, 83, 44], [108, 83, 44], [83, 64, 32], [94, 71, 36], [94, 71, 36], [83, 64, 32]]],
    ['gui_rim', 386, 676, [[151, 152, 157], [151, 152, 157], [123, 97, 57], [151, 152, 157], [151, 152, 157], [123, 97, 57], [151, 152, 157], [123, 97, 57], [123, 97, 57]]],
    ['gui_bottom', 396, 704, [[94, 71, 36], [94, 71, 36], [83, 64, 32], [94, 71, 36], [94, 71, 36], [83, 64, 32], [94, 71, 36], [94, 71, 36], [94, 71, 36]]],
    ['top_left_rim', 530, 350, [[57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61]]],
    ['top_rim', 740, 328, [[81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87], [81, 82, 87]]],
    ['top_right_rim', 1020, 314, [[57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61], [57, 57, 61]]],
    ['right_rim', 1044, 452, [[62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67], [62, 63, 67]]],
    ['left_rim', 540, 550, [[67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72], [67, 68, 72]]],
    ['back_left', 610, 410, [[48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16]]],
    ['back_center', 700, 480, [[50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20]]],
    ['back_top', 820, 400, [[48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16], [48, 35, 16]]],
    ['back_right', 940, 470, [[57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22], [57, 43, 22]]],
    ['back_bottom', 800, 600, [[50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [50, 39, 20], [44, 32, 14], [50, 39, 20], [50, 39, 20]]],
    ['lower_rim', 1055, 660, [[66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71], [66, 67, 71]]],
]

# Frozen plain unenchanted PNG SHA256 dbb18826ff7af1bbd15a0af1c6478cd3055c4fd1426249e160c7eae3344db6d7.
PLAIN_BASE_OFFHAND_PROBES = [
    ['gui_plate', 394, 684, [(108, 83, 44), (108, 83, 44), (94, 71, 36), (108, 83, 44), (108, 83, 44), (83, 64, 32), (94, 71, 36), (94, 71, 36), (83, 64, 32)]],
    ['gui_rim', 386, 676, [(151, 152, 157), (151, 152, 157), (123, 97, 57), (151, 152, 157), (151, 152, 157), (123, 97, 57), (151, 152, 157), (123, 97, 57), (123, 97, 57)]],
    ['gui_bottom', 396, 704, [(94, 71, 36), (94, 71, 36), (83, 64, 32), (94, 71, 36), (94, 71, 36), (83, 64, 32), (94, 71, 36), (94, 71, 36), (94, 71, 36)]],
    ['off_gui_plate', 307, 684, [(108, 83, 44), (108, 83, 44), (94, 71, 36), (108, 83, 44), (108, 83, 44), (83, 64, 32), (94, 71, 36), (94, 71, 36), (83, 64, 32)]],
    ['off_gui_rim', 298, 676, [(41, 36, 7), (151, 152, 157), (151, 152, 157), (41, 36, 7), (151, 152, 157), (151, 152, 157), (41, 36, 7), (151, 152, 157), (123, 97, 57)]],
    ['off_gui_bottom', 309, 704, [(94, 71, 36), (94, 71, 36), (83, 64, 32), (94, 71, 36), (94, 71, 36), (83, 64, 32), (94, 71, 36), (94, 71, 36), (94, 71, 36)]],
    ['off_top_left', 240, 380, [(57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61)]],
    ['off_top', 500, 400, [(81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87)]],
    ['off_top_right', 750, 420, [(57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61)]],
    ['off_left', 218, 560, [(67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72), (67, 68, 72)]],
    ['off_back_left', 290, 440, [(50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20)]],
    ['off_back_center', 400, 460, [(50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20)]],
    ['off_back_right', 560, 480, [(48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16)]],
    ['off_back_lower', 620, 600, [(48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16), (48, 35, 16)]],
    ['main_top_left', 720, 490, [(57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61)]],
    ['main_top', 980, 520, [(81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87), (81, 82, 87)]],
    ['main_top_right', 1250, 540, [(57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61), (57, 57, 61)]],
    ['main_left', 718, 610, [(62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67), (62, 63, 67)]],
    ['main_back_left', 800, 560, [(50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20), (50, 39, 20)]],
    ['main_back_center', 980, 610, [(57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22), (57, 43, 22)]],
    ['main_back_right', 1190, 660, [(56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22), (56, 42, 22)]],
]

# Frozen plain enchanted PNG SHA256 086338a8cad0dcd21f7077ab486438d26e3dc5747fbec957de1b640cd51d7487.
PLAIN_FOIL_PROBES = [
    ['gui_plate', 394, 684, [[113, 84, 57], [113, 84, 57], [99, 72, 49], [113, 84, 57], [113, 84, 57], [88, 65, 45], [99, 72, 49], [99, 72, 49], [88, 65, 45]]],
    ['gui_rim', 386, 676, [[156, 153, 171], [156, 153, 171], [128, 98, 71], [156, 153, 171], [156, 153, 171], [128, 98, 70], [156, 153, 170], [128, 98, 70], [128, 98, 70]]],
    ['gui_bottom', 396, 704, [[99, 72, 50], [99, 72, 50], [88, 65, 46], [99, 72, 50], [99, 72, 50], [88, 65, 46], [99, 72, 50], [99, 72, 50], [99, 72, 50]]],
    ['top_left_rim', 530, 350, [[59, 57, 66], [59, 57, 66], [59, 57, 66], [59, 57, 66], [59, 57, 66], [59, 57, 66], [59, 57, 66], [59, 57, 66], [59, 57, 66]]],
    ['top_rim', 740, 328, [[82, 82, 90], [82, 82, 90], [82, 82, 90], [82, 82, 90], [82, 82, 90], [82, 82, 90], [82, 82, 90], [82, 82, 90], [82, 82, 90]]],
    ['top_right_rim', 1020, 314, [[58, 57, 64], [58, 57, 64], [58, 57, 64], [58, 57, 64], [58, 57, 64], [58, 57, 64], [58, 57, 64], [58, 57, 64], [58, 57, 64]]],
    ['right_rim', 1044, 452, [[63, 63, 70], [63, 63, 70], [63, 63, 70], [63, 63, 70], [63, 63, 70], [63, 63, 70], [63, 63, 70], [63, 63, 70], [63, 63, 70]]],
    ['left_rim', 540, 550, [[69, 68, 78], [69, 68, 78], [69, 68, 78], [69, 68, 78], [69, 68, 78], [69, 68, 78], [69, 68, 78], [69, 68, 78], [69, 68, 78]]],
    ['back_left', 610, 410, [[49, 35, 20], [49, 35, 20], [49, 35, 20], [49, 35, 20], [49, 35, 20], [49, 35, 20], [49, 35, 20], [49, 35, 20], [49, 35, 20]]],
    ['back_center', 700, 480, [[51, 39, 24], [51, 39, 24], [51, 39, 24], [51, 39, 24], [51, 39, 24], [51, 39, 24], [51, 39, 24], [51, 39, 24], [51, 39, 24]]],
    ['back_top', 820, 400, [[49, 35, 19], [49, 35, 19], [49, 35, 19], [49, 35, 19], [49, 35, 19], [49, 35, 19], [49, 35, 19], [49, 35, 19], [49, 35, 19]]],
    ['back_right', 940, 470, [[58, 43, 25], [58, 43, 25], [58, 43, 25], [58, 43, 25], [58, 43, 25], [58, 43, 25], [58, 43, 25], [58, 43, 25], [58, 43, 25]]],
    ['back_bottom', 800, 600, [[51, 39, 24], [51, 39, 24], [51, 39, 24], [51, 39, 24], [51, 39, 24], [51, 39, 24], [45, 32, 18], [51, 39, 24], [51, 39, 24]]],
    ['lower_rim', 1055, 660, [[67, 67, 75], [67, 67, 75], [67, 67, 75], [67, 67, 75], [67, 67, 75], [67, 67, 75], [67, 67, 75], [67, 67, 75], [67, 67, 75]]],
]

# Frozen plain enchanted PNG SHA256 4bd7239aa196498495231fb4d5e629c39e9677623958f8313536dfd3c9216fc7.
PLAIN_FOIL_OFFHAND_PROBES = [
    ['gui_plate', 394, 684, [(113, 84, 57), (113, 84, 57), (99, 72, 49), (113, 84, 57), (113, 84, 57), (88, 65, 45), (99, 72, 49), (99, 72, 49), (88, 65, 45)]],
    ['gui_rim', 386, 676, [(156, 153, 171), (156, 153, 171), (128, 98, 71), (156, 153, 171), (156, 153, 171), (128, 98, 70), (156, 153, 170), (128, 98, 70), (128, 98, 70)]],
    ['gui_bottom', 396, 704, [(99, 72, 50), (99, 72, 50), (88, 65, 46), (99, 72, 50), (99, 72, 50), (88, 65, 46), (99, 72, 50), (99, 72, 50), (99, 72, 50)]],
    ['off_gui_plate', 307, 684, [(113, 84, 57), (113, 84, 57), (99, 72, 49), (113, 84, 57), (113, 84, 57), (88, 65, 45), (99, 72, 49), (99, 72, 49), (88, 65, 45)]],
    ['off_gui_rim', 298, 676, [(41, 36, 9), (156, 153, 171), (156, 153, 171), (41, 36, 9), (156, 153, 171), (156, 153, 171), (41, 36, 9), (156, 153, 170), (128, 98, 70)]],
    ['off_gui_bottom', 309, 704, [(99, 72, 50), (99, 72, 50), (88, 65, 46), (99, 72, 50), (99, 72, 50), (88, 65, 46), (99, 72, 50), (99, 72, 50), (99, 72, 50)]],
    ['off_top_left', 240, 380, [(59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67)]],
    ['off_top', 500, 400, [(82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90)]],
    ['off_top_right', 750, 420, [(58, 57, 64), (58, 57, 64), (58, 57, 64), (58, 57, 64), (58, 57, 64), (58, 57, 64), (58, 57, 64), (58, 57, 64), (58, 57, 64)]],
    ['off_left', 218, 560, [(69, 68, 79), (69, 68, 79), (69, 68, 78), (69, 68, 79), (69, 68, 79), (69, 68, 78), (69, 68, 79), (69, 68, 79), (69, 68, 79)]],
    ['off_back_left', 290, 440, [(52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25)]],
    ['off_back_center', 400, 460, [(51, 39, 24), (51, 39, 24), (51, 39, 24), (51, 39, 24), (51, 39, 24), (51, 39, 24), (51, 39, 24), (51, 39, 24), (51, 39, 24)]],
    ['off_back_right', 560, 480, [(49, 35, 19), (49, 35, 19), (49, 35, 19), (49, 35, 19), (49, 35, 19), (49, 35, 19), (49, 35, 19), (49, 35, 19), (49, 35, 19)]],
    ['off_back_lower', 620, 600, [(49, 35, 20), (49, 35, 20), (49, 35, 20), (49, 35, 20), (49, 35, 20), (49, 35, 20), (49, 35, 20), (49, 35, 20), (49, 35, 20)]],
    ['main_top_left', 720, 490, [(59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67), (59, 57, 67)]],
    ['main_top', 980, 520, [(82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90), (82, 82, 90)]],
    ['main_top_right', 1250, 540, [(58, 57, 63), (58, 57, 63), (58, 57, 63), (58, 57, 63), (58, 57, 63), (58, 57, 63), (58, 57, 63), (58, 57, 63), (58, 57, 63)]],
    ['main_left', 718, 610, [(64, 63, 73), (64, 63, 73), (64, 63, 73), (64, 63, 73), (64, 63, 73), (64, 63, 73), (64, 63, 73), (64, 63, 73), (64, 63, 73)]],
    ['main_back_left', 800, 560, [(52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25), (52, 39, 25)]],
    ['main_back_center', 980, 610, [(58, 43, 26), (58, 43, 26), (58, 43, 26), (58, 43, 26), (58, 43, 26), (58, 43, 26), (58, 43, 26), (58, 43, 26), (58, 43, 26)]],
    ['main_back_right', 1190, 660, [(57, 42, 25), (57, 42, 25), (57, 42, 25), (57, 42, 25), (57, 42, 25), (57, 42, 25), (57, 42, 25), (57, 42, 25), (57, 42, 25)]],
]
REFERENCES = {
    ('shield-patterns-foil','blocking'): (PROBES,REGIONS),
    ('shield-patterns-foil','blocking-offhand'): (OFFHAND_PROBES,OFFHAND_REGIONS),
    ('shield-patterns','blocking'): (BASE_PROBES,REGIONS),
    ('shield-patterns','blocking-offhand'): (BASE_OFFHAND_PROBES,OFFHAND_REGIONS),
    ('shield','blocking'): (PLAIN_BASE_PROBES,REGIONS),
    ('shield','blocking-offhand'): (PLAIN_BASE_OFFHAND_PROBES,OFFHAND_REGIONS),
    ('shield-foil','blocking'): (PLAIN_FOIL_PROBES,REGIONS),
    ('shield-foil','blocking-offhand'): (PLAIN_FOIL_OFFHAND_PROBES,OFFHAND_REGIONS),
}

def compare_paths(baseline, current, pose="blocking", fixture="shield-patterns-foil"):
    from PIL import Image
    with Image.open(baseline) as a, Image.open(current) as b:
        probes,regions = REFERENCES[fixture,pose]
        return _compare_images(a,b,probes,"shield-static-"+pose+"-"+fixture+"-v1",regions)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_owner(owner, ack, capture):
    """Tie the actual screenshot to one completed Rust-owned submission."""
    presentation = ack.get('wholeFramePresentationCorrelation', {})
    for field, key in (('gameplay_frame_id','gameplayFrameId'), ('correlation_id','correlationId'),
                       ('gal_submission_id','submissionId'), ('acquired_swapchain_image','acquiredSwapchainImage'),
                       ('presented_swapchain_image','presentedSwapchainImage')):
        require(type(owner.get(field)) is int and owner[field] > 0
                and type(presentation.get(key)) is int and owner[field] == presentation[key],
                'static shield selected presentation mismatch')
    frame = owner.get('deterministic_rendered_frame_index')
    require(type(frame) is int and frame > 0 and type(ack.get('renderedFrameIndex')) is int
            and type(capture.get('renderedFrameIndex')) is int
            and frame == ack['renderedFrameIndex'] == capture['renderedFrameIndex'],
            'static shield selected frame mismatch')
    require(owner.get('artifact_class') == 'rust_vulkan_whole_frame_gameplay_correlation'
            and owner.get('rust_whole_frame_presenter') is True
            and owner.get('java_vulkan_frame_execution') is False
            and owner.get('same_acquired_presented_image') is True
            and owner['acquired_swapchain_image'] == owner['presented_swapchain_image']
            and type(owner.get('world_lod_instances')) is int and owner['world_lod_instances'] == 0
            and owner.get('world_lod_route_selected') is False
            and type(owner.get('present_completed_submission_id')) is int
            and owner['present_completed_submission_id'] == owner['gal_submission_id'],
            'static shield requires completed exclusive Rust vanilla ownership')


def validate_document(doc, fixture, pose):
    expected = dict(fixture='held-shield-patterns-foil-v1', selectedSlot=1,
                    mainHand='minecraft:shield', count=1, foil=True, usingItem=True,
                    baseColor='yellow', patterns='minecraft:cross:red,minecraft:border:blue',
                    complete=True, speed=0.0, strength=0.5)
    if fixture == 'shield-patterns':
        expected.update(fixture='held-shield-patterns-v1',foil=False)
        del expected['speed'],expected['strength']
    receipt_key = 'shieldPatternFixture'
    if fixture in ('shield','shield-foil'):
        expected.update(fixture='held-shield-v1',foil=fixture == 'shield-foil')
        del expected['baseColor'],expected['patterns']
        receipt_key = 'shieldFoilFixture'
    receipt = doc.get(receipt_key, {})
    require(isinstance(receipt, dict) and receipt.keys() == expected.keys()
            and all(type(receipt[k]) is type(value) and receipt[k] == value for k,value in expected.items()),
            'static shield requires exact observed blocking item semantics')
    require(doc.get('hotbarItemFixture') == fixture and doc.get('shieldPose') == pose
            and doc.get('shieldUseHand') == ('OFF_HAND' if pose == 'blocking-offhand' else 'MAIN_HAND')
            and doc.get('cameraType') == 'FIRST_PERSON'
            and type(doc.get('selectedHotbarSlot')) is int and doc['selectedHotbarSlot'] == 1,
            'static shield pose, hand, camera or slot mismatch')
    reload = doc.get('worldResourceReload', {})
    require(reload.get('schema') == 'normal-world-resource-reload-v1'
            and all(reload.get(k) is True for k in ('requested','futureComplete','complete'))
            and type(reload.get('presentations')) is int and reload['presentations'] >= 2
            and reload.get('selectedBefore') == ['vanilla'] and reload.get('selectedAtCapture') == ['vanilla'],
            'static shield requires completed vanilla-only reload')
    captures = doc.get('captures', [])
    require(isinstance(captures, list) and len(captures) == 1, 'static shield requires one selected capture')
    c = captures[0]
    require(c.get('window') == dict(width=1280,height=720) and c.get('gameTime') == 6000
            and c.get('dimension') == 'minecraft:overworld'
            and c.get('position') == dict(x=150.5,y=100.0,z=530.5)
            and ('requestedPosition' not in c or c['requestedPosition'] == c['position'])
            and c.get('shaderEnabled') == 'false'
            and all(c.get(k) == v for k,v in (('requestedYaw',105.0),('observedYaw',105.0),
                                             ('requestedPitch',10.0),('observedPitch',10.0))),
            'static shield fixed camera/world mismatch')
    return c


def validate_execution(artifact, mode):
    health = artifact.get('validation', {})
    require(artifact.get('mode',{}).get('name') == mode and artifact.get('tool') == 'capture'
            and type(artifact.get('capture',{}).get('exit_code')) is int and artifact['capture']['exit_code'] == 0
            and all(health.get(k) is True for k in ('complete','crash_free','device_loss_free'))
            and all(health.get(k) is False for k in ('rss_guard_triggered','orphan_process_detected'))
            and (mode != 'current-rust-vulkan-shaders-off' or health.get('vulkan_validation_clean') is True),
            'static shield requires healthy actual Rust/Frozen executions')
    memory = artifact.get('metrics',{}).get('rss_and_native_memory',{}).get('client_observation',{})
    require(memory.get('complete') is True and memory.get('provider') == 'linux-proc-status'
            and all(type(memory.get(k)) is int and memory[k] > 0
                    for k in ('sample_count','peak_rss_kb','peak_hwm_kb')),
            'static shield requires complete process-memory evidence')


def report(visual, fixture, scenario, pose):
    import graphics_harness as h
    result = dict(requested=True, passed=False, capability_admitted=False, pairs=[])
    try:
        require(scenario == '' and (fixture,pose) in REFERENCES,
                'static shield pose/material has no inspected Frozen reference')
        require(visual.get('passed') is True and bool(visual.get('pairs')),
                'static shield requires complete paired fixture equivalence')
        for pair in visual['pairs']:
            captures = []
            for side,mode in (('baseline','frozen-opengl-shaders-off'),('current','current-rust-vulkan-shaders-off')):
                path = Path(pair[side+'_artifact'])
                validate_execution(h.read_json(path), mode)
                doc = h.deterministic_capture_document(path) or {}
                capture = validate_document(doc, fixture, pose)
                require(capture.get('screenshot') == pair[side+'_image'], 'static shield screenshot mismatch')
                meta = h.latest_capture_meta_path(path.parent/'capture')
                settings = h.read_key_values(meta) if meta else {}
                require(settings.get('forced_option_guiScale') == '3'
                        and not settings.get('gui_resource_pack_scenario'), 'static shield pack/scale mismatch')
                captures.append(capture)
            route = doc.get('shieldAtlasAdmission', {})
            require(route.get('schema') == 'rust-owned-shield-atlas-v1' and route.get('normalRoute') is True
                    and route.get('privateFlagsPresent') is False, 'static shield requires normal route without legacy flags')
            image = Path(pair['current_image'])
            ack = h.read_json(image.with_name('capture_request_01_initial.ack.json'))
            require(ack.get('status') == 'captured' and ack.get('captureMethod') == 'rust-vulkan-final-output'
                    and ack.get('screenshot') == str(image), 'static shield missing actual Rust screenshot acknowledgement')
            correlation = ack.get('wholeFramePresentationCorrelation',{}).get('gameplayFrameId')
            require(type(correlation) is int and correlation > 0, 'static shield missing selected gameplay frame')
            owner_path = path.parent/'capture'/'whole_frame_gameplay_attachments'/f'gameplay-correlation-frame-{correlation}.json'
            owner = h.read_json(owner_path)
            validate_owner(owner, ack, captures[1])
            pixels = compare_paths(pair['baseline_image'], pair['current_image'],pose,fixture)
            require(pixels.get('passed') is True, 'static shield Frozen anchor or paired pixels failed')
            result['pairs'].append(dict(passed=True, pixels=pixels, ownership=str(owner_path), normal_route=route,
                                        correlation=owner['correlation_id'], submission=owner['gal_submission_id']))
        result.update(passed=True, capability_admitted=True)
    except (OSError,KeyError,TypeError,ValueError,IndexError) as error:
        result['reason'] = str(error)
    return result
