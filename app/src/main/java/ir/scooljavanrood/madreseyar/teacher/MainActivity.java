package ir.scooljavanrood.madreseyar.teacher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import java.net.URLEncoder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.crypto.Cipher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * A05 / first from-scratch native foundation. Every View is Android native; without embedded browser.
 * Works against existing /app/api/staff and /app/api/teacher JSON endpoints.
 * Only operations implemented and verified against these API contracts are exposed.
 */
public final class MainActivity extends FragmentActivity {
    private static final int NAVY = Color.rgb(26, 48, 87);
    private static final int BLUE = Color.rgb(45, 104, 222);
    private static final int AQUA = Color.rgb(51, 179, 201);
    private static final int INK = Color.rgb(29, 50, 81);
    private static final int MUTED = Color.rgb(118, 136, 163);
    private static final int BG = Color.rgb(245, 248, 255);
    private static final int BORDER = Color.rgb(225, 234, 250);
    private static final int WHITE = Color.WHITE;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final String[] tabs = {"خانه", "کلاس‌ها", "حضور", "نمرات", "بیشتر"};
    private final String[] tabIcons = {"⌂", "▦", "✓", "★", "•••"};
    private final Map<String, Boolean> present = new HashMap<>();
    private BiometricVault vault;
    private String token = "";
    private JSONObject me = new JSONObject();
    private JSONObject dashboard = new JSONObject();
    private JSONObject workspace = new JSONObject();
    private LinearLayout screen;
    private LinearLayout content;
    private LinearLayout nav;
    private String section = "خانه";
    private boolean loading = false;
    private boolean unlocked = false;
    private boolean bioShowing = false;
    private boolean bioOfferShown = false;
    private long hiddenAt = 0;

