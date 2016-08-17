## The Python Server

The server is written in Python, and works best in Linux due to it’s reliance on PyBluez.

The server depends on
* Wander Costa’s [PyUSB](https://walac.github.io/pyusb/)
* karulis’s [PyBluez](https://github.com/karulis/pybluez)

The server can be set up by simply having the Python file run at  startup. 

The server itself is very simple - it just defines the commands to send to the missile launcher, waits for a Bluetooth connection, and then executes received commands.
At the top of the code are command sets, where you can define custom sequences of commands to execute. These are called just as with normal commands. 