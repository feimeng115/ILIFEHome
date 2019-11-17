package com.aliyun.iot.aep.sdk.contant;

import android.os.Build;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.sdk.android.openaccount.OpenAccountSDK;
import com.alibaba.sdk.android.openaccount.callback.LoginCallback;
import com.alibaba.sdk.android.openaccount.model.OpenAccountSession;
import com.alibaba.sdk.android.openaccount.ui.OpenAccountUIService;
import com.alibaba.sdk.android.openaccount.ui.util.ToastUtils;
import com.aliyun.alink.linksdk.channel.core.base.AError;
import com.aliyun.alink.linksdk.channel.mobile.api.IMobileConnectListener;
import com.aliyun.alink.linksdk.channel.mobile.api.IMobileDownstreamListener;
import com.aliyun.alink.linksdk.channel.mobile.api.IMobileSubscrbieListener;
import com.aliyun.alink.linksdk.channel.mobile.api.MobileChannel;
import com.aliyun.alink.linksdk.channel.mobile.api.MobileConnectState;
import com.aliyun.iot.aep.sdk._interface.OnAliBindDeviceResponse;
import com.aliyun.iot.aep.sdk._interface.OnAliResponse;
import com.aliyun.iot.aep.sdk._interface.OnAliResponseSingle;
import com.aliyun.iot.aep.sdk._interface.OnAliSetPropertyResponse;
import com.aliyun.iot.aep.sdk._interface.OnDevicePoropertyResponse;
import com.aliyun.iot.aep.sdk.apiclient.IoTAPIClient;
import com.aliyun.iot.aep.sdk.apiclient.IoTAPIClientFactory;
import com.aliyun.iot.aep.sdk.apiclient.callback.IoTCallback;
import com.aliyun.iot.aep.sdk.apiclient.callback.IoTResponse;
import com.aliyun.iot.aep.sdk.apiclient.callback.IoTUIThreadCallback;
import com.aliyun.iot.aep.sdk.apiclient.emuns.Scheme;
import com.aliyun.iot.aep.sdk.apiclient.request.IoTRequest;
import com.aliyun.iot.aep.sdk.apiclient.request.IoTRequestBuilder;
import com.aliyun.iot.aep.sdk.bean.DeviceInfoBean;
import com.aliyun.iot.aep.sdk.bean.HistoryRecordBean;
import com.aliyun.iot.aep.sdk.bean.OTAInfoBean;
import com.aliyun.iot.aep.sdk.bean.PropertyBean;
import com.aliyun.iot.aep.sdk.bean.RealTimeMapBean;
import com.aliyun.iot.aep.sdk.bean.ScheduleBean;
import com.aliyun.iot.aep.sdk.credential.IotCredentialManager.IoTCredentialManageImpl;
import com.aliyun.iot.aep.sdk.credential.listener.IoTTokenInvalidListener;
import com.aliyun.iot.aep.sdk.delegate.AliInterfaceDelegate;
import com.aliyun.iot.aep.sdk.delegate.BindDeviceDelagate;
import com.aliyun.iot.aep.sdk.delegate.GetHistoryMapDelegate;
import com.aliyun.iot.aep.sdk.delegate.GetHistoryRecordDelegate;
import com.aliyun.iot.aep.sdk.framework.AApplication;
import com.aliyun.iot.aep.sdk.login.ILoginCallback;
import com.aliyun.iot.aep.sdk.login.ILogoutCallback;
import com.aliyun.iot.aep.sdk.login.LoginBusiness;
import com.aliyun.iot.aep.sdk.login.data.UserInfo;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * //TODO WORKINGDEVICE 需要序列化到本地
 */
public class IlifeAli {
    private static final String TAG = "ILIFE_ALI_";
    private static IlifeAli instance;
    private OnAliResponseSingle<Boolean> tTokenInvalidListener;//登录失效监听
    /**
     * 可能为空，需要序列化到本地，并且在为空的时候重新反序列化回来
     */
    private DeviceInfoBean workingDevice;
    private AApplication aApplication;
    private IoTAPIClient ioTAPIClient;
    private String iotId;
    private IMobileDownstreamListener downListener;
    private IMobileSubscrbieListener topicListener;//订阅topic

    public static synchronized IlifeAli getInstance() {
        if (instance == null) {
            synchronized (IlifeAli.class) {
                if (instance == null) {
                    instance = new IlifeAli();
                }
            }
        }
        return instance;
    }

