package me.sheimi.sgit.database.models;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.util.SparseArray;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import me.sheimi.android.utils.FsUtils;
import me.sheimi.sgit.SGitApplication;
import me.sheimi.sgit.database.RepoContract;
import me.sheimi.sgit.database.RepoDbManager;
import me.sheimi.sgit.exception.StopTaskException;
import me.sheimi.sgit.preference.PreferenceHelper;
import me.sheimi.sgit.repo.tasks.repo.RepoOpTask;
import timber.log.Timber;

/**
 * Model for a local repo
 */
public class Repo implements Comparable<Repo>, Serializable {

    /**
     * Generated serialVersionID
     */
    private static final long serialVersionUID = -4921633809823078219L;

    public static final String TAG = Repo.class.getSimpleName();

    public static final int COMMIT_TYPE_HEAD = 0;
    public static final int COMMIT_TYPE_TAG = 1;
    public static final int COMMIT_TYPE_TEMP = 2;
    public static final int COMMIT_TYPE_REMOTE = 3;
    public static final int COMMIT_TYPE_UNKNOWN = -1;

    private int mID;
    private String mLocalPath;
    private String mRemoteURL;
    private String mUsername;
    private String mPassword;
    private String mRepoStatus;
    private String mLastCommitter;
    private String mLastCommitterEmail;
    private Date mLastCommitDate;
    private String mLastCommitMsg;
    private boolean isDeleted = false;

    // lazy load
    private Set<String> mRemotes;
    private Git mGit;
    private StoredConfig mStoredConfig;

    public static final String DOT_GIT_DIR = ".git";
    public static final String EXTERNAL_PREFIX = "external://";
    public static final String REPO_DIR = "repo";

    private static SparseArray<RepoOpTask> mRepoTasks = new SparseArray<RepoOpTask>();

    public Repo(Cursor cursor) {
        mID = RepoContract.getRepoID(cursor);
        mRemoteURL = RepoContract.getRemoteURL(cursor);
        mLocalPath = RepoContract.getLocalPath(cursor);
        mUsername = RepoContract.getUsername(cursor);
        mPassword = RepoContract.getPassword(cursor);
        mRepoStatus = RepoContract.getRepoStatus(cursor);
        mLastCommitter = RepoContract.getLatestCommitterName(cursor);
        mLastCommitterEmail = RepoContract.getLatestCommitterEmail(cursor);
        mLastCommitDate = RepoContract.getLatestCommitDate(cursor);
        mLastCommitMsg = RepoContract.getLatestCommitMsg(cursor);
    }

    public Bundle getBundle() {
        Bundle bundle = new Bundle();
        bundle.putSerializable(TAG, this);
        return bundle;
    }

    public static Repo createRepo(String localPath, String remoteURL, String status) {
        return getRepoById(RepoDbManager.createRepo(localPath, remoteURL, status));
    }

    public static Repo importRepo(String localPath, String status) {
        return getRepoById(RepoDbManager.importRepo(localPath, status));
    }

    public static Repo getRepoById(long id) {
        Cursor c = RepoDbManager.getRepoById(id);
        c.moveToFirst();
        Repo repo = new Repo(c);
        c.close();
        return repo;
    }

