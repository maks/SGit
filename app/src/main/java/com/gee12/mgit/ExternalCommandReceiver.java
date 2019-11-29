package com.gee12.mgit;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import me.sheimi.sgit.SGitApplication;
import me.sheimi.sgit.activities.RepoDetailActivity;
import me.sheimi.sgit.activities.delegate.actions.PullAction;
import me.sheimi.sgit.database.RepoDbManager;
import me.sheimi.sgit.database.models.Repo;

public class ExternalCommandReceiver {

    public static final String EXTRA_APP_NAME = "com.gee12.mytetroid.EXTRA_APP_NAME";
    public static final String EXTRA_SYNC_COMMAND = "com.gee12.mytetroid.EXTRA_SYNC_COMMAND";
    public static final String SENDER_APP_NAME = "com.gee12.mytetroid";

    private RepoDetailActivity activity;
    private String storagePath;
    private String command;
    private Repo repo;
    private boolean isExecExtCommand;

    public ExternalCommandReceiver(RepoDetailActivity activity, String storagePath, String command) {
        this.activity = activity;
        this.storagePath = storagePath;
        this.command = command;
    }

    public static ExternalCommandReceiver checkExternalCommand(RepoDetailActivity activity, @NotNull Intent intent) {
        String appName = intent.getStringExtra(EXTRA_APP_NAME);
        if (appName == null || !appName.startsWith(SENDER_APP_NAME)) {
            return null;
        }
        Uri uri = intent.getData();
        if (uri == null) {
            Toast.makeText(activity, "Ошибка получения расположения репозитория", Toast.LENGTH_LONG).show();
            return null;
        }
        String path = uri.getPath();
        if (TextUtils.isEmpty(path)) {
            Toast.makeText(activity, "Ошибка получения расположения репозитория", Toast.LENGTH_LONG).show();
            return null;
        }
        String command = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (!TextUtils.isEmpty(command)) {
            Toast.makeText(activity, "Не передана команда", Toast.LENGTH_LONG).show();
            return null;
        }
        return new ExternalCommandReceiver(activity, path, command);
    }

    public Repo selectRepo() {
        Repo repo = getRepoByName(storagePath);
        if (repo == null) {
            Toast.makeText(activity, "Репозиторий " + storagePath + " отсутствует в списке добавленных",
                Toast.LENGTH_LONG).show();
            return null;
        }
        return repo;

    }

    private Repo getRepoByName(String localPath) {
//        ((SGitApplication) getApplicationContext()).getPrefenceHelper().setRepoRoot(
//            "/storage/sdcard0/Android/data/com.manichord.mgit.debug/files/repo");
        File file = ((SGitApplication)activity.getApplicationContext()).getPrefenceHelper().getRepoRoot();
        // check repo path
        if (file == null || !localPath.startsWith(file.getAbsolutePath())) {
            return null;
        }
        // check repo name
        String repoName = new File(localPath).getName();
        Cursor cursor = RepoDbManager.searchRepo(repoName);
        List<Repo> repos = Repo.getRepoList(this, cursor);
        for (Repo repo : repos) {
            if (repoName.equalsIgnoreCase(repo.getLocalPath())) {
                return repo;
            }
        }
        return null;
    }

    private void syncRepo(Intent intent) {
        if (syncRepo(command)) {
            this.isExecExtCommand = true;
        }
    }

    private boolean syncRepo(String command) {
        if (!TextUtils.isEmpty(command)) {
            String[] words = command.split(" ");
            if (words.length == 0) {
                return false;
            }
            int operIndex = 0;
            if (words[0].equalsIgnoreCase("git"))
                operIndex = 1;

            if (operIndex == 1 && words.length == 1)
                return false;
            if (words[operIndex].equalsIgnoreCase("pull")) {
                int forceIndex = findParam(words, new String[] {"-f", "--force"}, operIndex+1);
                boolean forcePull = (forceIndex != -1);
                String remote = null;
                if (forceIndex != -1 && forceIndex < words.length - 1)
                    remote = words[words.length - 1];

                if (forcePull && !TextUtils.isEmpty(remote)) {
                    PullAction.pull(mRepo, this, remote, forcePull);
                } else {
                    new PullAction(mRepo, this).execute();
                }
                return true;
            }
        } else {
            Toast.makeText(this, "Не передана команда", Toast.LENGTH_LONG).show();
        }
        return false;
    }

    private int findParam(String[] words, String param, int startPos) {
        if (words == null || startPos >= words.length)
            return -1;
        for (int i = startPos; i < words.length; i++) {
            if (words[i].equalsIgnoreCase(param))
                return i;
        }
        return -1;
    }

    private int findParam(String[] words, String[] params, int startPos) {
        if (words == null || startPos >= words.length)
            return -1;
        for (int i = startPos; i < words.length; i++) {
            for (String param : params) {
                if (words[i].equalsIgnoreCase(param)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void onSyncRepoFinish(boolean isSuccess) {
//        Intent resIntent = new Intent("com.gee12.mytetroid.RESULT_ACTION");
//        setResult(Activity.RESULT_OK, resIntent);
        if (isSuccess)
            setResult(Activity.RESULT_OK);
        finish();
    }
}
