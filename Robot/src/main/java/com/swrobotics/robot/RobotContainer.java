// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package com.swrobotics.robot;

import java.util.ArrayList;
import java.util.HashMap;

import com.pathplanner.lib.PathConstraints;
import com.pathplanner.lib.PathPlanner;
import com.pathplanner.lib.PathPlannerTrajectory;
import com.pathplanner.lib.auto.SwerveAutoBuilder;
import com.swrobotics.mathlib.Vec2d;
import com.swrobotics.messenger.client.MessengerClient;
import com.swrobotics.robot.commands.DefaultDriveCommand;
import com.swrobotics.robot.commands.FollowPathCommand;
import com.swrobotics.robot.commands.LightCommand;
import com.swrobotics.robot.commands.LightTest;
import com.swrobotics.robot.commands.PathfindToPointCommand;
import com.swrobotics.robot.subsystems.DrivetrainSubsystem;
import com.swrobotics.robot.subsystems.Lights;
import com.swrobotics.robot.subsystems.Pathfinder;
import com.swrobotics.robot.subsystems.Vision;
import com.swrobotics.robot.subsystems.Lights.Color;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.GenericHID;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.PrintCommand;
import edu.wpi.first.wpilibj2.command.button.Button;

/**
 * This class is where the bulk of the robot should be declared. Since
 * Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in
 * the {@link Robot}
 * periodic methods (other than the scheduler calls). Instead, the structure of
 * the robot (including
 * subsystems, commands, and button mappings) should be declared here.
 */
public class RobotContainer {
    private static final String MESSENGER_HOST_ROBOT = "10.21.29.3";
    private static final String MESSENGER_HOST_SIM = "localhost";
    private static final int MESSENGER_PORT = 5805;
    private static final String MESSENGER_NAME = "Robot";

    private final SendableChooser<Command> autoSelector;

    // The robot's subsystems and commands are defined here...
    private final DrivetrainSubsystem m_drivetrainSubsystem = new DrivetrainSubsystem();
    private final Lights m_lights = new Lights();
    private final Vision m_vision = new Vision(m_drivetrainSubsystem);

    private final XboxController m_controller = new XboxController(0);

    private final MessengerClient messenger;

