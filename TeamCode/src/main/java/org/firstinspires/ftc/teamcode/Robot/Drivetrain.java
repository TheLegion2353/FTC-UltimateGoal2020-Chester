package org.firstinspires.ftc.teamcode.Robot;
import com.qualcomm.hardware.bosch.JustLoggingAccelerationIntegrator;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.hardware.bosch.BNO055IMU;
import org.firstinspires.ftc.robotcore.external.Func;
import org.firstinspires.ftc.robotcore.external.navigation.*;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.PID;
import java.util.concurrent.TimeUnit;

import static org.firstinspires.ftc.robotcore.external.BlocksOpModeCompanion.hardwareMap;


public class Drivetrain extends RobotPart {
	private ElapsedTime clock = null;
	private ControlType control;
	private HardwareController leftGroup = null;
	private HardwareController rightGroup = null;
	private HardwareController centerGroup = null;
	private PID anglePIDController = null;
	private PID xPIDController = null;
	private PID yPIDController = null;
	private double xPosition = 0;
	private double yPosition = 0;
	private double targetX = 0;
	private double targetY = 0;
	private double angle = 0;
	private double targetAngle = 0;
	private boolean needManualOdometry = true;
	private boolean init = true;
	private double lastEncoderX = 0;
	private double lastEncoderY = 0;
	private boolean spinInit = false;
	private double netPowerLeft = 0;
	private double netPowerRight = 0;
	private double netPowerCenter = 0;
	private Telemetry telemetry = null;
	private double IMUAngleAcum = 0;
	private double lastIMUAngle = 0;

	BNO055IMU imu;

	// State used for updating telemetry
	Orientation angles;

	public Drivetrain(ControlType ct, Gamepad gp, Telemetry t) {
		super(gp);
		telemetry = t;
		anglePIDController = new PID(0.01, 0, 0,0);
		xPIDController = new PID(0.00075, 0, 0.000025, 0.00001);
		yPIDController = new PID(0.00075, 0, 0.000025, 0.00001);
		control = ct;
		leftGroup = new HardwareController();
		rightGroup = new HardwareController();
		centerGroup = new HardwareController();
		centerGroup.setDirection(DcMotor.Direction.REVERSE);
	}

	public Drivetrain(ControlType ct, Gamepad gp) {
		super(gp);
		anglePIDController = new PID(0, 0, 0,0);
		xPIDController = new PID(0.001, 0, 0, 0);
		yPIDController = new PID(0.001, 0, 0, 0);
		control = ct;
		leftGroup = new HardwareController();
		rightGroup = new HardwareController();
		centerGroup = new HardwareController();
		centerGroup.setDirection(DcMotor.Direction.REVERSE);
	}

	public void setLeftGroup(DcMotor.RunMode mode, DcMotor ... motors) {
		leftGroup = new HardwareController(mode, motors);
	}

	public void setRightGroup(DcMotor.RunMode mode, DcMotor ... motors) {
		rightGroup = new HardwareController(mode, motors);
	}

	public void setSlideGroup(DcMotor.RunMode mode, DcMotor ... motors) {
		centerGroup = new HardwareController(mode, motors);
		centerGroup.setDirection(DcMotor.Direction.REVERSE);
	}

	public boolean move(double x, double y) {
		targetX = x;
		targetY = y;
		return (Math.abs(targetX - xPosition) < 50 && Math.abs(targetY - yPosition) < 50);
	}

	public boolean moveAngle(double a) {
		targetAngle = a;
		return (Math.abs(targetAngle - angle) < 0.5d);
	}

	public boolean move(double x, double y, double a) {
		targetX = x;
		targetY = y;
		targetAngle = a;
		return (Math.abs(targetX - xPosition) < 50 && Math.abs(targetY - yPosition) < 50 && Math.abs(targetAngle - angle) < 2);
	}

	public void setPosition(double x, double y, double a) {
		xPosition = x;
		yPosition = y;
		angle = a;
		IMUAngleAcum = a;
		needManualOdometry = false; //will set to false so the odometry calc doesn't get run.
	}

	@Override
	protected void autonomousUpdate() {
		if (init) {  // setting up the IMU
			BNO055IMU.Parameters parameters = new BNO055IMU.Parameters();
			parameters.angleUnit = BNO055IMU.AngleUnit.DEGREES;
			parameters.accelUnit = BNO055IMU.AccelUnit.METERS_PERSEC_PERSEC;
			parameters.calibrationDataFile = "BNO055IMUCalibration.json"; // see the calibration sample opmode
			parameters.loggingEnabled = true;
			parameters.loggingTag = "IMU";
			parameters.accelerationIntegrationAlgorithm = new JustLoggingAccelerationIntegrator();
			imu.initialize(parameters);
			init = false;
		}

		angles = imu.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES);  // getting the orientation from the IMU.

		double currentIMUAngle = AngleUnit.DEGREES.fromUnit(angles.angleUnit, angles.firstAngle);
		if ((currentIMUAngle - lastIMUAngle) > 300) {  // rollover check
			IMUAngleAcum += (currentIMUAngle - lastIMUAngle) - 360;
		} else if ((currentIMUAngle - lastIMUAngle) < -300) {
			IMUAngleAcum += (currentIMUAngle - lastIMUAngle) + 360;
		} else {
			IMUAngleAcum += currentIMUAngle - lastIMUAngle;
		}

