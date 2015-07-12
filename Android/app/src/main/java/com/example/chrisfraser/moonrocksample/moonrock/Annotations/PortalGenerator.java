package com.example.chrisfraser.moonrocksample.moonrock.Annotations;

import com.bluelinelabs.logansquare.LoganSquare;
import com.example.chrisfraser.moonrocksample.moonrock.MoonRock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;

import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;

public class PortalGenerator {
    private final MoonRock mMoonRock;

    private String mLoadedName;

    public PortalGenerator(MoonRock moonRock) {
        this.mMoonRock = moonRock;
    }

    public void setLoadedName(String loadedName) {
        mLoadedName = loadedName;
    }

    public <T> void generatePortals(Object host) {
        for (Field field : host.getClass().getDeclaredFields()) {
            Portal portal = field.getAnnotation(Portal.class);
            if (portal != null) {
                try {
                    field.setAccessible(true);
                    PublishSubject subject = PublishSubject.create();
                    field.set(host, subject);
                    String name = portal.name().isEmpty() ? field.getName() : portal.name();
                    portal(subject, name);
                } catch (Exception e) {
                }
            }
            ReversePortal reversePortal = field.getAnnotation(ReversePortal.class);
            if (reversePortal != null) {
                try {
                    field.setAccessible(true);
                    PublishSubject subject = PublishSubject.create();
                    field.set(host, subject.observeOn(AndroidSchedulers.mainThread()));
                    String name = reversePortal.name().isEmpty() ? field.getName() : reversePortal.name();
                    reversePortal(subject, name, classForField(field));
                } catch (Exception e) {
                }
            }
        }
        this.portalsGenerated();
    }

    Class<?> classForField(Field field) {
        return (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
    }

    public <T> void portal(PublishSubject<T> subject, String name) {
        String createScript = String.format("mrHelper.portal('%s', '%s')", mLoadedName, name);
        mMoonRock.runJS(createScript, null);

        subject.subscribe(input -> {
            try {
                String serializedInput = input != null ? LoganSquare.serialize(input) : "null";
                String mirrorScript = String.format("mrHelper.activatePortal('%s', '%s', '%s')", mLoadedName, name, serializedInput);
                mMoonRock.runJS(mirrorScript, null);
            } catch (IOException e) {
                e.printStackTrace();
            }

        });
    }

    public <T> void reversePortal(PublishSubject<T> mReverseSubject, String name, Class<T> unpackClass) {
        mMoonRock.getReversePortals().registerReverse(mReverseSubject, name, unpackClass);
        String reverseScript = String.format("mrHelper.reversePortal('%s', '%s')", mLoadedName, name);
        mMoonRock.runJS(reverseScript, null);
    }

    public void portalsGenerated() {
        String finishedScript = String.format("mrHelper.portalsGenerated('%s')", mLoadedName);
        mMoonRock.runJS(finishedScript, null);
    }
}