package com.ragres.mongodb.iotexample.modules;

import android.content.Context;
import android.location.LocationManager;

import com.google.gson.Gson;
import com.ragres.mongodb.iotexample.AndroidApplication;
import com.ragres.mongodb.iotexample.controllers.ConnectivityController;
import com.ragres.mongodb.iotexample.serviceClients.BrokerServiceClient;
import com.ragres.mongodb.iotexample.ui.activities.MainActivityPresenter;


import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module for application
 * dependencies.
 */
@Module(
        library = true,
        injects = {
                AndroidApplication.class,
                BrokerServiceClient.class,
                ConnectivityController.class,
                LocationManager.class,
                MainActivityPresenter.class,
                Gson.class,
        }
)
public class MainModule {

    /**
     * Application intstance.
     */
    private AndroidApplication androidApplication;

    /**
     * Public constructor.
     */
    public MainModule(AndroidApplication androidApplication) {
        this.androidApplication = androidApplication;
    }

    @Provides
    @Singleton
    public AndroidApplication provideAndroidApplication() {
        return androidApplication;
    }

    @Provides
    @Singleton
    public Gson provideGson() {
        return new Gson();
    }

    @Provides
    @Singleton
    public BrokerServiceClient provideBrokerServiceClient() {
        return new BrokerServiceClient(
                androidApplication.getObjectGraph().get(AndroidApplication.class),
                androidApplication.getObjectGraph().get(Gson.class));
    }

    @Provides
    @Singleton
    public ConnectivityController provideConnectivityController() {
        return new ConnectivityController(
                androidApplication.getObjectGraph().get(AndroidApplication.class),
                androidApplication.getObjectGraph().get(Gson.class));

    }


    @Provides
    @Singleton
    public MainActivityPresenter provideMainActivityPresenter() {
        return new MainActivityPresenter(
                androidApplication.getObjectGraph().get(AndroidApplication.class),
                androidApplication.getObjectGraph().get(BrokerServiceClient.class),
                androidApplication.getObjectGraph().get(ConnectivityController.class),
                androidApplication.getObjectGraph().get(LocationManager.class)
        );

    }

    @Provides
    @Singleton
    public LocationManager provideLocationManager() {
        return
        (LocationManager) androidApplication.getSystemService(Context.LOCATION_SERVICE);


    }

}