		lastIMUAngle = AngleUnit.DEGREES.fromUnit(angles.angleUnit, angles.firstAngle);

		if (needManualOdometry) {
			angle = IMUAngleAcum;
			double deltaX = (centerGroup.getPos() - lastEncoderX) / (28.0d * 40.0d) * (2 * Math.PI * 45.0d);
			double deltaY = (((leftGroup.getPos() - rightGroup.getPos()) / 2.0d) - lastEncoderY) / (28.0d * 40.0d * (26.0d / 20.0d)) * (2 * Math.PI * 45.0d);
			telemetry.addData("MOTOR DX: ", deltaX);
			telemetry.addData("MOTOR DY: ", deltaY);
			xPosition += deltaX * Math.cos(Math.toRadians(angle)) + deltaY * Math.sin(Math.toRadians(angle));
			yPosition += deltaX * Math.sin(Math.toRadians(angle)) + deltaY * Math.cos(Math.toRadians(angle));
		}

		netPowerLeft = 0;
		netPowerRight = 0;
		netPowerCenter = 0;
		double referenceAngle = angleToTarget() - (angle);
		double distance = Math.pow(Math.pow(targetX - xPosition, 2) + Math.pow(targetY - yPosition, 2), (double) 1 / 2);

		double deltax = Math.cos(Math.toRadians(referenceAngle + 180)) * distance;
		double deltay = Math.sin(Math.toRadians(referenceAngle + 180)) * distance;


		//---ANGLE---
		//This block of code controls the angle and is independent of the position block of code (below this block).
		anglePIDController.setSetPoint(targetAngle);
		double anglePower = -anglePIDController.PIDLoop(angle);
		netPowerLeft += anglePower;
		netPowerRight += anglePower;

		//---POSITION---
		//getting the position right;  This block of code controls the position and is independent of the angle block of code (above this block).
		xPIDController.setSetPoint(0); //the process variable is delta x and delta y, so this needs to be zero as we want to get close to the target position
		yPIDController.setSetPoint(0);
		double xPower = xPIDController.PIDLoop(deltax);
		double yPower = yPIDController.PIDLoop(deltay);
		netPowerLeft += yPower;
		netPowerRight -= yPower;
		netPowerCenter += xPower;
		telemetry.addData("Position X: ", xPosition);
		telemetry.addData("Position Y: ", yPosition);
		telemetry.addData("Target X: ", targetX);
		telemetry.addData("Target Y: ", targetY);
		telemetry.addData("Delta X: ", deltax);
		telemetry.addData("Delta Y: ", deltay);
		needManualOdometry = true; //set flag to true.  If the setPosition() is called, signifying a definitive position is known, needManualOdometry will be set back to false.
		lastEncoderY = (leftGroup.getPos() - rightGroup.getPos()) / 2.0d; //subtracted because they have different signs as the motors face opposite directions.
		lastEncoderX = (centerGroup.getPos());

		/*
		if (netPowerLeft > 1 || netPowerLeft < -1) {  // pseudo-normalization step
			netPowerRight /= netPowerLeft;
			if (netPowerLeft > 1) {
				netPowerLeft = 1;
			} else {
				netPowerLeft = -1;
			}
		} else if (netPowerRight > 1 || netPowerRight < -1) {
			netPowerLeft /= netPowerRight;
			if (netPowerRight > 1) {
				netPowerRight = 1;
			} else {
				netPowerRight = -1;
			}
		}
		*/

		leftGroup.setSpeed(netPowerLeft);
		rightGroup.setSpeed(netPowerRight);
		centerGroup.setSpeed(netPowerCenter);
	}

	@Override
	protected void driverUpdate() {
		if (gamepad != null) {
			switch (control) {
				case TANK:
					leftGroup.setSpeed(gamepad.left_stick_y);
					rightGroup.setSpeed(gamepad.right_stick_y);
					break;

				case ARCADE:
					double leftPower = gamepad.left_stick_y - gamepad.right_stick_x;
					double rightPower = -gamepad.left_stick_y - gamepad.right_stick_x;
					leftPower = -leftPower;
					rightPower = -rightPower;
					double strafePower = gamepad.left_stick_x;
					if (Math.abs(strafePower) < .5f) {
						strafePower /= 5;
					} else if (strafePower >= 0) {
						strafePower = Math.pow(10, strafePower - 1.10914) + 1 - Math.pow(10, -0.10914);
					} else {
						strafePower = -(Math.pow(10, ((-strafePower) - 1.10914)) + 1 - Math.pow(10, -0.10914));
					}
					leftGroup.setSpeed(leftPower);
					rightGroup.setSpeed(rightPower);
					centerGroup.setSpeed(strafePower);
					break;
			}
		}
	}

	private void odometryUpdate() {

	}

	private double angleToTarget() {
		double dx = targetX - xPosition;
		double dy = targetY - yPosition;
		double a = Math.toDegrees(Math.atan2(dy, dx));
		telemetry.addData("AngleToTarget: ", a);
		return a;
	}

	public void zeroMovement() {
		leftGroup.setSpeed(0);
		rightGroup.setSpeed(0);
		centerGroup.setSpeed(0);
	}

	public enum ControlType {
		TANK,
		ARCADE
	}

	public void setIMU(BNO055IMU mu) {
		imu = mu;
	}
}

