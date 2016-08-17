## The Android App
The app is written in pure Java using the Android SDK. The app is compiled for SDK v23, and is backwards compatible down to SDK v14. 
  
The app relies on:
* Google’s Android Vision library (available on all Android devices with Google Play Services)
* Philipp Jahoda’s [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)

The source files are laid out as follows:
```
/facetracker
	FaceGraphic.java
	FaceTrackerActivity.java
	/ui.camera
		CameraSourcePreview.java
		GraphicOverlay.java
	/utility
		MathUtility.java
		SoundMeter.java
```
`FaceTrackerActivity` is the main and only activity for this app and it controls all functionality. It has a number of global `DEBUG` variables which toggle the functionality of various parts of the app such as recording audio or displaying detailed facial information.

`FaceGraphic` handles all drawings on the camera preview. By default, only a colored box is drawn around the head. By setting `DEBUG_VIDEO_FACE_INFO` to true, various statistics such as Euler X and Y are also displayed. `DEBUG_VIDEO_SECTIONS` toggles the display of which section of the screen the face is in - this can be very useful when tweaking the sensitivity of the movement of the stand.

`/ui.camera` contains mostly back-end stuff and can be ignored for the most part.

`/utility/MathUtility` is used for testing if the face is within a given oval, and for finding the X and Y position of the face on the high-resolution preview canvas based off it’s position on the low-resolution video used for face tracking.

`/utility/SoundMeter` is used for measuring the the ambient volume around the phone. Simply put, it returns the highest volume level since it was last called. Since this method is called every few milliseconds, this generates a fast and (generally) accurate idea of the volume.

######Known issues: 
1. The app needs a way to obtain the Bluetooth address of the Raspberry Pi. Currently, the address is hardcoded. It would be difficult to do device discovery, as the imagined use case for this involves multiple copies of the phone stand, each with their own Raspberry Pi. Differentiating the different phone stands would be a challenge.
Thus, my suggestion would be to use an NFC tag on the phone stand, directly where the phone would go. This tag could be two-fold in purpose, opening the app while also providing the Bluetooth address of the stand. This bypasses device discovery altogether, and also makes the experience much more seamless for the end user.

2. The app tends to hang when trying to establish a device connection. This can easily be resolved by moving the Bluetooth connection to another thread.

3. When first opening the app, there is a long delay. Subsequent openings (even after force-closing the app) do not seem to have this delay. I am unsure of the cause behind it, possibly it is the way I check and request permissions.