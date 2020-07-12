package com.hankki.fooddeal.ui.register;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Debug;
import android.telephony.SmsManager;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.hankki.fooddeal.R;
import com.hankki.fooddeal.data.RegularCheck;
import com.hankki.fooddeal.data.security.AES256Util;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

// 본격 회원가입 창 이전에 휴대폰 번호를 인증하는 액티비티
// TODO TimeTask 대신 TextWatcher를 사용하면 더 자원관리가 효율적일 것 같으니 나중에 변경하면 될듯
public class PhoneAuthActivity extends AppCompatActivity {

    @SuppressLint("StaticFieldLeak")
    public static Activity activity;

    Button authNumSendButton, authNumCheckButton, postButton;
    EditText userPhoneNumEditText, authNumEditText;
    TextView timerTextView;
    String randomAuthNum;

    boolean isFirstExecuted = true;
    boolean isAuthDone, isAuthTimerOver, isBackPressed;

    Disposable disposable;

    Timer authNumTimer, checkRegularPhoneNoTimer;
    TimerTask authNumTimerTask, checkRegularPhoneNoTimerTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_auth_reg);

        activity = PhoneAuthActivity.this;
    }

    // 초기 UX 자원 할당
    private void initFindViewById() {
        authNumSendButton = (Button) findViewById(R.id.auth_num_send_button);
        // 인증번호 전송 버튼 클릭 시, 기입한 번호로 인증번호를 전송
        // TODO DB 연동 시, 해당 휴대폰 번호 중복여부 확인
        authNumSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phoneNo = userPhoneNumEditText.getText().toString();
                if(RegularCheck.isRegularPhoneNo(phoneNo)) {
                    setSMSTask(phoneNo);
                    authNumCheckButton.setEnabled(true);
                    authNumSendButton.setText("재전송");
                    authNumEditText.requestFocus();
                }
                else Toast.makeText(getApplicationContext(), "올바른 휴대폰 번호가 아닙니다\n휴대폰 번호를 확인해주세요", Toast.LENGTH_LONG).show();
            }
        });
        authNumCheckButton = (Button) findViewById(R.id.auth_num_check_button);
        /*
        인증번호 확인 버튼 클릭 시
        1. 제한시간 내에 인증 번호를 올바르게 입력했을 경우 -> RegisterActivity로 이동
        2. 인증 번호를 올바르게 입력했으나 제한시간을 오바한 경우 -> 재인증 요구
        3. 인증 번호가 올바르지 않은 경우 -> 인증번호 불일치 메시지
        */
        authNumCheckButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String authNumInput = authNumEditText.getText().toString();
                if(isAuthTimerOver) {
                    Toast.makeText(getApplicationContext(), "인증유효시간이 초과하였습니다.\n인증번호를 재발급받아주세요", Toast.LENGTH_LONG).show();
                } else {
                    if (authNumInput.equals(randomAuthNum)) {
                        stopAuthNumTimerTask();
                        if(v != null) {
                            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
                        }
                        randomAuthNum = null;
                        postButton.setEnabled(true);
                        isAuthDone = true;
                    } else {
                        Toast.makeText(getApplicationContext(), "인증번호가 일치하지 않습니다", Toast.LENGTH_LONG).show();
                        authNumEditText.setText("");
                        postButton.setEnabled(false);
                    }
                }
            }
        });
        postButton = findViewById(R.id.auth_num_post_button);
        postButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isAuthDone) {
                    authNumEditText.setText("");
                    String phoneNo = AES256Util.aesEncode(userPhoneNumEditText.getText().toString());
                    Intent toRegisterIntent = new Intent(PhoneAuthActivity.this, RegisterActivity.class);
                    toRegisterIntent.putExtra("phoneNo", phoneNo);
                    startActivity(toRegisterIntent);
                    isAuthDone = false;
                } else {
                    Toast.makeText(getApplicationContext(), "휴대폰 인증이 완료되지 않았습니다", Toast.LENGTH_LONG).show();
                }
            }
        });
        userPhoneNumEditText = (EditText) findViewById(R.id.user_phone_num_edittext);
        userPhoneNumEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus) {
                    startRegularPhoneNoCheckTimerTask();
                }
                else {
                    stopRegularPhoneNoCheckTimerTask();
                }
            }
        });
        authNumEditText = (EditText) findViewById(R.id.auth_num_edittext);
        timerTextView = (TextView) findViewById(R.id.auth_num_check_timer);
    }

    // 인증번호를 포함한 문자메시지 전송
    private void setSMSTask(String phoneNo) {
        //onPreExecute
        //doInBackground
        disposable = Observable.fromCallable(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                Random rand = new Random();
                StringBuilder sb = new StringBuilder();

                for(int i=0;i<6;i++) sb.append(Integer.toString(rand.nextInt(10)));

                randomAuthNum = sb.toString();
                String smsText = "HANKKi 인증번호" + "\n[" + randomAuthNum + "]";

                PendingIntent sentIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent("SMS_SENT_ACTION"), 0);

                SmsManager mSmsManager = SmsManager.getDefault();
                mSmsManager.sendTextMessage(phoneNo, null, smsText, sentIntent, null);

                return false;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object result) throws Exception {
                        //onPostExecute
                        // 문자메시지를 전송한 직후에 제한시간 TimerTask를 시작
                        disposable.dispose();
                        authNumEditText.setText("");
                        startAuthNumTimerTask();
                    }
                });
    }

    // 유효한 전화번호인지 체크하는 TimerTask 시작
    private void startRegularPhoneNoCheckTimerTask() {
        stopRegularPhoneNoCheckTimerTask();
        checkRegularPhoneNoTimer = new Timer();

        checkRegularPhoneNoTimerTask = new TimerTask() {
            @Override
            public void run() {
                authNumSendButton.post(new Runnable() {
                    @Override
                    public void run() {
                        String userInputPhoneNo = userPhoneNumEditText.getText().toString();
                        if(RegularCheck.isRegularPhoneNo(userInputPhoneNo)) authNumSendButton.setEnabled(true);
                        else authNumSendButton.setEnabled(false);
                    }
                });
            }
        };

        checkRegularPhoneNoTimer.schedule(checkRegularPhoneNoTimerTask, 0, 200);
    }

    // 유효한 전화번호인지 체크하는 TimerTask 중지
    private void stopRegularPhoneNoCheckTimerTask() {
        if(checkRegularPhoneNoTimerTask != null) {
            checkRegularPhoneNoTimerTask.cancel();
            checkRegularPhoneNoTimerTask = null;
        }
    }

    // 인증번호 유효시간 타이머 시작
    private void startAuthNumTimerTask() {
        stopAuthNumTimerTask();
        authNumTimer = new Timer();

        authNumTimerTask = new TimerTask() {
            int count = 180;

            @Override
            public void run() {
                timerTextView.post(new Runnable() {
                    @Override
                    public void run() {
                        String min = Integer.toString(count/60);
                        String sec;

                        if((count%60) < 10) sec = "0" + Integer.toString(count%60);
                        else sec = Integer.toString(count%60);

                        String time = "0" + min + ":" + sec;
                        timerTextView.setText(time);
                    }
                });
                count--;
                if(count == 0) {
                    isAuthTimerOver = true;
                    stopAuthNumTimerTask();
                }
            }
        };
        authNumTimer.schedule(authNumTimerTask, 0, 1000);
    }

    // 인증번호 표시 TimerTask 중지
    private void stopAuthNumTimerTask() {
        if(authNumTimerTask != null) {
            timerTextView.setText(getString(R.string.activity_phone_auth_default_timer));
            authNumTimerTask.cancel();
            authNumTimerTask = null;
        }
    }

    // 자원 해제
    private void releaseResource() {
        authNumSendButton = null;
        authNumCheckButton = null;
        userPhoneNumEditText = null;
        authNumEditText = null;
        timerTextView = null;
        authNumTimer = null;
        checkRegularPhoneNoTimer = null;

        disposable = null;
    }

    // 사용자에게 보여지기 전 자원 할당
    @Override
    protected void onStart() {
        super.onStart();
        if(isFirstExecuted) {
            initFindViewById();
            isFirstExecuted = false;
        }
    }

    // 사용자가 뒤로가기버튼으로 액티비티를 종료한 경우에서만 자원 할당 해제
    @Override
    protected void onStop() {
        super.onStop();
        if(isBackPressed) {
            stopAuthNumTimerTask();
            stopRegularPhoneNoCheckTimerTask();
            releaseResource();
            activity = null;
        }
    }

    // 개인정보입력 액티비티가 끝나면 이 액티비티도 자동으로 종료되게 만들 예정이라 자원할당 해제할 타이밍이 없음.
    // 비권장사항이지만 일단은 이렇게
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAuthNumTimerTask();
        stopRegularPhoneNoCheckTimerTask();
        releaseResource();
        Debug.stopMethodTracing();
    }

    // 뒤로가기 버튼
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        isBackPressed = true;
        finish();
    }
}