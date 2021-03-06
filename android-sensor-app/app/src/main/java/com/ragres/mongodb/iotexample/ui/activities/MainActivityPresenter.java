package com.ragres.mongodb.iotexample.ui.activities;

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.res.Configuration;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ListView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ValueFormatter;
import com.ragres.mongodb.iotexample.AndroidApplication;
import com.ragres.mongodb.iotexample.R;
import com.ragres.mongodb.iotexample.controllers.ConnectivityController;
import com.ragres.mongodb.iotexample.controllers.MetaWearTestController;
import com.ragres.mongodb.iotexample.domain.ConnectionState;
import com.ragres.mongodb.iotexample.domain.dto.SensorDataDTO;
import com.ragres.mongodb.iotexample.domain.dto.payloads.AccelerometerDataPayload;
import com.ragres.mongodb.iotexample.domain.dto.payloads.LocationDataPayload;
import com.ragres.mongodb.iotexample.domain.dto.payloads.TemperatureDataPayload;
import com.ragres.mongodb.iotexample.misc.Logging;
import com.ragres.mongodb.iotexample.serviceClients.BrokerServiceClient;
import com.ragres.mongodb.iotexample.ui.dialogs.AboutDialogFragment;
import com.ragres.mongodb.iotexample.ui.dialogs.ConnectMqttDialogFragment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import dagger.internal.ArrayQueue;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;

public class MainActivityPresenter {

    private Semaphore logListLock = new Semaphore(1);

    /**
     * Format string for sensor chart items.
     */
    public static final DateFormat FORMAT_DATE_HOUR = new SimpleDateFormat("HH:mm:ss");
    /**
     * Empty sensor chart entry array.
     */
    public static final Entry[] EMPTY_SENSOR_CHART_ENTRIES = new Entry[]{};

    /**
     * Pool for items in log list.
     */
    private final LogListItemPool logListItemPool;

    /**
     * Observable for server address text.
     */
    private BehaviorSubject<String> serverAddressObservable = BehaviorSubject.create();

    /**
     * Reference on main activity.
     * TODO: Probably should use weak reference?
     */
    private MainActivity mainActivity;

    public BehaviorSubject<Boolean> getMetaWearConnectionStateChangedObservable() {
        return metaWearConnectionStateChangedObservable;
    }

    private BehaviorSubject<Boolean> metaWearConnectionStateChangedObservable =
            BehaviorSubject.create();

    /**
     * Observable for display of location settings dialog.
     */
    private BehaviorSubject showLocationSettingsDialogObservable = BehaviorSubject.create();

    /**
     * Observable for calling activity floating action menu collapse.
     */
    private BehaviorSubject collapseFloatingActionsMenuObservable = BehaviorSubject.create();

    /**
     * Android application.
     */
    private AndroidApplication androidApplication;

    /**
     * Broker service client.
     */
    private BrokerServiceClient brokerServiceClient;

    /**
     * Observable for connection state changes in UI.
     */
    private BehaviorSubject<ConnectionState> updateUIForConnectionStateObservable = BehaviorSubject.create();

    /**
     * Connectivity controller.
     */
    private ConnectivityController connectivityController;

    /**
     * Queued sensor data in line chart.
     */
    private Queue<Entry> queuedSensorChartEntries = new ArrayQueue<>(7);

    /**
     * Sensor data chart values on X-axis (timestamps).
     */
    private ArrayList<String> chartXVals = new ArrayList<>();

    /**
     * Sensor data set for line chart.
     */
    private LineDataSet sensorDataSet;

    /**
     * Location manager.
     */
    private LocationManager locationManager;

    private MetaWearTestController metaWearTestController;

    /**
     * Adapter for log list items.
     */
    private LogListAdapter logListAdapter;
    private Subscription onGetSensorDataSubscription;
    private Subscription connectionStateChangedUISubscription;
    private Subscription getLogListItemSubscription;
    private Subscription connectionStateChangedSubscription;
    private Subscription test;

