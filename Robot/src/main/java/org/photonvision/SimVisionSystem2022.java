/*
 * MIT License
 *
 * Copyright (c) 2022 PhotonVision
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.photonvision;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.FieldObject2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import java.util.ArrayList;
import java.util.List;

import org.photonvision.targeting.PhotonTrackedTarget;
import org.photonvision.targeting.TargetCorner;

public class SimVisionSystem2022 {
    SimPhotonCamera cam;

    double camHorizFOVDegrees;
    double camVertFOVDegrees;
    double cameraHeightOffGroundMeters;
    double maxLEDRangeMeters;
    int cameraResWidth;
    int cameraResHeight;
    double minTargetArea;
    Transform3d cameraToRobot;

    Field2d dbgField;
    FieldObject2d dbgRobot;
    FieldObject2d dbgCamera;

    ArrayList<SimVisionTarget> tgtList;

    /**
     * Create a simulated vision system involving a camera and coprocessor mounted on a mobile robot
     * running PhotonVision, detecting one or more targets scattered around the field. This assumes a
     * fairly simple and distortion-less pinhole camera model.
     *
     * @param camName Name of the PhotonVision camera to create. Align it with the settings you use in
     *     the PhotonVision GUI.
     * @param camDiagFOVDegrees Diagonal Field of View of the camera used. Align it with the
     *     manufacturer specifications, and/or whatever is configured in the PhotonVision Setting
     *     page.
     * @param cameraToRobot Transform to move from the camera's mount position to the robot's position
     * @param maxLEDRangeMeters Maximum distance at which your camera can illuminate the target and
     *     make it visible. Set to 9000 or more if your vision system does not rely on LED's.
     * @param cameraResWidth Width of your camera's image sensor in pixels
     * @param cameraResHeight Height of your camera's image sensor in pixels
     * @param minTargetArea Minimum area that that the target should be before it's recognized as a
     *     target by the camera. Match this with your contour filtering settings in the PhotonVision
     *     GUI.
     */
    public SimVisionSystem2022(
            String camName,
            double camDiagFOVDegrees,
            Transform3d cameraToRobot,
            double maxLEDRangeMeters,
            int cameraResWidth,
            int cameraResHeight,
            double minTargetArea) {
        this.cameraToRobot = cameraToRobot;
        this.maxLEDRangeMeters = maxLEDRangeMeters;
        this.cameraResWidth = cameraResWidth;
        this.cameraResHeight = cameraResHeight;
        this.minTargetArea = minTargetArea;

        // Calculate horizontal/vertical FOV by similar triangles
        double hypotPixels = Math.hypot(cameraResWidth, cameraResHeight);
        this.camHorizFOVDegrees = camDiagFOVDegrees * cameraResWidth / hypotPixels;
        this.camVertFOVDegrees = camDiagFOVDegrees * cameraResHeight / hypotPixels;

        cam = new SimPhotonCamera(camName);
        tgtList = new ArrayList<>();

        dbgField = new Field2d();
        dbgRobot = dbgField.getRobotObject();
        dbgCamera = dbgField.getObject(camName + " Camera");
        SmartDashboard.putData(camName + " Sim Field", dbgField);
    }

    /**
     * Add a target on the field which your vision system is designed to detect. The PhotonCamera from
     * this system will report the location of the robot relative to the subset of these targets which
     * are visible from the given robot position.
     *
     * @param target Target to add to the simulated field
     */
    public void addSimVisionTarget(SimVisionTarget target) {
        tgtList.add(target);
        dbgField
                .getObject("Target " + Integer.toString(target.targetID))
                .setPose(target.targetPose.toPose2d());
        ;
    }

    /**
     * Adjust the camera position relative to the robot. Use this if your camera is on a gimbal or
     * turret or some other mobile platform.
     *
     * @param newCameraToRobot New Transform from the robot to the camera
     */
    public void moveCamera(Transform3d newCameraToRobot) {
        this.cameraToRobot = newCameraToRobot;
    }

