/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.samples.vision.face.facetracker;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;

import com.google.android.gms.samples.vision.face.facetracker.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.face.Face;

import java.util.Locale;

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
class FaceGraphic extends GraphicOverlay.Graphic {
    private static final String TAG = "FaceGraphic";
    private static final float FACE_POSITION_RADIUS = 10.0f;
    private static final float ID_TEXT_SIZE = 40.0f;
    private static final float ID_Y_OFFSET = 50.0f;
    private static final float ID_X_OFFSET = -50.0f;
    private static final float BOX_STROKE_WIDTH = 5.0f;

    private static final int COLOR_CHOICES[] = {
        Color.BLUE,
        Color.CYAN,
        Color.GREEN,
        Color.MAGENTA,
        Color.RED,
        Color.WHITE,
        Color.YELLOW
    };
    private static int mCurrentColorIndex = 0;

    private final Paint mFacePositionPaint;
    private final Paint mIdPaint;
    private final Paint mBoxPaint;

    private final Paint paintBlue;
    private final Paint paintTrans;
    private Path path;

    private volatile Face mFace;
    private int mFaceId;
    private String mSection;
    //private float mFaceHappiness;

    FaceGraphic(GraphicOverlay overlay) {
        super(overlay);

        mCurrentColorIndex = (mCurrentColorIndex + 1) % COLOR_CHOICES.length;
        final int selectedColor = COLOR_CHOICES[mCurrentColorIndex];

        mFacePositionPaint = new Paint();
        mFacePositionPaint.setColor(selectedColor);

        mIdPaint = new Paint();
        mIdPaint.setColor(selectedColor);
        mIdPaint.setTextSize(ID_TEXT_SIZE);

        mBoxPaint = new Paint();
        mBoxPaint.setColor(selectedColor);
        mBoxPaint.setStyle(Paint.Style.STROKE);
        mBoxPaint.setStrokeWidth(BOX_STROKE_WIDTH);

        paintBlue = new Paint();
        paintBlue.setColor(Color.BLUE);
        paintBlue.setStyle(Paint.Style.STROKE);
        paintBlue.setStrokeWidth(BOX_STROKE_WIDTH);

        paintTrans = new Paint();
        paintTrans.setColor(Color.argb(150, 255, 255, 255));
        paintTrans.setStyle(Paint.Style.FILL);
    }

    void setId(int id) {
        mFaceId = id;
    }


