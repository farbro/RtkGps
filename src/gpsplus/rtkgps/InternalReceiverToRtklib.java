package gpsplus.rtkgps;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.GnssMeasurementsEvent;
import android.location.GnssMeasurement;
import android.location.GnssClock;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Iterator;
import java.nio.ByteBuffer;


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

                    if (isStarting) // run only if starting
                    {
                        Log.i(TAG,"Starting streaming from internal receiver");
                        mLocalSocketThread.start();
                        isStarting = false;
                    }

                    GnssClock c = event.getClock();
                    Collection<GnssMeasurement> measurements = event.getMeasurements();

                    int msg_size = 92 + 4 + 144*measurements.size();

                    ByteBuffer buf = ByteBuffer.allocate(msg_size);
                    buf.order(ByteOrder.LITTLE_ENDIAN);

                    // byte[] syncWord = {0x00, 0x00};
                    // p.writeByteArray(syncWord);

                    buf.putDouble(c.getBiasNanos());
                    buf.putDouble(c.getBiasUncertaintyNanos());
                    buf.putDouble(c.getDriftNanosPerSecond());
                    buf.putDouble(c.getDriftUncertaintyNanosPerSecond());
                    buf.putLong(c.getFullBiasNanos());
                    buf.putInt(c.getHardwareClockDiscontinuityCount());
                    buf.putInt(c.getLeapSecond());
                    buf.putDouble(c.getTimeUncertaintyNanos());
                    buf.putInt(c.hasBiasNanos() ? 1 : 0);
                    buf.putInt(c.hasBiasUncertaintyNanos() ? 1 : 0);
                    buf.putInt(c.hasDriftNanosPerSecond() ? 1 : 0);
                    buf.putInt(c.hasDriftUncertaintyNanosPerSecond() ? 1 : 0);
                    buf.putInt(c.hasFullBiasNanos() ? 1 : 0);
                    buf.putInt(c.hasLeapSecond() ? 1 : 0);
                    buf.putInt(c.hasTimeUncertaintyNanos() ? 1 : 0);

                    buf.putInt(measurements.size());

                    for (GnssMeasurement m : measurements) {

                        buf.putDouble(m.getAccumulatedDeltaRangeMeters());
                        buf.putInt(m.getAccumulatedDeltaRangeState());
                        buf.putDouble(m.getAccumulatedDeltaRangeUncertaintyMeters());
                        buf.putDouble(m.getAutomaticGainControlLevelDb());
                        buf.putLong(m.getCarrierCycles());
                        buf.putFloat(m.getCarrierFrequencyHz());
                        buf.putDouble(m.getCarrierPhase());
                        buf.putDouble(m.getCarrierPhaseUncertainty());
                        buf.putDouble(m.getCn0DbHz());
                        buf.putInt(m.getConstellationType());
                        buf.putInt(m.getMultipathIndicator());
                        buf.putDouble(m.getPseudorangeRateUncertaintyMetersPerSecond());
                        buf.putLong(m.getReceivedSvTimeNanos());
                        buf.putLong(m.getReceivedSvTimeUncertaintyNanos());
                        buf.putDouble(m.getSnrInDb());
                        buf.putInt(m.getState());
                        buf.putInt(m.getSvid());
                        buf.putDouble(m.getTimeOffsetNanos());
                        buf.putInt(m.hasAutomaticGainControlLevelDb() ? 1 : 0);
                        buf.putInt(m.hasCarrierCycles() ? 1 : 0);
                        buf.putInt(m.hasCarrierFrequencyHz() ? 1 : 0);
                        buf.putInt(m.hasCarrierPhase() ? 1 : 0);
                        buf.putInt(m.hasCarrierPhaseUncertainty() ? 1 : 0);
                        buf.putInt(m.hasSnrInDb() ? 1 : 0);
                    }

                    byte[] bytes = buf.array();

                    try {
                        mLocalSocketThread.write(bytes, 0, bytes.length);
                    } catch (IOException e) {
                        e.printStackTrace();
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
