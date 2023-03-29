package com.swrobotics.lib.encoder;

import com.ctre.phoenix.sensors.*;
import com.swrobotics.mathlib.Angle;
import com.swrobotics.mathlib.CCWAngle;
import com.swrobotics.mathlib.CWAngle;

/**
 * Represents a CTRE CANCoder, which can measure both relative and absolute
 * angles.
 *
 * Clockwise and counterclockwise are from the perspective of looking at
 * the top (LED) side of the CANCoder.
 */
public final class CanCoder {
    private final CANCoder can;
    private final Encoder relative;
    private final Encoder absolute;

    public CanCoder(int canID) {
        this(canID, "");
    }

    public CanCoder(int canID, String canBus) {
        can = new CANCoder(canID, canBus);

        CANCoderConfiguration config = new CANCoderConfiguration();
        config.initializationStrategy = SensorInitializationStrategy.BootToAbsolutePosition;
        config.absoluteSensorRange = AbsoluteSensorRange.Unsigned_0_to_360;
        config.sensorTimeBase = SensorTimeBase.PerSecond;
        // TODO: Check if correct, documentation has conflicting information
        config.sensorDirection = false;
        can.configAllSettings(config);

        relative = new Encoder() {
            private double flip = 1;

            @Override
            public Angle getAngle() {
                return CCWAngle.deg(can.getPosition() * flip);
            }

            @Override
            public Angle getVelocity() {
                return CCWAngle.deg(can.getVelocity() * flip);
            }

            @Override
            public void setAngle(Angle angle) {
                can.setPosition(angle.ccw().deg() * flip);
            }

            @Override
            public void setInverted(boolean inverted) {
                flip = inverted ? -1 : 1;
            }
        };

        // Cannot set absolute position, it's absolute
        absolute = new Encoder() {
            private double flip = 1;

            @Override
            public Angle getAngle() {
                return CWAngle.deg(can.getAbsolutePosition() * flip);
            }

            @Override
            public Angle getVelocity() {
                return CCWAngle.deg(can.getVelocity() * flip);
            }

            @Override
            public void setInverted(boolean inverted) {
                flip = inverted ? -1 : 1;
            }
        };
    }

    public Encoder getRelative() {
        return relative;
    }

    public Encoder getAbsolute() {
        return absolute;
    }
}