    /** Keep this app Persian, independently of the phone-wide language. */
    @Override protected void attachBaseContext(Context base) {
        Locale persian = new Locale("fa", "IR");
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(persian);
        config.setLayoutDirection(persian);
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        vault = new BiometricVault(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        renderLogin();
        if (vault.isEnabled()) screen.postDelayed(this::unlockSavedSession, 360);
    }
    @Override protected void onStop() {
        super.onStop();
        hiddenAt = System.currentTimeMillis();
        // Never keep personal data in the Android task thumbnail.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    @Override protected void onResume() {
        super.onResume();
        // Screenshot is permitted only while the app is visibly foregrounded.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (hiddenAt > 0 && System.currentTimeMillis() - hiddenAt > 30000 && unlocked && !bioShowing) {
            unlocked = false;
            token = "";
            renderLogin();
            if (vault.isEnabled()) screen.postDelayed(this::unlockSavedSession, 160);
        }
    }
    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }

    private int dp(float value) { return (int)(getResources().getDisplayMetrics().density * value + .5f); }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private GradientDrawable edged(int fill, int radius, int stroke) {
        GradientDrawable d = rounded(fill, radius); d.setStroke(dp(1), stroke); return d;
    }
    private static String digitsFa(String value) {
        if (value == null) return "";
        StringBuilder output = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') c = (char) ('۰' + (c - '0'));
            else if (c >= '٠' && c <= '٩') c = (char) ('۰' + (c - '٠'));
            output.append(c);
        }
        return output.toString();
    }

    /** Messages sent by the server may be in either language. */
    private String persianError(Throwable error) {
        if (error instanceof SchoolApi.HttpError) {
            SchoolApi.HttpError http = (SchoolApi.HttpError) error;
            if (http.code == 401) return "نشست شما پایان یافته یا اطلاعات ورود صحیح نیست. دوباره وارد شوید.";
            if (http.code == 403) return "شما برای انجام این عملیات دسترسی ندارید.";
            if (http.code == 404) return "این بخش در سرور مدرسه پیدا نشد.";
            if (http.code >= 500) return "سرور مدرسه فعلاً پاسخگو نیست. کمی بعد تلاش کنید.";
        }
        if (error instanceof java.net.SocketTimeoutException)
            return "زمان انتظار برای پاسخ سرور تمام شد. اتصال اینترنت را بررسی کنید.";
        if (error instanceof java.net.UnknownHostException || error instanceof java.net.ConnectException)
            return "اتصال به سرور برقرار نشد. اینترنت گوشی را بررسی کنید.";
        if (error instanceof javax.net.ssl.SSLException)
            return "اتصال امن به سرور برقرار نشد. ساعت و اینترنت گوشی را بررسی کنید.";
        String message = error == null ? "" : error.getMessage();
        if (message == null || message.trim().isEmpty()) return "در ارتباط با مدرسه خطایی رخ داد. دوباره تلاش کنید.";
        // The server's Persian explanation is useful; unknown technical English is not user-facing.
        return message.matches("(?s).*[\u0600-\u06FF].*") ? digitsFa(message) : "عملیات انجام نشد. لطفاً دوباره تلاش کنید.";
    }

    private String localizedStatus(String status) {
        if (status == null || status.trim().isEmpty()) return "نامشخص";
        switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "draft": return "پیش‌نویس";
            case "published": return "منتشرشده";
            case "active": case "open": return "فعال";
            case "scheduled": return "زمان‌بندی‌شده";
            case "closed": case "finished": case "completed": return "پایان‌یافته";
            case "pending": return "در انتظار بررسی";
            case "submitted": return "ارسال‌شده";
            case "graded": return "تصحیح‌شده";
            case "archived": return "بایگانی‌شده";
            case "cancelled": case "canceled": return "لغوشده";
            default:
                // Do not expose raw machine statuses in English.
                return status.matches("[-A-Za-z_ ]+") ? "نامشخص" : digitsFa(status);
        }
    }

    private TextView text(String label, int sp, int color, boolean strong) {
        TextView t = new TextView(this); t.setText(digitsFa(label)); t.setTextColor(color); t.setTextSize(sp);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        t.setTypeface(Typeface.create("sans-serif", strong ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height)); }
    private void pad(View v, int left, int top, int right, int bottom) { v.setPadding(dp(left), dp(top), dp(right), dp(bottom)); }
    private void gap(LinearLayout parent, int h) { View s = new View(this); parent.addView(s, lp(1, h)); }
    private LinearLayout card(int padding) {
        LinearLayout box = column(); box.setBackground(edged(WHITE, 22, BORDER)); pad(box,padding,padding,padding,padding); box.setElevation(dp(2)); return box;
    }
    private void addCard(LinearLayout parent, LinearLayout card) {
        LinearLayout.LayoutParams p = lp(-1,-2); p.bottomMargin=dp(13); parent.addView(card,p);
    }
    private void toast(String value) { Toast.makeText(this,digitsFa(value),Toast.LENGTH_LONG).show(); }
    private void fail(String error) { if (!isFinishing()) new AlertDialog.Builder(this).setTitle("مدرسه‌یار").setMessage(digitsFa(error)).setPositiveButton("متوجه شدم",null).show(); }
    private String s(JSONObject o, String key) { return o == null ? "" : o.optString(key,""); }
    private String fullName(JSONObject o) { return (s(o,"firstName")+" "+s(o,"lastName")).trim(); }
    private JSONArray arr(JSONObject o,String key) { JSONArray value=o==null?null:o.optJSONArray(key);return value==null?new JSONArray():value; }
    private JSONObject obj(JSONArray a,int index) { JSONObject value=a.optJSONObject(index);return value==null?new JSONObject():value; }
    private interface Work { JSONObject run() throws Exception; }
    private void background(Work work, Consumer<JSONObject> onSuccess) {
        if (loading) return;
        loading = true;
        io.execute(() -> {
            try {
                JSONObject result=work.run(); runOnUiThread(() -> {
                    loading=false; if(!isFinishing()) onSuccess.accept(result);
                });
            } catch(Exception error) {runOnUiThread(() -> {
                loading=false; if(isFinishing())return;
                if(error instanceof SchoolApi.HttpError && ((SchoolApi.HttpError)error).code==401 && !token.isEmpty()){
                    token=""; unlocked=false; vault.clear(); renderLogin();
                }
                if(token.isEmpty() && !unlocked) renderLogin();
                fail(persianError(error));
            });}
        });
    }
    private String nameOfClass(String id) {
        JSONArray a=arr(workspace,"classes");for(int i=0;i<a.length();i++){JSONObject x=obj(a,i);if(id.equals(s(x,"id")))return s(x,"name");}
        return "کلاس";
    }
    private int studentCount(String classId) { int n=0;JSONArray a=arr(workspace,"students");for(int i=0;i<a.length();i++)if(classId.equals(s(obj(a,i),"classId")))n++;return n; }
    private ArrayList<JSONObject> studentsOf(String classId) {
        ArrayList<JSONObject> list=new ArrayList<>();JSONArray a=arr(workspace,"students");for(int i=0;i<a.length();i++){JSONObject x=obj(a,i);if(classId.equals(s(x,"classId")))list.add(x);}return list;
    }
    private void shell() {
        screen=column();screen.setBackgroundColor(BG);setContentView(screen);
        LinearLayout toolbar=row();toolbar.setBackgroundColor(WHITE);pad(toolbar,20,16,20,14);
        LinearLayout labels=column();TextView title=text("مدرسه‌یار  ·  معلم",18,NAVY,true);labels.addView(title);TextView sub=text(section,12,MUTED,false);labels.addView(sub);
        toolbar.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        TextView refresh=text("↻",27,BLUE,true);refresh.setGravity(Gravity.CENTER);refresh.setBackground(rounded(BG,14));toolbar.addView(refresh,lp(45,45));refresh.setOnClickListener(v->refresh());
        screen.addView(toolbar,lp(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content=column();pad(content,18,20,18,22);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        nav=row();nav.setBackground(edged(WHITE,18,BORDER));pad(nav,4,6,4,7);nav.setElevation(dp(12));
        for(int i=0;i<tabs.length;i++){
            final String target=tabs[i];boolean active=section.equals(target);
            LinearLayout button=column();button.setGravity(Gravity.CENTER);pad(button,2,7,2,6);
            if(active)button.setBackground(rounded(0xffe9f1ff,14));
            TextView icon=text(tabIcons[i],20,active?BLUE:MUTED,true);icon.setGravity(Gravity.CENTER);
            TextView label=text(target,11,active?BLUE:MUTED,active);label.setGravity(Gravity.CENTER);
            button.addView(icon);button.addView(label);
            nav.addView(button,new LinearLayout.LayoutParams(0,dp(59),1f));button.setOnClickListener(v->openTab(target));
        }
        screen.addView(nav,lp(-1,-2));
    }
    private void openTab(String tab) { section=tab;render(); }
    private void refresh(){ if(token.isEmpty()){renderLogin();return;} fetchData(); }
    private void fetchData() {
        section="خانه";shell();TextView t=text("در حال دریافت اطلاعات امن از مدرسه…",15,MUTED,false);content.addView(t);
        background(() -> {
            JSONObject meResponse=SchoolApi.request("/api/staff/me","GET",token,null);
            if(!"teacher".equals(s(meResponse.optJSONObject("user"),"role")))throw new Exception("این برنامه فقط برای نقش معلم طراحی شده است.");
            JSONObject d=SchoolApi.request("/api/teacher/dashboard","GET",token,null);
            JSONObject w=SchoolApi.request("/api/teacher/workspace","GET",token,null);
            JSONObject bundle=new JSONObject();bundle.put("user",meResponse.optJSONObject("user"));bundle.put("dashboard",d);bundle.put("workspace",w);return bundle;
        }, result -> {
            me=result.optJSONObject("user");dashboard=result.optJSONObject("dashboard");workspace=result.optJSONObject("workspace");
            unlocked=true;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);render();
            if(!bioOfferShown && !vault.isEnabled()) { bioOfferShown=true; maybeOfferBiometric(); }
        });
    }
    private Button primary(String name) {
        Button b=new Button(this); b.setText(digitsFa(name));b.setTextColor(WHITE);b.setTextSize(15);b.setAllCaps(false);b.setTextDirection(View.TEXT_DIRECTION_RTL);b.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(rounded(BLUE,16));b.setMinHeight(dp(54));return b;
    }
    private Button outline(String name) {
        Button b=new Button(this);b.setText(digitsFa(name));b.setTextColor(BLUE);b.setTextSize(13);b.setAllCaps(false);b.setTextDirection(View.TEXT_DIRECTION_RTL);b.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);b.setBackground(edged(0xfff8fbff,12,BORDER));return b;
    }
    private EditText field(String hint,boolean secret) {
        EditText t=new EditText(this);t.setSingleLine(true);t.setTextSize(15);t.setTextColor(INK);t.setHintTextColor(MUTED);
        t.setHint(digitsFa(hint));t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);t.setTextDirection(View.TEXT_DIRECTION_RTL);t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        t.setBackground(edged(WHITE,14,BORDER));pad(t,15,9,15,9);
        if(secret)t.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return t;
    }
    private void renderLogin() {
        section="خانه";screen=column();screen.setBackgroundColor(BG);setContentView(screen);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);screen.addView(scroll,lp(-1,-1));
        LinearLayout body=column();pad(body,24,24,24,25);scroll.addView(body);
        gap(body,18);
        LinearLayout hero=column();GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{NAVY,BLUE,AQUA});gradient.setCornerRadius(dp(30));hero.setBackground(gradient);pad(hero,24,35,24,35);
        TextView mark=text("م",50,WHITE,true);mark.setGravity(Gravity.CENTER);mark.setBackground(rounded(0x33ffffff,28));LinearLayout.LayoutParams icon=lp(90,90);icon.gravity=Gravity.RIGHT;hero.addView(mark,icon);
        gap(hero,18);hero.addView(text("خوش آمدید، معلم عزیز",23,WHITE,true));gap(hero,8);
        hero.addView(text("مدرسه‌یار  |  همراه هوشمند روزهای تدریس",14,0xffedf7ff,false));
        body.addView(hero,lp(-1,-2));gap(body,28);
        body.addView(text("ورود به فضای اختصاصی معلم",21,NAVY,true));gap(body,7);body.addView(text("سریع، امن و طراحی‌شده مخصوص گوشی",13,MUTED,false));gap(body,20);
        EditText id=field("کد ملی / نام کاربری",false);body.addView(id,lp(-1,54));gap(body,13);
        EditText pass=field("رمز عبور",true);body.addView(pass,lp(-1,54));gap(body,18);
        Button enter=primary("ورود به اپلیکیشن");body.addView(enter,lp(-1,56));gap(body,16);
        TextView reminder=text("با حساب معلم مدرسه‌یار وارد شوید. رمز عبور فقط به سرور اصلی ارسال می‌شود.",12,MUTED,false);body.addView(reminder);
        enter.setOnClickListener(v->{
            String username=id.getText().toString().trim(),password=pass.getText().toString();
            if(username.isEmpty()||password.isEmpty()){toast("نام کاربری و رمز را وارد کنید.");return;}
            enter.setEnabled(false);enter.setText("در حال ورود امن…");
            background(() -> {JSONObject data=new JSONObject();data.put("identifier",username);data.put("password",password);return SchoolApi.request("/api/staff/login","POST",null,data);}, result -> {
                enter.setEnabled(true);enter.setText("ورود به اپلیکیشن");
                JSONObject user=result.optJSONObject("user");if(!"teacher".equals(s(user,"role"))){fail("فقط حساب معلم در این اپ مجاز است.");return;}
                String newToken=s(result,"token");if(newToken.isEmpty()){fail("توکن ورود دریافت نشد.");return;}
                token=newToken;me=user;
                if(result.optBoolean("mustChangePassword",false) || user.optBoolean("mustChangePassword",false))showChangePassword(true);else fetchData();
            });
        });
        if(vault.isEnabled()){
            gap(body,16);Button bio=outline("ورود با اثر انگشت 👆");body.addView(bio,lp(-1,50));bio.setOnClickListener(v->unlockSavedSession());
        }
        gap(body,32);TextView footer=text("نسخه آزمایشی ۰٫۷ · کارنامه تفصیلی بومی",11,MUTED,false);footer.setGravity(Gravity.CENTER);body.addView(footer);
    }
    private void render() {
        if(!unlocked){renderLogin();return;}
        shell();switch(section){case "خانه":renderHome();break;case "کلاس‌ها":renderClasses();break;case "حضور":renderAttendance();break;case "نمرات":renderGrades();break;default:renderMore();break;}
    }
    private void sectionHead(String title,String subtitle) {
        TextView t=text(title,22,NAVY,true);content.addView(t);gap(content,5);content.addView(text(subtitle,12,MUTED,false));gap(content,18);
    }
    private LinearLayout panelTitle(String icon,String title,String subtitle) {
        LinearLayout row=row();TextView emblem=text(icon,23,BLUE,true);emblem.setGravity(Gravity.CENTER);emblem.setBackground(rounded(0xffebf3ff,15));row.addView(emblem,lp(48,48));
        LinearLayout words=column();pad(words,13,0,12,0);words.addView(text(title,16,INK,true));gap(words,4);words.addView(text(subtitle,12,MUTED,false));row.addView(words,new LinearLayout.LayoutParams(0,-2,1f));return row;
    }
    private void empty(String title,String detail) {
        LinearLayout box=card(22);box.addView(text(title,17,INK,true));gap(box,8);box.addView(text(detail,13,MUTED,false));addCard(content,box);
    }
    private void quickAction(LinearLayout wrap,String title,String caption,String glyph,String target) {
        LinearLayout c=card(13);c.addView(text(glyph,24,BLUE,true));gap(c,11);c.addView(text(title,14,NAVY,true));gap(c,5);c.addView(text(caption,11,MUTED,false));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(148),1f);p.setMargins(dp(4),dp(4),dp(4),dp(4));wrap.addView(c,p);c.setOnClickListener(v->openTab(target));
    }
    private void renderHome() {
        LinearLayout hero=column();GradientDrawable gd=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{NAVY,BLUE,0xff5cbbf1});gd.setCornerRadius(dp(24));hero.setBackground(gd);pad(hero,20,22,20,22);
        hero.addView(text("روز بخیر، "+fullName(me),19,WHITE,true));gap(hero,8);
        hero.addView(text("امروز هم یک فرصت تازه برای یادگیری است ✨",12,0xffe7f4ff,false));gap(hero,14);
        JSONObject clock=dashboard.optJSONObject("clock");hero.addView(text(s(clock,"dayName")+"  ·  "+s(clock,"dateFa"),12,0xffe6f4ff,false));addCard(content,hero);gap(content,8);
        sectionHead("دسترسی سریع","ابزارهای روزانه معلم، بدون نمایش سایت");
        LinearLayout a=row();quickAction(a,"کلاس‌های من","درس‌ها و دانش‌آموزان","▦","کلاس‌ها");quickAction(a,"حضور و غیاب","کلاس‌های امروز","✓","حضور");content.addView(a);
        LinearLayout b=row();quickAction(b,"دفتر نمره","ثبت و مشاهده نمرات","★","نمرات");quickAction(b,"ابزارهای بیشتر","برنامه، آزمون، حساب","⋯","بیشتر");content.addView(b);gap(content,18);
        JSONArray today=arr(dashboard,"todaySchedule");LinearLayout box=card(17);box.addView(panelTitle("◷","برنامه امروز",today.length()+" زنگ در برنامه ثبت شده"));gap(box,15);
        if(today.length()==0)box.addView(text("برای امروز کلاسی در برنامه تعریف نشده است.",13,MUTED,false));
        for(int i=0;i<today.length();i++){
            JSONObject entry=obj(today,i);LinearLayout line=row();TextView start=text(s(entry,"startTime")+" – "+s(entry,"endTime"),11,BLUE,true);
            line.addView(text(s(entry,"subject")+"  ·  "+s(entry,"className"),13,INK,true),new LinearLayout.LayoutParams(0,-2,1f));line.addView(start);
            box.addView(line);gap(box,12);
        }
        addCard(content,box);
    }
    private void renderClasses() {
        sectionHead("کلاس‌های من","تخصیص‌های واقعی دریافت‌شده از سرور مدرسه");
        JSONArray assignments=arr(workspace,"assignments");if(assignments.length()==0){empty("کلاسی تعریف نشده","از مدیر مدرسه بخواه تخصیص تدریس را بررسی کند.");return;}
        for(int i=0;i<assignments.length();i++){
            JSONObject a=obj(assignments,i);String classId=s(a,"classId"),subject=s(a,"subject");
            LinearLayout c=card(18);c.addView(panelTitle("▦",nameOfClass(classId),subject));gap(c,14);
            c.addView(text(studentCount(classId)+" دانش‌آموز  ·  پایه "+gradeOfClass(classId),12,MUTED,false));gap(c,13);
            Button open=outline("نمایش دانش‌آموزان و جزئیات ←");c.addView(open,lp(-1,48));open.setOnClickListener(v->classDetail(classId,subject));addCard(content,c);
        }
    }
    private String gradeOfClass(String id) {
        JSONArray classes=arr(workspace,"classes");for(int i=0;i<classes.length();i++){JSONObject c=obj(classes,i);if(id.equals(s(c,"id")))return s(c,"grade");}return "—";
    }
    private void classDetail(String classId,String subject) {
        section="کلاس‌ها";shell();sectionHead(nameOfClass(classId),"درس "+subject+" · فهرست دانش‌آموزان");
        Button back=outline("بازگشت به کلاس‌ها");content.addView(back,lp(-1,48));back.setOnClickListener(v->openTab("کلاس‌ها"));gap(content,14);
        ArrayList<JSONObject> students=studentsOf(classId);
        if(students.isEmpty()){empty("هنوز دانش‌آموزی ثبت نشده","فهرست فقط از اطلاعات مجاز معلم تهیه می‌شود.");return;}
        for(JSONObject st:students){LinearLayout c=card(15);c.addView(panelTitle("◯",fullName(st),"کد دانش‌آموزی: "+s(st,"studentCode")));addCard(content,c);}
    }
    private void renderAttendance() {
        sectionHead("حضور و غیاب","ثبت فقط در زمان مجاز همان کلاس انجام می‌شود");
        JSONArray rows=arr(dashboard,"todaySchedule");if(rows.length()==0){empty("امروز کلاس برنامه‌ریزی‌شده ندارید","اطلاعات از برنامه هفتگی سرور گرفته می‌شود.");return;}
        for(int i=0;i<rows.length();i++){
            JSONObject item=obj(rows,i);LinearLayout c=card(17);
            c.addView(panelTitle("✓",s(item,"className")+" · "+s(item,"subject"),s(item,"startTime")+" تا "+s(item,"endTime")));gap(c,11);
            JSONObject att=item.optJSONObject("attendance");JSONObject stats=att==null?null:att.optJSONObject("summary");
            c.addView(text(item.optBoolean("attendanceMarked",false)?"قبلاً ثبت شده  ·  حاضر: "+(stats==null?"۰":stats.optInt("present",0)):"هنوز حضور ثبت نشده است",12,MUTED,false));gap(c,14);
            Button act=outline(item.optBoolean("attendanceOpen",false)?"انتخاب دانش‌آموزان حاضر":"مشاهده وضعیت کلاس");c.addView(act,lp(-1,48));
            act.setOnClickListener(v->attendanceDetail(item));addCard(content,c);
        }
    }
    private void attendanceDetail(JSONObject entry) {
        section="حضور";shell();sectionHead(s(entry,"className"),"حضور و غیاب · "+s(entry,"subject"));
        Button back=outline("بازگشت به حضور امروز");content.addView(back,lp(-1,48));back.setOnClickListener(v->openTab("حضور"));gap(content,12);
        boolean canSubmit=entry.optBoolean("attendanceOpen",false);
        LinearLayout info=card(15);info.addView(text(canSubmit?"بازه ثبت حضور هم‌اکنون باز است":"ثبت حضور در این ساعت مجاز نیست",15,canSubmit?0xff147d66:0xffa36b27,true));
        String blocked=s(entry,"blockedReason");if(!blocked.isEmpty()){gap(info,5);info.addView(text(blocked.matches("(?s).*[\u0600-\u06FF].*") ? blocked : "زمان ثبت حضور این کلاس هنوز فعال نیست.",12,MUTED,false));}
        addCard(content,info);
        String classId=s(entry,"classId");ArrayList<JSONObject> students=studentsOf(classId);
        if(students.isEmpty()){empty("فهرست خالی است","دانش‌آموزی در کلاس انتخاب‌شده یافت نشد.");return;}
        JSONObject attendance=entry.optJSONObject("attendance");JSONArray records=arr(attendance,"records");
        Set<String> alreadyPresent=new HashSet<>();for(int i=0;i<records.length();i++){JSONObject r=obj(records,i);if("present".equals(s(r,"status")))alreadyPresent.add(s(r,"studentId"));}
        present.clear();boolean wasRecorded=entry.optBoolean("attendanceMarked",false);
        for(JSONObject student:students){String id=s(student,"id");present.put(id,wasRecorded&&alreadyPresent.contains(id));
            LinearLayout row=card(12);CheckBox check=new CheckBox(this);check.setText(digitsFa(fullName(student)));check.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);check.setTextSize(15);check.setTextColor(INK);check.setTextDirection(View.TEXT_DIRECTION_RTL);
            check.setChecked(present.get(id));check.setEnabled(canSubmit);check.setOnCheckedChangeListener((b,v)->present.put(id,v));row.addView(check,lp(-1,-2));addCard(content,row);
        }
        if(canSubmit){
            TextView note=text("فقط دانش‌آموزان تیک‌خورده «حاضر» ثبت می‌شوند. افراد بدون تیک طبق قوانین سرور به‌عنوان غیبت در انتظار بررسی ثبت خواهند شد.",12,MUTED,false);content.addView(note);gap(content,14);
            Button save=primary("ثبت حضور انتخاب‌شده‌ها");content.addView(save,lp(-1,54));
            save.setOnClickListener(v->{
                int count=0;for(Boolean chosen:present.values())if(chosen)count++;
                final int selected=count;
                new AlertDialog.Builder(this).setTitle("تأیید ثبت حضور")
                    .setMessage("حاضر: "+selected+" نفر از "+students.size()+" نفر. آیا ثبت روی سرور انجام شود؟")
                    .setNegativeButton("انصراف",null).setPositiveButton("ثبت نهایی",(d,which)->{
                        background(() -> {
                            JSONArray ids=new JSONArray();for(Map.Entry<String,Boolean> v1:present.entrySet())if(Boolean.TRUE.equals(v1.getValue()))ids.put(v1.getKey());
                            JSONObject payload=new JSONObject();payload.put("scheduleEntryId",s(entry,"id"));payload.put("presentStudentIds",ids);
                            return SchoolApi.request("/api/teacher/attendance/submit","POST",token,payload);
                        }, result -> {toast("حضور با موفقیت ثبت شد.");fetchData();});
                    }).show();
            });
        }
    }
    /** A06: native gradebook UI, sourced directly from the scoped staff API. */
    private void renderGrades() {
        sectionHead("دفتر نمره", "ثبت و مشاهده نمرات کلاس با مجوز مدرسه");
        LinearLayout intro=card(15);
        intro.addView(text("نمرات با همان قوانین دفتر نمره وب ثبت می‌شوند؛ بارم و قفل کارنامه را سرور کنترل می‌کند.",13,INK,false));
        addCard(content,intro);
        JSONArray assignments=arr(workspace,"assignments");
        if(assignments.length()==0)empty("درس تخصیص داده نشده", "ابتدا از مدیر بخواهید درس و کلاس را به حساب شما متصل کند.");
        for(int i=0;i<assignments.length();i++){
            JSONObject a=obj(assignments,i);String classId=s(a,"classId"),subject=s(a,"subject");
            if(classId.isEmpty()||subject.isEmpty())continue;
            LinearLayout box=card(17);box.addView(panelTitle("★",nameOfClass(classId),subject));gap(box,12);
            box.addView(text("دانش‌آموزان: "+studentCount(classId)+" · سال: "+s(workspace.optJSONObject("school"),"year"),12,MUTED,false));gap(box,11);
            Button first=outline("دفتر نمره نیم‌سال اول");box.addView(first,lp(-1,46));gap(box,7);
            Button second=outline("دفتر نمره نیم‌سال دوم");box.addView(second,lp(-1,46));
            first.setOnClickListener(v->loadGradebook(classId,subject,"semester1"));
            second.setOnClickListener(v->loadGradebook(classId,subject,"semester2"));
            addCard(content,box);
        }
        Button examList=outline("مشاهده نتایج آزمون‌ها");content.addView(examList,lp(-1,50));
        examList.setOnClickListener(v->{section="نمرات";shell();renderExamList();});
    }
    private String queryParam(String value) {
        try { return URLEncoder.encode(value,"UTF-8"); }
        catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException("UTF-8",e);}
    }
    private String gradebookQuery(String classId,String subject,String period) {
        return "?classId="+queryParam(classId)+"&subject="+queryParam(subject)+"&academicPeriod="+queryParam(period);
    }
    private String periodLabel(String period) {return "semester1".equals(period)?"نیم‌سال اول":"نیم‌سال دوم";}
    private void loadGradebook(String classId,String subject,String period) {
        section="نمرات";shell();sectionHead("دفتر نمره · "+periodLabel(period),nameOfClass(classId)+" · "+subject);
        content.addView(text("در حال دریافت دفتر نمره از سرور…",14,MUTED,false));
        background(() -> SchoolApi.request("/api/staff/gradebook"+gradebookQuery(classId,subject,period),"GET",token,null),
            result->{if(unlocked && "نمرات".equals(section))showGradebook(classId,subject,period,result);});
    }
    private void showGradebook(String classId,String subject,String period,JSONObject data) {
        section="نمرات";shell();sectionHead("دفتر نمره · "+periodLabel(period),nameOfClass(classId)+" · "+subject);
        Button back=outline("بازگشت به درس‌ها");content.addView(back,lp(-1,46));back.setOnClickListener(v->openTab("نمرات"));gap(content,12);
        JSONObject config=data.optJSONObject("config");if(config==null)config=new JSONObject();
        final JSONObject cfg=config;
        boolean configured=config.optBoolean("enabled",false);
        JSONObject lock=data.optJSONObject("lock");boolean locked=lock!=null&&lock.optBoolean("active",true);
        double continuousMax=config.optDouble("outputMaxScore",Double.NaN);
        if(!Double.isFinite(continuousMax) || continuousMax<0 || continuousMax>20)continuousMax=Double.NaN;
        final double continuousCeiling=continuousMax;
        final boolean writable=configured && !locked && Double.isFinite(continuousCeiling);
        LinearLayout info=card(16);info.addView(panelTitle("▤","وضعیت دفتر نمره",periodLabel(period)));gap(info,12);
        info.addView(text("سقف مستمر: "+(Double.isFinite(continuousCeiling)?GradebookRules.formatScore(continuousCeiling):"نامشخص")+" از ۲۰",14,INK,true));gap(info,6);
        info.addView(text("سقف پایانی: "+(Double.isFinite(continuousCeiling)?GradebookRules.formatScore(GradebookRules.finalCeiling(continuousCeiling)):"نامشخص")+" از ۲۰",14,INK,true));gap(info,9);
        if(locked)info.addView(text("نمرات این درس قفل شده‌اند؛ ثبت یا ویرایش مجاز نیست.",13,0xffb34d36,true));
        else if(!configured)info.addView(text("تنظیم بارم این دفتر توسط مدیریت فعال نشده است؛ برای جلوگیری از ثبت بی‌اثر، فعلاً فقط مشاهده امکان‌پذیر است.",12,0xffae712b,true));
        else info.addView(text("دفتر فعال است. ثبت نمره با تأیید شما انجام می‌شود.",12,0xff197963,false));
        addCard(content,info);
        final JSONArray students=arr(data,"students"),categories=arr(cfg,"categories"),entries=arr(data,"entries");
        if(students.length()==0){empty("دانش‌آموزی پیدا نشد","فهرست دانش‌آموزان برای این کلاس خالی است.");return;}
        for(int i=0;i<students.length();i++){
            JSONObject student=obj(students,i);
            JSONObject result=student.optJSONObject("result");
            Object score=result==null?null:result.opt("score");
            String scoreLabel=(score==null||score==JSONObject.NULL)?"بدون میانگین":"میانگین "+digitsFa(String.valueOf(score))+" از ۲۰";
            LinearLayout c=card(15);c.addView(panelTitle("◯",fullName(student),scoreLabel));gap(c,10);
            Button details=outline("مشاهده سوابق و مدیریت نمره");c.addView(details,lp(-1,43));
            details.setOnClickListener(v->showStudentGradebook(classId,subject,period,student,categories,entries,writable,continuousCeiling,data));
            addCard(content,c);
        }
    }
    private void showStudentGradebook(String classId,String subject,String period,JSONObject student,
            JSONArray categories,JSONArray entries,boolean writable,double continuousCeiling,JSONObject snapshot) {
        section="نمرات";shell();sectionHead(fullName(student),nameOfClass(classId)+" · "+subject+" · "+periodLabel(period));
        Button back=outline("بازگشت به فهرست دفتر نمره");content.addView(back,lp(-1,47));
        back.setOnClickListener(v->showGradebook(classId,subject,period,snapshot));gap(content,12);
        JSONObject result=student.optJSONObject("result");
        Object average=result==null?null:result.opt("score");
        LinearLayout summary=card(15);
        summary.addView(text("میانگین مستمر محاسباتی: "+((average==null||average==JSONObject.NULL)?"ثبت نشده":digitsFa(String.valueOf(average))+" از ۲۰"),15,INK,true));
        gap(summary,10);summary.addView(text("سقف مستمر: "+GradebookRules.formatScore(continuousCeiling)+" · سقف پایانی: "+GradebookRules.formatScore(GradebookRules.finalCeiling(continuousCeiling)),12,MUTED,false));
        addCard(content,summary);
        LinearLayout history=card(15);history.addView(text("فعالیت‌های ثبت‌شده",15,NAVY,true));gap(history,12);
        int count=0;
        for(int j=0;j<entries.length();j++){
            JSONObject entry=obj(entries,j);if(!s(student,"id").equals(s(entry,"studentId")))continue;
            count++;
            String label=s(entry,"title")+" · "+GradebookRules.formatScore(entry.optDouble("score",0))+" از "+GradebookRules.formatScore(entry.optDouble("maxScore",20));
            TextView line=text("• "+label,13,INK,false);history.addView(line);gap(history,10);
            if(writable){JSONObject cat=findCategory(categories,s(entry,"categoryId"));
                if(cat!=null && !"exam".equals(s(cat,"source"))){line.setTextColor(BLUE);
                    line.setOnClickListener(v->showEntryEditor(classId,subject,period,student,cat,entry,continuousCeiling));}
            }
        }
        if(count==0)history.addView(text("برای این دانش‌آموز فعالیت دستی ثبت نشده است.",12,MUTED,false));
        addCard(content,history);
        if(writable){
            Button manual=primary("افزودن فعالیت کلاسی");content.addView(manual,lp(-1,52));gap(content,9);
            manual.setOnClickListener(v->chooseManualCategory(classId,subject,period,student,categories,continuousCeiling));
            Button approved=outline("تأیید نمره مستمر");content.addView(approved,lp(-1,48));gap(content,8);
            approved.setOnClickListener(v->showApproveContinuous(classId,subject,period,student,continuousCeiling));
            Button finalScore=outline("ثبت / مشاهده نمره پایانی");content.addView(finalScore,lp(-1,48));
            finalScore.setOnClickListener(v->loadFinalMark(classId,subject,period,student,continuousCeiling,true));
        }else{
            Button finalRead=outline("مشاهده نمره پایانی (فقط خواندنی)");content.addView(finalRead,lp(-1,48));
            finalRead.setOnClickListener(v->loadFinalMark(classId,subject,period,student,continuousCeiling,false));
        }
    }

    private JSONObject findCategory(JSONArray categories,String id){
        for(int i=0;i<categories.length();i++){JSONObject c=obj(categories,i);if(id.equals(s(c,"id")))return c;}return null;
    }
    private void chooseManualCategory(String classId,String subject,String period,JSONObject student,JSONArray categories,double ceiling) {
        ArrayList<JSONObject> manual=new ArrayList<>();
        for(int i=0;i<categories.length();i++){
            JSONObject c=obj(categories,i);if("manual".equals(s(c,"source")))manual.add(c);
        }
        if(manual.isEmpty()){fail("مولفه دستی در تنظیمات دفتر نمره تعریف نشده است.");return;}
        String[] labels=new String[manual.size()];for(int i=0;i<manual.size();i++)labels[i]=s(manual.get(i),"title");
        new AlertDialog.Builder(this).setTitle("انتخاب نوع فعالیت").setItems(labels,(dialog,index)->
            showEntryEditor(classId,subject,period,student,manual.get(index),null,ceiling)).setNegativeButton("انصراف",null).show();
    }
    private EditText numericField(String hint,String initial) {
        EditText input=field(hint,false);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);input.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        if(initial!=null)input.setText(digitsFa(initial));return input;
    }
    private void showEntryEditor(String classId,String subject,String period,JSONObject student,JSONObject category,JSONObject existing,double ceiling) {
        LinearLayout fields=column();pad(fields,17,7,17,7);
        fields.addView(text(fullName(student)+" · "+s(category,"title"),14,INK,true));gap(fields,10);
        EditText title=field("عنوان فعالیت",false);title.setText(existing==null?s(category,"title"):s(existing,"title"));fields.addView(title,lp(-1,54));gap(fields,9);
        EditText score=numericField("نمره کسب‌شده",existing==null?"":s(existing,"score"));fields.addView(score,lp(-1,54));gap(fields,9);
        EditText max=numericField("بارم فعالیت",existing==null?"۲۰":s(existing,"maxScore"));fields.addView(max,lp(-1,54));gap(fields,9);
        EditText note=field("توضیح اختیاری",false);note.setSingleLine(false);note.setMinLines(2);note.setMaxLines(3);note.setText(existing==null?"":s(existing,"note"));fields.addView(note,lp(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.addView(fields);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"ثبت فعالیت کلاسی":"ویرایش فعالیت کلاسی")
            .setView(scroll).setNegativeButton("انصراف",null).setPositiveButton("بررسی و ثبت",null).create();
        dialog.setOnShowListener(unused->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            Double value=GradebookRules.readScore(score.getText().toString()),maxValue=GradebookRules.readScore(max.getText().toString());
            String t=title.getText().toString().trim();
            if(t.isEmpty()||t.length()>120||maxValue==null||value==null||!GradebookRules.within(value,maxValue)){
                toast("عنوان، نمره و بارم را بررسی کنید. نمره نباید از بارم بیشتر باشد.");return;
            }
            String n=note.getText().toString().trim();
            new AlertDialog.Builder(this).setTitle("تأیید ثبت نمره")
                .setMessage(digitsFa(fullName(student)+"\n"+t+"\n"+GradebookRules.formatScore(value)+" از "+GradebookRules.formatScore(maxValue)+"\nآیا در دفتر نمره ثبت شود؟"))
                .setNegativeButton("انصراف",null).setPositiveButton("ثبت در سرور",(d,w)->{
                    background(() -> {
                        JSONObject payload=new JSONObject();payload.put("studentId",s(student,"id"));payload.put("classId",classId);
                        payload.put("subject",subject);payload.put("categoryId",s(category,"id"));payload.put("academicPeriod",period);
                        payload.put("title",t);payload.put("score",value);payload.put("maxScore",maxValue);payload.put("note",n);
                        if(existing!=null)payload.put("id",s(existing,"id"));
                        return SchoolApi.request("/api/staff/gradebook/entry","POST",token,payload);
                    },response->{dialog.dismiss();toast("فعالیت ثبت شد.");loadGradebook(classId,subject,period);});
                }).show();
        }));dialog.show();
    }
    private void showApproveContinuous(String classId,String subject,String period,JSONObject student,double ceiling) {
        LinearLayout fields=column();pad(fields,18,9,18,9);
        fields.addView(text("سقف مستمر: "+GradebookRules.formatScore(ceiling),13,INK,true));gap(fields,12);
        EditText value=numericField("نمره مستمر مورد تأیید","");fields.addView(value,lp(-1,55));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("تأیید مستمر · "+fullName(student))
            .setView(fields).setNegativeButton("انصراف",null).setPositiveButton("بررسی",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            Double score=GradebookRules.readScore(value.getText().toString());
            if(score==null||!GradebookRules.within(score,ceiling)){toast("نمره باید بین صفر و سقف مستمر باشد.");return;}
            new AlertDialog.Builder(this).setTitle("تأیید نهایی معلم")
                .setMessage(digitsFa("نمره مستمر "+fullName(student)+" برابر "+GradebookRules.formatScore(score)+" از "+GradebookRules.formatScore(ceiling)+" ثبت شود؟"))
                .setNegativeButton("انصراف",null).setPositiveButton("ثبت نهایی",(d,w)->{
                    background(() -> {JSONObject body=new JSONObject();body.put("studentId",s(student,"id"));body.put("classId",classId);body.put("subject",subject);body.put("academicPeriod",period);body.put("score",score);
                        return SchoolApi.request("/api/staff/gradebook/approve-continuous","POST",token,body);
                    },response->{dialog.dismiss();toast("مستمر تأیید شد.");loadGradebook(classId,subject,period);});
                }).show();
        }));dialog.show();
    }
    private void loadFinalMark(String classId,String subject,String period,JSONObject student,double continuousMax,boolean editable) {
        String path="/api/staff/gradebook/final-mark"+gradebookQuery(classId,subject,period)+"&studentId="+queryParam(s(student,"id"));
        background(() -> SchoolApi.request(path,"GET",token,null),result->{
            JSONObject current=result.optJSONObject("finalMark");
            showFinalMarkDialog(classId,subject,period,student,continuousMax,editable,current);
        });
    }
    private void showFinalMarkDialog(String classId,String subject,String period,JSONObject student,double continuousMax,boolean editable,JSONObject current) {
        double max=GradebookRules.finalCeiling(continuousMax);
        LinearLayout fields=column();pad(fields,18,9,18,9);
        fields.addView(text("سقف پایانی: "+(Double.isFinite(max)?GradebookRules.formatScore(max):"نامشخص")+" از ۲۰",13,INK,true));gap(fields,11);
        String prior=current==null?"":s(current,"score");
        if(!editable){fields.addView(text("نمره ثبت‌شده: "+(prior.isEmpty()?"ثبت نشده":prior),15,INK,true));
            new AlertDialog.Builder(this).setTitle("پایانی · "+fullName(student)).setView(fields).setPositiveButton("بستن",null).show();return;}
        EditText score=numericField("نمره پایانی",prior);fields.addView(score,lp(-1,54));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("پایانی · "+fullName(student))
            .setView(fields).setNegativeButton("انصراف",null).setPositiveButton("بررسی",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
            Double value=GradebookRules.readScore(score.getText().toString());
            if(value==null||!GradebookRules.within(value,max)){toast("نمره پایانی نمی‌تواند از سقف تعیین‌شده بیشتر باشد.");return;}
            new AlertDialog.Builder(this).setTitle("تأیید ثبت پایانی")
                .setMessage(digitsFa("نمره پایانی "+fullName(student)+" برابر "+GradebookRules.formatScore(value)+" از "+GradebookRules.formatScore(max)+" ثبت شود؟"))
                .setNegativeButton("انصراف",null).setPositiveButton("ثبت در سرور",(d,w)->{
                    background(() -> {JSONObject body=new JSONObject();body.put("studentId",s(student,"id"));body.put("classId",classId);body.put("subject",subject);body.put("academicPeriod",period);body.put("score",value);
                        return SchoolApi.request("/api/staff/gradebook/final-mark","POST",token,body);
                    },response->{dialog.dismiss();toast("پایانی ثبت شد.");loadGradebook(classId,subject,period);});
                }).show();
        }));dialog.show();
    }

    private void renderExamList() {
        sectionHead("نتایج آزمون‌ها","گزارش نمرات آزمون‌های مجاز");
        JSONArray exams=arr(workspace,"exams"),attempts=arr(workspace,"attempts");
        if(exams.length()==0){empty("آزمونی در دسترس نیست","آزمون‌های مربوط به دسترسی تدریس شما اینجا نمایش داده می‌شوند.");return;}
        for(int i=0;i<exams.length();i++){
            JSONObject exam=obj(exams,i);String eid=s(exam,"id");int n=0;
            for(int j=0;j<attempts.length();j++)if(eid.equals(s(obj(attempts,j),"examId")))n++;
            LinearLayout c=card(16);c.addView(panelTitle("★",s(exam,"title"),s(exam,"subject")+" · "+nameOfClass(s(exam,"classId"))));gap(c,11);
            c.addView(text("پاسخ‌های ثبت‌شده: "+n+" · وضعیت: "+localizedStatus(s(exam,"status")),12,MUTED,false));gap(c,11);
            Button show=outline("نمایش نتایج ثبت‌شده");c.addView(show,lp(-1,48));show.setOnClickListener(v->showExamResults(exam));addCard(content,c);
        }
    }
    /** Real exam results. Every question detail is fetched using the existing teacher-only API.
     * Never guess correctness from the slim workspace list and never call student-only APIs.
     */
    private void showExamResults(JSONObject exam) {
        section="نمرات";shell();sectionHead(s(exam,"title"),"نتایج آزمون و کارنامه تفصیلی هر دانش‌آموز");
        Button back=outline("بازگشت به نمرات");content.addView(back,lp(-1,48));back.setOnClickListener(v->openTab("نمرات"));gap(content,14);
        String eid=s(exam,"id");
        JSONArray attempts=arr(workspace,"attempts"),submissions=arr(workspace,"submissions"),grades=arr(workspace,"grades");
        HashSet<String> seen=new HashSet<>();int rows=0;
        for(int i=0;i<attempts.length();i++){
            JSONObject at=obj(attempts,i);if(!eid.equals(s(at,"examId")))continue;
            String sid=s(at,"studentId");if(sid.isEmpty()||!seen.add(sid))continue;
            String name=examStudentName(sid);String points=s(at,"score"),max=s(at,"total");
            String scoreText=points.isEmpty()||max.isEmpty()?"نتیجه ثبت‌شده":"نمره ثبت‌شده: "+digitsFa(points)+" از "+digitsFa(max);
            addExamStudentResult(exam,sid,name,scoreText);rows++;
        }
        // Essay/mixed submissions are listed only when a grade record exists;
        // the detail API independently validates that the grade is published.
        for(int i=0;i<submissions.length();i++){
            JSONObject sub=obj(submissions,i);if(!eid.equals(s(sub,"examId")))continue;
            String sid=s(sub,"studentId");if(sid.isEmpty()||seen.contains(sid))continue;
            boolean graded=false;
            for(int j=0;j<grades.length();j++){
                if(s(sub,"id").equals(s(obj(grades,j),"submissionId"))){graded=true;break;}
            }
            if(!graded)continue;
            seen.add(sid);
            addExamStudentResult(exam,sid,examStudentName(sid),"بررسی نتیجه نهایی در سرور");rows++;
        }
        if(rows==0)empty("هنوز نتیجه نهایی موجود نیست","پس از ثبت یا تصحیح آزمون، نام دانش‌آموزان اینجا دیده می‌شود.");
    }
    private String examStudentName(String studentId) {
        JSONArray students=arr(workspace,"students");
        for(int i=0;i<students.length();i++){
            JSONObject student=obj(students,i);
            if(studentId.equals(s(student,"id"))){
                String name=fullName(student);return name.isEmpty()?"دانش‌آموز":name;
            }
        }
        return "دانش‌آموز";
    }
    private void addExamStudentResult(JSONObject exam,String sid,String name,String hint) {
        LinearLayout card=card(16);
        card.addView(panelTitle("◉",name,hint));gap(card,13);
        Button details=outline("مشاهده کارنامه تفصیلی و پاسخ سؤال‌ها");
        card.addView(details,lp(-1,48));
        details.setOnClickListener(v->fetchStudentExamDetail(exam,sid,name));
        addCard(content,card);
    }
    private void fetchStudentExamDetail(JSONObject exam,String studentId,String studentName) {
        if(studentId.isEmpty()||s(exam,"id").isEmpty()){toast("شناسه آزمون یا دانش‌آموز معتبر نیست.");return;}
        section="نمرات";shell();sectionHead("کارنامه تفصیلی",studentName+" · "+s(exam,"title"));
        content.addView(text("در حال دریافت پاسخ‌های نهایی از سرور مدرسه…",14,MUTED,false));
        background(() -> {
            String path="/api/teacher/continuous-p11?studentId="+URLEncoder.encode(studentId,"UTF-8")
                +"&examId="+URLEncoder.encode(s(exam,"id"),"UTF-8");
            return SchoolApi.request(path,"GET",token,null);
        }, data -> {
            JSONObject serverExam=data.optJSONObject("exam");
            if(serverExam==null||!s(exam,"id").equals(s(serverExam,"examId"))){
                fail("گزارش معتبر آزمون از سرور دریافت نشد.");showExamResults(exam);return;
            }
            showStudentExamDetail(exam,studentName,data);
        });
    }
    private boolean examQuestionAnswered(JSONObject question) {
        Object selected=question.opt("selectedIndex");
        if(selected!=null && selected!=JSONObject.NULL)return true;
        String answer=s(question,"studentAnswer").trim();
        if(!answer.isEmpty()&&!"بدون پاسخ".equals(answer)&&!"پاسخی ثبت نشده است.".equals(answer))return true;
        String status=s(question,"status").trim().toLowerCase(Locale.ROOT);
        return "correct".equals(status)||"partial".equals(status);
    }
    private LinearLayout examStat(String caption,int value,int color) {
        LinearLayout c=card(12);c.addView(text(String.valueOf(value),23,color,true));gap(c,5);
        c.addView(text(caption,11,MUTED,false));return c;
    }
    private void examStatRow(String a,int aValue,int aColor,String b,int bValue,int bColor){
        LinearLayout row=row();LinearLayout left=examStat(a,aValue,aColor),right=examStat(b,bValue,bColor);
        LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,dp(86),1f);l.setMargins(dp(3),0,dp(3),0);
        row.addView(left,l);LinearLayout.LayoutParams r=new LinearLayout.LayoutParams(0,dp(86),1f);r.setMargins(dp(3),0,dp(3),0);
        row.addView(right,r);content.addView(row);gap(content,9);
    }
    private void showStudentExamDetail(JSONObject selectedExam,String name,JSONObject payload) {
        section="نمرات";shell();
        JSONObject serverExam=payload.optJSONObject("exam");JSONArray questions=arr(payload,"questions");
        sectionHead("کارنامه تفصیلی آزمون",name+" · "+s(serverExam,"title"));
        Button back=outline("بازگشت به فهرست دانش‌آموزان آزمون");content.addView(back,lp(-1,48));
        back.setOnClickListener(v->showExamResults(selectedExam));gap(content,12);
        ArrayList<ExamDetailRules.Outcome> outcomes=new ArrayList<>();
        for(int i=0;i<questions.length();i++){
            JSONObject q=obj(questions,i);
            outcomes.add(ExamDetailRules.classify(s(q,"type"),s(q,"status"),examQuestionAnswered(q),
                q.optDouble("earnedScore",Double.NaN),q.optDouble("maxScore",Double.NaN)));
        }
        ExamDetailRules.Stats stats=ExamDetailRules.summarize(outcomes);
        LinearLayout hero=card(18);hero.addView(text("نمره نهایی ثبت‌شده",13,MUTED,false));gap(hero,5);
        hero.addView(text(digitsFa(s(serverExam,"score"))+" از "+digitsFa(s(serverExam,"total")),23,BLUE,true));gap(hero,10);
        hero.addView(text("تعداد کل سؤال‌ها: "+stats.total+" · وضعیت: نتیجه نهایی",12,INK,false));
        addCard(content,hero);
        examStatRow("پاسخ داده",stats.answered,BLUE,"بدون پاسخ",stats.unanswered,MUTED);
        examStatRow("درست",stats.correct,0xff12805d,"نادرست",stats.wrong,0xffc54747);
        if(stats.partial>0)examStatRow("نیمه‌صحیح",stats.partial,0xffb76f1c,"کل سؤال‌ها",stats.total,NAVY);
        if(questions.length()==0){empty("جزئیات سؤال‌ها موجود نیست","سرور برای این آزمون نتیجه نهایی دارد اما فهرست سؤال‌ها را برنگردانده است.");return;}
        sectionHead("بررسی سؤال‌به‌سؤال","پاسخ دانش‌آموز، پاسخ صحیح و امتیاز دریافتی");
        for(int i=0;i<questions.length();i++) {
            JSONObject q=obj(questions,i);
            appendExamQuestion(q,i+1,outcomes.get(i));
        }
        String overall=s(payload,"feedback");
        if(!overall.isEmpty()){
            LinearLayout note=card(15);note.addView(text("نظر دبیر",15,NAVY,true));gap(note,8);
            note.addView(text(overall,13,INK,false));addCard(content,note);
        }
        if(payload.optBoolean("hasSubmissionFiles",false)){
            LinearLayout notice=card(15);
            notice.addView(text("پاسخ تصویری در پرونده آزمون ثبت شده است.",13,NAVY,true));gap(notice,6);
            notice.addView(text("نمایش فایل اصلی به مجوز دانلود جداگانه نیاز دارد؛ اطلاعات این صفحه از گزارش تأییدشده سرور خوانده شده‌اند.",12,MUTED,false));
            addCard(content,notice);
        }
    }
    private void appendExamQuestion(JSONObject q,int number,ExamDetailRules.Outcome outcome) {
        LinearLayout c=card(15);int color;String label;
        switch(outcome){
            case CORRECT:color=0xff12805d;label="درست";break;
            case WRONG:color=0xffc54747;label="نادرست";break;
            case PARTIAL:color=0xffb76f1c;label="نیمه‌صحیح";break;
            default:color=MUTED;label="بی‌پاسخ";
        }
        String kind="mcq".equalsIgnoreCase(s(q,"type"))?"تستی":"تشریحی / ترکیبی";
        c.addView(text("سؤال "+number+" · "+kind+" · "+label,14,color,true));gap(c,8);
        String question=s(q,"text");c.addView(text(question.isEmpty()?"متن سؤال در گزارش سرور موجود نیست.":question,14,INK,true));gap(c,9);
        c.addView(text("امتیاز: "+digitsFa(s(q,"earnedScore"))+" از "+digitsFa(s(q,"maxScore")),12,BLUE,true));
        JSONArray choices=arr(q,"choices");
        Object selectedObj=q.opt("selectedIndex"),correctObj=q.opt("correctIndex");
        int chosen=selectedObj==null||selectedObj==JSONObject.NULL?-1:q.optInt("selectedIndex",-1);
        int correct=correctObj==null||correctObj==JSONObject.NULL?-1:q.optInt("correctIndex",-1);
        if(choices.length()>0){
            gap(c,10);
            for(int j=0;j<choices.length();j++){
                String markers=(j==correct?" ✓ پاسخ صحیح":"")+(j==chosen?" ← انتخاب دانش‌آموز":"");
                TextView option=text((j+1)+") "+choices.optString(j)+markers,12,j==correct?0xff12805d:j==chosen?BLUE:INK,false);
                option.setBackground(rounded(j==correct?0xffeffaf5:j==chosen?0xffedf3ff:BG,11));pad(option,10,9,10,9);
                c.addView(option,lp(-1,-2));gap(c,5);
            }
        }
        String studentAnswer=s(q,"studentAnswer");if(!studentAnswer.isEmpty()&&choices.length()==0){
            gap(c,9);c.addView(text("پاسخ دانش‌آموز: "+studentAnswer,12,INK,false));
        }
        String expected=s(q,"correctAnswer");if(expected.isEmpty())expected=s(q,"referenceAnswer");
        if(!expected.isEmpty()&&choices.length()==0){gap(c,7);c.addView(text("پاسخ معیار: "+expected,12,0xff12805d,false));}
        String feedback=s(q,"feedback");if(!feedback.isEmpty()){
            gap(c,8);c.addView(text("توضیح / بازخورد: "+feedback,12,MUTED,false));
        }
        JSONArray criteria=arr(q,"criteria");
        for(int j=0;j<criteria.length();j++){
            JSONObject row=obj(criteria,j);gap(c,6);
            c.addView(text("معیار: "+s(row,"title")+" · "+s(row,"score")+" از "+s(row,"maxScore"),11,MUTED,false));
        }
        addCard(content,c);
    }
    private void moreAction(String icon,String title,String subtitle,Runnable next) {
        LinearLayout c=card(13);c.addView(panelTitle(icon,title,subtitle));addCard(content,c);c.setOnClickListener(v->next.run());
    }
    private void renderMore() {
        sectionHead("امکانات بیشتر","ابزارهای مستقل و بومی مدرسه‌یار معلم");
        moreAction("▦","برنامه هفتگی","نمایش برنامه واقعی سرور",this::weekView);
        moreAction("◯","دانش‌آموزان من","فهرست دانش‌آموزان کلاس‌های تحت تدریس",this::allStudents);
        moreAction("★","دفتر نمره و آزمون‌ها","ثبت فعالیت، تأیید مستمر و پایانی",()->openTab("نمرات"));
        moreAction("✓","حضور و غیاب","ثبت و مرور بر اساس زمان مجاز",()->openTab("حضور"));
        moreAction("⌁","تغییر رمز عبور","تغییر رمز حساب از طریق سرور",()->showChangePassword(false));
        moreAction("◉","ورود با اثر انگشت",vault.isEnabled()?"فعال است؛ برای غیرفعال‌سازی لمس کنید":"بعد از ورود موفق قابل فعال‌سازی است",this::toggleBiometric);
        moreAction("⎋","خروج ایمن","پایان نشست جاری در سرور",this::logoutConfirm);
        LinearLayout notice=card(16);notice.addView(text("در نسخه بومی فعلی، دفتر نمره قابل ویرایش اضافه شده است؛ آزمون‌ساز، بانک سؤال و مرکز تکالیف هنوز به‌صورت کامل منتقل نشده‌اند. عملیات تأییدشده فقط با مجوز سرور انجام می‌شوند.",12,MUTED,false));addCard(content,notice);
    }
    private void weekView(){section="بیشتر";shell();sectionHead("برنامه هفتگی","داده‌های واقعی برنامه معلم");
        JSONArray schedule=arr(dashboard,"weekSchedule");if(schedule.length()==0){empty("برنامه‌ای ثبت نشده","مدرسه هنوز برنامه هفتگی این حساب را ثبت نکرده است.");return;}
        for(int i=0;i<schedule.length();i++){JSONObject x=obj(schedule,i);LinearLayout c=card(16);
            c.addView(panelTitle("◷",s(x,"day")+" · "+s(x,"className"),s(x,"subject")+" · "+s(x,"startTime")+" تا "+s(x,"endTime")));addCard(content,c);}
        Button back=outline("بازگشت به بیشتر");content.addView(back,lp(-1,50));back.setOnClickListener(v->openTab("بیشتر"));
    }
    private void allStudents() {section="بیشتر";shell();sectionHead("دانش‌آموزان من","فقط کلاس‌های دارای تخصیص تدریس");
        JSONArray a=arr(workspace,"students");if(a.length()==0){empty("دانش‌آموزی یافت نشد","کلاس‌های فعال خود را بررسی کنید.");return;}
        for(int i=0;i<a.length();i++){JSONObject st=obj(a,i);LinearLayout c=card(14);
            c.addView(panelTitle("◯",fullName(st),nameOfClass(s(st,"classId"))));addCard(content,c);}
        Button back=outline("بازگشت به بیشتر");content.addView(back,lp(-1,50));back.setOnClickListener(v->openTab("بیشتر"));
    }
    private void showChangePassword(boolean forced) {
        LinearLayout box=column();pad(box,20,8,20,8);
        EditText old=field("رمز فعلی",true),next=field("رمز جدید (حداقل ۱۲ نویسه)",true),repeat=field("تکرار رمز جدید",true);
        box.addView(old,lp(-1,56));gap(box,10);box.addView(next,lp(-1,56));gap(box,10);box.addView(repeat,lp(-1,56));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(forced?"تغییر رمز اولیه الزامی است":"تغییر رمز عبور").setView(box)
            .setNegativeButton(forced?"خروج":"انصراف",(d,w)->{if(forced){token="";renderLogin();}})
            .setPositiveButton("ثبت رمز جدید",null).create();dialog.setCancelable(!forced);
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
            String pass1=next.getText().toString();if(pass1.length()<12||!pass1.equals(repeat.getText().toString())){toast("رمز جدید معتبر نیست یا تکرارش متفاوت است.");return;}
            background(() -> {JSONObject body=new JSONObject();body.put("currentPassword",old.getText().toString());body.put("newPassword",pass1);
                return SchoolApi.request("/api/staff/change-password","POST",token,body);}, result -> {
                if(!s(result,"token").isEmpty())token=s(result,"token");dialog.dismiss();toast("رمز با موفقیت تغییر کرد.");
                if(forced)fetchData();else render();
            });
        }));dialog.show();
    }
    private void logoutConfirm() {new AlertDialog.Builder(this).setTitle("خروج از حساب")
        .setMessage("نشست فعال شما در سرور پایان می‌یابد و ورود با اثر انگشت این دستگاه غیرفعال می‌شود.")
        .setNegativeButton("انصراف",null).setPositiveButton("خروج",(d,w)->{
            final String old=token;token="";unlocked=false;vault.clear();
            dashboard=new JSONObject();workspace=new JSONObject();me=new JSONObject();renderLogin();
            io.execute(()->{try{SchoolApi.request("/api/staff/logout","POST",old,new JSONObject());}catch(Exception ignored){}});
        }).show();}
    private boolean canBio() {return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)==BiometricManager.BIOMETRIC_SUCCESS;}
    private void promptBio(String title,Cipher cipher,Consumer<Cipher> success) {
        if(bioShowing||!canBio()){toast("اثر انگشت امن روی این گوشی فعال نیست.");return;}
        bioShowing=true;
        BiometricPrompt prompt=new BiometricPrompt(this,ContextCompat.getMainExecutor(this),new BiometricPrompt.AuthenticationCallback(){
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r){bioShowing=false;Cipher c=r.getCryptoObject()==null?null:r.getCryptoObject().getCipher();if(c!=null)success.accept(c);}
            @Override public void onAuthenticationError(int code,CharSequence text){bioShowing=false;}
        });
        BiometricPrompt.PromptInfo info=new BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("مدرسه‌یار · حساب معلم")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("انصراف").build();
        prompt.authenticate(info,new BiometricPrompt.CryptoObject(cipher));
    }
    private void maybeOfferBiometric() {
        if(!canBio()||vault.isEnabled())return;
        new AlertDialog.Builder(this).setTitle("ورود سریع با اثر انگشت")
            .setMessage("مایلی دفعات بعد بدون واردکردن دوباره رمز، با اثر انگشت وارد شوی؟ فقط نشست رمزنگاری‌شده در فضای امن گوشی نگهداری می‌شود.")
            .setNegativeButton("فعلاً نه",null).setPositiveButton("فعال‌سازی",(d,w)->enableBio()).show();
    }
    private void toggleBiometric(){if(vault.isEnabled()){new AlertDialog.Builder(this).setMessage("ورود بیومتریک در این گوشی غیرفعال شود؟")
        .setNegativeButton("انصراف",null).setPositiveButton("غیرفعال کن",(d,w)->{vault.clear();render();}).show();}else enableBio();}
    private void enableBio() {
        if(token.isEmpty()||!unlocked)return;
        try{Cipher c=vault.encryptionCipher();promptBio("تأیید اثر انگشت برای فعال‌سازی",c,verified->{
            try{vault.saveAfterBiometric(verified,token);toast("ورود با اثر انگشت فعال شد.");render();}
            catch(Exception e){vault.clear();fail("فعال‌سازی ورود با اثر انگشت انجام نشد. دوباره تلاش کنید.");}
        });}catch(Exception e){fail("امکان فعال‌سازی اثر انگشت وجود ندارد.");}
    }
    private void unlockSavedSession() {
        if(!vault.isEnabled()||bioShowing||unlocked)return;
        try{Cipher c=vault.decryptionCipher();promptBio("ورود با اثر انگشت",c,verified->{
            try{token=vault.readAfterBiometric(verified);fetchData();}
            catch(Exception e){token="";vault.clear();renderLogin();toast("نشست ذخیره‌شده معتبر نیست؛ با رمز وارد شوید.");}
        });}catch(Exception e){vault.clear();renderLogin();toast("کلید اثر انگشت تغییر کرده است؛ با رمز وارد شوید.");}
    }
    @Override public void onBackPressed() {if(unlocked && !"خانه".equals(section))openTab("خانه");else super.onBackPressed();}
}
