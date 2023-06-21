package com.swrobotics.lib.motor;

import com.swrobotics.lib.encoder.Encoder;
import com.swrobotics.lib.encoder.SimEncoder;
import com.swrobotics.mathlib.Angle;
import com.swrobotics.mathlib.CCWAngle;
import com.swrobotics.mathlib.MathUtil;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

import java.util.function.Supplier;

// FIXME: Do a proper simulation and fully implement
//        This is currently assuming no motor load with no inertia
public final class SimMotor extends SubsystemBase implements FeedbackMotor {
    public static final class MotorCaps {
        public final Angle freeSpeed;
        public final double sensorUnitsPerRot;
        public final double sensorUnitsPerRPS;
        public final double pidCalcInterval;
        public final double pidOutputScale;

        public MotorCaps(Angle freeSpeed, double sensorUnitsPerRot, double sensorUnitsPerRPS, double pidCalcInterval, double pidOutputScale) {
            this.freeSpeed = freeSpeed;
            this.sensorUnitsPerRot = sensorUnitsPerRot;
            this.sensorUnitsPerRPS = sensorUnitsPerRPS;
            this.pidCalcInterval = pidCalcInterval;
            this.pidOutputScale = pidOutputScale;
        }
    }

    public static final MotorCaps TALON_FX = new MotorCaps(CCWAngle.rad(Units.rotationsPerMinuteToRadiansPerSecond(6380)), 2048, 2048/10.0, 0.001, 1023);
    public static final MotorCaps NEO = new MotorCaps(CCWAngle.rad(Units.rotationsPerMinuteToRadiansPerSecond(5676)), 1, 60, 0.001, 1);
    public static final MotorCaps NEO550 = new MotorCaps(CCWAngle.rad(Units.rotationsPerMinuteToRadiansPerSecond(11000)), 1, 60, 0.001, 1);

    private enum ControlMode {
        PERCENT,
        POSITION,
        VELOCITY
    }

    private final MotorCaps caps;
    private final SimEncoder integratedEncoder;
    private Angle rawAngle;

    private double flip;
    private ControlMode controlMode;
    private Supplier<Double> controlModeFn;

    // PIDF constant units:
    // kP: output per error
    // kI: (output per error) per period
    // kD: output per (error per period)
    // kF: output per setpoint
    private double kP, kI, kD, kF;
    private double integralAcc, prevError;

    public SimMotor(MotorCaps caps) {
        if (!RobotBase.isSimulation()) {
            DriverStation.reportError("SimMotor used on real robot!", true);
        }

        this.caps = caps;
        rawAngle = Angle.ZERO;
        flip = 1;
        integratedEncoder = new SimEncoder(() -> rawAngle.mul(flip));

        kP = kI = kD = kF = 0;
        integralAcc = prevError = 0;

        stop();
    }

    @Override
    public void periodic() {
        rawAngle = rawAngle.add(caps.freeSpeed.mul(MathUtil.clamp(controlModeFn.get(), -1, 1) * flip * 0.02));
    }

    // Setpoint and measure are in native sensor units
    private double calcPID(double measure, double setpoint) {
        double error = setpoint - measure;
        double p = error * kP;

        // error = inc per 0.02s, equivalent to (inc per period) * (period per 0.02s)
        integralAcc += error * kI;

        // (error - prevError) = delta per 0.02s
        // (error - prevError) / 0.02 = delta per 1s
        // (error - prevError) / 0.02 * caps.pidCalcInterval = delta per interval
        double d = (error - prevError) / 0.02 * caps.pidCalcInterval * kD;
        prevError = error;

        double f = setpoint * kF;

        return MathUtil.clamp((p + integralAcc + d + f) / caps.pidOutputScale, -1, 1);
    }

    @Override
    public void setPercentOut(double percent) {
        controlMode = ControlMode.PERCENT;
        controlModeFn = () -> percent;
    }

    @Override
    public void setPosition(Angle position) {
        if (controlMode != ControlMode.POSITION)
            resetIntegrator();
        controlMode = ControlMode.POSITION;
        controlModeFn = () -> calcPID(
                integratedEncoder.getAngle().ccw().rot() * caps.sensorUnitsPerRot,
                position.ccw().rot() * caps.sensorUnitsPerRot);
    }

    @Override
    public void setVelocity(Angle velocity) {
        if (controlMode != ControlMode.VELOCITY)
            resetIntegrator();
        controlMode = ControlMode.VELOCITY;
        controlModeFn = () -> calcPID(
                integratedEncoder.getVelocity().ccw().rot() * caps.sensorUnitsPerRPS,
                velocity.ccw().rot() * caps.sensorUnitsPerRPS);
    }

    /**
     * @return integrated encoder. Guaranteed to be a SimEncoder
     */
    @Override
    public Encoder getIntegratedEncoder() {
        return integratedEncoder;
    }

    @Override
    public void useExternalEncoder(Encoder encoder) {
        throw new AssertionError("Not yet implemented");
    }

    @Override
    public void setInverted(boolean inverted) {
        flip = inverted ? -1 : 1;
    }

    @Override
    public void follow(Motor leader) {
        throw new AssertionError("Not yet implemented");
    }

    @Override
    public void resetIntegrator() {
        integralAcc = 0;
        prevError = 0;
    }

    @Override
    public void setP(double kP) {
        this.kP = kP;
    }

    @Override
    public void setI(double kI) {
        this.kI = kI;
    }

    @Override
    public void setD(double kD) {
        this.kD = kD;
    }

    @Override
    public void setF(double kF) {
        this.kF = kF;
    }
}