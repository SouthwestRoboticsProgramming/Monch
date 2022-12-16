package com.swrobotics.lib.wpilib;

import com.swrobotics.lib.schedule.Scheduler;
import com.swrobotics.lib.time.Duration;
import com.swrobotics.lib.time.Repeater;
import com.swrobotics.lib.time.TimeUnit;
import com.swrobotics.profiler.Profiler;

import edu.wpi.first.hal.HAL;
import edu.wpi.first.wpilibj.RobotBase;

/**
 * The base class for any robot using this library.
 */
public abstract class AbstractRobot extends RobotBase {
    /**
     * Gets whether the robot code is currently running in a simulation.
     * 
     * @return whether in a simulation
     */
    public static boolean isSimulation() {
        return !isReal();
    }

    private static AbstractRobot INSTANCE = null;

    /**
     * Gets the one robot instance.
     * 
     * @return instance
     */
    public static AbstractRobot get() {
        return INSTANCE;
    }

    private final double periodicPerSecond;

    private volatile boolean running;
    private RobotState prevState = null;

    /**
     * Initializes the robot and the library.
     * 
     * @param periodicPerSecond desired number of periodic invocations per second
     */
    public AbstractRobot(double periodicPerSecond) {
        this.periodicPerSecond = periodicPerSecond;

        if (INSTANCE != null)
            throw new IllegalStateException("Robot already initialized");
        INSTANCE = this;

        running = false;
    }

    /**
     * This method is where your robot code should schedule its subsystems.
     */
    protected abstract void addSubsystems();

    // Override these to use them
    public void periodic() {}
    public void disabledInit() {}
    public void disabledPeriodic() {}
    public void autonomousInit() {}
    public void autonomousPeriodic() {}
    public void teleopInit() {}
    public void teleopPeriodic() {}
    public void testInit() {}
    public void testPeriodic() {}

    @Override
    public final void startCompetition() {
        running = true;
        addSubsystems();

        System.out.println("**** Robot program startup complete ****");
        HAL.observeUserProgramStarting(); // This is not an error even if VS Code disagrees

        // Initialize periodic repeater
        Repeater repeater = new Repeater(
                new Duration(1 / periodicPerSecond, TimeUnit.SECONDS),
                () -> {
                    Profiler.beginMeasurements("Root");

                    RobotState state = getCurrentState();
                    if (state != prevState) {
                        switch (state) {
                            case DISABLED: disabledInit(); break;
                            case AUTONOMOUS: autonomousInit(); break;
                            case TELEOP: teleopInit(); break;
                            case TEST: testInit(); break;
                        }
                    }
                    prevState = state;
                    periodic();
                    switch (state) {
                        case DISABLED: disabledPeriodic(); break;
                        case AUTONOMOUS: autonomousPeriodic(); break;
                        case TELEOP: teleopPeriodic(); break;
                        case TEST: testPeriodic(); break;
                    }
                    Scheduler.get().periodicState(state);

                    Profiler.endMeasurements();
                }
        );

        Thread currentThread = Thread.currentThread();
        while (running && !currentThread.isInterrupted()) {
            repeater.tick();
        }
    }

    @Override
    public final void endCompetition() {
        running = false;
    }

    /**
     * Gets the current state of the robot.
     * 
     * @return current state
     */
    public final RobotState getCurrentState() {
        if (isDisabled()) return RobotState.DISABLED;
        if (isAutonomous()) return RobotState.AUTONOMOUS;
        if (isTeleop()) return RobotState.TELEOP;
        if (isTest()) return RobotState.TEST;

        throw new IllegalStateException("Illegal robot state");
    }

    /**
     * Gets the number of periodic invocations per second the main loop is
     * running at.
     * 
     * @return periodic per second
     */
    public final double getPeriodicPerSecond() {
        return periodicPerSecond;
    }
}