    /**
     * Public constructor.
     */
    public MainActivityPresenter(AndroidApplication androidApplication,
                                 BrokerServiceClient brokerServiceClient,
                                 ConnectivityController connectivityController,
                                 LocationManager locationManager,
                                 LogListItemPool logListItemPool,
                                 MetaWearTestController metaWearTestController) {
        this.androidApplication = androidApplication;
        this.brokerServiceClient = brokerServiceClient;
        this.connectivityController = connectivityController;
        this.locationManager = locationManager;
        this.logListItemPool = logListItemPool;
        this.metaWearTestController = metaWearTestController;

    }


    /**
     * Handle clicks to test button.
     */
    public void onTestButtonClick() {
        AsyncTask sendTestTask = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                collapseFloatingActionsMenu();
                brokerServiceClient.sendTest();
                return null;
            }
        };
        sendTestTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Collapse floating actions menu.
     */
    private void collapseFloatingActionsMenu() {
        collapseFloatingActionsMenuObservable.onNext(null);
    }

    /**
     * Set up line chart.
     */
    public void setUpLineChart() {

        LineChart lineChart = mainActivity.getSensorDataChart();

        ArrayList<Entry> yVals = new ArrayList<>();
        sensorDataSet = new LineDataSet(yVals, "DataSet 1");
        sensorDataSet.setColor(androidApplication.getResources().getColor(android.R.color.black));
        sensorDataSet.setCircleColor(androidApplication.getResources().getColor(R.color.chart_circle_color));
        sensorDataSet.setFillColor(androidApplication.getResources().getColor(R.color.chart_circle_fill_color));

        ArrayList<LineDataSet> dataSets = new ArrayList<>();
        dataSets.add(sensorDataSet);

        final LineData data = new LineData(chartXVals, dataSets);

        ValueFormatter f = new ValueFormatter() {
            @Override
            public String getFormattedValue(float v) {
                return "";
            }
        };
        lineChart.setValueFormatter(f);
        lineChart.setData(data);


        androidApplication.getSensorDataObservable().map(new Func1<SensorDataDTO, Integer>() {

            @Override
            public Integer call(SensorDataDTO sensorDataDTO) {
                return null == sensorDataDTO ? 0 : 1;
            }
        }).buffer(1, TimeUnit.SECONDS).map(new Func1<List<Integer>, Integer>() {

            @Override
            public Integer call(List<Integer> list) {
                int sum = 0;
                for (Integer item : list) {
                    if (null == item)
                        continue;
                    sum += item.intValue();
                }
                list.clear();
                return sum;
            }
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Integer>() {
                    @Override
                    public void call(Integer count) {
                        updateSensorDataChart(count);
                    }
                });

    }

    /**
     * Update sensor chart data for last sensor values.
     *
     * @param count Count of sensor data in last second.
     */
    private void updateSensorDataChart(Integer count) {

        LineChart lineChart = mainActivity.getSensorDataChart();

        String timeText = FORMAT_DATE_HOUR.format(new Date());

        while (queuedSensorChartEntries.size() > 5) {
            Entry entry = queuedSensorChartEntries.poll();
            sensorDataSet.removeEntry(entry);
            chartXVals.remove(0);
        }

        int peekVal = 0;

        for (int i = 0; i < queuedSensorChartEntries.size(); ++i) {
            Entry currentEntry = queuedSensorChartEntries.toArray(EMPTY_SENSOR_CHART_ENTRIES)[i];
            currentEntry.setXIndex(i);
            if ((int) currentEntry.getVal() > peekVal) {
                peekVal = (int) currentEntry.getVal();
            }
        }

        if (count > peekVal)
            peekVal = count;

        peekVal += 1;

        Entry entry = new Entry(count, sensorDataSet.getEntryCount());
        sensorDataSet.addEntry(entry);
        chartXVals.add(timeText);

        queuedSensorChartEntries.offer(entry);

        if (null == lineChart)
            return;

        lineChart.setYRange(0, peekVal, false);


        lineChart.centerViewPort(sensorDataSet.getEntryCount(), count);
        lineChart.notifyDataSetChanged();

        lineChart.invalidate();
    }

    /**
     * Check for GPS and
     * show settins dialog for it if necessaary.
     */
    public void checkAndEnableGps() {
        try {
            boolean isGPSEnabled = locationManager
                    .isProviderEnabled(LocationManager.GPS_PROVIDER);
            Log.i(Logging.TAG, "isGPSEnabled: " + String.valueOf(isGPSEnabled));
            if (!isGPSEnabled) {
                // TODO: Enable again.
                //showLocationSettingsDialog();
            }
        } catch (Exception ex0) {
            Log.e(Logging.TAG, "Error: " + ex0.toString());
        }
    }

    private void showLocationSettingsDialog() {
        showLocationSettingsDialogObservable.onNext(null);
    }

    /**
     * On activity create.
     */
    public void onCreate(MainActivity mainActivity) {

        this.mainActivity = mainActivity;

        logListAdapter = new LogListAdapter(androidApplication, logListItemPool);

        serverAddressObservable.onNext(connectivityController.getServerAddress());

        onGetSensorDataSubscription = androidApplication.getSensorDataObservable().subscribe(new Action1<SensorDataDTO>() {

            @Override
            public void call(SensorDataDTO value) {
                onGetSensorData(value);
            }
        });

        getLogListItemSubscription = androidApplication.getLogListItemObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<LogListItem>() {

                    @Override
                    public void call(LogListItem logListItem) {
                        onGetLogListItem(logListItem);
                    }
                });

        test = metaWearTestController.getMetaWearConnectionStateChangedObservable()
                .subscribe(new Action1<Boolean>() {

                    @Override
                    public void call(Boolean isConnected) {
                        metaWearConnectionStateChangedObservable.onNext(isConnected);
                    }
                });


        BehaviorSubject<ConnectionState> connectionStateChangedSubject = connectivityController.
                getConnectionStateChangedSubject();

        // Handle UI logic for connection state change.
        connectionStateChangedUISubscription =connectionStateChangedSubject
                .subscribe(new Action1<ConnectionState>() {
                    @Override
                    public void call(ConnectionState connectionState) {
                        updateUIForConnectionState();
                        pushLogItemForConnectionState(connectionState);
                    }
                });

        // Handle business logic for connection state change.
        // TODO: Refactor / move.
        connectionStateChangedSubscription = connectionStateChangedSubject
                .subscribe(new Action1<ConnectionState>() {
                    @Override
                    public void call(ConnectionState connectionState) {
                        boolean sendSensorData = ConnectionState.CONNECTED.equals(connectionState);
                        androidApplication.setSendSensorData(sendSensorData);
                    }
                });
    }

    public void onDestroy() {
        if (null != onGetSensorDataSubscription) {
            onGetSensorDataSubscription.unsubscribe();
            onGetSensorDataSubscription = null;
        }

        if (null != getLogListItemSubscription) {
            getLogListItemSubscription.unsubscribe();
            getLogListItemSubscription = null;
        }

        if (null != connectionStateChangedUISubscription) {
            connectionStateChangedUISubscription.unsubscribe();
            connectionStateChangedUISubscription = null;
        }

        if (null != connectionStateChangedSubscription) {
            connectionStateChangedSubscription.unsubscribe();
            connectionStateChangedSubscription = null;
        }
    }

    private void onGetLogListItem(LogListItem logListItem) {

        try {
            logListLock.acquire();
        } catch (InterruptedException e) {

        }
        while (logListAdapter.getCount() > 10) {
            LogListItem item = logListAdapter.getItem(0);
            logListAdapter.remove(item);
            logListItemPool.add(item);
        }
        logListAdapter.add(logListItem);
        logListLock.release();
    }

    private void onGetSensorData(SensorDataDTO value) {
        LogListItem logListItem = logListItemPool.get();
        logListItem.setTimestamp(new Date(value.getTimestamp()));
        if (value.getPayload() instanceof AccelerometerDataPayload) {
            logListItem.setType(LogListItemType.SENSOR_ACCELEROMETER);
        }
        if (value.getPayload() instanceof TemperatureDataPayload) {
            logListItem.setType(LogListItemType.SENSOR_TEMPERATURE);
        }
        if (value.getPayload() instanceof LocationDataPayload) {
            logListItem.setType(LogListItemType.SENSOR_GPS);
        }
        androidApplication.getLogListItemObservable().onNext(logListItem);
    }

    private void pushLogItemForConnectionState(ConnectionState connectionState) {
        if (ConnectionState.CONNECTED.equals(connectionState) ||
                ConnectionState.DISCONNECTED.equals(connectionState)) {
            LogListItem logListItem = logListItemPool.get();
            logListItem.setTimestamp(new Date());
            logListItem.setType(ConnectionState.CONNECTED.equals(connectionState) ?
                    LogListItemType.CONNECTED : LogListItemType.DISCONNECTED);
            androidApplication.getLogListItemObservable().onNext(logListItem);
        }
    }

    /**
     * Update UI for new connection state.
     */
    private void updateUIForConnectionState() {
        updateUIForConnectionStateObservable.onNext(connectivityController.getConnectionState());
        serverAddressObservable.onNext(connectivityController.getServerAddress());
    }

    /**
     * Get UI Connection State Update observable.
     *
     * @return UI Connection State Update observable.
     */
    public BehaviorSubject<ConnectionState> getUpdateUIForConnectionStateObservable() {
        return updateUIForConnectionStateObservable;
    }

    /**
     * Show connect to MQTT broker dialog.
     */
    public void showConnectToMqttDialog() {
        FragmentTransaction ft = mainActivity.getFragmentManager().beginTransaction();
        Fragment prev = mainActivity.getFragmentManager().findFragmentByTag("dialog");
        if (null != prev) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        DialogFragment newFragment = ConnectMqttDialogFragment.newInstance();
        newFragment.show(ft, "dialog");
    }

    /**
     * Disconnect from MQTT broker.
     */
    public void disconnectFromServer() {

        AsyncTask disconnectTask = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                connectivityController.disconnectFromServer();
                return null;
            }
        };
        disconnectTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }

    public BehaviorSubject<String> getServerAddressObservable() {
        return serverAddressObservable;
    }

    public BehaviorSubject getShowLocationSettingsDialogObservable() {
        return showLocationSettingsDialogObservable;
    }

    public void forceUpdateUIForConnectionState() {
        updateUIForConnectionState();
    }

    public void setLogListAdapter(ListView logList) {
        logList.setAdapter(logListAdapter);
    }

    public BehaviorSubject getCollapseFloatingActionsMenuObservable() {
        return collapseFloatingActionsMenuObservable;
    }

    public void onAboutButtonClick() {
        collapseFloatingActionsMenu();
        showAboutDialog();
    }

    private void showAboutDialog() {
        FragmentTransaction ft = mainActivity.getFragmentManager().beginTransaction();
        Fragment prev = mainActivity.getFragmentManager().findFragmentByTag("dialog");
        if (null != prev) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        DialogFragment newFragment = AboutDialogFragment.newInstance();
        newFragment.show(ft, "dialog");
    }

    public void onConfigurationChanged(Configuration newConfig) {

    }

    public void connectBluetooth() {
        androidApplication.getConnectBluetoothObservable().onNext(null);
    }

    public void disconnectBluetooth() {
        androidApplication.getDisconnectBluetoothObservable().onNext(null);
    }

    public void forceUpdateMetaWearConnectionState() {
        metaWearConnectionStateChangedObservable.onNext(metaWearTestController.isConnected());
    }
}
