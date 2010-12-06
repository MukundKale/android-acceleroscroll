package com.vutbr.fit.tam.acceleroscroll;

import android.util.Log;
import android.view.Surface;
import android.hardware.SensorManager;

public class ScrollManager implements AcceleroSensorListener {
	 
	private static final String TAG = "AcceleroScrollManager";

	private float[] speed = new float[2]; //in mm/s
	private float[] movement = new float[2]; //in mm
	
	private float threshold = 0.1f;
	private float minSpeed = 30.0f;
	private float maxSpeed = 100.0f;
	private float acceleration = 1.0f;
	private float springness = 1.5f;
	
	private static int HISTORY_SIZE = 2;
	private float[] accelerationHistory = new float[HISTORY_SIZE*3];
	private float[] magSensorValues = new float[3];
	private int historyIndex = 0;
	
	private boolean onStart = true;
	private int orientation = Surface.ROTATION_0;
	private boolean useHandBased = false;
	
	/**
	 * rotateX - rotated around X axis -> up, down
	 * rotateY - rotated around Y axis -> left right
	 */
	private float rotateXReference;
	private float rotateYReference;
	
	public synchronized boolean resetState(){
		if(onStart) {
			Log.w(TAG, "Reset called before accelerometer could initialize");
			return false;	
		}
		float[] avgValues = new float[3];
		//now we can start counting
		for(int i = 0; i<HISTORY_SIZE; i++){
			avgValues[0] += accelerationHistory[i*3];
			avgValues[1] += accelerationHistory[i*3+1];
			avgValues[2] += accelerationHistory[i*3+2];
		}
		
		for(int i=0; i<3; i++){
			avgValues[i] /= HISTORY_SIZE;
		}
		getReferenceAngles(avgValues);
		
		speed[0] = 0.0f;
		speed[1] = 0.0f;
		movement[0] = 0.0f;
		movement[1] = 0.0f;
		Log.v(TAG, "Reseted state: " + rotateXReference + ", " + rotateYReference);
		return true;
	}
	
	private synchronized void getReferenceAngles(float[] avgValues){
		float[] rotationAngles = new float[2];
		if(useHandBased){
			handGetAngles(avgValues, rotationAngles);
		} else {
			phoneGetAngles(avgValues, rotationAngles);
		}
		rotateXReference = rotationAngles[0];
		rotateYReference = rotationAngles[1];
	}
	
	public float getThreshold() {
		return threshold;
	}

	public void setThreshold(float threshold) {
		this.threshold = threshold;
	}

	public float getMinSpeed() {
		return minSpeed;
	}

	public void setMinSpeed(float minSpeed) {
		this.minSpeed = minSpeed;
	}

	public float getMaxSpeed() {
		return (float) (maxSpeed*Math.PI/2.0);
	}

	public void setMaxSpeed(float maxSpeed) {
		this.maxSpeed = (float) (maxSpeed*2.0f/Math.PI);
		this.setMinSpeed(this.maxSpeed*0.3f);
	}
	
	public float getAcceleration() {
		return acceleration;
	}

	public void setAcceleration(float acceleration) {
		this.acceleration = acceleration;
	}

	public float getSpringness() {
		return springness;
	}

	public void setSpringness(float springness) {
		this.springness = springness;
	}

	public int getOrientation() {
		return orientation;
	}

	public void setOrientation(int orientation) {
		this.orientation = orientation;
	}

	/**
	 * Get the movement since last called this function.
	 * returns the movement in milimeters
	 * @param outMovement array filled with the values
	 */
	public void getMovement(float[] outMovement){
		synchronized (this) {
			for(int i = 0; i<2; i++){
				outMovement[i] = this.movement[i];
				this.movement[i] = 0.0f;
			}
		}
		float tmp;
		switch(orientation){
		//device counterclockwise rotation
			case Surface.ROTATION_0:
				//everything fine
				break;
			case Surface.ROTATION_90:
				tmp = outMovement[0];
				outMovement[0] = -outMovement[1];
				outMovement[1] = tmp;
				Log.v(TAG, "rotate90");
				break;
			case Surface.ROTATION_180:
				outMovement[0] = -outMovement[0];
				outMovement[1] = -outMovement[1];
				Log.v(TAG, "rotate180");
				break;
			case Surface.ROTATION_270:
				tmp = outMovement[0];
				outMovement[0] = outMovement[1];
				outMovement[1] = -tmp;
				Log.v(TAG, "rotate270");
				break;
		}
	}
	
	public void getMovement(float[] outMovement, boolean useEmulator){
		getMovement(outMovement);
		if(useEmulator){
			if(!useHandBased){
				outMovement[0] *= -1;
			}
			outMovement[1] *= -1;
		}
	}
	
	
	/**
	 * Get the current scrolling speeds in mm/s
	 * @param speed array to be filled with current values
	 */
	public void getSpeed(float[] speed) {
		synchronized (this) {
			for(int i = 0; i<2; i++){
				speed[i] = this.speed[i];
			}
		}
	}
	
	private float getAngle(float axis1, float axis2){
		return (float) Math.acos(axis1/Math.sqrt(axis1*axis1 + axis2*axis2));
	}
	
	public boolean isUseHandBased() {
		return useHandBased;
	}

	public void setUseHandBased(boolean useHandBased) {
		this.useHandBased = useHandBased;
		Log.v(TAG, "using orientation sensors"+(!useHandBased));
		this.resetState();
	}