    /**
     * Updates the face instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    void updateFace(Face face, String section) {
        mFace = face;
        mSection = section;
        postInvalidate();
    }

    /**
     * Draws the face annotations for position on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        Face face = mFace;
        if (face == null) {
            //Log.i("FaceGraphic", "face is null");
            return;
        }
        //Log.i("FaceGraphic", "face is NOT null");

        // Draws a circle at the position of the detected face, with the face's track id below.
        float x = translateX(face.getPosition().x + face.getWidth() / 2);
        float y = translateY(face.getPosition().y + face.getHeight() / 2);
        Locale l = Locale.getDefault();

        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getWidth() / 2.0f);
        float yOffset = scaleY(face.getHeight() / 2.0f);
        float left = x - xOffset;
        float top = y - yOffset;
        float right = x + xOffset;
        float bottom = y + yOffset;
        canvas.drawRect(left, top, right, bottom, mBoxPaint);

        if (FaceTrackerActivity.DEBUG && FaceTrackerActivity.DEBUG_VIDEO_FACE_INFO) {
            canvas.drawCircle(x, y, FACE_POSITION_RADIUS, mFacePositionPaint);
            canvas.drawText("id: " + mFaceId, x + ID_X_OFFSET, y + ID_Y_OFFSET, mIdPaint);
            canvas.drawText("happiness: " + String.format(l, "%.2f", face.getIsSmilingProbability()), x - ID_X_OFFSET, y - ID_Y_OFFSET, mIdPaint);
            canvas.drawText("right eye: " + String.format(l, "%.2f", face.getIsRightEyeOpenProbability()), x + ID_X_OFFSET * 2, y + ID_Y_OFFSET * 2, mIdPaint);
            canvas.drawText("left eye: " + String.format(l, "%.2f", face.getIsLeftEyeOpenProbability()), x - ID_X_OFFSET*2, y - ID_Y_OFFSET*2, mIdPaint);
            canvas.drawText("euler y: " + String.format(l, "%.2f", face.getEulerY()), xOffset, bottom + 40, mIdPaint);
            canvas.drawText("euler z: " + String.format(l, "%.2f", face.getEulerZ()), xOffset, bottom + 80, mIdPaint);
        }

        if (FaceTrackerActivity.DEBUG && FaceTrackerActivity.DEBUG_VIDEO_SECTIONS) {
            float sizeX = canvas.getWidth();
            float sizeY = canvas.getHeight();

            path = new Path();

            float o1 = (1f - 0.33f) / 2;
            float o2 = (1f - 0.55f) / 2;

            switch(mSection) {
                case "L-1":
                    path.addArc(new RectF(sizeX * o1, sizeY * o1, sizeX - sizeX * o1, sizeY - sizeY * o1), 225f, -90f);
                    path.arcTo(new RectF(sizeX * o2, sizeY * o2, sizeX - sizeX * o2, sizeY - sizeY * o2), 135f, 90f);
                    break;
                case "R-1":
                    path.addArc(new RectF(sizeX * o1, sizeY * o1, sizeX - sizeX * o1, sizeY - sizeY * o1), 45f, -90f);
                    path.arcTo(new RectF(sizeX * o2, sizeY * o2, sizeX - sizeX * o2, sizeY - sizeY * o2), 315f, 90f);
                    break;
                case "U-1":
                    path.addArc(new RectF(sizeX * o1, sizeY * o1, sizeX - sizeX * o1, sizeY - sizeY * o1), 315f, -90f);
                    path.arcTo(new RectF(sizeX * o2, sizeY * o2, sizeX - sizeX * o2, sizeY - sizeY * o2), 225f, 90f);
                    break;
                case "D-1":
                    path.addArc(new RectF(sizeX * o1, sizeY * o1, sizeX - sizeX * o1, sizeY - sizeY * o1), 135f, -90f);
                    path.arcTo(new RectF(sizeX * o2, sizeY * o2, sizeX - sizeX * o2, sizeY - sizeY * o2), 45f, 90f);
                    break;
                case "L-2":
                    path.addArc(new RectF(sizeX * o2, sizeY * o2, sizeX - sizeX * o2, sizeY - sizeY * o2), 135f, 90f);
                    path.lineTo(0f, 0f);
                    path.lineTo(0f, sizeY);
                    break;
                case "R-2":
                    path.addArc(new RectF(sizeX * o2, sizeY * o2, sizeX - sizeX * o2, sizeY - sizeY * o2), 45f, -90f);
                    path.lineTo(sizeX, 0f);
                    path.lineTo(sizeX, sizeY);
                    break;
                case "U-2":
                    path.addArc(new RectF(sizeX * o2, sizeY * o2, sizeX - sizeX * o2, sizeY - sizeY * o2), 225f, 90f);
                    path.lineTo(sizeX, 0f);
                    path.lineTo(0f, 0f);
                    break;
                case "D-2":
                    path.addArc(new RectF(sizeX * o2, sizeY * o2, sizeX - sizeX * o2, sizeY - sizeY * o2), 45f, 90f);
                    path.lineTo(0f, sizeY);
                    path.lineTo(sizeX, sizeY);
                    break;
                case "Center":
                    path.addOval(new RectF(sizeX * o1, sizeY * o1, sizeX - sizeX * o1, sizeY - sizeY * o1), Path.Direction.CW);
                    break;
                default:
                    //Log.e(TAG, "Received unknown screen section: " + mSection);
                    break;
            }

            path.close();
            canvas.drawPath(path, paintTrans);

            // Draw guidelines
            canvas.drawLine(0, 0, sizeX, sizeY, paintBlue);
            canvas.drawLine(sizeX, 0, 0, sizeY, paintBlue);
            float oval_size = o1;
            canvas.drawOval(new RectF(sizeX * oval_size, sizeY * oval_size, sizeX - sizeX * oval_size, sizeY - sizeY * oval_size), paintBlue);
            oval_size = o2;
            canvas.drawOval(new RectF(sizeX * oval_size, sizeY * oval_size, sizeX - sizeX * oval_size, sizeY - sizeY * oval_size), paintBlue);
        }
    }
}
