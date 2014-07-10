/*
 * Copyright (C) 2014  Xiao-Long Chen <chenxiaolong@cxl.epac.to>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.chenxiaolong.dualbootpatcher.switcher;

import android.content.Context;
import android.os.AsyncTask;

import com.github.chenxiaolong.dualbootpatcher.R;
import com.github.chenxiaolong.dualbootpatcher.RomUtils;
import com.github.chenxiaolong.dualbootpatcher.RomUtils.RomInformation;
import com.squareup.otto.Bus;

public class SwitcherTasks {
    private static final Bus BUS = new Bus();

    // Otto bus

    public static Bus getBusInstance() {
        return BUS;
    }

    // Events

    public static class SwitcherTaskEvent {
    }

    public static class OnChoseRomEvent extends SwitcherTaskEvent {
        boolean failed;
        String message;
        String kernelId;
    }

    public static class OnSetKernelEvent extends SwitcherTaskEvent {
        boolean failed;
        String message;
        String kernelId;
    }

    // Task starters

    public static void chooseRom(String kernelId) {
        new ChooseRomTask(kernelId).execute();
    }

    public static void setKernel(String kernelId) {
        new SetKernelTask(kernelId).execute();
    }

    // Tasks

    private static class ChooseRomTask extends AsyncTask<Void, Void, Void> {
        private String mKernelId;

        private boolean mFailed;
        private String mMessage;

        public ChooseRomTask(String kernelId) {
            mKernelId = kernelId;
        }

        @Override
        protected Void doInBackground(Void... params) {
            mFailed = true;
            mMessage = "";

            try {
                SwitcherUtils.writeKernel(mKernelId);
                mFailed = false;
            } catch (Exception e) {
                mMessage = e.getMessage();
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            OnChoseRomEvent event = new OnChoseRomEvent();
            event.failed = mFailed;
            event.message = mMessage;
            event.kernelId = mKernelId;
            getBusInstance().post(event);
        }
    }

    private static class SetKernelTask extends AsyncTask<Void, Void, Void> {
        private String mKernelId;

        private boolean mFailed;
        private String mMessage;

        public SetKernelTask(String kernelId) {
            mKernelId = kernelId;
        }

        @Override
        protected Void doInBackground(Void... params) {
            mFailed = true;
            mMessage = "";

            try {
                SwitcherUtils.backupKernel(mKernelId);
                mFailed = false;
            } catch (Exception e) {
                mMessage = e.getMessage();
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            OnSetKernelEvent event = new OnSetKernelEvent();
            event.failed = mFailed;
            event.message = mMessage;
            event.kernelId = mKernelId;
            getBusInstance().post(event);
        }
    }
}