    /**
     * The container for the robot. Contains subsystems, OI devices, and commands.
     */
    public RobotContainer() {
        // Set up the default command for the drivetrain.
        // The controls are for field-oriented driving:
        // Left stick Y axis -> forward and backwards movement
        // Left stick X axis -> left and right movement
        // Right stick X axis -> rotation
        m_drivetrainSubsystem.setDefaultCommand(new DefaultDriveCommand(
                m_drivetrainSubsystem,
                () -> -modifyAxis(m_controller.getLeftY()) * DrivetrainSubsystem.MAX_VELOCITY_METERS_PER_SECOND,
                () -> -modifyAxis(m_controller.getLeftX()) * DrivetrainSubsystem.MAX_VELOCITY_METERS_PER_SECOND,
                () -> -modifyAxis(m_controller.getRightX())
                        * DrivetrainSubsystem.MAX_ANGULAR_VELOCITY_RADIANS_PER_SECOND));

        m_vision.register();

        // Configure the button bindings
        configureButtonBindings();

        // Initialize Messenger
        messenger = new MessengerClient(
                RobotBase.isSimulation() ? MESSENGER_HOST_SIM : MESSENGER_HOST_ROBOT,
                MESSENGER_PORT,
                MESSENGER_NAME
        );

        // Generate autos to choose from
        Command blankAuto = new InstantCommand();
        Command printAuto = new PrintCommand("Auto chooser is working!");

        Command justLights = new LightCommand(m_lights, Color.BLUE, 0.2).andThen(
            new LightCommand(m_lights, Color.GOLD, 1),
            new LightCommand(m_lights, Color.GREEN, 2),
            new LightCommand(m_lights, Color.WHITE, 3),
            new LightCommand(m_lights, Color.RAINBOW, 5.0)
        );

        // Command justLights = new LightTest(m_lights);

        // Generate drive commands using PathPlanner
        HashMap<String, Command> eventMap = new HashMap<>();

        // Add all of the colors as potential markers
        for (Color color : Color.values()) {
            // eventMap.put(color.name(), new PrintCommand("Color: " + color.name()));
            System.out.println("Add color: " + color.name());
            eventMap.put(color.name(), new LightCommand(m_lights, color, 0.02));
        }

        eventMap.put("marker1", new PrintCommand("Passed marker 1"));

        SwerveAutoBuilder builder = m_drivetrainSubsystem.getAutoBuilder(eventMap);

        Command smallPathAuto = builder.fullAuto(getPath("Small Path"));
        Command bigPathAuto = builder.fullAuto(getPath("Big Path"));
        Command twentyOne = builder.fullAuto(getPath("2129"));
        Command lightShow = builder.fullAuto(getPath("Light Show"));
        Command tinyAuto = builder.fullAuto(getPath("Tiny Path"));
        Command doorToWindow = builder.fullAuto(getPath("Door to Window"));

        m_drivetrainSubsystem.showTrajectory(getPath("Door to Window").get(0));
        // m_drivetrainSubsystem.showTrajectory(getPath("Small Path").get(0));

        Pathfinder pathfinder = new Pathfinder(messenger, m_drivetrainSubsystem);

        Command pathTest = new FollowPathCommand(m_drivetrainSubsystem, m_lights);

        // For now PathfindToPointCommand resets odometry pose to center of field on init
        // This should be done automatically by another system later (i.e. Vision or ShuffleLog)
        Command pathToPoint = new PathfindToPointCommand(
                m_drivetrainSubsystem,
                pathfinder,
                m_lights,
                new Vec2d(8.2296 + 2, 8.2296/2), // 2 meters forward from field center
                Rotation2d.fromDegrees(90)
        );

        // Create a chooser to select the autonomous
        autoSelector = new SendableChooser<>();
        autoSelector.setDefaultOption("No Auto", blankAuto);
        autoSelector.addOption("Print Auto", printAuto);
        autoSelector.addOption("Small Path", smallPathAuto);
        autoSelector.addOption("Big Path", bigPathAuto);
        autoSelector.addOption("2129", twentyOne);
        autoSelector.addOption("Light Show", lightShow);
        autoSelector.addOption("Tiny Auto", tinyAuto);
        autoSelector.addOption("Door to Window", doorToWindow);
        autoSelector.addOption("Follow Path", pathTest);
        autoSelector.addOption("Path to Point", pathToPoint);
        autoSelector.addOption("Just lights", justLights);
        SmartDashboard.putData(autoSelector);
    }

    /**
     * Use this method to define your button->command mappings. Buttons can be
     * created by
     * instantiating a {@link GenericHID} or one of its subclasses ({@link
     * edu.wpi.first.wpilibj.Joystick} or {@link XboxController}), and then passing
     * it to a {@link
     * edu.wpi.first.wpilibj2.command.button.JoystickButton}.
     */
    private void configureButtonBindings() {
        // Back button zeros the gyroscope
        new Button(m_controller::getBackButton)
                // No requirements because we don't need to interrupt anything
                .whenPressed(m_drivetrainSubsystem::zeroGyroscope);
    }

    /**
     * Use this to pass the autonomous command to the main {@link Robot} class.
     *
     * @return the command to run in autonomous
     */
    public Command getAutonomousCommand() {
        return autoSelector.getSelected();
    }

    private static double deadband(double value, double deadband) {
        if (Math.abs(value) > deadband) {
            if (value > 0.0) {
                return (value - deadband) / (1.0 - deadband);
            } else {
                return (value + deadband) / (1.0 - deadband);
            }
        } else {
            return 0.0;
        }
    }

    private static double modifyAxis(double value) {
        // Deadband
        value = deadband(value, 0.05);

        // Square the axis
        value = Math.copySign(value * value, value);

        return value;
    }

    private static ArrayList<PathPlannerTrajectory> getPath(String name) {
        return PathPlanner.loadPathGroup(name, new PathConstraints(0.2, 0.1)); // FIXME: Add throws and catch
    }

    public MessengerClient getMessenger() {
        return messenger;
    }
}