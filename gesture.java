package testing;

import net.beadsproject.beads.data.Buffer;
import net.beadsproject.beads.ugens.Gain;
import net.beadsproject.beads.ugens.Glide;
import net.beadsproject.beads.ugens.WavePlayer;
import net.happybrackets.core.HBAction;
import net.happybrackets.device.HB;

import java.io.*;
import java.lang.invoke.MethodHandles;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import net.happybrackets.device.sensors.*;

// happy brackets and audio libraries
//import net.beadsproject.beads.core.Bead;
//import net.beadsproject.beads.data.Buffer;
//import net.beadsproject.beads.data.SampleManager;
//import net.beadsproject.beads.events.KillTrigger;
//import net.beadsproject.beads.ugens.Envelope;
//import net.beadsproject.beads.ugens.Gain;
//import net.beadsproject.beads.ugens.SamplePlayer;
//import net.beadsproject.beads.ugens.WavePlayer;
//import net.happybrackets.device.sensors.AccelerometerListener;

//import pi.dynamic.DynamoPI;
//import pi.sensors.MiniMU.MiniMUListener;
//import controller.network.SendToPI;
//import core.EZShell;
//import core.PIPO;
//import core.Synchronizer.BroadcastListener;

public class gesture implements HBAction {

    //movement
    enum MovementState {
        UNKNOWN, STILL, ROLLING, SLIGHT, SPINNING, SHAKING, FALLING, IMPACT, TWISTX, TWISTY, TWISTZ;
    }
    enum ModeState {
        GROUNDED, HANDS, AIR
    }

    enum direction { LFT, RGT, FRT, BCK, UPP, DWN}

    //variables for values
    float gyroX, gyroY, gyroZ, accelX, accelY, accelZ;

    //thresholds
    double twistTH = 1.5;
    double groundTH = 0.05;

