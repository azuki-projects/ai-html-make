package com.azuki.htmlmake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final String PREFS = "htmlmake_data";
    private static final String API_KEY = "groq_key";
    private static final String PROJECTS_KEY = "projects";
    private static final String MODEL = "openai/gpt-oss-120b";
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final int ZIP_REQUEST = 7101;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> projects = new ArrayList<>();
    private final ArrayList<JSONObject> chatMessages = new ArrayList<>();
    private JSONObject project;
    private String selectedFile = "index.html";
    private String apiKey = "";
    private String currentPage = "home";
    private boolean busy = false;
    private LinearLayout root, content, nav, chatList, fileList, projectList;
    private ScrollView chatScroll;
    private EditText editor, composer;
    private TextView status, projectTitle, editorTitle;
    private WebView preview;
    private Button sendButton;
    private ArrayList<GeneratedFile> pendingZip = new ArrayList<>();

    private static class GeneratedFile {
        String path, content, action;
        GeneratedFile(String p, String c, String a) { path=p; content=c; action=a; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF090D12);
        getWindow().setNavigationBarColor(0xFF090D12);
        getWindow().getDecorView().setSystemUiVisibility(0);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        apiKey = prefs.getString(API_KEY, "");
        loadProjects();
        if (projects.isEmpty()) createProjectObject("My first website");
        project = projects.get(0);
        buildShell();
        showPage("home");
        if (apiKey.isEmpty()) main.post(this::showApiKeyDialog);
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private LinearLayout vertical() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private Button button(String value) {
        Button b=new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(12); b.setTextColor(0xFFEAF3F7);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF20303B)); return b;
    }
    private void buildShell() {
        root=vertical(); root.setBackgroundColor(0xFF090D12);
        LinearLayout header=horizontal(); header.setPadding(dp(16),dp(10),dp(12),dp(10)); header.setBackgroundColor(0xFF111922);
        LinearLayout brand=vertical();
        TextView name=text("✦  HTML MAKE",18,0xFFF3F8FA); name.setTypeface(Typeface.DEFAULT_BOLD);
        brand.addView(name); brand.addView(text("GROQ AI WEBSITE BUILDER",9,0xFF8CE6BA));
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button key=button("⚙"); key.setOnClickListener(v->showApiKeyDialog()); header.addView(key,new LinearLayout.LayoutParams(dp(52),dp(42)));
        root.addView(header);
        projectTitle=text("No project",11,0xFF8C9BA9); projectTitle.setPadding(dp(17),dp(8),dp(17),dp(5)); root.addView(projectTitle);
        content=vertical(); root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        nav=horizontal(); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(4),dp(5),dp(4),dp(5)); nav.setBackgroundColor(0xFF111922);
        String[] labels={"⌂\nHome","▤\nFiles","✦\nAI Chat","◫\nPreview"};
        String[] pages={"home","files","chat","preview"};
        for(int i=0;i<pages.length;i++){
            final String page=pages[i]; Button b=button(labels[i]); b.setTextSize(10); b.setGravity(Gravity.CENTER);
            b.setOnClickListener(v->showPage(page));
            nav.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));
        }
        root.addView(nav); setContentView(root);
    }

    private void showPage(String page) {
        currentPage=page; content.removeAllViews();
        projectTitle.setText(project==null?"No project":"PROJECT  /  "+project.optString("name"));
        if("home".equals(page)) buildHome();
        else if("files".equals(page)) buildFiles();
        else if("chat".equals(page)) buildChat();
        else buildPreview();
    }

    private void heading(String eyebrow,String title,String subtitle) {
        LinearLayout box=vertical(); box.setPadding(dp(17),dp(14),dp(17),dp(13));
        TextView e=text(eyebrow,10,0xFF8CE6BA); e.setTypeface(Typeface.DEFAULT_BOLD); box.addView(e);
        TextView h=text(title,28,Color.WHITE); h.setTypeface(Typeface.DEFAULT_BOLD); box.addView(h);
        TextView s=text(subtitle,12,0xFF8796A4); s.setPadding(0,dp(3),0,0); box.addView(s); content.addView(box);
    }

    private void buildHome() {
        heading("YOUR CREATIVE WORKSPACE","Make something on the web.","Create projects and let AI build the pages for you.");
        LinearLayout actions=horizontal(); actions.setPadding(dp(16),0,dp(16),dp(14));
        Button create=button("＋ New project"); create.setTextColor(0xFF092117); create.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF8CE6BA));
        create.setOnClickListener(v->newProjectDialog()); actions.addView(create);
        content.addView(actions);
        TextView label=text("YOUR PROJECTS",10,0xFF8CE6BA); label.setPadding(dp(17),dp(5),dp(17),dp(8)); content.addView(label);
        ScrollView scroll=new ScrollView(this); projectList=vertical(); projectList.setPadding(dp(13),0,dp(13),dp(16)); scroll.addView(projectList);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); renderProjects();
    }

    private void renderProjects() {
        if(projectList==null)return; projectList.removeAllViews();
        for(JSONObject p:projects) {
            LinearLayout card=vertical(); card.setPadding(dp(15),dp(13),dp(15),dp(13)); card.setBackground(bg(0xFF141E28,14));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(9); projectList.addView(card,lp);
            TextView n=text("▧  "+p.optString("name"),16,Color.WHITE); n.setTypeface(Typeface.DEFAULT_BOLD); card.addView(n);
            JSONObject files=p.optJSONObject("files"); int count=files==null?0:files.length();
            TextView sub=text(count+" files  ·  Tap to open",11,0xFF8A9AA8); sub.setPadding(0,dp(5),0,0); card.addView(sub);
            card.setOnClickListener(v->{saveEditorIfNeeded();project=p;selectedFile="index.html";saveProjects();showPage("files");});
        }
        Button fresh=button("＋ Create another project"); fresh.setOnClickListener(v->newProjectDialog()); projectList.addView(fresh);
    }

    private void buildFiles() {
        heading("PROJECT WORKSPACE","Files","Select a file to edit its source code.");
        LinearLayout bar=horizontal(); bar.setPadding(dp(12),0,dp(12),dp(8));
        Button add=button("＋ New file"); add.setOnClickListener(v->newFileDialog()); bar.addView(add);
        Button save=button("Save"); save.setOnClickListener(v->saveEditor()); bar.addView(save);
        Button zip=button("Export ZIP"); zip.setOnClickListener(v->exportProjectZip()); bar.addView(zip);
        content.addView(bar);
        LinearLayout body=vertical(); body.setPadding(dp(12),0,dp(12),dp(10)); body.setBackgroundColor(0xFF0D141C);
        fileList=vertical(); ScrollView treeScroll=new ScrollView(this); treeScroll.setBackground(bg(0xFF121B24,10)); treeScroll.addView(fileList);
        body.addView(text("PROJECT FILES",10,0xFF8CE6BA)); body.addView(treeScroll,new LinearLayout.LayoutParams(-1,dp(125)));
        editorTitle=text(selectedFile,12,0xFF8CE6BA); editorTitle.setPadding(0,dp(10),0,dp(7)); body.addView(editorTitle);
        editor=new EditText(this); editor.setGravity(Gravity.TOP|Gravity.LEFT); editor.setTextSize(12); editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextColor(0xFFE1EAF0); editor.setHintTextColor(0xFF738291); editor.setPadding(dp(12),dp(12),dp(12),dp(12));
        editor.setBackground(bg(0xFF080E14,10)); editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHorizontallyScrolling(true); body.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(body,new LinearLayout.LayoutParams(-1,0,1)); renderFileTree();
    }

    private void renderFileTree() {
        if(fileList==null)return; fileList.removeAllViews(); JSONObject files=project.optJSONObject("files"); if(files==null)return;
        Iterator<String> it=files.keys(); while(it.hasNext()) {
            String path=it.next(); Button item=button((path.equals(selectedFile)?"▸  ":"    ")+path);
            item.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL); item.setTextSize(11);
            item.setOnClickListener(v->{saveEditorIfNeeded();selectedFile=path;editorTitle.setText(path);editor.setText(project.optJSONObject("files").optString(path,""));renderFileTree();});
            fileList.addView(item,new LinearLayout.LayoutParams(-1,dp(38)));
        }
        editorTitle.setText(selectedFile); editor.setText(files.optString(selectedFile,""));
    }

    private void saveEditorIfNeeded() {
        if(editor==null||project==null)return;
        JSONObject files=project.optJSONObject("files"); if(files!=null)try{files.put(selectedFile,editor.getText().toString());}catch(Exception ignored){}
        saveProjects();
    }
    private void saveEditor() { saveEditorIfNeeded(); toast("Saved "+selectedFile); }

    private void buildChat() {
        heading("GROQ AI BUILDER","AI Chat","Ask the AI to create pages, add features, or edit files.");
        LinearLayout top=horizontal(); top.setPadding(dp(13),0,dp(13),dp(6));
        status=text(busy?"● Working...":"● Ready",11,0xFF8CE6BA); top.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        Button clear=button("New chat"); clear.setOnClickListener(v->{chatMessages.clear();addChatBubble("assistant","New chat started. What should we build?");}); top.addView(clear); content.addView(top);
        chatScroll=new ScrollView(this); chatList=vertical(); chatList.setPadding(dp(12),dp(5),dp(12),dp(8)); chatScroll.addView(chatList);
        content.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));
        if(chatMessages.isEmpty()) addChatBubble("assistant","Welcome to HTML Make! I can create and edit your project's HTML, CSS and JavaScript files. What would you like to build?");
        LinearLayout compose=vertical(); compose.setPadding(dp(11),dp(9),dp(11),dp(10)); compose.setBackgroundColor(0xFF121922);
        composer=new EditText(this); composer.setTextColor(Color.WHITE); composer.setHintTextColor(0xFF8091A0); composer.setHint("Describe a website or feature...");
        composer.setTextSize(14); composer.setMinHeight(dp(48)); composer.setMaxLines(4); composer.setPadding(dp(13),dp(9),dp(13),dp(9)); composer.setBackground(bg(0xFF253340,18));
        compose.addView(composer);
        sendButton=button("Send  ↑"); sendButton.setTextColor(0xFF092117); sendButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF8CE6BA));
        sendButton.setOnClickListener(v->sendPrompt()); LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(44)); slp.topMargin=dp(7); compose.addView(sendButton,slp);
        content.addView(compose);
        renderChatHistory();
    }

    private void renderChatHistory() {
        if(chatList==null)return; chatList.removeAllViews();
        for(JSONObject m:chatMessages) {
            String role=m.optString("role"); if("system".equals(role))continue;
            addChatBubble(role,m.optString("content"),m.optJSONArray("changes"));
        }
    }
    private void addChatBubble(String role,String value) { addChatBubble(role,value,null); }
    private void addChatBubble(String role,String value,JSONArray changes) {
        if(chatList==null)return;
        boolean user="user".equals(role);
        LinearLayout bubble=vertical(); bubble.setPadding(dp(13),dp(10),dp(13),dp(10)); bubble.setBackground(bg(user?0xFF145B4B:0xFF1B2834,14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(8); chatList.addView(bubble,lp);
        TextView who=text(user?"YOU":"AI BUILDER",9,user?0xFFB5F3D9:0xFF8CE6BA); who.setTypeface(Typeface.DEFAULT_BOLD); bubble.addView(who);
        TextView body=text(value,13,0xFFEAF1F5); body.setPadding(0,dp(5),0,0); body.setTextIsSelectable(true); body.setLineSpacing(dp(2),1f); bubble.addView(body);
        if(changes!=null)for(int i=0;i<changes.length();i++){
            JSONObject c=changes.optJSONObject(i); if(c==null)continue;
            TextView event=text((c.optString("action").equals("created")?"＋ ":"↻ ")+c.optString("path")+" "+c.optString("action"),11,0xFF8CE6BA);
            event.setPadding(0,dp(7),0,0); bubble.addView(event);
        }
        if(!user) {
            Button copy=button("Copy response"); copy.setTextSize(10); copy.setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("AI response",value));toast("Copied");}); bubble.addView(copy);
        }
        if(chatScroll!=null)chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendPrompt() {
        if(busy)return;
        String prompt=composer.getText().toString().trim(); if(prompt.isEmpty())return;
        if(apiKey.isEmpty()){showApiKeyDialog();return;}
        if(editor!=null)saveEditorIfNeeded();
        addHistory("user",prompt); addChatBubble("user",prompt); composer.setText("");
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(composer.getWindowToken(),0);
        setBusy(true,"Thinking...");
        final JSONObject target=project;
        worker.execute(()->{
            try {
                JSONObject answer=callGroq(prompt,target);
                main.post(()->applyAIChanges(answer,target));
            } catch(Exception e) { main.post(()->{addHistory("assistant","Request failed: "+e.getMessage());addChatBubble("assistant","Request failed: "+e.getMessage());setBusy(false,"Ready");}); }
        });
    }

    private JSONObject callGroq(String prompt,JSONObject target) throws Exception {
        JSONObject payload=new JSONObject(); payload.put("model",MODEL); payload.put("temperature",0.35); payload.put("max_tokens",12000);
        payload.put("reasoning_effort","medium"); payload.put("response_format",new JSONObject().put("type","json_object"));
        JSONArray history=new JSONArray();
        history.put(new JSONObject().put("role","system").put("content",makeSystemPrompt(target)));
        for(JSONObject m:chatMessages) if(!"system".equals(m.optString("role"))) history.put(m);
        history.put(new JSONObject().put("role","user").put("content",prompt));
        payload.put("messages",history);
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        try {
            c.setRequestMethod("POST"); c.setConnectTimeout(25000); c.setReadTimeout(120000); c.setDoOutput(true);
            c.setRequestProperty("Authorization","Bearer "+apiKey); c.setRequestProperty("Content-Type","application/json");
            try(OutputStream out=c.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
            int code=c.getResponseCode(); InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            String raw=readAll(in); JSONObject response=new JSONObject(raw);
            if(code<200||code>=300)throw new Exception(response.optJSONObject("error")==null?"HTTP "+code:response.getJSONObject("error").optString("message","HTTP "+code));
            String result=response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","");
            if(result.trim().isEmpty())throw new Exception("AI returned an empty response.");
            return parseAIJson(result);
        } finally { c.disconnect(); }
    }

    private String makeSystemPrompt(JSONObject target) {
        JSONObject files=target.optJSONObject("files"); JSONArray snapshot=new JSONArray();
        if(files!=null){Iterator<String> it=files.keys();while(it.hasNext()){String path=it.next();snapshot.put(new JSONObject().put("path",path).put("content",files.optString(path).substring(0,Math.min(16000,files.optString(path).length()))));}}
        return "You are HTML Make, a website-building coding assistant. Work continuously on the current project. Return ONLY valid JSON: {\"message\":\"short natural progress summary\",\"changes\":[{\"path\":\"index.html\",\"content\":\"complete file content\",\"action\":\"created\"}]}. Every changed file must include its complete final content. Use action created or updated. Include all needed HTML/CSS/JS files. Make polished responsive mobile-friendly websites. Keep existing features unless asked to remove them. Main entry is index.html. Never include secrets. If no file changes are needed, use an empty changes array. Current project: "+target.optString("name")+". Existing files: "+snapshot.toString();
    }

    private JSONObject parseAIJson(String raw) throws Exception {
        String clean=raw.trim().replaceAll("^\\`\\`\\`(?:json)?\\s*","").replaceAll("\\s*\\`\\`\\`$","");
        try{return new JSONObject(clean);}catch(Exception ignored){}
        int a=clean.indexOf("{"),b=clean.lastIndexOf("}");
        if(a>=0&&b>a)return new JSONObject(clean.substring(a,b+1));
        return new JSONObject().put("message",raw).put("changes",new JSONArray());
    }

    private void applyAIChanges(JSONObject result,JSONObject target) {
        setBusy(false,"Working...");
        JSONArray edits=result.optJSONArray("changes"); JSONArray events=new JSONArray();
        JSONObject files=target.optJSONObject("files");
        if(files==null){files=new JSONObject();try{target.put("files",files);}catch(Exception ignored){}}
        if(edits!=null)for(int i=0;i<edits.length();i++){
            JSONObject edit=edits.optJSONObject(i); if(edit==null)continue;
            String path=safePath(edit.optString("path","")); String data=edit.optString("content,null");
            if(path==null||!edit.has("content"))continue;
            boolean exists=files.has(path); String action=exists?"updated":"created";
            try{
                files.put(path,edit.getString("content"));
                JSONObject event=new JSONObject().put("path",path).put("action",action); events.put(event);
                addHistory("assistant",path+" "+action+"...");
                addChatBubble("assistant",path+" "+action+"...");
                status.setText("● "+(exists?"Updating ":"Creating ")+path+"...");
                saveProjects();
            }catch(Exception ignored){}
        }
        String message=result.optString("message","Finished updating your project.");
        try{JSONObject m=new JSONObject().put("role","assistant").put("content",message).put("changes",events);chatMessages.add(m);}catch(Exception ignored){}
        addChatBubble("assistant",message,events);
        saveProjects(); setBusy(false,"Ready");
        if("files".equals(currentPage))buildFiles();
        toast(events.length()+" file(s) changed");
    }

    private String safePath(String raw) {
        String p=raw.trim().replace("\\","/").replaceAll("^/+","");
        if(p.isEmpty()||p.length()>180||p.contains("../")||p.equals("..")||p.contains("//"))return null;
        return p;
    }

    private void setBusy(boolean value,String message) {
        busy=value;
        if(status!=null){status.setText("● "+message);status.setTextColor(value?0xFFFFC66D:0xFF8CE6BA);}
        if(sendButton!=null){sendButton.setEnabled(!value);sendButton.setText(value?"Working...":"Send  ↑");}
    }

    private void buildPreview() {
        heading("LIVE WEBSITE","Preview","Your project's index.html output.");
        LinearLayout bar=horizontal(); bar.setPadding(dp(12),0,dp(12),dp(8));
        Button refresh=button("↻ Refresh"); refresh.setOnClickListener(v->refreshPreview()); bar.addView(refresh);
        content.addView(bar);
        preview=new WebView(this); preview.setBackgroundColor(Color.WHITE);
        WebSettings settings=preview.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(false);
        preview.setWebChromeClient(new android.webkit.WebChromeClient());
        content.addView(preview,new LinearLayout.LayoutParams(-1,0,1)); refreshPreview();
    }

    private void refreshPreview() {
        if(preview==null||project==null)return;
        JSONObject files=project.optJSONObject("files");
        String html=files==null?"":files.optString("index.html","");
        if(html.isEmpty())html="<html><body><h2>index.html not found</h2></body></html>";
        String css=files.optString("style.css",files.optString("styles.css",""));
        String js=files.optString("app.js",files.optString("script.js",""));
        if(!css.isEmpty()){
            String style="<style>"+css+"</style>";
            if(html.toLowerCase().contains("</head>"))html=html.replaceFirst("(?i)</head>",java.util.regex.Matcher.quoteReplacement(style)+"</head>");
            else html=style+html;
        }
        if(!js.isEmpty()){
            String script="<script>"+js.replace("</script","<\\/script")+"</script>";
            if(html.toLowerCase().contains("</body>"))html=html.replaceFirst("(?i)</body>",java.util.regex.Matcher.quoteReplacement(script)+"</body>");
            else html+=script;
        }
        preview.loadDataWithBaseURL("https://htmlmake.local/",html,"text/html","UTF-8",null);
    }

    private void newProjectDialog() {
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("My website");
        new AlertDialog.Builder(this).setTitle("Create project").setView(input).setNegativeButton("Cancel",null)
            .setPositiveButton("Create",(d,w)->{String n=input.getText().toString().trim();if(n.isEmpty())n="Untitled project";createProjectObject(n);project=projects.get(0);showPage("chat");}).show();
    }

    private void newFileDialog() {
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("pages/about.html");
        new AlertDialog.Builder(this).setTitle("Create file").setView(input).setNegativeButton("Cancel",null)
            .setPositiveButton("Create",(d,w)->{
                String path=safePath(input.getText().toString());if(path==null){toast("Enter a valid file path");return;}
                JSONObject files=project.optJSONObject("files");if(files.has(path)){toast("File already exists");return;}
                try{files.put(path,"");selectedFile=path;saveProjects();showPage("files");toast(path+" created");}catch(Exception ignored){}
            }).show();
    }

    private void showApiKeyDialog() {
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("gsk_...");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setText(apiKey);
        new AlertDialog.Builder(this).setTitle("Groq API key").setMessage("Your key is stored only inside this app on this device.")
            .setView(input).setNegativeButton("Cancel",null).setNeutralButton("Remove",(d,w)->{apiKey="";getSharedPreferences(PREFS,MODE_PRIVATE).edit().remove(API_KEY).apply();toast("API key removed");})
            .setPositiveButton("Save",(d,w)->{apiKey=input.getText().toString().trim();getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(API_KEY,apiKey).apply();toast("API key saved");}).show();
    }

    private void exportProjectZip() {
        if(editor!=null)saveEditorIfNeeded();
        pendingZip.clear();JSONObject files=project.optJSONObject("files");if(files==null)return;
        Iterator<String> it=files.keys();while(it.hasNext()){String p=it.next();pendingZip.add(new GeneratedFile(p,files.optString(p),""));}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,project.optString("name","Website").replaceAll("[^A-Za-z0-9_-]","_")+".zip");
        try{startActivityForResult(i,ZIP_REQUEST);}catch(Exception e){toast("Unable to open ZIP saver");}
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=ZIP_REQUEST||result!=RESULT_OK||data==null)return;
        Uri uri=data.getData();ArrayList<GeneratedFile> copy=new ArrayList<>(pendingZip);
        worker.execute(()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri);ZipOutputStream zip=new ZipOutputStream(out)){
                for(GeneratedFile f:copy){zip.putNextEntry(new ZipEntry(f.path));zip.write(f.content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
                main.post(()->toast("Project ZIP exported"));
            }catch(Exception e){main.post(()->toast("ZIP export failed: "+e.getMessage()));}
        });
    }

    private void createProjectObject(String name) {
        try {
            JSONObject p=new JSONObject();p.put("id",System.currentTimeMillis());p.put("name",name);p.put("files",starterFiles());projects.add(0,p);project=p;saveProjects();
        }catch(Exception ignored){}
    }

    private JSONObject starterFiles() throws Exception {
        JSONObject f=new JSONObject();
        f.put("index.html","<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n<title>My Website</title>\n<link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n<main class=\"hero\"><span>MY FIRST PROJECT</span><h1>Hello, world.</h1><p>Ask AI to make this website yours.</p><button id=\"hello\">Click me</button></main>\n<script src=\"app.js\"></script>\n</body>\n</html>");
        f.put("style.css","body{margin:0;min-height:100vh;display:grid;place-items:center;background:#101827;color:#f8fafc;font-family:system-ui,sans-serif}.hero{text-align:center;padding:30px}h1{font-size:clamp(40px,8vw,72px)}p{color:#aab8c8}button{padding:12px 20px;border:0;border-radius:10px;background:#6ee7b7;font-weight:bold}");
        f.put("app.js","document.getElementById('hello')?.addEventListener('click',()=>alert('Your website is working!'));");
        return f;
    }

    private void loadProjects() {
        projects.clear();
        try {
            JSONArray a=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString(PROJECTS_KEY,"[]"));
            for(int i=0;i<a.length();i++)projects.add(a.getJSONObject(i));
        }catch(Exception ignored){}
    }
    private void saveProjects() {
        try{getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(PROJECTS_KEY,new JSONArray(projects).toString()).apply();}catch(Exception ignored){}
    }
    private void addHistory(String role,String value) {
        try{chatMessages.add(new JSONObject().put("role",role).put("content",value));}catch(Exception ignored){}
    }
    private String readAll(InputStream in)throws Exception {
        StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line);}return b.toString();
    }
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}

    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
