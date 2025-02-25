package io.github.chipppppppppp.lime.hooks;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.chipppppppppp.lime.LimeOptions;
import io.github.chipppppppppp.lime.R;

public class ReadChecker implements IHook {
    private SQLiteDatabase limeDatabase;
    private SQLiteDatabase db3 = null;
    private SQLiteDatabase db4 = null;
    private boolean shouldHookOnCreate = false;
    private String currentGroupId = null;

    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (!limeOptions.ReadChecker.checked) return;
        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Application appContext = (Application) param.thisObject;


                if (appContext == null) {
                    return;
                }


                File dbFile3 = appContext.getDatabasePath("naver_line");
                File dbFile4 = appContext.getDatabasePath("contact");


                if (dbFile3.exists() && dbFile4.exists()) {
                    SQLiteDatabase.OpenParams.Builder builder1 = new SQLiteDatabase.OpenParams.Builder();
                    builder1.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                    SQLiteDatabase.OpenParams dbParams1 = builder1.build();


                    SQLiteDatabase.OpenParams.Builder builder2 = new SQLiteDatabase.OpenParams.Builder();
                    builder2.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                    SQLiteDatabase.OpenParams dbParams2 = builder2.build();


                    db3 = SQLiteDatabase.openDatabase(dbFile3, dbParams1);
                    db4 = SQLiteDatabase.openDatabase(dbFile4, dbParams2);


                    initializeLimeDatabase(appContext);
                    catchNotification(loadPackageParam, db3, db4, appContext);
                }
            }
        });


        Class<?> chatHistoryRequestClass = XposedHelpers.findClass("com.linecorp.line.chat.request.ChatHistoryRequest", loadPackageParam.classLoader);
        XposedHelpers.findAndHookMethod(chatHistoryRequestClass, "getChatId", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                String chatId = (String) param.getResult();
                // XposedBridge.log(chatId);
                if (groupExists(chatId)) {
                    shouldHookOnCreate = true;
                    currentGroupId = chatId;
                } else {
                    shouldHookOnCreate = false;
                    currentGroupId = null;
                }
            }
        });


        Class<?> chatHistoryActivityClass = XposedHelpers.findClass("jp.naver.line.android.activity.chathistory.ChatHistoryActivity", loadPackageParam.classLoader);
        XposedHelpers.findAndHookMethod(chatHistoryActivityClass, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (shouldHookOnCreate && currentGroupId != null) {

                    if (!isNotGroup(currentGroupId)) {
                        Activity activity = (Activity) param.thisObject;
                        addButton(activity);
                    }
                }
            }
        });


    }

    private boolean groupExists(String groupId) {
        if (limeDatabase == null) {
            // XposedBridge.log("Database is not initialized.");
            return false;
        }


        String query = "SELECT 1 FROM group_messages WHERE group_id = ?";
        Cursor cursor = limeDatabase.rawQuery(query, new String[]{groupId});
        boolean exists = cursor.moveToFirst();
        cursor.close();


        return exists;
    }

    private boolean isNotGroup(String groupId) {
        if (limeDatabase == null) {
            // XposedBridge.log("Database is not initialized.");
            return true;
        }


        String query = "SELECT group_name FROM group_messages WHERE group_id = ?";
        Cursor cursor = limeDatabase.rawQuery(query, new String[]{groupId});

        boolean noGroup = true;

        if (cursor.moveToFirst()) {
            String groupName = cursor.getString(cursor.getColumnIndex("group_name"));
            noGroup = groupName == null || groupName.isEmpty();
        }

        cursor.close();
        return noGroup;
    }

    private void addButton(Activity activity) {
        Button button = new Button(activity);
        button.setText("R");


        button.setBackgroundColor(Color.BLACK);

        button.setTextColor(Color.WHITE);

        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        frameParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        frameParams.topMargin = 150;
        button.setLayoutParams(frameParams);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentGroupId != null) {
                    showDataForGroupId(activity, currentGroupId);
                }
            }
        });

        ViewGroup layout = activity.findViewById(android.R.id.content);
        layout.addView(button);
    }


    private void showDataForGroupId(Activity activity, String groupId) {
        if (limeDatabase == null) {
            return;
        }

        String query = "SELECT server_id, content, created_time FROM group_messages WHERE group_id=? ORDER BY created_time ASC";
        Cursor cursor = limeDatabase.rawQuery(query, new String[]{groupId});

        Map<String, DataItem> dataItemMap = new HashMap<>();

        while (cursor.moveToNext()) {
            String serverId = cursor.getString(0);
            String content = cursor.getString(1);
            String createdTime = cursor.getString(2);

            List<String> user_nameList = getuser_namesForServerId(serverId);

            if (dataItemMap.containsKey(serverId)) {
                DataItem existingItem = dataItemMap.get(serverId);
                existingItem.user_names.addAll(user_nameList);
            } else {
                DataItem dataItem = new DataItem(serverId, content, createdTime);
                dataItem.user_names.addAll(user_nameList);
                dataItemMap.put(serverId, dataItem);
            }
        }
        cursor.close();

        List<DataItem> sortedDataItems = new ArrayList<>(dataItemMap.values());
        Collections.sort(sortedDataItems, Comparator.comparing(item -> item.createdTime));

        StringBuilder resultBuilder = new StringBuilder();
        for (DataItem item : sortedDataItems) {
            resultBuilder.append("Content: ").append(item.content != null ? item.content : "Media").append("\n");
            resultBuilder.append("Created Time: ").append(item.createdTime).append("\n");

            if (!item.user_names.isEmpty()) {
                resultBuilder.append("既読者 (").append(item.user_names.size()).append("):\n");
                for (String user_name : item.user_names) {
                    resultBuilder.append("- ").append(user_name).append("\n");
                }
            } else {
                resultBuilder.append("No talk names found.\n");
            }
            resultBuilder.append("\n");
        }

        TextView textView = new TextView(activity);
        textView.setText(resultBuilder.toString());
        textView.setPadding(20, 20, 20, 20);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(textView);

        // ダイアログ作成
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("READ Data");
        builder.setView(scrollView);

        // OKボタン
        builder.setPositiveButton("OK", null);

        // 削除ボタン
        builder.setNegativeButton("削除", (dialog, which) -> {
            // 削除の確認ダイアログを表示
            new AlertDialog.Builder(activity)
                    .setTitle("確認")
                    .setMessage("本当に削除しますか？")
                    .setPositiveButton("はい", (confirmDialog, confirmWhich) -> deleteGroupData(groupId, activity))
                    .setNegativeButton("いいえ", null)
                    .show();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void deleteGroupData(String groupId, Activity activity) {
        if (limeDatabase == null) {
            return;
        }

        String deleteQuery = "DELETE FROM group_messages WHERE group_id=?";
        limeDatabase.execSQL(deleteQuery, new String[]{groupId});
        Toast.makeText(activity, "データが削除されました。", Toast.LENGTH_SHORT).show();
    }

    private List<String> getuser_namesForServerId(String serverId) {
        if (limeDatabase == null) {
            return Collections.emptyList();
        }

        String query = "SELECT user_name FROM group_messages WHERE server_id=?";
        Cursor cursor = limeDatabase.rawQuery(query, new String[]{serverId});
        List<String> user_names = new ArrayList<>();

        if (cursor.moveToFirst()) {
            String userNameStr = cursor.getString(0);
            if (userNameStr != null) {
                // 改行で区切ってユーザー名を取得
                String[] names = userNameStr.split("\n");
                Collections.addAll(user_names, names);
            }
        }
        cursor.close();
        return user_names;
    }

    private static class DataItem {
        String serverId;
        String content;
        String createdTime;
        Set<String> user_names;

        DataItem(String serverId, String content, String createdTime) {
            this.serverId = serverId;
            this.content = content;
            this.createdTime = createdTime;
            this.user_names = new HashSet<>();
        }
    }

    private void catchNotification(XC_LoadPackage.LoadPackageParam loadPackageParam, SQLiteDatabase db3, SQLiteDatabase db4, Context appContext) {
        try {
            XposedBridge.hookAllMethods(
                    loadPackageParam.classLoader.loadClass(Constants.NOTIFICATION_READ_HOOK.className),
                    "invokeSuspend",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String paramValue = param.args[0].toString();
                            // XposedBridge.log(paramValue);
                            if (appContext == null) {
                                // XposedBridge.log("appContext is null!");
                                return;
                            }

                            Context moduleContext;
                            try {
                                moduleContext = appContext.createPackageContext(
                                        "io.github.chipppppppppp.lime", Context.CONTEXT_IGNORE_SECURITY);
                            } catch (PackageManager.NameNotFoundException e) {
                                // XposedBridge.log("Failed to create package context: " + e.getMessage());
                                return;
                            }

                            if (paramValue != null && paramValue.contains("type:NOTIFIED_READ_MESSAGE")) {
                                List<String> messages = extractMessages(paramValue);
                                for (String message : messages) {
                                    fetchDataAndSave(db3, db4, message, appContext, moduleContext);
                                }
                            }
                        }
                    }
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> extractMessages(String paramValue) {
        List<String> messages = new ArrayList<>();
        Pattern pattern = Pattern.compile("type:NOTIFIED_READ_MESSAGE.*?(?=type:|$)");
        Matcher matcher = pattern.matcher(paramValue);

        while (matcher.find()) {
            messages.add(matcher.group().trim());
        }

        return messages;
    }

    private void fetchDataAndSave(SQLiteDatabase db3, SQLiteDatabase db4, String paramValue, Context context, Context moduleContext) {
        File dbFile = new File(context.getFilesDir(), "data_log.txt");

        try {
            String serverId = extractServerId(paramValue, context);
            String checkedUser = extractCheckedUser(paramValue);

            if (serverId == null || checkedUser == null) {
                writeToFile(dbFile, "Missing parameters: serverId=" + serverId + ", checkedUser=" + checkedUser);
                return;
            }

            String groupId = queryDatabase(db3, "SELECT chat_id FROM chat_history WHERE server_id=?", serverId);
            String groupName = queryDatabase(db3, "SELECT name FROM groups WHERE id=?", groupId);
            String content = queryDatabase(db3, "SELECT content FROM chat_history WHERE server_id=?", serverId);
            String user_name = queryDatabase(db4, "SELECT profile_name FROM contacts WHERE mid=?", checkedUser);
            String timeEpochStr = queryDatabase(db3, "SELECT created_time FROM chat_history WHERE server_id=?", serverId);
            String timeFormatted = formatMessageTime(timeEpochStr);

            String media = queryDatabase(db3, "SELECT attachement_type FROM chat_history WHERE server_id=?", serverId);
            String mediaDescription = "";

            if (media != null) {
                switch (media) {
                    case "7":
                        mediaDescription = moduleContext.getResources().getString(R.string.sticker);
                        break;
                    case "1":
                        mediaDescription = moduleContext.getResources().getString(R.string.picture);
                        break;
                    case "2":
                        mediaDescription = moduleContext.getResources().getString(R.string.video);
                        break;
                    default:
                        mediaDescription = "";
                        break;
                }
            }

            String finalContent = (content != null && !content.isEmpty()) ? content : (!mediaDescription.isEmpty() ? mediaDescription : "No content:" + serverId);

            saveData(groupId, serverId, checkedUser, groupName, finalContent, user_name, timeFormatted, context);
            markPreviousMessagesAsRead(groupId, checkedUser, timeEpochStr, context);
        } catch (Exception e) {
            Log.e("fetchDataAndSave", "Unexpected error:", e);
        }
    }

    private void markPreviousMessagesAsRead(String groupId, String checkedUser, String timeEpochStr, Context context) {
        initializeLimeDatabase(context);

        try {
            String query = "SELECT server_id, content, created_time, user_name FROM group_messages " +
                    "WHERE group_id=? AND created_time<? AND user_name NOT LIKE ?";
            Cursor cursor = limeDatabase.rawQuery(query, new String[]{groupId, timeEpochStr, "%" + checkedUser + "%"});

            while (cursor.moveToNext()) {
                String previousServerId = cursor.getString(cursor.getColumnIndex("server_id"));
                String previousContent = cursor.getString(cursor.getColumnIndex("content"));
                String previousTimeEpochStr = cursor.getString(cursor.getColumnIndex("created_time"));
                String previousUserName = cursor.getString(cursor.getColumnIndex("user_name"));
                String previousTimeFormatted = formatMessageTime(previousTimeEpochStr);

                String updatedUserName = previousUserName + (previousUserName.isEmpty() ? "" : "\n") + checkedUser;

                ContentValues values = new ContentValues();
                values.put("user_name", updatedUserName);

                limeDatabase.update("group_messages", values, "group_id=? AND server_id=?",
                        new String[]{groupId, previousServerId});

                // XposedBridge.log("Marked as read in lime_data.db: Group_id: " + groupId + ", Server_id: " + previousServerId + ", Updated user_name: " + updatedUserName);
            }
            cursor.close();

        } catch (Exception e) {
            Log.e("markPreviousMessagesAsRead", "Error marking previous messages as read:", e);
        }
    }

    private void writeToFile(File file, String text) {
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(text + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String formatMessageTime(String timeEpochStr) {
        if (timeEpochStr == null) return null;
        long timeEpoch = Long.parseLong(timeEpochStr);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timeEpoch));
    }

    private String extractCheckedUser(String paramValue) {
        Pattern pattern = Pattern.compile("param2:([a-zA-Z0-9]+)");
        Matcher matcher = pattern.matcher(paramValue);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractServerId(String paramValue, Context context) {
        Pattern pattern = Pattern.compile("param3:([0-9]+)");
        Matcher matcher = pattern.matcher(paramValue);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            saveParamToFile(paramValue, context);
            return null;
        }
    }

    private void saveParamToFile(String paramValue, Context context) {
        try {
            File logFile = new File(context.getFilesDir(), "missing_param_values.txt");

            if (!logFile.exists()) {
                logFile.createNewFile();
            }

            FileWriter writer = new FileWriter(logFile, true);
            writer.append("Missing serverId in paramValue:").append(paramValue).append("\n");
            writer.close();
        } catch (IOException e) {
            // XposedBridge.log("Error writing paramValue to file: " + e.getMessage());
        }
    }

    private String queryDatabase(SQLiteDatabase db, String query, String... selectionArgs) {
        if (db == null) {
            // XposedBridge.log("Database is not initialized.");
            return null;
        }
        Cursor cursor = db.rawQuery(query, selectionArgs);
        String result = null;
        if (cursor.moveToFirst()) {
            result = cursor.getString(0);
        }
        cursor.close();
        return result;
    }

    private void initializeLimeDatabase(Context context) {
        // 旧データベースファイルの確認と削除
        File oldDbFile = new File(context.getFilesDir(), "lime_data.db");
        if (oldDbFile.exists()) {
            boolean deleted = oldDbFile.delete();
            if (deleted) {
                XposedBridge.log("Old database file lime_data.db deleted.");
            } else {
                XposedBridge.log("Failed to delete old database file lime_data.db.");
            }
        }
        // 新しいデータベースファイルの初期化
        File dbFile = new File(context.getFilesDir(), "lime_checked_data.db");
        limeDatabase = SQLiteDatabase.openOrCreateDatabase(dbFile, null);

        String createGroupTable = "CREATE TABLE IF NOT EXISTS group_messages (" +
                "group_id TEXT NOT NULL, " +
                "server_id TEXT NOT NULL, " +
                "checked_user TEXT, " +
                "group_name TEXT, " +
                "content TEXT, " +
                "user_name TEXT, " +
                "created_time TEXT, " +
                "PRIMARY KEY(group_id, server_id, checked_user)" +
                ");";

        limeDatabase.execSQL(createGroupTable);
        // XposedBridge.log("Database initialized and group_messages table created.");
    }

    private void saveData(String groupId, String serverId, String checkedUser, String groupName, String content, String user_name, String createdTime, Context context) {
        File dbFile = new File(context.getFilesDir(), "operation_log.txt");

        if (limeDatabase == null) {
            writeToFile(dbFile, "Database is not initialized.");
            return;
        }

        Cursor cursor = null;
        try {
            String checkQuery = "SELECT COUNT(*), user_name FROM group_messages WHERE server_id=? AND checked_user=?";
            cursor = limeDatabase.rawQuery(checkQuery, new String[]{serverId, checkedUser});

            if (cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                String existingUserName = cursor.getString(1);

                if (count > 0) {
                    // `user_name`にすでに同じ名前がないかチェックし、ない場合のみ追加する
                    if (!existingUserName.contains(user_name)) {
                        String updatedUserName = existingUserName + (existingUserName.isEmpty() ? "" : "\n") + "-" + user_name;
                        ContentValues values = new ContentValues();
                        values.put("user_name", updatedUserName);
                        limeDatabase.update("group_messages", values, "server_id=? AND checked_user=?", new String[]{serverId, checkedUser});
                        // XposedBridge.log("User name updated for server_id: " + serverId + ", checked_user: " + checkedUser);
                    }
                } else {
                    // 新しいレコードを挿入
                    insertNewRecord(groupId, serverId, checkedUser, groupName, content, "-" + user_name, createdTime);
                }

                // 同じ groupId 内の他のレコードの user_name カラムを更新
                updateOtherRecordsUserNames(groupId, user_name);
            }

        } catch (Exception e) {
            Log.e("saveData", "Error during data existence check or update:", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void updateOtherRecordsUserNames(String groupId, String user_name) {
        Cursor cursor = null;
        try {
            String selectOtherQuery = "SELECT server_id, user_name FROM group_messages WHERE group_id=? AND user_name NOT LIKE ?";
            cursor = limeDatabase.rawQuery(selectOtherQuery, new String[]{groupId, "%-" + user_name + "%"});

            while (cursor.moveToNext()) {
                String serverId = cursor.getString(cursor.getColumnIndex("server_id"));
                String existingUserName = cursor.getString(cursor.getColumnIndex("user_name"));

                // `user_name`にすでに同じ名前がないかチェック
                if (!existingUserName.contains(user_name)) {
                    String updatedUserName = existingUserName + (existingUserName.isEmpty() ? "" : "\n") + "-" + user_name;
                    ContentValues values = new ContentValues();
                    values.put("user_name", updatedUserName);

                    limeDatabase.update("group_messages", values, "group_id=? AND server_id=?", new String[]{groupId, serverId});
                    // XposedBridge.log("Updated user_name for other records in group_id: " + groupId + ", server_id: " + serverId);
                }
            }
        } catch (Exception e) {
            Log.e("updateOtherRecordsUserNames", "Error updating other records' user names:", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void insertNewRecord(String groupId, String serverId, String checkedUser, String groupName, String content, String user_name, String createdTime) {
        try {
            String insertQuery = "INSERT INTO group_messages(group_id, server_id, checked_user, group_name, content, user_name, created_time)" +
                    "VALUES(?, ?, ?, ?, ?, ?, ?);";
            limeDatabase.execSQL(insertQuery, new Object[]{groupId, serverId, checkedUser, groupName, content, user_name, createdTime});

            XposedBridge.log("Saved to DB: Group_Id: " + groupId + ", Server_id: " + serverId + ", Checked_user: " + checkedUser +
                    ", Group_Name: " + groupName + ", Content: " + content + ", user_name: " + user_name + ", Created_Time: " + createdTime);
        } catch (Exception e) {
            Log.e("insertNewRecord", "Error saving data to database:", e);
        }
    }

}