//     /**
//      * Periodic update. Call this once per frame of image data you wish to process and send to
//      * NetworkTables
//      *
//      * @param robotPoseMeters current pose of the robot on the field. Will be used to calculate which
//      *     targets are actually in view, where they are at relative to the robot, and relevant
//      *     PhotonVision parameters.
//      */
//     public void processFrame(Pose2d robotPoseMeters) {
//         var robotPose3d =
//                 new Pose3d(
//                         robotPoseMeters.getX(),
//                         robotPoseMeters.getY(),
//                         0.0,
//                         new Rotation3d(0, 0, robotPoseMeters.getRotation().getRadians()));
//         processFrame(robotPose3d);
//     }

    /**
     * Periodic update. Call this once per frame of image data you wish to process and send to
     * NetworkTables
     *
     * @param robotPoseMeters current pose of the robot in space. Will be used to calculate which
     *     targets are actually in view, where they are at relative to the robot, and relevant
     *     PhotonVision parameters.
     */
    public void processFrame(Pose2d robotPoseMeters) {
        Transform2d cameraToRobot2d = new Transform2d(cameraToRobot.getTranslation().toTranslation2d(), cameraToRobot.getRotation().toRotation2d());
        Pose2d cameraPose2d = robotPoseMeters.transformBy(cameraToRobot2d.inverse());

        Pose3d cameraPose = new Pose3d(cameraPose2d.getX(), cameraPose2d.getY(), 0.0 , new Rotation3d(0, 0, cameraPose2d.getRotation().getRadians()));

        dbgRobot.setPose(robotPoseMeters);
        dbgCamera.setPose(cameraPose2d);

        ArrayList<PhotonTrackedTarget> visibleTgtList = new ArrayList<>(tgtList.size());

        tgtList.forEach(
                (tgt) -> {
                //     var camToTargetTrans = new Transform3d(cameraPose, tgt.targetPose);
                    var camToTargetTrans = new Transform2d(cameraPose2d, tgt.targetPose.toPose2d());
                    var t = camToTargetTrans.getTranslation();

                    // Rough approximation of the alternate solution, which is (so far) always incorrect.
                    var altTrans =
                            new Translation2d(
                                    t.getX(),
                                    -1.0 * t.getY()); // mirrored across camera axis in Y direction
                    var altRot = camToTargetTrans.getRotation().times(-1.0); // flipped
                    var camToTargetTransAlt = new Transform2d(altTrans, altRot);

                    double distMeters = t.getNorm();

                    double area_px = tgt.tgtAreaMeters2 / getM2PerPx(distMeters);

                    double yawDegrees = Units.radiansToDegrees(Math.atan2(t.getY(), t.getX()));

                    double camHeightAboveGround = cameraPose.getZ();
                    double tgtHeightAboveGround = tgt.targetPose.getZ();
                    double camPitchDegrees = Units.radiansToDegrees(cameraPose.getRotation().getY());

                    var transformAlongGround =
                            new Transform2d(cameraPose.toPose2d(), tgt.targetPose.toPose2d());
                    double distAlongGround = transformAlongGround.getTranslation().getNorm();

                    double pitchDegrees =
                            Units.radiansToDegrees(
                                            Math.atan2((tgtHeightAboveGround - camHeightAboveGround), distAlongGround))
                                    - camPitchDegrees;

                    if (camCanSeeTarget(distMeters, yawDegrees, pitchDegrees, area_px)) {

                        var camToTargetTrans3d = new Transform3d(
                                new Translation3d(camToTargetTrans.getX(), camToTargetTrans.getY(), 0.0), new Rotation3d(0, 0, camToTargetTrans.getRotation().getRadians()));

                        var camToTargetTransAlt3d = new Transform3d(
                                new Translation3d(camToTargetTransAlt.getX(), camToTargetTransAlt.getY(), 0.0), new Rotation3d(0, 0, camToTargetTransAlt.getRotation().getRadians()));

                        // TODO simulate target corners
                        visibleTgtList.add(
                                new PhotonTrackedTarget(
                                        yawDegrees,
                                        pitchDegrees,
                                        area_px,
                                        0.0,
                                        tgt.targetID,
                                        camToTargetTrans3d,
                                        camToTargetTransAlt3d,
                                        0.0, // TODO - simulate ambiguity when straight on?
                                        List.of(
                                                new TargetCorner(0, 0), new TargetCorner(0, 0),
                                                new TargetCorner(0, 0), new TargetCorner(0, 0))));
                    }
                });

        cam.submitProcessedFrame(0.0, visibleTgtList);
    }

    double getM2PerPx(double dist) {
        double widthMPerPx =
                2 * dist * Math.tan(Units.degreesToRadians(this.camHorizFOVDegrees) / 2) / cameraResWidth;
        double heightMPerPx =
                2 * dist * Math.tan(Units.degreesToRadians(this.camVertFOVDegrees) / 2) / cameraResHeight;
        return widthMPerPx * heightMPerPx;
    }

    boolean camCanSeeTarget(double distMeters, double yaw, double pitch, double area) {
        boolean inRange = (distMeters < this.maxLEDRangeMeters);
        boolean inHorizAngle = Math.abs(yaw) < (this.camHorizFOVDegrees / 2);
        boolean inVertAngle = Math.abs(pitch) < (this.camVertFOVDegrees / 2);
        boolean targetBigEnough = area > this.minTargetArea;
        return (inRange && inHorizAngle && inVertAngle && targetBigEnough);
    }
}