    public static List<Repo> getRepoList(Context context, Cursor cursor) {
        List<Repo> repos = new ArrayList<Repo>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            repos.add(new Repo(cursor));
            cursor.moveToNext();
        }
        return repos;
    }

    public int getID() {
        return mID;
    }

    public String getLocalPath() {
        return mLocalPath;
    }

    public String getDisplayName() {
        if (!isExternal())
            return mLocalPath;
        String[] strs = mLocalPath.split("/");
        return strs[strs.length - 1] + " (external)";
    }

    public static boolean isExternal(String path) {
        return path.startsWith(EXTERNAL_PREFIX);
    }

    public boolean isExternal() {
        return isExternal(getLocalPath());
    }

    public String getRemoteURL() {
        return mRemoteURL;
    }

    public String getRepoStatus() {
        return mRepoStatus;
    }

    public String getLastCommitter() {
        return mLastCommitter;
    }

    public String getLastCommitterEmail() {
        return mLastCommitterEmail;
    }

    public String getLastCommitMsg() {
        return mLastCommitMsg;
    }

    public String getLastCommitFullMsg() {
	RevCommit commit = getLatestCommit();
	if (commit == null) {
	    return getLastCommitMsg();
	}
	return commit.getFullMessage();
    }

    public Date getLastCommitDate() {
        return mLastCommitDate;
    }

    public String getPassword() {
        return mPassword;
    }

    public String getUsername() {
        return mUsername;
    }

    public void setUsername(String username) {
        mUsername = username;
    }

    public void setPassword(String password) {
        mPassword = password;
    }

    public void cancelTask() {
        RepoOpTask task = mRepoTasks.get(getID());
        if (task == null)
            return;
        task.cancelTask();
        removeTask(task);
    }

    public boolean addTask(RepoOpTask task) {
        if (mRepoTasks.get(getID()) != null)
            return false;
        mRepoTasks.put(getID(), task);
        return true;
    }

    public void removeTask(RepoOpTask task) {
        RepoOpTask runningTask = mRepoTasks.get(getID());
        if (runningTask == null || runningTask != task)
            return;
        mRepoTasks.remove(getID());
    }

    public void updateStatus(String status) {
        ContentValues values = new ContentValues();
        mRepoStatus = status;
        values.put(RepoContract.RepoEntry.COLUMN_NAME_REPO_STATUS, status);
        RepoDbManager.updateRepo(mID, values);
    }

    public void updateRemote() {
        ContentValues values = new ContentValues();
        values.put(RepoContract.RepoEntry.COLUMN_NAME_REMOTE_URL,
                getRemoteOriginURL());
        RepoDbManager.updateRepo(mID, values);
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeInt(mID);
        out.writeObject(mRemoteURL);
        out.writeObject(mLocalPath);
        out.writeObject(mUsername);
        out.writeObject(mPassword);
        out.writeObject(mRepoStatus);
        out.writeObject(mLastCommitter);
        out.writeObject(mLastCommitterEmail);
        out.writeObject(mLastCommitDate);
        out.writeObject(mLastCommitMsg);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException,
            ClassNotFoundException {
        mID = in.readInt();
        mRemoteURL = (String) in.readObject();
        mLocalPath = (String) in.readObject();
        mUsername = (String) in.readObject();
        mPassword = (String) in.readObject();
        mRepoStatus = (String) in.readObject();
        mLastCommitter = (String) in.readObject();
        mLastCommitterEmail = (String) in.readObject();
        mLastCommitDate = (Date) in.readObject();
        mLastCommitMsg = (String) in.readObject();
    }

    @Override
    public int compareTo(Repo repo) {
        return repo.getID() - getID();
    }

    public void deleteRepo() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                deleteRepoSync();
            }
        });
        thread.start();
    }

    public void deleteRepoSync() {
        if (isDeleted)
            return;
        RepoDbManager.deleteRepo(mID);
        if (!isExternal()) {
            File fileToDelete = getDir();
            FsUtils.deleteFile(fileToDelete);
        }
        isDeleted = true;
    }

    public boolean renameRepo(String repoName) {
        File directory = getDir();
        if (FsUtils.renameDirectory(directory, repoName)) {
            ContentValues values = new ContentValues();
            mLocalPath = isExternal()
                ? EXTERNAL_PREFIX + directory.getParent() + File.separator + repoName
                : repoName;
            values.put(RepoContract.RepoEntry.COLUMN_NAME_LOCAL_PATH, mLocalPath);
            RepoDbManager.updateRepo(getID(), values);

            return true;
        }

        return false;
    }

    public void updateLatestCommitInfo() {
        RevCommit commit = getLatestCommit();
        ContentValues values = new ContentValues();
        String email = "";
        String uname = "";
        String commitDateStr = "";
        String msg = "";
        if (commit != null) {
            PersonIdent committer = commit.getCommitterIdent();
            if (committer != null) {
                email = committer.getEmailAddress() != null ? committer
                        .getEmailAddress() : email;
                uname = committer.getName() != null ? committer.getName()
                        : uname;
            }
            msg = commit.getShortMessage() != null ? commit.getShortMessage()
                    : msg;
            long date = committer.getWhen().getTime();
            commitDateStr = Long.toString(date);

        }
        values.put(RepoContract.RepoEntry.COLUMN_NAME_LATEST_COMMIT_DATE,
                commitDateStr);
        values.put(RepoContract.RepoEntry.COLUMN_NAME_LATEST_COMMIT_MSG, msg);
        values.put(RepoContract.RepoEntry.COLUMN_NAME_LATEST_COMMITTER_EMAIL,
                email);
        values.put(RepoContract.RepoEntry.COLUMN_NAME_LATEST_COMMITTER_UNAME,
                uname);
        RepoDbManager.updateRepo(getID(), values);
    }

    public String getBranchName() {
        try {
            return getGit().getRepository().getFullBranch();
        } catch (IOException|StopTaskException e) {
            Timber.e(e, "error getting branch name");
        }
        return "";
    }

    public String[] getBranches() {
        try {
            Set<String> branchSet = new HashSet<String>();
            List<String> branchList = new ArrayList<String>();
            List<Ref> localRefs = getGit().branchList().call();
            for (Ref ref : localRefs) {
                branchSet.add(ref.getName());
                branchList.add(ref.getName());
            }
            List<Ref> remoteRefs = getGit().branchList()
                    .setListMode(ListBranchCommand.ListMode.REMOTE).call();
            for (Ref ref : remoteRefs) {
                String name = ref.getName();
                String localName = convertRemoteName(name);
                if (branchSet.contains(localName))
                    continue;
                branchList.add(name);
            }
            return branchList.toArray(new String[0]);
        } catch (GitAPIException|StopTaskException e) {
            Timber.e(e);
        }
        return new String[0];
    }

    private RevCommit getLatestCommit() {
        try {
            Iterable<RevCommit> commits = getGit().log().setMaxCount(1).call();
            Iterator<RevCommit> it = commits.iterator();
            if (!it.hasNext())
                return null;
            return it.next();
        } catch (GitAPIException|StopTaskException e) {
            Timber.e(e);
        }
        return null;
    }

    private RevCommit getCommitByRevStr(String commitRevStr) {
        try {
            Repository repository = getGit().getRepository();
            ObjectId id = repository.resolve(commitRevStr);
            RevWalk revWalk = new RevWalk(getGit().getRepository());
            return (id != null) ? revWalk.parseCommit(id) : null;
        } catch (StopTaskException | IOException e) {
            Timber.e(e, "error parsing commit id: %s", commitRevStr);
            return null;
        }
    }

    public boolean isInitialCommit(String commit) {
        RevCommit revCommit = getCommitByRevStr(commit);
        return revCommit != null && revCommit.getParentCount() == 0;
    }

    public List<Ref> getLocalBranches() {
        try {
            List<Ref> localRefs = getGit().branchList().call();
            return localRefs;
        } catch (GitAPIException|StopTaskException e) {
            Timber.e(e);
        }
        return new ArrayList<Ref>();
    }

    public String[] getTags() {
        try {
            List<Ref> refs = getGit().tagList().call();
            String[] tags = new String[refs.size()];
            // convert refs/tags/[branch] -> heads/[branch]
            for (int i = 0; i < tags.length; ++i) {
                tags[i] = refs.get(i).getName();
            }
            return tags;
        } catch (GitAPIException|StopTaskException e) {
            Timber.e(e);
        }
        return new String[0];
    }

    public String getCurrentDisplayName() {
        return getCommitDisplayName(getBranchName());
    }

    public static int getCommitType(String[] splits) {
        if (splits.length == 4)
            return COMMIT_TYPE_REMOTE;
        if (splits.length != 3)
            return COMMIT_TYPE_TEMP;
        String type = splits[1];
        if ("tags".equals(type))
            return COMMIT_TYPE_TAG;
        return COMMIT_TYPE_HEAD;
    }

    /**
     * Returns the type of ref based on the refs full path within .git/
     * @param fullRefName
     * @return
     */
    public static int getCommitType(String fullRefName) {
        if (fullRefName != null && fullRefName.startsWith(Constants.R_REFS)) {
            if (fullRefName.startsWith(Constants.R_HEADS)) {
                return COMMIT_TYPE_HEAD;
            } else if (fullRefName.startsWith(Constants.R_TAGS)) {
                return COMMIT_TYPE_TAG;
            } else if (fullRefName.startsWith(Constants.R_REMOTES)) {
                return  COMMIT_TYPE_REMOTE;
            }
        }
        return COMMIT_TYPE_UNKNOWN;
    }

    /**
     * Return just the name of the ref, with any prefixes like "heads", "remotes", "tags" etc.
     * @param name
     * @return
     */
    public static String getCommitName(String name) {
        String[] splits = name.split("/");
        int type = getCommitType(splits);
        switch (type) {
            case COMMIT_TYPE_TEMP:
            case COMMIT_TYPE_TAG:
            case COMMIT_TYPE_HEAD:
                return getCommitDisplayName(name);
            case COMMIT_TYPE_REMOTE:
                return splits[3];
        }
        return null;
    }

    /**
     *
     * @param ref
     * @return  Shortened version of full ref path, suitable for display in UI
     */
    public static String getCommitDisplayName(String ref) {
        if (getCommitType(ref) == COMMIT_TYPE_REMOTE) {
            return (ref != null && ref.length() > Constants.R_REFS.length()) ? ref.substring(Constants.R_REFS.length()) : "";
        }
        return Repository.shortenRefName(ref);
    }

    /**
     *
     * @param remote
     * @return null if remote is not found to be a remote ref in this repo
     */
    public static String convertRemoteName(String remote) {
        if (getCommitType(remote) != COMMIT_TYPE_REMOTE) {
            return null;
        } else {
            String[] splits = remote.split("/");
            return String.format("refs/heads/%s", splits[3]);
        }
    }

    public static File getDir(PreferenceHelper preferenceHelper, String localpath) {
        if (Repo.isExternal(localpath)) {
            return new File(localpath.substring(Repo.EXTERNAL_PREFIX.length()));
        }
        File repoDir = preferenceHelper.getRepoRoot();
        if (repoDir == null) {
            repoDir = FsUtils.getExternalDir(REPO_DIR, true);
            Timber.d("PRESET repo path:"+new File(repoDir, localpath).getAbsolutePath());
            return new File(repoDir, localpath);
        } else {
            repoDir = new File(preferenceHelper.getRepoRoot(), localpath);
            Timber.d("CUSTOM repo path:"+repoDir);
            return repoDir;
        }
    }

    public static void setLocalRepoRoot(Context context, File repoRoot) {
        PreferenceHelper prefs = ((SGitApplication) context.getApplicationContext()).getPrefenceHelper();
        File oldRoot = prefs.getRepoRoot();
        prefs.setRepoRoot(repoRoot.getAbsolutePath());

        // need to make any existing "internal" repos "external" so that their paths are still correct
        List<Repo> allRepos = Repo.getRepoList(context,  RepoDbManager.queryAllRepo());
        for (Repo repo:allRepos) {
            if (!repo.isExternal()) {
                repo.mLocalPath = EXTERNAL_PREFIX + oldRoot.getAbsolutePath() + "/" + repo.mLocalPath;
                RepoDbManager.setLocalPath(repo.getID(), repo.mLocalPath);
            }
        }
    }

    public File getDir() {
        PreferenceHelper prefHelper = ((SGitApplication) SGitApplication.getContext()).getPrefenceHelper();
        return Repo.getDir(prefHelper, getLocalPath());
    }

    public Git getGit() throws StopTaskException {
        if (mGit != null)
            return mGit;
        try {
            File repoFile = getDir();
            mGit = Git.open(repoFile);
            return mGit;
        } catch (IOException e) {
            Timber.e(e);
            throw new StopTaskException();
        }
    }

    public StoredConfig getStoredConfig() throws StopTaskException {
        if (mStoredConfig == null) {
            mStoredConfig = getGit().getRepository().getConfig();
        }
        return mStoredConfig;
    }

    public String getRemoteOriginURL() {
        try {
            StoredConfig config = getStoredConfig();
            String origin = config.getString("remote", "origin", "url");
            if (origin != null && !origin.isEmpty())
                return origin;
            Set<String> remoteNames = config.getSubsections("remote");
            if (remoteNames.size() == 0)
                return "";
            String url = config.getString("remote", remoteNames.iterator()
                    .next(), "url");
            return url;
        } catch (StopTaskException e) {
        }
        return "";
    }

    public Set<String> getRemotes() {
        if (mRemotes != null)
            return mRemotes;
        try {
            StoredConfig config = getStoredConfig();
            Set<String> remotes = config.getSubsections("remote");
            mRemotes = new HashSet<String>(remotes);
            return mRemotes;
        } catch (StopTaskException e) {
        }
        return new HashSet<String>();
    }

    public void setRemote(String remote, String url) throws IOException {
        try {
            StoredConfig config = getStoredConfig();
            Set<String> remoteNames = config.getSubsections("remote");
            if (remoteNames.contains(remote)) {
                throw new IOException(String.format(
                        "Remote %s already exists.", remote));
            }
            config.setString("remote", remote, "url", url);
            String fetch = String.format("+refs/heads/*:refs/remotes/%s/*",
                    remote);
            config.setString("remote", remote, "fetch", fetch);
            config.save();
            mRemotes.add(remote);
        } catch (StopTaskException e) {
        }
    }

    public void removeRemote(String remote) throws IOException {
        try {
            StoredConfig config = getStoredConfig();
            Set<String> remoteNames = config.getSubsections("remote");
            if (!remoteNames.contains(remote)) {
                throw new IOException(String.format("Remote %s does not exist.", remote));
            }
            config.unsetSection("remote", remote);
            config.save();
            mRemotes.remove(remote);
        } catch (StopTaskException e) {
        }
    }

    public void saveCredentials() {
        RepoDbManager.persistCredentials(getID(), getUsername(), getPassword());
    }
}
