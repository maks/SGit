package com.gee12.mgit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import me.sheimi.sgit.R;
import me.sheimi.sgit.SGitApplication;
import me.sheimi.sgit.activities.RepoDetailActivity;
import me.sheimi.sgit.activities.delegate.actions.PullAction;
import me.sheimi.sgit.database.RepoDbManager;
import me.sheimi.sgit.database.models.Repo;
import me.sheimi.sgit.repo.tasks.repo.PullTask;

public class ExternalCommandReceiver {

    public static final String EXTRA_APP_NAME = "com.gee12.mytetroid.EXTRA_APP_NAME";
    public static final String EXTRA_SYNC_COMMAND = "com.gee12.mytetroid.EXTRA_SYNC_COMMAND";
    public static final String SENDER_APP_NAME = "com.gee12.mytetroid";

    private RepoDetailActivity activity;
    private String storagePath;
    private String command;
    private Repo repo;
//    private boolean isExecExtCommand;

    public ExternalCommandReceiver(RepoDetailActivity activity, String storagePath, String command) {
        this.activity = activity;
        this.storagePath = storagePath;
        this.command = command;
    }

    /**
     * Create instance from Intent data
     * @param activity
     * @param intent
     * @return
     */
    public static ExternalCommandReceiver checkExternalCommand(RepoDetailActivity activity, @NotNull Intent intent) {
        String appName = intent.getStringExtra(EXTRA_APP_NAME);
        if (appName == null || !appName.startsWith(SENDER_APP_NAME)) {
            return null;
        }
        Uri uri = intent.getData();
        if (uri == null) {
            Toast.makeText(activity, R.string.error_getting_repo_location, Toast.LENGTH_LONG).show();
            return null;
        }
        String path = uri.getPath();
        if (TextUtils.isEmpty(path)) {
            Toast.makeText(activity, R.string.error_getting_repo_location, Toast.LENGTH_LONG).show();
            return null;
        }
        String command = intent.getStringExtra(EXTRA_SYNC_COMMAND);
        if (TextUtils.isEmpty(command)) {
            Toast.makeText(activity, R.string.command_not_transmitted, Toast.LENGTH_LONG).show();
            return null;
        }
        return new ExternalCommandReceiver(activity, path, command);
    }

    /**
     * Search repo by full path
     * @return
     */
    public Repo selectRepo() {
        Repo repo = getRepoByFullName(activity, storagePath);
        if (repo == null) {
            Toast.makeText(activity, String.format(activity.getString(R.string.repo_is_not_in_list), storagePath),
                Toast.LENGTH_LONG).show();
            return null;
        }
        this.repo = repo;
        return repo;
    }

    /**
     * Search repo (internal or external) by full path
     * @param context
     * @param repoLocalPath
     * @return
     */
    private static Repo getRepoByFullName(Context context, String repoLocalPath) {
//        ((SGitApplication) getApplicationContext()).getPrefenceHelper().setRepoRoot(
//            "/storage/sdcard0/Android/data/com.manichord.mgit.debug/files/repo");
        File repoRoot = ((SGitApplication)context.getApplicationContext()).getPrefenceHelper().getRepoRoot();
        // check repo path
//        if (repoRoot == null || !repoLocalPath.startsWith(repoRoot.getAbsolutePath())) {
//            return null;
//        }
        // check repo name
        String repoName = new File(repoLocalPath).getName();
        Cursor cursor = RepoDbManager.searchRepo(repoName);
        List<Repo> repos = Repo.getRepoList(context, cursor);
        for (Repo repo : repos) {
            String path, name;
            if (repo.isExternal()) {
                path = repo.getLocalPath().substring(Repo.EXTERNAL_PREFIX.length());
//                String[] strs = path.split("/");
//                name = strs[strs.length - 1];
            } else {
                path = repoRoot.getAbsolutePath() + File.separator + repo.getLocalPath();
//                name = repo.getLocalPath();
            }
            if (repoLocalPath.equalsIgnoreCase(path) /*&& repoName.equalsIgnoreCase(name)*/) {
                return repo;
            }
        }
        return null;
    }

    /**
     * Parsing and executing the command
     * @return
     */
    public boolean syncRepo() {
         return syncRepo(command);
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
            // pull command
            if (words[operIndex].equalsIgnoreCase("pull")) {
                int forceIndex = findParam(words, new String[] {"-f", "--force"}, operIndex+1);
                boolean forcePull = (forceIndex != -1);
                String remote = null;
//                if (forceIndex != -1 && forceIndex < words.length - 1)
                int remoteIndex = (forcePull) ? forceIndex + 1 : operIndex + 1;
                if (remoteIndex < words.length)
                    remote = words[words.length - 1];
                // executing
                if (!TextUtils.isEmpty(remote)) { // if entered remote, call pull command directly
                    // without PullDialog
                    pull(repo, activity, remote, forcePull);
                } else {
                    new PullAction(repo, activity).execute();
                }
                return true;
            }
        } else {
            Toast.makeText(activity, R.string.command_not_transmitted, Toast.LENGTH_LONG).show();
        }
        return false;
    }

    /**
     * Run pull task (method was copied from PullAction class, because there it is private)
     * TODO: make original method public or at least protected
     * @param repo
     * @param activity
     * @param remote
     * @param forcePull
     */
    private static void pull(Repo repo, RepoDetailActivity activity,
                            String remote, boolean forcePull) {
        PullTask pullTask = new PullTask(repo, remote, forcePull, activity.new ProgressCallback(
            R.string.pull_msg_init));
        pullTask.executeTask();
        activity.closeOperationDrawer();
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

    /**
     * Processing the result of the command
     * @param isSuccess
     */
    public void onSyncFinish(boolean isSuccess) {
//        Intent resIntent = new Intent("com.gee12.mytetroid.RESULT_ACTION");
//        setResult(Activity.RESULT_OK, resIntent);
        if (isSuccess)
            activity.setResult(Activity.RESULT_OK);
        activity.finish();
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getCommand() {
        return command;
    }

    public Repo getRepo() {
        return repo;
    }
}
