#! /usr/bin/env python
import libardrone.libardrone as lib_drone
import time
import numpy as np
import cv2 as cv
import os
import math
from flask import Flask



class DroneController:
    
    loop_running = True
    view_front_camera = True
    turn = 0
    automatic_mode = False

    def __init__(self, use_webcam=False):
        self.use_webcam = use_webcam
        # if self.use_webcam:
        #     self.cam = cv.VideoCapture(0)
        self.drone = lib_drone.ARDrone2(hd=False)
        time.sleep(1)
        self.time = time.time()
        self.control = {"x": 0, "y":0, "height": 0, "distance": 0}
        self.height = 0  # [mm]
        self.drone.set_camera_view(True)
        self.battery_level = self.drone.navdata.get(0, dict()).get('battery', 0)

        # Initialize pygame
        #pygame.init()
        # self.image_shape = self.drone.image_shape  # (720, 1280, 3) = (height, width, color_depth)
        # self.img = np.array([1], ndmin=3)
        # self.screen = pygame.display.set_mode((self.image_shape[1], self.image_shape[0]))  # width, height
        # self.img_manuals = pygame.image.load(os.path.join("media", "commands.png")).convert()
        # self.screen.blit(self.img_manuals, (0, 0))
        # pygame.display.flip()

        print ("Drone initialized")

    # def start_main_loop(self):
    #     print ("Main loop started")
    #     while self.loop_running:
    #         dt = time.time() - self.time
    #         if dt < 0.04:
    #             time.sleep(0.04 - dt)
    #         self.time = time.time()
    #         self.handle_key_stroke()
    #         if not self.use_webcam:
    #             self.update_video_from_drone()
    #         else:
    #             self.update_video_from_webcam()
    #         self.height = self.drone.navdata[0]['altitude']
    #         self.print_intel()
    #         self.refresh_img(self.img, -90)

    #     self.drone.halt()

    # def handle_key_stroke(self):
    #     for event in pygame.event.get():
    #         if event.type == pygame.QUIT:
    #             self.loop_running = False
    #             self.drone.halt()
    #         elif event.type == pygame.KEYUP and not self.automatic_mode:
    #             self.drone.hover()
    #         elif event.type == pygame.KEYDOWN:
    #             if event.key not in [pygame.K_RIGHT, pygame.K_LEFT]:
    #                 self.turn = 0
    #             if event.key in [pygame.K_BACKSPACE, pygame.K_ESCAPE]:
    #                 self.drone.halt()
    #                 self.loop_running = False
    #             # takeoff / land
    #             elif event.key == pygame.K_RETURN:
    #                 print("Return pressed, taking off")
    #                 self.drone.takeoff()
    #                 self.drone.speed = 0.3
    #             elif event.key == pygame.K_SPACE:
    #                 print("Space pressed, landing")
    #                 self.drone.land()
    #             elif event.key == pygame.K_r:
    #                 self.drone.reset()
                
    #             elif self.automatic_mode:
    #                 continue
    #             # video
    #             elif event.key == pygame.K_v:
    #                 self.view_front_camera = not self.view_front_camera
    #                 self.drone.set_camera_view(self.view_front_camera)
    #             # forward / backward
    #             elif event.key == pygame.K_w:
    #                 print ("Move forward")
    #                 self.drone.move_forward()
    #             elif event.key == pygame.K_s:
    #                 print ("Move backward")
    #                 self.drone.move_backward()
    #             # left / right
    #             elif event.key == pygame.K_a:
    #                 print ("Move left")
    #                 self.drone.move_left()
    #             elif event.key == pygame.K_d:
    #                 print ("Move right")
    #                 self.drone.move_right()
    #             # up / down
    #             elif event.key == pygame.K_UP:
    #                 print ("Move up")
    #                 self.drone.move_up()
    #             elif event.key == pygame.K_DOWN:marker_size
    #                 print("Move down")
    #                 self.drone.move_down()
    #             # turn left / turn right
    #             elif event.key == pygame.K_LEFT:
    #                 print ("Turn left")
    #                 self.drone.turn_left()
    #                 self.turn = +1
    #             elif event.key == pygame.K_RIGHT:
    #                 print ("Turn right")
    #                 self.drone.turn_right()
    #                 self.turn = -1

        
    def update_video_from_drone(self):
        self.img = self.drone.get_image()  # (360, 640, 3) or (720, 1280, 3)

    def update_video_from_webcam(self):
        ret, self.img = self.cam.read()
        self.img = cv.cvtColor(self.img, cv.COLOR_BGR2RGB)

    # def refresh_img(self, array, rotate=0):
    #     surface = pygame.surfarray.make_surface(array)
    #     surface = pygame.transform.rotate(surface, rotate)
    #     surface = pygame.transform.flip(surface, True, False)
    #     self.screen.blit(surface, (0, 0))
    #     pygame.display.flip()

    
    def print_intel(self):
        font = cv.FONT_HERSHEY_SIMPLEX
        font_color = (0, 0, 0)
        font_size = 0.5
        font_weight = 2
        self.battery_level = self.drone.navdata.get(0, dict()).get('battery', 0)
        battery_text = "Battery level: {0:2.1f}%".format(self.battery_level)
        height_text = "Drone height: {0:d} mm".format(self.height)
        control_text_x = "dx = {0:.2f}".format(self.control["x"])
        control_text_y = "dy = {0:.2f}".format(self.control["y"])
        control_text_distance = "distance = {0:f}".format(self.control["distance"])
        cv.putText(self.img, battery_text, (5, 25), font, font_size, font_color, font_weight)
        cv.putText(self.img, height_text, (5, 55), font, font_size, font_color, font_weight)


    def move_forward(self):
        self.drone.move_forward()

    def move_backward(self):
        self.drone.move_backward()

    def turn_left(self):
        self.drone.turn_left()
        
    def turn_right(self):
        self.drone.turn_right()
        
    def move_up(self):
        self.drone.move_up()
    
    def move_down(self):
        self.drone.move_down()

    def take_off(self):
        #self.drone = lib_drone.ARDrone(True)
        self.drone.takeoff()
        self.drone.speed = 0.3

    def land(self):
        self.drone.land()


droneAPI= DroneController()
#script del api rest
app = Flask(__name__)
@app.route("/")
def Index():
    print("llego aqio ")
    return "testtttt"

@app.route("/api/encender" )
def encender():
    global droneAPI
    #drone= libardrone.ARDrone(True)
    droneAPI.take_off()
    print("llego aqui ")

@app.route("/apagar",  methods=['GET'])
def apagar():
    return 'Hello, World'
app.run(host='0.0.0.0', port=8080, debug=True)



