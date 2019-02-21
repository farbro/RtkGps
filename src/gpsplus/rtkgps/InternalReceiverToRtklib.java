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
import java.util.Collection;
import java.util.Iterator;

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

                    Parcel p = Parcel.obtain();

                    // byte[] syncWord = {0x00, 0x00};
                    // p.writeByteArray(syncWord);

                    p.writeDouble(c.getBiasNanos());
                    p.writeDouble(c.getBiasUncertaintyNanos());
                    p.writeDouble(c.getDriftNanosPerSecond());
                    p.writeDouble(c.getDriftUncertaintyNanosPerSecond());
                    p.writeLong(c.getFullBiasNanos());
                    p.writeInt(c.getHardwareClockDiscontinuityCount());
                    p.writeInt(c.getLeapSecond());
                    p.writeLong(c.getTimeNanos());
                    p.writeDouble(c.getTimeUncertaintyNanos());
                    p.writeByte((byte) (c.hasBiasNanos() ? 1 : 0));
                    p.writeByte((byte) (c.hasBiasUncertaintyNanos() ? 1 : 0));
                    p.writeByte((byte) (c.hasDriftNanosPerSecond() ? 1 : 0));
                    p.writeByte((byte) (c.hasDriftUncertaintyNanosPerSecond() ? 1 : 0));
                    p.writeByte((byte) (c.hasFullBiasNanos() ? 1 : 0));
                    p.writeByte((byte) (c.hasLeapSecond() ? 1 : 0));
                    p.writeByte((byte) (c.hasTimeUncertaintyNanos() ? 1 : 0));

                    p.writeInt(measurements.size());

                    for (GnssMeasurement m : measurements) {

                        p.writeDouble(m.getAccumulatedDeltaRangeMeters());
                        p.writeInt(m.getAccumulatedDeltaRangeState());
                        p.writeDouble(m.getAccumulatedDeltaRangeUncertaintyMeters());
                        p.writeDouble(m.getAutomaticGainControlLevelDb());
                        p.writeLong(m.getCarrierCycles());
                        p.writeFloat(m.getCarrierFrequencyHz());
                        p.writeDouble(m.getCarrierPhase());
                        p.writeDouble(m.getCarrierPhaseUncertainty());
                        p.writeDouble(m.getCn0DbHz());
                        p.writeInt(m.getConstellationType());
                        p.writeInt(m.getMultipathIndicator());
                        p.writeDouble(m.getPseudorangeRateUncertaintyMetersPerSecond());
                        p.writeLong(m.getReceivedSvTimeNanos());
                        p.writeLong(m.getReceivedSvTimeUncertaintyNanos());
                        p.writeDouble(m.getSnrInDb());
                        p.writeInt(m.getState());
                        p.writeInt(m.getSvid());
                        p.writeDouble(m.getTimeOffsetNanos());
                        p.writeByte((byte) (m.hasAutomaticGainControlLevelDb() ? 1 : 0));
                        p.writeByte((byte) (m.hasCarrierCycles() ? 1 : 0));
                        p.writeByte((byte) (m.hasCarrierFrequencyHz() ? 1 : 0));
                        p.writeByte((byte) (m.hasCarrierPhase() ? 1 : 0));
                        p.writeByte((byte) (m.hasCarrierPhaseUncertainty() ? 1 : 0));
                        p.writeByte((byte) (m.hasSnrInDb() ? 1 : 0));
                    }

                    byte[] packet = p.marshall();

                    try {
                        mLocalSocketThread.write(packet, 0, packet.length);
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