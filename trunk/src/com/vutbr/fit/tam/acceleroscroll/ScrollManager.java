package com.vutbr.fit.tam.acceleroscroll;

import android.util.Log;


public class ScrollManager implements AcceleroSensorListener {
	 
	private static final String TAG = "AcceleroScrollManager";

	private float[] speed = new float[2]; //in mm/s
	private float[] movement = new float[2]; //in mm
	
	private float threshold = 0.03f;
	private float minSpeed = 30.0f;
	private float maxSpeed = 100.0f;
	
	private static int HISTORY_SIZE = 2;
	private float[] accelerationHistory = new float[HISTORY_SIZE*3];
	private int historyIndex = 0;
	
	private boolean onStart = true;
	private int portrait = 1;
	
	/**
	 * tilt - right/left when tilted - rotated around the mobile phones Z axis
	 * rotateX - rotated around X axis -> up, down
	 * rotateY - rotated around Y axis -> left right
	 */
	private float tiltReference;
	private float rotateXReference;
	private float rotateYReference;
	
	public synchronized boolean resetState(){
		if(onStart)
			return false;
		float[] avgValues = new float[3];
		//now we can start counting
		for(int i = 0; i<HISTORY_SIZE; i++){
			avgValues[0] += accelerationHistory[i*3];
			avgValues[1] += accelerationHistory[i*3+1];
			avgValues[2] += accelerationHistory[i*3+2];
		}
		
		for(int i=0; i<3; i++){
			avgValues[i] /= HISTORY_SIZE + 1;
		}
		tiltReference = this.getAngle(avgValues[0], avgValues[1]);
		rotateXReference = this.getAngle(avgValues[1], avgValues[2]);
		rotateYReference = this.getAngle(avgValues[0], avgValues[2]);
		speed[0] = 0.0f;
		speed[1] = 0.0f;
		movement[0] = 0.0f;
		movement[1] = 0.0f;
		Log.v(TAG, "Reseted state: " + tiltReference + ", " + rotateXReference + ", " + rotateYReference);
		return true;
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
		return maxSpeed;
	}

	public void setMaxSpeed(float maxSpeed) {
		this.maxSpeed = maxSpeed;
	}

	public int getPortrait() {
		return portrait;
	}

	public void setPortrait(int portrait) {
		this.portrait = portrait;
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
	
	@Override
	public void onAccelerationChanged(float x, float y, float z, float timeDiff) {
		
		//first fill the history before giving any data away
		if(onStart){
			accelerationHistory[historyIndex] = x;
			accelerationHistory[historyIndex+1] = y;
			accelerationHistory[historyIndex+2] = z;
			historyIndex++;
			if(historyIndex == HISTORY_SIZE) {
				historyIndex = 0;
				onStart = false;
			}
			this.resetState();
			return;
		}
		
		//store history
		accelerationHistory[historyIndex*3] = x;
		accelerationHistory[historyIndex*3+1] = y;
		accelerationHistory[historyIndex*3+2] = z;

		historyIndex = (historyIndex+1) % HISTORY_SIZE;
		
		float[] avgValues = new float[3];
		//now we can start counting
		for(int i = 0; i<HISTORY_SIZE; i++){
			avgValues[0] += accelerationHistory[i*3];
			avgValues[1] += accelerationHistory[i*3+1];
			avgValues[2] += accelerationHistory[i*3+2];
		}
		
		float resultSize = 0.0f;
		for(int i=0; i<3; i++){
			avgValues[i] /= HISTORY_SIZE + 1;
			//get the vector size to be able to get the angles
			resultSize += avgValues[i]*avgValues[i];
		}
		//for now everything in here, if too slow with reaction maybe Looper 
		
		/**
		 * x - mobile device center to right
		 * y - mobile device center to top
		 * z - camera direction at the back of the device
		 * 
		 * all below anticlockwise is positive
		 * tilt - right/left when tilted - rotated around the mobile phones Z axis
		 * rotateX - rotated around X axis -> up, down
		 * rotateY - rotated around Y axis -> left right
		 */
		float tilt = tiltReference - this.getAngle(avgValues[0], avgValues[1]);
		float rotateX = rotateXReference - this.getAngle(avgValues[1], avgValues[2]);
		float rotateY = rotateYReference - this.getAngle(avgValues[0], avgValues[2]);
		
		float movementX=0, movementY=0;
		float speedX = 0, speedY =0, cspeedX, cspeedY;
		synchronized(this){
			cspeedX = speed[0];
			cspeedY = speed[1];
		}
		
		//check if tilt is over threshold
		if(tilt > threshold){
			if(portrait % 2 == 0){
				movementX += Math.sin(tilt);
			} else {
				movementY += Math.sin(tilt);
			}
		}
		
		//TODO fix this
		//if in landscape mode switch axis
		if(portrait % 2 == 0){
			float tmp = rotateX;
			rotateX = rotateY;
			rotateY = tmp;
		}
		
		//calculate the next speed based on the current
		if(movementX > 0 || Math.abs(rotateY) > threshold){
			if(Math.abs(rotateY) > threshold) 
				movementX += Math.sin(rotateY);
			speedX = getCurrentSpeed(cspeedX, movementX, timeDiff);
		} else {
			//speedX = (1 - timeDiff/1.0e9f)*cspeedX;
		}
		
		//calculate the next speed
		if(movementY > 0 || Math.abs(rotateX) > threshold){
			if(Math.abs(rotateX) > threshold)
				movementY += Math.sin(rotateX);
			speedY = getCurrentSpeed(cspeedY, movementY, timeDiff);
		} else {
			//speedY = (1 - timeDiff/1e9f)*cspeedY;
		}
		
		synchronized (this) {
			speed[0] = speedX;
			speed[1] = speedY;
			movement[0] += speedX*timeDiff/10e9f;
			movement[1] += speedY*timeDiff/10e9f;
		}
		Log.v(TAG, "Current speed: " + speedX + ", " + speedY);
		Log.v(TAG, "Current difference: " + tilt + ", " + rotateX + ", " + rotateY);
	}
	
	private float getCurrentSpeed(float currentSpeed, float movement, float timeDiff){
		return currentSpeed - movement*minSpeed*(
				(float) Math.cos(Math.PI + Math.abs(currentSpeed/maxSpeed))+1
				)/2.0f;
	}

}
