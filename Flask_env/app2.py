#! /usr/bin/env python
from flask import Flask
from libardrone import libardrone 
import ps_drone

# drone = ps_drone.Drone()  # Initializes the PS-Drone-API
#drone.startup()           # Connects to the drone and starts subprocesses
drone = None
drone = libardrone.ARDrone()
    
app = Flask(__name__)
@app.route("/")
def Index():
    return "testtttt"

@app.route("/api/encender")
def encender():
    drone.takeoff()
    drone.hover()
    return 'encendido'

@app.route("/api/apagar")
def apagar():
    drone.land()
    drone.halt()
    return 'apagado'

app.run(debug=True)





