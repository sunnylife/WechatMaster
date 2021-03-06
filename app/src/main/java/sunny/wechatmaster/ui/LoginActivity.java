package sunny.wechatmaster.ui;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.hyphenate.EMCallBack;
import com.hyphenate.chat.EMClient;
import com.hyphenate.easeui.utils.EaseCommonUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import sunny.wechatmaster.Constant;
import sunny.wechatmaster.R;
import sunny.wechatmaster.WeApplication;
import sunny.wechatmaster.WeHelper;
import sunny.wechatmaster.db.WeDBManager;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends BaseActivity {
    private static final String TAG = "LoginActivity";
    public static final int REQUEST_CODE_SETNICK = 1;
    private EditText usernameEditText;
    private EditText passwordEditText;
    private EditText appIdEditText;
    private EditText appSecretEditText;
    private EditText tokenEditText;

    private boolean progressShow;
    private boolean autoLogin = false;

    private String currentUsername;
    private String currentPassword;
    private String currentAppId;
    private String currentAppSecret;
    private String currentToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 如果登录成功过，直接进入主页面
        if (WeHelper.getInstance().isLoggedIn()) {
            autoLogin = true;
            startActivity(new Intent(LoginActivity.this, MainActivity.class));

            return;
        }
        setContentView(R.layout.em_activity_login);

        usernameEditText = (EditText) findViewById(R.id.username);
        passwordEditText = (EditText) findViewById(R.id.password);
        appIdEditText = (EditText) findViewById(R.id.appid);
        appSecretEditText = (EditText) findViewById(R.id.appsecret);
        tokenEditText = (EditText) findViewById(R.id.token);

        // 如果用户名改变，清空密码
        usernameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                passwordEditText.setText(null);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        if (WeHelper.getInstance().getCurrentUsernName() != null) {
            usernameEditText.setText(WeHelper.getInstance().getCurrentUsernName());
        }
    }

    /**
     * 登录
     *
     * @param view
     */
    public void login(View view) {
        if (!EaseCommonUtils.isNetWorkConnected(this)) {
            Toast.makeText(this, R.string.network_isnot_available, Toast.LENGTH_SHORT).show();
            return;
        }
        currentUsername = usernameEditText.getText().toString().trim();
        currentPassword = passwordEditText.getText().toString().trim();
        currentAppId = appIdEditText.getText().toString().trim();
        currentAppSecret = appSecretEditText.getText().toString().trim();
        currentToken = tokenEditText.getText().toString().trim();

        if (TextUtils.isEmpty(currentUsername)) {
            Toast.makeText(this, R.string.User_name_cannot_be_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(currentPassword)) {
            Toast.makeText(this, R.string.Password_cannot_be_empty, Toast.LENGTH_SHORT).show();
            return;
        }
//        if (TextUtils.isEmpty(currentAppId)) {
//            Toast.makeText(this, R.string.App_id_cannot_be_empty, Toast.LENGTH_SHORT).show();
//            return;
//        }
//        if (TextUtils.isEmpty(currentAppSecret)) {
//            Toast.makeText(this, R.string.App_secret_cannot_be_empty, Toast.LENGTH_SHORT).show();
//            return;
//        }
//        if (TextUtils.isEmpty(currentToken)) {
//            Toast.makeText(this, R.string.Token_cannot_be_empty, Toast.LENGTH_SHORT).show();
//            return;
//        }

        progressShow = true;
        final ProgressDialog pd = new ProgressDialog(LoginActivity.this);
        pd.setCanceledOnTouchOutside(false);
        pd.setOnCancelListener(new DialogInterface.OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                Log.d(TAG, "EMClient.getInstance().onCancel");
                progressShow = false;
            }
        });
        pd.setMessage(getString(R.string.Is_landing));
        pd.show();

        // After logout，the DemoDB may still be accessed due to async callback, so the DemoDB will be re-opened again.
        // close it before login to make sure DemoDB not overlap
        WeDBManager.getInstance().closeDB();

        // reset current user name before login
        WeHelper.getInstance().setCurrentUserName(currentUsername);

        final long start = System.currentTimeMillis();
        // 调用sdk登陆方法登陆聊天服务器
        Log.d(TAG, "EMClient.getInstance().login");

        RequestQueue queue = Volley.newRequestQueue(this);
        Map<String, String> params = new HashMap<>();
        params.put("name", currentUsername);
        params.put("pwd", currentPassword);
        params.put("imgcode", "");
        params.put("app_id", "wxb06dc71c11a0567c");
        params.put("secret", "f1945727d91e9973f99c7f2d095050d0");
        params.put("token", "sunny");

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.POST, Constant.BASE_URL + "/login", new JSONObject(params), new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            String password = response.getString("password");
                            EMClient.getInstance().login(currentUsername, password, new EMCallBack() {

                                @Override
                                public void onSuccess() {
                                    Log.d(TAG, "login: onSuccess");

                                    if (!LoginActivity.this.isFinishing() && pd.isShowing()) {
                                        pd.dismiss();
                                    }

                                    // ** 第一次登录或者之前logout后再登录，加载所有本地群和回话
                                    // ** manually load all local groups and
                                    EMClient.getInstance().groupManager().loadAllGroups();
                                    EMClient.getInstance().chatManager().loadAllConversations();

                                    // 更新当前用户的nickname 此方法的作用是在ios离线推送时能够显示用户nick
                                    boolean updatenick = EMClient.getInstance().updateCurrentUserNick(
                                            WeApplication.currentUserNick.trim());
                                    if (!updatenick) {
                                        Log.e("LoginActivity", "update current user nick fail");
                                    }
                                    //异步获取当前用户的昵称和头像(从自己服务器获取，demo使用的一个第三方服务)
                                    WeHelper.getInstance().getUserProfileManager().asyncGetCurrentUserInfo();

                                    // 进入主页面
                                    Intent intent = new Intent(LoginActivity.this,
                                            MainActivity.class);
                                    startActivity(intent);

                                    finish();
                                }

                                @Override
                                public void onProgress(int progress, String status) {
                                    Log.d(TAG, "login: onProgress");
                                }

                                @Override
                                public void onError(final int code, final String message) {
                                    Log.d(TAG, "login: onError: " + code);
                                    if (!progressShow) {
                                        return;
                                    }
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            pd.dismiss();
                                            Toast.makeText(getApplicationContext(), getString(R.string.Login_failed) + message,
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            });
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                });
        queue.add(jsonObjectRequest);
    }
}