    private IoTRequest buildRequest(String path, HashMap<String, Object> params) {
        return new IoTRequestBuilder()
                .setAuthType(EnvConfigure.IOT_AUTH)
                .setScheme(Scheme.HTTPS)        // 如果是HTTPS，可以省略本设置
                .setPath(path)                  // 参考业务API文档，设置path
                .setApiVersion(EnvConfigure.API_VER)          // 参考业务API文档，设置apiVersion
                .setParams(params)
                .build();
    }


    /**
     * 解注册，重置变量 etc
     */
    public void reset() {
        workingDevice = null;
        MobileChannel.getInstance().unSubscrbie(EnvConfigure.TOPIC, topicListener);
        MobileChannel.getInstance().unRegisterDownstreamListener(downListener);
        topicListener = null;
        downListener = null;
    }

    public void setWorkingDevice(DeviceInfoBean workingDevice) {
        this.workingDevice = workingDevice;
        this.iotId = workingDevice.getIotId();
    }

    public DeviceInfoBean getWorkingDevice() {
        return workingDevice;
    }

    public String getIotId() {
        return iotId;
    }

    public void setIotId(String iotId) {
        this.iotId = iotId;
    }

    /**
     * 设置账号登录异常监听器
     *
     * @param tTokenInvalidListener
     */
    public void settTokenInvalidListener(OnAliResponseSingle<Boolean> tTokenInvalidListener) {
        this.tTokenInvalidListener = tTokenInvalidListener;
    }

    /**
     * 初始化账号，通道等
     *
     * @param context
     */
    public void init(AApplication context) {
        this.aApplication = context;
        ioTAPIClient = new IoTAPIClientFactory().getClient();
        //TODO 登录失败处理
    }

    public void logOut(final OnAliResponse<String> onAliResponse) {
        LoginBusiness.logout(new ILogoutCallback() {
            @Override
            public void onLogoutSuccess() {
                onAliResponse.onSuccess("login success");
            }

            @Override
            public void onLogoutFailed(int i, String s) {
                onAliResponse.onFailed(i, s);
            }
        });
    }

    /**
     * 判断账号是否登录
     *
     * @return
     */
    public boolean isLogin() {
        boolean isLogin = LoginBusiness.isLogin();
        ;
        Log.d(TAG, "是否已登录。。。" + isLogin);
        return isLogin;
    }

    /**
     * 登录/判断已登录
     *
     * @param onAliResponse
     */
    public void login(final OnAliResponse<Boolean> onAliResponse) {
        if (isLogin()) {
            onAliResponse.onSuccess(true);
        } else {
            LoginBusiness.login(new ILoginCallback() {
                @Override
                public void onLoginSuccess() {
                    onAliResponse.onSuccess(true);
                }

                @Override
                public void onLoginFailed(int code, String error) {
                    onAliResponse.onFailed(code, error);
                }
            });
        }
    }

    /**
     * 强制登录
     *
     * @param onAliResponse
     */
    public void forceLogin(final OnAliResponse<Boolean> onAliResponse) {
        LoginBusiness.login(new ILoginCallback() {
            @Override
            public void onLoginSuccess() {
                onAliResponse.onSuccess(true);
            }

            @Override
            public void onLoginFailed(int code, String error) {
                onAliResponse.onFailed(code, error);
            }
        });
    }


    /**
     * 绑定设备
     *
     * @param homeSsid
     * @param homePassword
     * @param onBindDeviceComplete
     */
    public void bindDevice(String homeSsid, String homePassword, OnAliBindDeviceResponse<String> onBindDeviceComplete) {
        BindDeviceDelagate bindDeviceDelagate = new BindDeviceDelagate(aApplication, homeSsid, homePassword, onBindDeviceComplete);
        bindDeviceDelagate.connectDevice();
    }

