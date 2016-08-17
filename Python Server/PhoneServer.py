import os
import sys
import platform
import time
import socket
import re
import json
import urllib2
import base64

import usb.core
import usb.util

from bluetooth import *
from datetime import datetime

def log(text):
    print str(datetime.now()) + " | " + text

##########################  USB CONFIG  #########################

COMMAND_SETS = {
    "test" : (
        ("led", 0),
        ("led", 1),
        ("right", 1500),
        ("up", 1500),
        ("down", 1500),
        ("left", 1500),
        ("led", 0),
    ),
}

DOWN    = 0x01
UP      = 0x02
LEFT    = 0x04
RIGHT   = 0x08
FIRE    = 0x10
STOP    = 0x20

DEVICE = None
DEVICE_TYPE = None

def setup_usb():

    global DEVICE 
    global DEVICE_TYPE

    DEVICE = usb.core.find(idVendor=0x2123, idProduct=0x1010)

    if DEVICE is None:
        DEVICE = usb.core.find(idVendor=0x0a81, idProduct=0x0701)
        if DEVICE is None:
            raise ValueError('Missile device not found')
        else:
            DEVICE_TYPE = "Original"
    else:
        DEVICE_TYPE = "Thunder"

    

    # On Linux we need to detach usb HID first
    if "Linux" == platform.system():
	try:
	    DEVICE.detach_kernel_driver(0)
	except Exception, e:
	    pass # already unregistered    

	DEVICE.set_configuration()


def send_cmd(cmd):
    if "Thunder" == DEVICE_TYPE:
	DEVICE.ctrl_transfer(0x21, 0x09, 0, 0, [0x02, cmd, 0x00,0x00,0x00,0x00,0x00,0x00])
    elif "Original" == DEVICE_TYPE:
	DEVICE.ctrl_transfer(0x21, 0x09, 0x0200, 0, [cmd])

def led(cmd):
    if "Thunder" == DEVICE_TYPE:
	DEVICE.ctrl_transfer(0x21, 0x09, 0, 0, [0x03, cmd, 0x00,0x00,0x00,0x00,0x00,0x00])
    elif "Original" == DEVICE_TYPE:
	log("There is no LED on this device")

def send_move_timed(cmd, duration_ms):
    send_cmd(cmd)
    time.sleep(duration_ms / 1000.0)
    send_cmd(STOP)

def send_move(cmd):
    send_cmd(cmd)

def run_command(command):
    command = command.lower()
    if command == "right":
        send_move(RIGHT)
    elif command == "left":
        send_move(LEFT)
    elif command == "up":
        send_move(UP)
    elif command == "down":
        send_move(DOWN)
    elif command == "stop":
        send_cmd(STOP)
    elif command == "zero" or command == "park" or command == "reset":
        # Move to bottom-left
        send_move_timed(DOWN, 2000)
        send_move_timed(LEFT, 7000)
    elif command == "led-on":
        led(0x01)
    elif command == "led-off":
        led(0x00)
    else:
        log("Error: Unknown command: '%s'" % command)


def run_command_set(commands):
    for cmd, value in commands:
        run_command(cmd)

setup_usb()
led(0x01)

##########################  MAIN LOOP  #########################



while True:
    
    ### BLUETOOTH CONFIG ###

	server_sock=BluetoothSocket( RFCOMM )
	server_sock.bind(("",PORT_ANY))
	server_sock.listen(1)

	port = server_sock.getsockname()[1]
    
    # Random identifier, feel free to change
	uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"

	advertise_service( server_sock, "PhoneDemoServer",
			   service_id = uuid,
			   service_classes = [ uuid, SERIAL_PORT_CLASS ],
			   profiles = [ SERIAL_PORT_PROFILE ], 
			  )
	
	log("Waiting for connection on RFCOMM channel %d" % port)

	client_sock, client_info = server_sock.accept()
	log("Accepted connection from " + str(client_info))

	while True:          
		
        # Device control
        
		try:
			data = client_sock.recv(1024)
			if len(data) == 0: break
			log("received [%s]" % data)

			if data == 'disconnect':
				log("manual disconnect")

				client_sock.close()
				server_sock.close()
				log("all done")

				break
			else:
				run_command(data)

		except IOError:
			log("disconnected, IO error")

			client_sock.close()
			server_sock.close()
			log("all done")

			break

		except KeyboardInterrupt:

			log("disconnected, keyboard interrupt")

			client_sock.close()
			server_sock.close()
			log("all done")

			break
