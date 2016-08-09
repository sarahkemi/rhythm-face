package com.google.android.gms.samples.vision.face.facetracker.utility;

import com.google.android.gms.vision.face.Face;

public class MathUtility {

    public static String getFaceLocation(int sizeX, int sizeY, int faceX, int faceY) {
        int x = faceX;
        int y = (int) ((faceY / (double) sizeY) * ((double) sizeX));

        String locSide, locRing;

        // Get the side
        if (x > y) {
            if (sizeX - x > y) {
                locSide = "U";
            } else {
                locSide = "R";
            }
        }
        else {
            if (sizeX - x > y) {
                locSide = "L";
            } else {
                locSide = "D";
            }
        }

        if (isInOval(faceX, faceY, (int) (sizeX * 0.33), (int) (sizeY * 0.33), sizeX/2, sizeY/2)) {
            locRing = "0";
        } else if (isInOval(faceX, faceY, (int) (sizeX * 0.55), (int) (sizeY * 0.55), sizeX/2, sizeY/2)) {
            locRing = "1";
        } else {
            locRing = "2";
        }

        if (locRing == "0") {
            return "Center";
        } else {
            return locSide + "-" + locRing;
        }
    }

    private static boolean isInOval(int pointX, int pointY, int ovalWidth, int ovalHeight, int centerX, int centerY) {
        int ovalRadiusX = ovalWidth / 2;
        int ovalRadiusY = ovalHeight / 2;
        // (pointX - centerX)^2 / ovalRadiusX^2 + (pointY - centerY)^2 / ovalRadiusY^2 <= 1
        return (Math.pow((pointX - centerX), 2) / Math.pow(ovalRadiusX, 2))
                + Math.pow((pointY - centerY), 2) / Math.pow(ovalRadiusY, 2) <= 1;
    }

    public static float getFaceX(Face face, float width, float scaleX) {
        //translateX(face.getPosition().x + face.getWidth() / 2);
        return width - scaleX * (face.getPosition().x + face.getWidth() / 2);
    }

    public static float getFaceY(Face face, float height, float scaleY) {
        //translateX(face.getPosition().x + face.getWidth() / 2);
        return scaleY * (face.getPosition().y + face.getHeight() / 2);
    }
}