    public void reNameDevice(String name, final OnAliResponseSingle<Boolean> onAliResponseSingle) {
        if (iotId == null || iotId.isEmpty()) {
            onAliResponseSingle.onResponse(false);
            return;
        }
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        params.put(EnvConfigure.KEY_DEV_NICKNAME, name);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_SET_DEV_NICK_NAME, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onAliResponseSingle.onResponse(false);
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                if (ioTResponse.getCode() != 200) {
                    onAliResponseSingle.onResponse(false);
                } else {
                    onAliResponseSingle.onResponse(true);
                }
            }
        }));
    }

    /**
     * 获取账号下的设备列表
     *
     * @param onAliResponse
     */
    public void listDeviceByAccount(OnAliResponse<List<DeviceInfoBean>> onAliResponse) {
        AliInterfaceDelegate aliInterfaceDelegate = new AliInterfaceDelegate();
        aliInterfaceDelegate.listDeviceByAccount(onAliResponse);
    }

    /**
     * 解绑设备
     *
     * @param iotId
     * @param onAliResponse
     */
    public void unBindDevice(final String iotId, final OnAliResponse<Boolean> onAliResponse) {
        AliInterfaceDelegate aliInterfaceDelegate = new AliInterfaceDelegate();
        aliInterfaceDelegate.requestUnbind(iotId, new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                //unbind fail
                onAliResponse.onFailed(0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                //unbind success
                if (ioTResponse.getCode() != 200) {
                    onAliResponse.onFailed(0, ioTResponse.getMessage());
                } else {
                    onAliResponse.onSuccess(true);
                }
            }
        }));
    }


    /**
     * 订阅TOPIC
     */
    public void registerSubscribeTopic() {
        if (topicListener == null) {
            topicListener = new IMobileSubscrbieListener() {
                @Override
                public void onSuccess(String s) {
                    Log.d(TAG, "registerSubscribeTopic success: " + s);
                }

                @Override
                public void onFailed(String s, AError aError) {
                    Log.d(TAG, "registerSubscribeTopic failed: " + s + "----" + aError.getDomain());
                }

                @Override
                public boolean needUISafety() {
                    return false;
                }
            };
        }
        MobileChannel.getInstance().subscrbie(EnvConfigure.TOPIC, topicListener);
    }

    public void registerDownStream(final OnDevicePoropertyResponse onDevicePoropertyResponse) {
        if (downListener == null) {
            downListener = new IMobileDownstreamListener() {
                @Override
                public void onCommand(String method, String data) {
                    Log.d(TAG, "method:      " + method + "---      data:" + data);
                    JSONObject object = JSONObject.parseObject(data);
                    String iot = object.getString(EnvConfigure.KEY_IOT_ID);
                    if (!iot.equals(iotId)) {//
                        return;
                    }
                    if (method.equals(EnvConfigure.METHOD_THING_PROP)) {//property,status
                        JSONObject items = object.getJSONObject(EnvConfigure.KEY_ITEMS);
                        if (items != null) {
                            if (items.containsKey(EnvConfigure.KEY_POWER_SWITCH)) {
                                ToastUtils.toast(aApplication, items.getJSONObject(EnvConfigure.KEY_POWER_SWITCH).getString(EnvConfigure.KEY_VALUE));
                            } else if (items.containsKey(EnvConfigure.KEY_WORK_MODE)) {//工作状态
                                onDevicePoropertyResponse.onStatusChange(items.getJSONObject(EnvConfigure.KEY_WORK_MODE).getIntValue(EnvConfigure.KEY_VALUE));
                            } else if (items.containsKey(EnvConfigure.KEY_REALTIMEMAP)) {//实时地图数据
                                Gson gson = new Gson();
                                RealTimeMapBean realTimeMapBean = gson.fromJson(items.getJSONObject(EnvConfigure.KEY_REALTIMEMAP).getString(EnvConfigure.KEY_VALUE), RealTimeMapBean.class);
                                onDevicePoropertyResponse.onRealMap(realTimeMapBean);
                            } else if (items.containsKey(EnvConfigure.KEY_REAL_TIME_MAP_START)) {
//                            long start = items.getJSONObject(EnvConfigure.KEY_REAL_TIME_MAP_START).getLongValue(EnvConfigure.KEY_TIME);
                                onDevicePoropertyResponse.onRealTimeMapStart();
                            } else if (items.containsKey(EnvConfigure.KEY_BATTERY_STATE)) {

                            }
                        }
                    } else if (method.equals(EnvConfigure.METHOD_THING_EVENT)) {
                        int errorCode = object.getJSONObject(EnvConfigure.KEY_VALUE).getIntValue(EnvConfigure.KEY_ERRORCODE);
//                    if (errorCode==0){
//                        return;
//                    }
                        onDevicePoropertyResponse.onError(errorCode);
                    }
                }

                @Override
                public boolean shouldHandle(String method) {
                    // method 即为Topic,e.g. /thing/properties,/thing/events,/thing/status，如果该Topic需要处理，返回true后onCommand才会回调。
                    Log.d(TAG, "method:      " + method);
                    return true;
                }
            };
        }
        registerSubscribeTopic();
        MobileChannel.getInstance().registerDownstreamListener(true, downListener);

        /** 注册通道的状态变化,记得调用 unRegisterConnectListener */
        MobileChannel.getInstance().registerConnectListener(true, new IMobileConnectListener() {
            @Override
            public void onConnectStateChange(MobileConnectState state) {
                int stateCode = -1;
                String value = "";
                //参考 MobileConnectState.CONNECTED
                switch (state) {
                    case CONNECTED:
                        //已连接
                        value = "连接改变：已连接";
                        stateCode = 1;
                        break;
                    case DISCONNECTED:
                        value = "连接改变，已断开";
                        //已断开
                        stateCode = 2;
                        break;
                    case CONNECTING:
                        value = "连接改变，连接中";
                        //连接中
                        stateCode = 3;
                        break;
                    case CONNECTFAIL:
                        value = "连接改变，连接失败";
                        //连接失败
                        stateCode = 4;
                        break;
                }
                Log.d(TAG, value);
            }
        });

    }

    public void setProperties(HashMap<String, Object> params, OnAliSetPropertyResponse onAliResponse) {
        AliInterfaceDelegate.setPropertyToDevice(params, onAliResponse);
    }

    public void getProperties(final OnAliResponse<PropertyBean> onAliResponse) {
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_TAG, EnvConfigure.VALUE_GET_PROPERTY);
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_GET_PROPERTIES, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onAliResponse.onFailed(0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                if (ioTResponse.getCode() == 200) {
                    Log.d(TAG, "get propertied data:    " + ioTResponse.getData().toString());
                    JSONObject jsonObject = JSON.parseObject(ioTResponse.getData().toString());
                    boolean max = jsonObject.getJSONObject(EnvConfigure.KEY_MAX_MODE).getIntValue(EnvConfigure.KEY_VALUE) == 1;
                    int battery = jsonObject.getJSONObject(EnvConfigure.KEY_BATTERY_STATE).getIntValue(EnvConfigure.KEY_VALUE);
                    int workMode = jsonObject.getJSONObject(EnvConfigure.KEY_WORK_MODE).getIntValue(EnvConfigure.KEY_VALUE);
                    int waterLevel = jsonObject.getJSONObject(EnvConfigure.KEY_WATER_CONTROL).getIntValue(EnvConfigure.KEY_VALUE);
                    long startTimeLine;
                    if (jsonObject.containsKey(EnvConfigure.KEY_REAL_TIME_MAP_START)) {
                        startTimeLine = jsonObject.getJSONObject(EnvConfigure.KEY_REAL_TIME_MAP_START).getLongValue(EnvConfigure.KEY_TIME);
                    } else {
                        startTimeLine = System.currentTimeMillis();
                    }
//                    long historyTimeLine = jsonObject.getJSONObject(EnvConfigure.KEY_HISTORY_START_TIME).getIntValue(EnvConfigure.KEY_VALUE);
                    long historyTimeLine = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000;//获取一周的清扫记录
                    boolean voiceOpen = jsonObject.getJSONObject(EnvConfigure.KEY_BEEP_NO_DISTURB).getJSONObject(EnvConfigure.KEY_VALUE).getIntValue(EnvConfigure.KEY_SWITCH) == 0;
                    onAliResponse.onSuccess(new PropertyBean(max, workMode, battery, waterLevel, startTimeLine, historyTimeLine, voiceOpen));
                } else {
                    onAliResponse.onFailed(0, ioTResponse.getMessage());
                }
            }
        }));
    }

    public UserInfo getUserInfo() {
        return LoginBusiness.getUserInfo();
    }

    public void queryUserData(final OnAliResponse<String> onAliResponse) {
        HashMap<String, Object> params = new HashMap<>();
        HashMap<String, Object> ids = new HashMap<>();
        List<String> list = new ArrayList<>();
        list.add(IoTCredentialManageImpl.getInstance(aApplication).getIoTCredential().identity);
        ids.put("identityIds", list);
        params.put("request", ids);
        IoTRequest request = new IoTRequestBuilder()
                .setScheme(Scheme.HTTPS)        // 如果是HTTPS，可以省略本设置
                .setPath(EnvConfigure.PATH_QUERY_ACCOUNT)                  // 参考业务API文档，设置path
                .setApiVersion("1.0.4")          // 参考业务API文档，设置apiVersion
                .setParams(params)
                .build();
        ioTAPIClient.send(request, new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onAliResponse.onFailed(0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                Log.e(TAG, "user account data:" + ioTResponse.getData().toString());
                if (ioTResponse.getCode() != 200) {
                    onAliResponse.onFailed(0, ioTResponse.getMessage());
                } else {
                    onAliResponse.onSuccess(ioTResponse.getData().toString());

                }
            }
        }));
    }

    /**
     * {
     * "id":1508232047194,
     * "request": {
     * "iotToken": "109049c80bcde4c06b15f6f62e29a3ba",
     * "apiVer": "1.0.5"
     * },
     * "params": {
     * "request": {"identityId":"50e5opda16ebf5558e000a660ac9632a038c2479", "accountMetaV2":{"phone":"15757286621", "appKey":"60039075","nickName":"test"}}
     * },
     * "version": "1.0"
     * }
     *
     * @param onAliResponse
     */
    public void resetNickName(String nickName, final OnAliResponseSingle<Boolean> onAliResponse) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("displayName", nickName);
        OpenAccountUIService oas = OpenAccountSDK.getService(OpenAccountUIService.class);
        oas.updateProfile(aApplication, map, new LoginCallback() {
            @Override
            public void onSuccess(OpenAccountSession openAccountSession) {
                onAliResponse.onResponse(true);
            }

            @Override
            public void onFailure(int i, String s) {
                onAliResponse.onResponse(false);
            }
        });
    }

    public void qrBindDevice(String shareCode, final OnAliResponseSingle<Boolean> onAliResponse) {
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_QR_KEY, shareCode);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_BIND_BY_SHARECODE, params), new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onAliResponse.onResponse(false);
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                onAliResponse.onResponse(ioTResponse.getCode() == 200);
            }
        });

    }

    /**
     * 非管理员不能分享
     *
     * @param iotId
     * @param onAliResponse
     */
    public void showShareQrCode(final String iotId, final OnAliResponse<String> onAliResponse) {
        ArrayList<String> iotIdList = new ArrayList<>();
        iotIdList.add(iotId);
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_IOT_ID_LIST, iotIdList);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_GENE_SHARE_CODE, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onAliResponse.onFailed(0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                if (ioTResponse.getCode() != 200) {
                    onAliResponse.onFailed(0, ioTResponse.getMessage());
                } else {
                    onAliResponse.onSuccess(ioTResponse.getData().toString());
                }

            }
        }));

    }

    /**
     * 请求示例： "params": {
     * "appVersion": "10.0.0",
     * "type": 1,
     * "productKey": "a1OwEjYFJNb",
     * "content": "重试多次配网失败",
     * "mobileModel": "iPhoneX",
     * "contact": "15066666666",
     * "mobileSystem": "ios",
     * "appVersion": "1.1",
     * "iotId": "fy2Z1oZFWZQVii6kkFVM00101edf00",
     * "topic": "设备无法配网",
     * "devicename": "手环"
     * }
     *
     * @param contact
     * @param content
     * @param appVersion
     * @param iotId
     * @param onAliResponse
     */
    public void commitFeedback(String contact, String content, String type, String appVersion, String iotId, final OnAliResponseSingle<Boolean> onAliResponse) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("mobileSystem", "Android");
        params.put("appVersion", appVersion);
        params.put("type", 103);
        params.put("productKey", EnvConfigure.PRODUCT_KEY_X800);
        params.put("content", content);
        params.put("iotId", iotId);
        params.put("mobileModel", Build.MODEL);
        params.put("contact", contact);
        params.put("topic", "意见反馈主题");
        params.put("devicename", "智意扫地机-" + type);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_ADD_FEEDBACK, params), new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onAliResponse.onResponse(false);
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                onAliResponse.onResponse(true);
            }
        });
    }


    public void setTimeZone() {
        String tz = "{\"TimeZone\":{\"TimeZone\":1,\"SummerTime\":2}}";
        TimeZone timeZone = TimeZone.getDefault();
        HashMap<String, Object> params = new HashMap<>();
        JSONObject json = JSONObject.parseObject(tz);
        json.getJSONObject("TimeZone").put("TimeZone", timeZone.getRawOffset() / (3600 * 1000));
        json.getJSONObject("TimeZone").put("SummerTime", timeZone.getDSTSavings());
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        params.put(EnvConfigure.KEY_ITEMS, json);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_SET_PROPERTIES, params), new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                Log.d(TAG, "设置时钟失败。。。。。。。。。。。。");
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                Log.d(TAG, "设置时钟成功。。。。。。。。。。。。");
            }
        });
    }

    public void getErrorEvent(final OnAliResponseSingle<Integer> onAliResponse) {
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_TAG, EnvConfigure.VALUE_GET_EVENT);
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_GET_EVENT, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                Log.d(TAG, "获取设备事件----错误码失败！");
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                if (ioTResponse.getCode() == 200) {
                    String data = ioTResponse.getData().toString();
                    Log.d(TAG, "获取设备事件成功：  " + data);
                    JSONArray errors = JSONObject.parseArray(data);
                    if (errors != null && errors.size() > 0) {
                        JSONObject object = (JSONObject) errors.get(0);
                        if (object.containsKey(EnvConfigure.KEY_EVENTBODY)) {
                            int errorCode = object.getJSONObject(EnvConfigure.KEY_EVENTBODY).getIntValue(EnvConfigure.KEY_ERRORCODE);
                            if (errorCode != 0) {
                                onAliResponse.onResponse(errorCode);
                            }
                        }
                    }
                }
            }
        }));
    }

    public void findDevice(final OnAliSetPropertyResponse onResponse) {
        JSONObject json_find = JSONObject.parseObject("{\"FindRobot\":1}");
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        params.put(EnvConfigure.KEY_ITEMS, json_find);
        params.put(EnvConfigure.KEY_TAG, EnvConfigure.VALUE_FIND_ROBOT);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_SET_PROPERTIES, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onResponse.onFailed(ioTRequest.getPath(), EnvConfigure.VALUE_FIND_ROBOT, 0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                onResponse.onSuccess(ioTRequest.getPath(), EnvConfigure.VALUE_FIND_ROBOT, 1, ioTResponse.getCode());
            }
        }));

    }

    public void resetDeviceToFactory(final OnAliSetPropertyResponse onResponse) {
        JSONObject json = JSONObject.parseObject("{\"ResetFactory\":1}");
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        params.put(EnvConfigure.KEY_ITEMS, json);
        params.put(EnvConfigure.KEY_TAG, EnvConfigure.VALUE_FAC_RESET);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_SET_PROPERTIES, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onResponse.onFailed(ioTRequest.getPath(), EnvConfigure.VALUE_FAC_RESET, 0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                onResponse.onSuccess(EnvConfigure.PATH_SET_PROPERTIES, EnvConfigure.VALUE_FAC_RESET, 1, ioTResponse.getCode());
            }
        }));
    }

    public void waterControl(final int level, final OnAliSetPropertyResponse onResponse) {
        String jsonStr = "";
        switch (level) {
            case 0:
                jsonStr = "{\"WaterTankContrl\":0}";
                break;
            case 1:
                jsonStr = "{\"WaterTankContrl\":1}";
                break;
            case 2:
                jsonStr = "{\"WaterTankContrl\":2}";
                break;
        }
        JSONObject json = JSONObject.parseObject(jsonStr);
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        params.put(EnvConfigure.KEY_ITEMS, json);
        params.put(EnvConfigure.KEY_EXTRA, level);
        params.put(EnvConfigure.KEY_TAG, EnvConfigure.VALUE_SET_WATER);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_SET_PROPERTIES, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onResponse.onFailed(ioTRequest.getPath(), EnvConfigure.VALUE_SET_WATER, 0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                onResponse.onSuccess(EnvConfigure.PATH_SET_PROPERTIES, EnvConfigure.VALUE_SET_WATER, level, ioTResponse.getCode());
            }
        }));
    }

    public void setMaxMode(int maxMode, final OnAliSetPropertyResponse onResponse) {
        String jsonStr;
        final int mode;
        if (maxMode == 0) {
            mode = 1;
            jsonStr = "{\"MaxMode\":1}";
        } else {
            mode = 0;
            jsonStr = "{\"MaxMode\":0}";
        }
        JSONObject json = JSONObject.parseObject(jsonStr);
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        params.put(EnvConfigure.KEY_ITEMS, json);
        params.put(EnvConfigure.KEY_EXTRA, mode);
        params.put(EnvConfigure.KEY_TAG, EnvConfigure.VALUE_SET_MAX);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_SET_PROPERTIES, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onResponse.onFailed(ioTRequest.getPath(), EnvConfigure.VALUE_SET_MAX, 0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                onResponse.onSuccess(EnvConfigure.PATH_SET_PROPERTIES, EnvConfigure.VALUE_SET_MAX, mode, ioTResponse.getCode());
            }
        }));
    }


    public void setVoiceOpen(boolean isOpen, final OnAliSetPropertyResponse onResponse) {
        final int isDisturbOpen = isOpen ? 0 : 1;
        String jst = "{\"BeepNoDisturb\":{\"Switch\":0,\"Time\":0}}";
        JSONObject json = JSONObject.parseObject(jst);
        json.getJSONObject(EnvConfigure.KEY_BEEP_NO_DISTURB).put(EnvConfigure.KEY_SWITCH, isDisturbOpen);
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        params.put(EnvConfigure.KEY_ITEMS, json);
        params.put(EnvConfigure.KEY_EXTRA, isDisturbOpen);
        params.put(EnvConfigure.KEY_TAG, EnvConfigure.VALUE_SET_VOICE_SWITCH);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_SET_PROPERTIES, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onResponse.onFailed(ioTRequest.getPath(), EnvConfigure.VALUE_SET_VOICE_SWITCH, 0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                if (ioTResponse.getCode() == 200) {
                    onResponse.onSuccess(EnvConfigure.PATH_SET_PROPERTIES, EnvConfigure.VALUE_SET_VOICE_SWITCH, isDisturbOpen, ioTResponse.getCode());
                } else {
                    Log.d(TAG, "请求失败，错误信息： " + ioTResponse.getLocalizedMsg());
                }
            }
        }));
    }


    public void setSchedule(int position, final int open, final int hour, final int minute, final OnAliResponse<ScheduleBean> onResponse) {
        final String schedule = "{\"Schedule\":{\"ScheduleHour\":0,\"ScheduleType\":0,\"ScheduleEnd\":300,\"ScheduleEnable\":0,\"ScheduleMode\":6,\"ScheduleWeek\":1,\"ScheduleArea\":\"AAAAAAAAAAAAAAAA\",\"ScheduleMinutes\":0}}";
        position = position == 0 ? 7 : position;
        String str = EnvConfigure.KEY_SCHEDULE + position;
        String schedule_ = schedule.replaceFirst(EnvConfigure.KEY_SCHEDULE, str);
        final JSONObject json = JSONObject.parseObject(schedule_);
        json.getJSONObject(str).put(EnvConfigure.KEY_SCHEDULE_ENABLE, open);
        json.getJSONObject(str).put(EnvConfigure.KEY_SCHEDULE_HOUR, hour);
        json.getJSONObject(str).put(EnvConfigure.KEY_SCHEDULE_MINUTES, minute);
        json.getJSONObject(str).put(EnvConfigure.KEY_SCHEDULE_WEEK, position);
        HashMap params = new HashMap();
        params.clear();
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        params.put(EnvConfigure.KEY_ITEMS, json);
        params.put(EnvConfigure.KEY_POSITION, position);
        Log.d(TAG, "VALUE:   " + json);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_SET_PROPERTIES, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onResponse.onFailed(0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                if (ioTResponse.getCode() == 200) {
                    ScheduleBean scheduleBean = new ScheduleBean();
                    scheduleBean.setScheduleEnable(open);
                    scheduleBean.setScheduleMinutes(minute);
                    scheduleBean.setScheduleHour(hour);
                    onResponse.onSuccess(scheduleBean);
                }
            }
        }));
    }

    public void getScheduleInfo(final OnAliResponse<String> onAliResponse) {
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_TAG, EnvConfigure.VALUE_GET_PROPERTY);
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_GET_PROPERTIES, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onAliResponse.onFailed(0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                if (ioTResponse.getCode() == 200) {
                    String content = ioTResponse.getData().toString();
                    onAliResponse.onSuccess(content);
                }
            }
        }));
    }

    public void queryConsumer(final OnAliResponse<String> onAliResponse) {
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_TAG, EnvConfigure.VALUE_GET_PROPERTY);
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_GET_PROPERTIES, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onAliResponse.onFailed(0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                if (ioTResponse.getCode() == 200) {
                    onAliResponse.onSuccess(ioTResponse.getData().toString());
                }
            }
        }));
    }

    public void resetConsumer(int sideBrushLife, int rollLife, int fillterLife, final OnAliResponse<String> onAliResponse) {
        String str = "{\"PartsStatus\":{\"FilterLife\":0,\"SideBrushLife\":0,\"MainBrushLife\":0}}";
        JSONObject json = JSONObject.parseObject(str);
        json.getJSONObject(EnvConfigure.KEY_PARTS_STATUS).put(EnvConfigure.KEY_SIDE_BRUSH_LIFE, sideBrushLife);
        json.getJSONObject(EnvConfigure.KEY_PARTS_STATUS).put(EnvConfigure.KEY_MAIN_BRUSH_LIFE, rollLife);
        json.getJSONObject(EnvConfigure.KEY_PARTS_STATUS).put(EnvConfigure.KEY_FILTER_LIFE, fillterLife);
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        params.put(EnvConfigure.KEY_ITEMS, json);
        params.put(EnvConfigure.KEY_TAG, EnvConfigure.VALUE_SET_PARTSTIME);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_SET_PROPERTIES, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onAliResponse.onFailed(0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                if (ioTResponse.getCode() == 200) {
                    onAliResponse.onSuccess(ioTResponse.getData().toString());
                }
            }
        }));
    }

    /**
     * 实时地图页面的历史清扫数据
     *
     * @param start
     * @param end
     * @param onAliResponse
     */
    //地图数据多于200包的处理
    public void getCleaningHistory(long start, long end, OnAliResponse<List<RealTimeMapBean>> onAliResponse) {
        GetHistoryMapDelegate history = new GetHistoryMapDelegate(ioTAPIClient, iotId, start, end, onAliResponse);
        history.getRealHistoryMap();
    }


    /**
     * 清扫记录数据
     *
     * @param end
     */
    public void getHistoryRecords(long start, long end, final OnAliResponse<List<HistoryRecordBean>> onAliResponse) {
        GetHistoryRecordDelegate delegate = new GetHistoryRecordDelegate(start, end, onAliResponse, ioTAPIClient, iotId);
        delegate.getHistoryRecords();
    }

    /**
     * 查询固件升级版本
     *
     * @param onAliResponse
     */
    public void queryOtaVersion(final OnAliResponse<OTAInfoBean> onAliResponse) {
        HashMap<String, Object> params = new HashMap<>();
        params.put(EnvConfigure.KEY_TAG, EnvConfigure.VALUE_GET_PROPERTY);
        params.put("identifier", EnvConfigure.KEY_OTA_INFO);
        params.put(EnvConfigure.KEY_IOT_ID, iotId);
        ioTAPIClient.send(buildRequest(EnvConfigure.PATH_GET_PROPERTIES, params), new IoTUIThreadCallback(new IoTCallback() {
            @Override
            public void onFailure(IoTRequest ioTRequest, Exception e) {
                onAliResponse.onFailed(0, e.getMessage());
            }

            @Override
            public void onResponse(IoTRequest ioTRequest, IoTResponse ioTResponse) {
                String content = ioTResponse.getData().toString();
                if (ioTResponse.getCode() != 200 || content == null) {
                    onAliResponse.onFailed(0, "response error,and error message is" + ioTResponse.getMessage());
                } else {
                    Log.d(TAG, "获取设备OTA信息：" + ioTResponse.getData().toString());
                    JSONObject json = JSONObject.parseObject(content);
                    if (json.containsKey(EnvConfigure.KEY_OTA_INFO)) {
                        String data = json.getJSONObject(EnvConfigure.KEY_OTA_INFO).getString(EnvConfigure.KEY_VALUE);
                        OTAInfoBean otaInfoBean = new Gson().fromJson(data, OTAInfoBean.class);
                        if (otaInfoBean == null) {
                            onAliResponse.onFailed(0, "response error,and error message is" + ioTResponse.getMessage());
                        } else {
                            onAliResponse.onSuccess(otaInfoBean);
                        }
                    } else {
//                        onAliResponse.onFailed(0, "response error,and error message is 未发现OTA信息" );
                        OTAInfoBean otaInfoBean = new OTAInfoBean();
                        otaInfoBean.setCurrentVer(100);
                        otaInfoBean.setTargetVer(101);
                        otaInfoBean.setUpdateProgess(0);
                        otaInfoBean.setUpdateState(1);
                        onAliResponse.onSuccess(otaInfoBean);
                    }
                }
            }
        }));
    }

}
