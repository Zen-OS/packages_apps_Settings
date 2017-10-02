/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.development;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.support.annotation.VisibleForTesting;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

public class ShowSurfaceUpdatesPreferenceController extends DeveloperOptionsPreferenceController
        implements Preference.OnPreferenceChangeListener {

    private static final String SHOW_SCREEN_UPDATES_KEY = "show_screen_updates";

    private static final int SETTING_VALUE_ON = 1;
    private static final int SETTING_VALUE_OFF = 0;

    @VisibleForTesting
    static final String SURFACE_FLINGER_SERVICE_KEY = "SurfaceFlinger";
    @VisibleForTesting
    static final int SURFACE_FLINGER_READ_CODE = 1010;

    private static final int SURFACE_FLINGER_WRITE_SURFACE_UPDATES_CODE = 1002;
    private static final String SURFACE_COMPOSER_INTERFACE_KEY = "android.ui.ISurfaceComposer";

    private final IBinder mSurfaceFlinger;
    private SwitchPreference mPreference;

    public ShowSurfaceUpdatesPreferenceController(Context context) {
        super(context);
        mSurfaceFlinger = ServiceManager.getService(SURFACE_FLINGER_SERVICE_KEY);
    }

    @Override
    public String getPreferenceKey() {
        return SHOW_SCREEN_UPDATES_KEY;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);

        mPreference = (SwitchPreference) screen.findPreference(getPreferenceKey());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final boolean isEnabled = (Boolean) newValue;
        writeShowUpdatesSetting(isEnabled);
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        updateShowUpdatesSetting();
    }

    @Override
    protected void onDeveloperOptionsSwitchEnabled() {
        mPreference.setEnabled(true);
    }

    @Override
    protected void onDeveloperOptionsSwitchDisabled() {
        if (mPreference.isChecked()) {
            // Writing false to the preference when the setting is already off will have a
            // side effect of turning on the preference that we wish to avoid
            writeShowUpdatesSetting(false);
            mPreference.setChecked(false);
        }
        mPreference.setEnabled(false);
    }

    @VisibleForTesting
    void updateShowUpdatesSetting() {
        // magic communication with surface flinger.
        try {
            if (mSurfaceFlinger != null) {
                final Parcel data = Parcel.obtain();
                final Parcel reply = Parcel.obtain();
                data.writeInterfaceToken(SURFACE_COMPOSER_INTERFACE_KEY);
                mSurfaceFlinger.transact(SURFACE_FLINGER_READ_CODE, data, reply, 0 /* flags */);
                @SuppressWarnings("unused") final int showCpu = reply.readInt();
                @SuppressWarnings("unused") final int enableGL = reply.readInt();
                final int showUpdates = reply.readInt();
                mPreference.setChecked(showUpdates != SETTING_VALUE_OFF);
                reply.recycle();
                data.recycle();
            }
        } catch (RemoteException ex) {
            // intentional no-op
        }
    }

    @VisibleForTesting
    void writeShowUpdatesSetting(boolean isEnabled) {
        try {
            if (mSurfaceFlinger != null) {
                final Parcel data = Parcel.obtain();
                data.writeInterfaceToken(SURFACE_COMPOSER_INTERFACE_KEY);
                final int showUpdates = isEnabled ? SETTING_VALUE_ON : SETTING_VALUE_OFF;
                data.writeInt(showUpdates);
                mSurfaceFlinger.transact(SURFACE_FLINGER_WRITE_SURFACE_UPDATES_CODE, data,
                        null /* reply */, 0 /* flags */);
                data.recycle();
            }
        } catch (RemoteException ex) {
            // intentional no-op
        }
        updateShowUpdatesSetting();
    }
}