	public void onAccelerationChanged(float x, float y, float z, float timeDiff) {
		
		//first fill the history before giving any data away
		if(onStart){
			Log.v(TAG, x+" "+y+" "+z+" onstart");
			synchronized (this) {
				accelerationHistory[historyIndex*3] = x;
				accelerationHistory[historyIndex*3+1] = y;
				accelerationHistory[historyIndex*3+2] = z;
				historyIndex++;
				if(historyIndex == HISTORY_SIZE) {
					historyIndex = 0;
					onStart = false;
					Log.v(TAG, "reset called from onstart");
					this.resetState();
				}
			}
			return;
		}
		
		float[] avgValues = new float[3];
		//store history
		synchronized(this) {
			accelerationHistory[historyIndex*3] = x;
			accelerationHistory[historyIndex*3+1] = y;
			accelerationHistory[historyIndex*3+2] = z;

			historyIndex = (historyIndex+1) % HISTORY_SIZE;

			//now we can start counting
			for(int i = 0; i<HISTORY_SIZE; i++){
				avgValues[0] += accelerationHistory[i*3];
				avgValues[1] += accelerationHistory[i*3+1];
				avgValues[2] += accelerationHistory[i*3+2];
			}
		}
		
		for(int i=0; i<3; i++){
			avgValues[i] /= HISTORY_SIZE;
		}
		//for now everything in here, if too slow with reaction maybe Looper 
		float[] rotationAngles = new float[2];
		if(useHandBased){
			handGetAngles(avgValues, rotationAngles);
		} else {
			phoneGetAngles(avgValues, rotationAngles);
		}
		/**
		 * x - mobile device center to right
		 * y - mobile device center to top
		 * z - camera direction at the back of the device
		 * 
		 * all below anticlockwise is positive
		 * rotateX - rotated around X axis -> up, down
		 * rotateY - rotated around Y axis -> left right
		 */
		float rotateX = rotationAngles[0] - rotateXReference;
		float rotateY = rotationAngles[1] - rotateYReference;
		
		float movementX=0, movementY=0;
		float speedX = 0, speedY =0, cspeedX, cspeedY;
		synchronized(this){
			cspeedX = speed[0];
			cspeedY = speed[1];
		}
		
		
		//calculate the next speed based on the current
		if(Math.abs(rotateY) > threshold) { 
			movementX = (float) Math.sin(rotateY);
			speedX = getCurrentSpeed(cspeedX, movementX, timeDiff);
			if(Math.signum(speedX) != Math.signum(movementX)){
				speedX *= (1 - timeDiff*springness/1.0e9f);
			}
		} else {
			speedX = (1 - timeDiff*springness/1.0e9f)*cspeedX;
		}
		
		//calculate the next speed
		if(Math.abs(rotateX) > threshold) {
			movementY = (float) Math.sin(rotateX);
			speedY = getCurrentSpeed(cspeedY, movementY, timeDiff);
			if(Math.signum(speedY) != Math.signum(movementY)){
				speedY *= (1 - timeDiff*springness/1.0e9f);
			}
		} else {
			speedY = (1 - timeDiff*springness/1e9f)*cspeedY;
		}
		
		synchronized (this) {
			speed[0] = speedX;
			speed[1] = speedY;
			movement[0] += speedX*timeDiff/1e9f;
			movement[1] += speedY*timeDiff/1e9f;
		}
		//Log.v(TAG, "Current speed: " + speedX + ", " + speedY + " [" + movementX + ", " + movementY + "]");
		//Log.v(TAG, "Current difference: " + rotateX + ", " + rotateY);
	}
	
	private void phoneGetAngles(float[] avgValues, float[] rotationAngles){
		rotationAngles[0] = this.getAngle(avgValues[1], 
				(float) Math.sqrt(avgValues[2]*avgValues[2]+avgValues[0]*avgValues[0]));
		rotationAngles[1] = this.getAngle(avgValues[0], 
				(float) Math.sqrt(avgValues[2]*avgValues[2]+avgValues[1]*avgValues[1]));
		Log.v(TAG, "phoneGetAngles: "+rotationAngles[0]+" "+rotationAngles[1]);
	}
		
		
	
	private void handGetAngles(float[] avgValues, float[] rotationAngles){

        float[] rotationMatrix = new float[16];
        float[] inclinationMatrix = new float[16];
        float[] orientationAngles = new float[3];
        SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, avgValues, this.magSensorValues);
        SensorManager.getOrientation(rotationMatrix, orientationAngles);
        rotationAngles[0] = orientationAngles[1];
        rotationAngles[1] = orientationAngles[2];
		Log.v(TAG, "orientationGetAngles: "+rotationAngles[0]+" "+rotationAngles[1]);
	}
	
	private float getCurrentSpeed(float currentSpeed, float movement, float timeDiff){
		return currentSpeed + movement*minSpeed*(
				(float) Math.cos(Math.min(
						Math.abs((currentSpeed+Math.signum(movement)))/(maxSpeed*movement), 
						Math.PI
						))
				)*timeDiff/1e9f*acceleration;
	}

	public void onMagSensorChanged(float field1, float field2, float field3) {
		magSensorValues[0] = field1;
		magSensorValues[1] = field2;
		magSensorValues[2] = field3;
	}

}