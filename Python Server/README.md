## The Python Server

The server is written in Python, and is meant to be run on a Raspberry Pi running Linux. Currently, it has been tested on a Raspberry Pi 2 with a Bluetooth dongle, and a Raspberry Pi 3, both running Raspbian Jesse. 

The server depends on
* Wander Costa’s [PyUSB](https://walac.github.io/pyusb/)
* karulis’s [PyBluez](https://github.com/karulis/pybluez)

The server can be set up by simply having the Python file run at  startup on the Raspberry Pi. 

The server itself is very simple - it just defines the commands to send to the missile launcher, waits for a Bluetooth connection, and then executes received commands.
At the top of the code are command sets, where you can define custom sequences of commands to execute. These are called just as with normal commands. 