/*
 * Copyright (C) 2018 xuexiangjys(xuexiangjys@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xuexiang.xutil.system.wifi;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.xuexiang.xutil.R;
import com.xuexiang.xutil.common.StringUtils;
import com.xuexiang.xutil.net.NetworkUtils;
import com.xuexiang.xutil.system.ThreadPoolManager;
import com.xuexiang.xutil.tip.ToastUtil;

/**
 * 热点连接辅助类
 * @author xuexiang
 * @date 2018/2/19 上午12:29
 */
public class WifiAPHelper {

    private static volatile WifiAPHelper sInstance;

    /**
     * 热点ssid
     */
    private String mWifiAPSsid;
    /**
     * 热点的密码
     */
    private String mWifiAPPassword;

    private Handler mWifiHandler;
    private WifiManager mWifiManager;

    //工作进程
    private CloseWifiRunnable mCloseWifiRunnable;
    private StartWifiApRunnable mStartWifiApRunnable;
    private CloseWifiApRunnable mCloseWifiApRunnable;

    private OnWifiAPStatusChangedListener mListener;

    /**
     * 构造方法
     */
    public WifiAPHelper() {
        mWifiManager = NetworkUtils.getWifiManager();
        mWifiHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 设置wifi热点的ssid和pwd
     * @param wifiAPSsid
     * @param wifiAPPassword
     * @return
     */
    public WifiAPHelper setWifiAPConfig(String wifiAPSsid, String wifiAPPassword) {
        mWifiAPSsid = wifiAPSsid;
        mWifiAPPassword = wifiAPPassword;
        return this;
    }

    public WifiAPHelper setWifiAPSsid(String wifiAPSsid) {
        mWifiAPSsid = wifiAPSsid;
        return this;
    }

    public WifiAPHelper setWifiAPPassword(String wifiAPPassword) {
        mWifiAPPassword = wifiAPPassword;
        return this;
    }

    public WifiAPHelper setOnWifiAPStatusChangedListener(OnWifiAPStatusChangedListener listener) {
        mListener = listener;
        return this;
    }

    /**
     * 获取热点连接助手
     * @return
     */
    public static WifiAPHelper get() {
        if (sInstance == null) {
            synchronized (WifiAPHelper.class) {
                if (sInstance == null) {
                    sInstance = new WifiAPHelper();
                }
            }
        }
        return sInstance;
    }


    /**
     * 开启WLAN热点
     */
    public void startWifiAp() {
        if (mWifiManager.isWifiEnabled()) {
            closeWifiTh();
        } else {
            startWifiApTh();
        }
    }

    /**
     * 关闭WLAN热点
     */
    public void closeWifiAp() {
        stopWifiApTh();
    }

    /**
     * 关闭Wifi的线程
     */
    private void closeWifiTh() {
        mCloseWifiRunnable = new CloseWifiRunnable();
        ThreadPoolManager.get().addTask(mCloseWifiRunnable);
    }

    /**
     * 开启热点线程
     */
    private void startWifiApTh() {
        if (!WifiAPUtil.isWifiApEnable()) {
            WifiAPUtil.startWifiAp(mWifiAPSsid, mWifiAPPassword);
        }
        mStartWifiApRunnable = new StartWifiApRunnable();
        ThreadPoolManager.get().addTask(mStartWifiApRunnable);
    }

    /**
     * 关闭热点线程
     */
    private void stopWifiApTh() {
        mCloseWifiApRunnable = new CloseWifiApRunnable();
        ThreadPoolManager.get().addTask(mCloseWifiApRunnable);
    }


    /**
     * 关闭wifi的线程
     */
    private class CloseWifiRunnable implements Runnable {
        @Override
        public void run() {
            int state = mWifiManager.getWifiState();
            if (state == WifiManager.WIFI_STATE_ENABLED) {
                mWifiManager.setWifiEnabled(false);
                mWifiHandler.postDelayed(mCloseWifiRunnable, 100);
            } else if (state == WifiManager.WIFI_STATE_DISABLING) {
                mWifiHandler.postDelayed(mCloseWifiRunnable, 100);
            } else if (state == WifiManager.WIFI_STATE_DISABLED) {
                mWifiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        startWifiApTh();
                        ToastUtil.get().toast(R.string.tip_close_wifi_success);
                    }
                });
            }
        }
    }

    /**
     * 打开wifi热点的线程
     */
    private class StartWifiApRunnable implements Runnable {
        public void run() {
            int state = WifiAPUtil.getWifiApState();
            if (state == WifiAPUtil.WIFI_AP_STATE_FAILED) {
                mWifiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ToastUtil.get().toast(R.string.tip_open_wifiap_failed);
                        if (mListener != null) {
                            mListener.onWifiAPStatusChanged(false);
                        }
                    }
                });
            } else if (state == WifiAPUtil.WIFI_AP_STATE_DISABLED) {
                mWifiHandler.postDelayed(mStartWifiApRunnable, 100);
            } else if (state == WifiAPUtil.WIFI_AP_STATE_ENABLING) {
                mWifiHandler.postDelayed(mStartWifiApRunnable, 100);
            } else if (state == WifiAPUtil.WIFI_AP_STATE_ENABLED) {
                mWifiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ToastUtil.get().toast(R.string.tip_open_wifiap_success);
                        if (mListener != null) {
                            mListener.onWifiAPStatusChanged(true);
                        }
                    }
                });
            }
        }
    }

    /**
     * 关闭wifi热点的线程
     */
    private class CloseWifiApRunnable implements Runnable {
        @Override
        public void run() {
            int state = WifiAPUtil.getWifiApState();
            if (state == WifiAPUtil.WIFI_AP_STATE_ENABLED) {
                WifiAPUtil.stopWifiAp(mWifiAPSsid, mWifiAPPassword);
                mWifiHandler.postDelayed(mCloseWifiApRunnable, 100);
            } else if (state == WifiAPUtil.WIFI_AP_STATE_DISABLING || state == WifiAPUtil.WIFI_AP_STATE_FAILED) {
                mWifiHandler.postDelayed(mCloseWifiApRunnable, 100);
            } else if (state == WifiAPUtil.WIFI_AP_STATE_DISABLED) {
                mWifiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ToastUtil.get().toast(R.string.tip_close_wifiap_success);
                        if (mListener != null) {
                            mListener.onWifiAPStatusChanged(false);
                        }
                    }
                });
            }
        }
    }

    /**
     * 资源释放
     */
    public void release() {
        mWifiHandler.removeCallbacksAndMessages(null);
        mListener = null;
    }


    //=========wifi状态========//
    /**
     * 判断wifi是否连接成功
     *
     * @return
     */
    public boolean isWifiConnectSuccess(String ssid) {
        return mWifiManager.isWifiEnabled() && checkSSIDState(ssid) && NetworkUtils.isHaveInternet();
    }

    /**
     * 判断某个网络有没有连接
     *
     * @param ssid
     * @return
     */
    private boolean checkSSIDState(String ssid) {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            String SSID = getSSID(wifiInfo);
            if (!StringUtils.isEmpty(SSID)) {
                if (SSID.equals("\"" + ssid + "\"") || SSID.equals(ssid)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 得到接入点的SSID
     */
    public String getSSID(WifiInfo wifiInfo) {
        return (wifiInfo == null) ? "" : wifiInfo.getSSID();
    }

    /**
     * 热点状态改变的事件监听
     *
     * @author xx
     */
    public interface OnWifiAPStatusChangedListener {

        /**
         * 热点状态发生改变
         * @param isEnable
         */
        void onWifiAPStatusChanged(boolean isEnable);
    }
}
