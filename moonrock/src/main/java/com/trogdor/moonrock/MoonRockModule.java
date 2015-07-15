package com.trogdor.moonrock;

import com.google.gson.Gson;

import java.io.IOException;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.AsyncSubject;

/**
 * Created by chrisfraser on 8/07/15.
 */
public class MoonRockModule {
    private final MRPortalGenerator mPortalGenerator;
    private final AsyncSubject<MoonRockModule> mReadySubject;
    private final MoonRock mMoonRock;
    private Object mPortalHost;
    private final String mLoadedName;
    private final Gson mGson;

    public MoonRockModule(MoonRock moonRock, String module, String instanceName, Object portalHost, AsyncSubject<MoonRockModule> readySubject) {
        mMoonRock = moonRock;
        mReadySubject = readySubject;
        mPortalGenerator = new MRPortalGenerator(moonRock, portalHost);
        mGson = new Gson();
        mLoadedName = instanceName;
        load(module);
    }

    public MoonRock getMoonRock() {
        return mMoonRock;
    }

    public <T> Observable<T> function(String functionName, Class<T> unpackClass, Object... args)
    {
        MRStreamManager streamManager = mMoonRock.getStreams();
        String streamKey = streamManager.makeKey();
        String script = null;
        try {
            script = makeFunctionInvocation(functionName, streamKey, args);
        } catch (Exception e) {
            return Observable.error(e);
        }

        MRStream<T> resultStream = streamManager.makeSingleShotStream(streamKey, unpackClass);
        mMoonRock.getWebView().evaluateJavascript(script, result -> {
            if (result != "null")
                resultStream.push(result);
        });
        return resultStream.getObservable().observeOn(AndroidSchedulers.mainThread());
    }

    private String makeFunctionInvocation(String functionName, String streamKey, Object[] args) throws IOException {
        StringBuilder script = new StringBuilder(String.format("%s.%s('%s'", mLoadedName, functionName, streamKey));
        for (Object arg : args)
            script.append(String.format(", '%s'", mGson.toJson(arg)));
        script.append(")");
        return script.toString();
    }

    public Observable<MoonRockModule> ready() {
        return mReadySubject.asObservable().observeOn(AndroidSchedulers.mainThread());
    }

    private void load(String module) {
        mPortalGenerator.setLoadedName(mLoadedName);
        String loadScript = String.format("mrhelper.loadModule('%s', '%s')", module, mLoadedName);

        MRStream<String> loadedStream = mMoonRock.getStreams().makeSingleShotStream(mLoadedName, String.class);
        loadedStream.getObservable().observeOn(AndroidSchedulers.mainThread()).subscribe(r -> {
            mReadySubject.onNext(this);
            mReadySubject.onCompleted();
        });

        mMoonRock.runJS(loadScript, null);
    }

    public <T> void subscribeOnActivity(Observable<T> observable) {

    }

    public String getLoadedName() {
        return mLoadedName;
    }

    public MRPortalGenerator getPortalGenerator() {
        return mPortalGenerator;
    }

    public void generatePortals() {
        mPortalGenerator.generatePortals();
    }

    public void unlinkPortals() {
        mPortalGenerator.unlinkPortals();
        setPortalHost(null);
    }

    public void setPortalHost(Object portalHost) {
        this.mPortalHost = portalHost;
        mPortalGenerator.setPortalHost(portalHost);
    }
}