    @Override
    public void action(HB hb) {
        hb.reset(); //Clears any running code on the device

        //sound
        // Set up the synthesis
        Glide freq = new Glide(0, 20);
        WavePlayer wp = new WavePlayer(freq, Buffer.SINE);
        Gain gainAmplifier = new Gain(HB.getNumOutChannels(),0.01f);
        gainAmplifier.addInput(wp);
        gainAmplifier.connectTo(HB.getAudioOutput());

        hb.setStatus("started");

        //file
        try {
            File file = new File("gestureData.txt");
            if (file.createNewFile()) {
                System.out.println("file created");
            }
            else {
                System.out.println("file already exists");
            }
        } catch (IOException e){
            e.printStackTrace();
            hb.setStatus("error");
        }

        //values buffer queue
        Queue<Float> bufferAX = new LinkedList<Float>();
        Queue<Float> bufferAY = new LinkedList<Float>();
        Queue<Float> bufferAZ = new LinkedList<Float>();
        Queue<Float> bufferGX = new LinkedList<Float>();
        Queue<Float> bufferGY = new LinkedList<Float>();
        Queue<Float> bufferGZ = new LinkedList<Float>();
        Queue<Float> bufferTM = new LinkedList<Float>();

        Queue<MovementState> bufferST = new LinkedList<MovementState>();

        float[] acc_vals = new float[6];

        //gyroscope listener
        new GyroscopeListener(hb) {

            @Override
            public void sensorUpdated(float pitch, float roll, float yaw) {// Write your code below this line
                //update values
                gyroX = Math.round(pitch * 10000f) / 10000f;
                gyroY = Math.round(roll * 10000f) / 10000f;
                gyroZ = Math.round(yaw * 10000f) / 10000f;

                // previous states status
                double dgyro = Math.sqrt(pitch*pitch + roll*roll + yaw*yaw);
                double daccel = Math.sqrt(accelX*accelY + accelY*accelY + accelZ*accelZ);
                double agyro = Math.abs(pitch) + Math.abs(roll) + Math.abs(yaw);
                double aaccel = (Math.abs(accelX) + Math.abs(accelY) + Math.abs(accelZ));
                double asquare = Math.sqrt(accelX*accelX + accelY*accelY + accelZ*accelZ);
                MovementState newState;
                ModeState mode;

                String statusMsg = null;

                direction orientation = direction.BCK;

                float max_val = 0;
                acc_vals[0] = accelX;
                acc_vals[1] = accelX * -1;
                acc_vals[2] = accelY;
                acc_vals[3] = accelY * -1;
                acc_vals[4] = accelZ;
                acc_vals[5] = accelZ * -1;


                for (int i =0; i < 6; i++){
                    if (acc_vals[i] > max_val){

                        orientation = direction.values()[i];
                        max_val = acc_vals[i];

                    }
                }

                //add values to buffer
                Date date = new Date();
                DateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
                String timeS = sdf.format(date);

                if(bufferAX.size() == 30){
                    bufferAX.remove();
                    bufferAY.remove();
                    bufferAZ.remove();
                    bufferGX.remove();
                    bufferGY.remove();
                    bufferGZ.remove();
                    bufferTM.remove();
                    bufferST.remove();
                }

                bufferAX.add(accelX);
                bufferAY.add(accelY);
                bufferAZ.add(accelZ);
                bufferGX.add(gyroX);
                bufferGY.add(gyroY);
                bufferGZ.add(gyroZ);
                bufferTM.add(Float.parseFloat(timeS.substring(6)));

                // start modes
                if (asquare >= 1-groundTH && asquare <= 1+groundTH && agyro < 0.01 && agyro > -0.01){
                    mode = ModeState.GROUNDED;
                    newState = MovementState.STILL;
                }
                else if (aaccel < 0.02 && aaccel > - 0.02){
                    mode = ModeState.AIR;
                    newState = MovementState.FALLING;
                }
                else {
                    mode = ModeState.HANDS;
                    if (agyro < 1.2){
                        newState = MovementState.STILL;
                    }
                    else {
                            if (gyroX >= twistTH && gyroY < twistTH && gyroZ < twistTH){
                                newState = MovementState.TWISTX;
                            }
                            else if (gyroX < twistTH && gyroY >= twistTH && gyroZ < twistTH) {
                                newState = MovementState.TWISTY;
                            }
                            else if (gyroX < twistTH && gyroY < 1 && gyroZ >= twistTH){
                                newState = MovementState.TWISTZ;
                            }
                            else {
                                if (aaccel > 0.5){
                                    newState = MovementState.SHAKING;
                                }
                                else {
                                    newState = MovementState.UNKNOWN;
                                }
                            }
                    }
                }

                bufferST.add(newState);

                statusMsg = String.format("%.3f", asquare) + " " + String.format("%.3f", agyro) + ": "+ orientation + " | " + mode + " " + newState + " " + bufferTM.peek();
                hb.setStatus(statusMsg);

            }
        };// End gyroscopeSensor code

        new AccelerometerListener(hb) {
            int stall = 0; //write less to file

            float x_val_MAX = 0;
            float x_val_MIN = 0;
            float x_RANGE= 0;
            float out_val_MAX = 880;
            float out_val_MIN = 220;
            float out_RANGE = out_val_MAX - out_val_MIN;
            float out_OFFSET = out_val_MIN;

            @Override
            public void sensorUpdated(float x_val, float y_val, float z_val) { // Write your code below this line
                //update values
                accelX = Math.round(x_val * 10000f) / 10000f;
                accelY = Math.round(y_val * 10000f) / 10000f;
                accelZ = Math.round(z_val * 10000f) / 10000f;

                if (stall == 2){
                    stall = stall % 2;

                    //time
                    Date date = new Date();
                    DateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
                    String timeS = sdf.format(date);

                    //write to file

//                    try (FileWriter writer = new FileWriter("data.txt", true);
//                         BufferedWriter bw = new BufferedWriter(writer);
//                         PrintWriter out = new PrintWriter(bw);)
//                    {
//                        out.println(timeS + " " + gyroX + " " + gyroY + " " + gyroZ + " " + accelX + " " + accelY + " " + accelZ);
//                    } catch (IOException e) {
//                        System.out.println("error");
//                        e.printStackTrace();
//                    }
                }
                else {
                    stall +=2;
                    //stall +=1;
                }

                if (x_val > x_val_MAX){
                    x_val_MAX = x_val;
                }

                if (x_val < x_val_MIN){
                    x_val_MIN = x_val;
                }

                x_RANGE = x_val_MAX - x_val_MIN;

                float out = (x_val - x_val_MIN) / x_RANGE * out_RANGE + out_OFFSET;

                //freq.setValue(out);

            }
        };//  End accelerometerSensor

    }



    //<editor-fold defaultstate="collapsed" desc="Debug Start">

    /**
     * This function is used when running sketch in IntelliJ IDE for debugging or testing
     *
     * @param args standard args required
     */
    public static void main(String[] args) {

        try {
            HB.runDebug(MethodHandles.lookup().lookupClass());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //</editor-fold>
}
