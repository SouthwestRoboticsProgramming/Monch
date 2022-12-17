package com.swrobotics.robot.subsystems;

import java.util.HashMap;

import com.kauailabs.navx.frc.AHRS;
import com.pathplanner.lib.auto.PIDConstants;
import com.pathplanner.lib.auto.SwerveAutoBuilder;
import com.swervedrivespecialties.swervelib.SdsModuleConfigurations;
import com.swrobotics.robot.VisionConstants;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveDriveOdometry;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.Trajectory;
import edu.wpi.first.wpilibj.SPI.Port;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

public class DrivetrainSubsystem extends SubsystemBase {

    public static final double DRIVETRAIN_TRACKWIDTH_METERS = 0.3; // FIXME - Measure
    public static final double DRIVETRAIN_WHEELBASE_METERS = 0.3; // FIXME - Measure

    /**
     * The maximum velocity of the robot in meters per second.
     * <p>
     * This is a measure of how fast the robot should be able to drive in a straight
     * line.
     */
    public static final double MAX_VELOCITY_METERS_PER_SECOND = 6380.0 / 60.0 *
            SdsModuleConfigurations.MK3_STANDARD.getDriveReduction() *
            SdsModuleConfigurations.MK3_STANDARD.getWheelDiameter() * Math.PI;
    /**
     * The maximum angular velocity of the robot in radians per second.
     * <p>
     * This is a measure of how fast the robot can rotate in place.
     */
    // Here we calculate the theoretical maximum angular velocity. You can also
    // replace this with a measured amount.
    public static final double MAX_ANGULAR_VELOCITY_RADIANS_PER_SECOND = MAX_VELOCITY_METERS_PER_SECOND /
            Math.hypot(DRIVETRAIN_TRACKWIDTH_METERS / 2.0, DRIVETRAIN_WHEELBASE_METERS / 2.0);

    private final SwerveDriveKinematics kinematics = new SwerveDriveKinematics(
            // Front left
            new Translation2d(DRIVETRAIN_TRACKWIDTH_METERS / 2.0, DRIVETRAIN_WHEELBASE_METERS / 2.0),
            // Front right
            new Translation2d(DRIVETRAIN_TRACKWIDTH_METERS / 2.0, -DRIVETRAIN_WHEELBASE_METERS / 2.0),
            // Back left
            new Translation2d(-DRIVETRAIN_TRACKWIDTH_METERS / 2.0, DRIVETRAIN_WHEELBASE_METERS / 2.0),
            // Back right
            new Translation2d(-DRIVETRAIN_TRACKWIDTH_METERS / 2.0, -DRIVETRAIN_WHEELBASE_METERS / 2.0));

    // Initialize a NavX over MXP port
    private final AHRS gyro = new AHRS(Port.kMXP);
    private Rotation2d gyroOffset = new Rotation2d(); // Subtracted to get angle

    // Create a field sim to view where the odometry thinks we are
    public final Field2d field = new Field2d();

    private final SwerveModule[] modules = new SwerveModule[] {
        new SwerveModule(5, 9, 1,   225.0 + 180, new Translation2d(1, 1)), // Front left
        new SwerveModule(6, 10, 2,   95.7 + 180, new Translation2d(1, -1)),  // Front right
        new SwerveModule(7, 11, 3, 77.871 + 180, new Translation2d(-1, 1)),  // Back left
        new SwerveModule(8, 12, 4, 44.297 + 180, new Translation2d(-1, -1))  // Back right
    };
    
    private final SwerveDriveOdometry odometry;
    
    public DrivetrainSubsystem() {
        SmartDashboard.putData("Field", field);
        System.out.println("Target Position: " + VisionConstants.DOOR_POSE.toPose2d());
        field.getObject("target").setPose(VisionConstants.DOOR_POSE.toPose2d());
        // field.getObject("traj").setTrajectory(new Trajectory()); // Clear trajectory view

        printEncoderOffsets();

        odometry = new SwerveDriveOdometry(kinematics, getGyroscopeRotation());
    }

    public Rotation2d getGyroscopeRotation() {
        return gyro.getRotation2d().minus(gyroOffset);
    }

    private Rotation2d getRawGyroscopeRotation() {
        return gyro.getRotation2d();
    }

    public void zeroGyroscope() {
        gyroOffset = getRawGyroscopeRotation();
    }

    /**
     * 
     * @param newRotation New gyro rotation, CCW +
     */
    public void setGyroscopeRotation(Rotation2d newRotation) {
        gyroOffset = getRawGyroscopeRotation().minus(newRotation);
    }

    public void setChassisSpeeds(ChassisSpeeds speeds) {
        SwerveModuleState[] states = kinematics.toSwerveModuleStates(speeds);
        SwerveDriveKinematics.desaturateWheelSpeeds(states, 4.0);

        setModuleStates(states);
    }

    public Pose2d getPose() {
        return odometry.getPoseMeters();
    }

    public void resetPose(Pose2d newPose) {
        odometry.resetPosition(newPose, getGyroscopeRotation());
    }

    public void setModuleStates(SwerveModuleState[] states) {
        for (int i = 0; i < modules.length; i++) {
            modules[i].setState(states[i]);
        }
    }

    public SwerveModuleState[] getModuleStates() {
        SwerveModuleState[] states = new SwerveModuleState[modules.length];
        for (int i = 0; i < modules.length; i++) {
            states[i] = modules[i].getState();
        }

        return states;
    }

    public SwerveAutoBuilder getAutoBuilder(HashMap<String, Command> eventMap) {
        // Create the AutoBuilder. This only needs to be created once when robot code
        // starts, not every time you want to create an auto command. A good place to
        // put this is in RobotContainer along with your subsystems.
        SwerveAutoBuilder autoBuilder = new SwerveAutoBuilder(
                this::getPose, // Pose2d supplier
                this::resetPose, // Pose2d consumer, used to reset odometry at the beginning of auto
                kinematics, // SwerveDriveKinematics
                new PIDConstants(5.0, 0.0, 0.0), // PID constants to correct for translation error (used to create the X
                                                 // and Y PID controllers)
                new PIDConstants(0.5, 0.0, 0.0), // PID constants to correct for rotation error (used to create the
                                                 // rotation controller)
                this::setModuleStates, // Module states consumer used to output to the drive subsystem
                eventMap,
                this // The drive subsystem. Used to properly set the requirements of path following
                     // commands
        );

        return autoBuilder;
    }

    public void showTrajectory(Trajectory trajectory) {
        field.getObject("traj").setTrajectory(trajectory);
    }

    public void printEncoderOffsets() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < modules.length; i++) {
            builder.append("M");
            builder.append(i);
            builder.append(" ");

            builder.append(String.format("%.3f", modules[i].getCalibrationAngle()));
            builder.append(" ");
        }
        System.out.println(builder);
    }

    @Override
    public void periodic() {
        // Freshly estimated the new rotation based off of the wheels
        
        odometry.update(getGyroscopeRotation(), getModuleStates());
        
        field.setRobotPose(getPose());
    }
    
    @Override
    public void simulationPeriodic() {
        ChassisSpeeds estimatedChassis = kinematics.toChassisSpeeds(getModuleStates());
        gyroOffset = gyroOffset.plus(new Rotation2d(-estimatedChassis.omegaRadiansPerSecond * 0.02));
    }
}
