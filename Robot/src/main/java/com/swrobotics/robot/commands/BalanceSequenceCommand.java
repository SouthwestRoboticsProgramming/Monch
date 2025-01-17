package com.swrobotics.robot.commands;

import com.swrobotics.lib.drive.swerve.commands.DriveBlindCommand;
import com.swrobotics.mathlib.Angle;
import com.swrobotics.robot.RobotContainer;
import com.swrobotics.robot.config.NTData;
import com.swrobotics.robot.subsystems.drive.DrivetrainSubsystem;

import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;

import java.util.function.Supplier;

public class BalanceSequenceCommand extends SequentialCommandGroup {
    public BalanceSequenceCommand(RobotContainer robot, boolean reversed) {
        Supplier<Angle> angle = DrivetrainSubsystem.FIELD::getAllianceForwardAngle;

        if (reversed) {
            angle = DrivetrainSubsystem.FIELD::getAllianceReverseAngle;
        }

        double blindSpeed = NTData.BALANCE_BLIND_SPEED.get();
        addCommands(
                new StartBalanceCommand(robot, angle, blindSpeed, false).withTimeout(3),
                new DriveBlindCommand(robot.swerveDrive, angle, blindSpeed, false)
                        .withTimeout(1.25), // Keep driving for 1 second
                new AutoBalanceCommand(robot));
    }
}
