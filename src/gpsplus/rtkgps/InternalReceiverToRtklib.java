package gpsplus.rtkgps;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.GnssMeasurement;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import gpsplus.rtkgps.settings.StreamInternalFragment.Value;

@SuppressWarnings("ALL")
public class InternalReceiverToRtklib implements GpsStatus.Listener, LocationListener {

    private static final boolean DBG = BuildConfig.DEBUG & true;
    static final String TAG = InternalReceiverToRtklib.class.getSimpleName();

    final LocalSocketThread mLocalSocketThread;
    private static Context mParentContext = null;
    private Value mInternalReceiverSettings;
    LocationManager locationManager = null;
    FileOutputStream autoCaptureFileOutputStream = null;
    File autoCaptureFile=null;
    private int nbSat = 0;
    private boolean isStarting = false;
    private String mSessionCode;
    private int rawMeasurementStatus;

    public InternalReceiverToRtklib(Context parentContext, @Nonnull Value internalReceiverSettings, String sessionCode) {
        InternalReceiverToRtklib.mParentContext = parentContext;
        mSessionCode = sessionCode;
        this.mInternalReceiverSettings = internalReceiverSettings;
        mLocalSocketThread = new LocalSocketThread(mInternalReceiverSettings.getPath());
        mLocalSocketThread.setBindpoint(mInternalReceiverSettings.getPath());
    }

    public void start()
    {
        isStarting = true;
        locationManager = (LocationManager) mParentContext.getSystemService(Context.LOCATION_SERVICE);
        locationManager.addGpsStatusListener(this);
        locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000, 0.5f, this);

        locationManager.registerGnssMeasurementsCallback(mRawMeasurementListener);

    }

    public void stop()
    {
        locationManager.removeUpdates(this);
        locationManager.unregisterGnssMeasurementsCallback(mRawMeasurementListener);
        mLocalSocketThread.cancel();
    }


    public boolean isRawMeasurementsSupported() {
        return (rawMeasurementStatus == GnssMeasurementsEvent.Callback.STATUS_READY);
    }

    private final GnssMeasurementsEvent.Callback mRawMeasurementListener =
            new GnssMeasurementsEvent.Callback() {
                @Override
                public void onStatusChanged(int status) {
                    rawMeasurementStatus = status;
                }

                @Override
                public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {

                    for (GnssMeasurement measurement : event.getMeasurements()) {
                        int constellationType = measurement.getConstellationType();
                        float carrierFrequencyHz = measurement.getCarrierFrequencyHz();
                        int accumulatedDeltaRangeState = measurement.getAccumulatedDeltaRangeState();
                        double accumulatedDeltaRangeMeters = measurement.getAccumulatedDeltaRangeMeters();
                        double pseudorangeRate = measurement.getPseudorangeRateMetersPerSecond();

                        Log.v(TAG, "ConstellationType: " + constellationType);
                        Log.v(TAG, "Carrier Frequency: " + carrierFrequencyHz);
                        Log.v(TAG, "Accumulated Delta Range State: " + accumulatedDeltaRangeState);
                        Log.v(TAG, "Accumulated Delta Range, meters: " + accumulatedDeltaRangeMeters);
                        Log.v(TAG, "Pseudorange rate, meters per second: " + pseudorangeRate);


                        if (isStarting) // run only if starting
                        {
                            Log.i(TAG,"Starting streaming from internal receiver");
                            mLocalSocketThread.start();
                            isStarting = false;
                        }

                        // write to socket
                        // byte[] buffer = new byte[0x10];
                        // mLocalSocketThread.write(buffer,0,packet.getLength());
                    }
                }

            };

    @Override
    public void onGpsStatusChanged(int i) {
        GpsStatus gpsStatus = locationManager.getGpsStatus(null);
        if(DBG) {
            Log.d(TAG, "GPS Status changed");
        }
        if(gpsStatus != null) {

            Iterable<GpsSatellite> satellites = gpsStatus.getSatellites();
            Iterator<GpsSatellite> sat = satellites.iterator();
            nbSat = 0;
            while (sat.hasNext()) {
                GpsSatellite satellite = sat.next();
                if (satellite.usedInFix()) {
                    nbSat++;
                    Log.d(TAG, "PRN:" + satellite.getPrn() + ", SNR:" + satellite.getSnr() + ", AZI:" + satellite.getAzimuth() + ", ELE:" + satellite.getElevation());
                }
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    private final class LocalSocketThread extends RtklibLocalSocketThread {

        public LocalSocketThread(String socketPath) {
            super(socketPath);
        }

        @Override
        protected boolean isDeviceReady() {
            return isRawMeasurementsSupported();
        }

        @Override
        protected void waitDevice() {

        }

        @Override
        protected boolean onDataReceived(byte[] buffer, int offset, int count) {
            /*if (count <= 0) return true;
                   PoGoPin.writeDevice(BytesTool.get(buffer,offset), count);
                   */
            return true;
        }

        @Override
        protected void onLocalSocketConnected() {

        }
    }
}