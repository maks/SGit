package me.sheimi.sgit.activities;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import me.sheimi.android.activities.SheimiFragmentActivity;
import me.sheimi.android.utils.CodeGuesser;
import me.sheimi.android.utils.FsUtils;
import me.sheimi.android.utils.Profile;
import me.sheimi.sgit.R;
import me.sheimi.sgit.database.models.Repo;
import me.sheimi.sgit.repo.tasks.repo.CommitDiffTask;
import me.sheimi.sgit.repo.tasks.repo.CommitDiffTask.CommitDiffResult;

public class CommitDiffActivity extends SheimiFragmentActivity {

    public final static String OLD_COMMIT = "old commit";
    public final static String NEW_COMMIT = "new commit";
    public final static String SHOW_DESCRIPTION = "show_description";
    private final static int REQUEST_SAVE_DIFF = 1;
    private static final String JS_INF = "CodeLoader";
    private WebView mDiffContent;
    private ProgressBar mLoading;
    private String mOldCommit;
    private String mNewCommit;
    private boolean mShowDescription;
    private Repo mRepo;
    private RevCommit mCommit;
    private List<String> mDiffStrs;
    private List<DiffEntry> mDiffEntries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_diff);
        setupActionBar();
        mDiffContent = (WebView) findViewById(R.id.fileContent);
        mLoading = (ProgressBar) findViewById(R.id.loading);

        Bundle extras = getIntent().getExtras();
        mOldCommit = extras.getString(OLD_COMMIT);
        mNewCommit = extras.getString(NEW_COMMIT);
        mShowDescription = extras.getBoolean(SHOW_DESCRIPTION);
        mRepo = (Repo) extras.getSerializable(Repo.TAG);

        String title = Repo.getCommitDisplayName(mNewCommit);
        if (mOldCommit != null)
            title += " : " + Repo.getCommitDisplayName(mOldCommit);

        setTitle(getString(R.string.title_activity_commit_diff) + title);
        loadFileContent();
    }

    private void loadFileContent() {
        mDiffContent.addJavascriptInterface(new CodeLoader(), JS_INF);
        mDiffContent.loadUrl("file:///android_asset/local_commits_diff.html");
        WebSettings webSettings = mDiffContent.getSettings();
        webSettings.setJavaScriptEnabled(true);
        mDiffContent.setWebChromeClient(new WebChromeClient() {
            public void onConsoleMessage(String message, int lineNumber,
                                         String sourceID) {
                Log.d("MyApplication", message + " -- From line " + lineNumber
                        + " of " + sourceID);
            }

            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        mDiffContent.setBackgroundColor(Color.TRANSPARENT);
    }

    private void setupActionBar() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.diff_commits, menu);
        MenuItem item = menu.findItem(R.id.action_share_diff);
        ShareActionProvider shareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
        final Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri futurePathName = Uri.fromFile(sharedDiffPathName());
        shareIntent.putExtra(Intent.EXTRA_STREAM, futurePathName);
        shareIntent.setData(futurePathName);
        shareIntent.setType("text/x-patch");

        shareActionProvider.setOnShareTargetSelectedListener(new ShareActionProvider.OnShareTargetSelectedListener () {
            public boolean onShareTargetSelected(ShareActionProvider source, Intent intent) {
                try {
                    File diff = sharedDiffPathName();
                    saveDiff(new FileOutputStream(diff));
                } catch (IOException e) {
                    showToastMessage(R.string.alert_file_creation_failure);
                }
                return false;
            }
        });

        shareActionProvider.setShareIntent(shareIntent);
        return true;
    }

    private String formatCommitInfo() {
        PersonIdent committer, author;
        committer = mCommit.getCommitterIdent();
        author = mCommit.getAuthorIdent();
        return "commit " + mNewCommit + "\n"
                + "Author:     " + author.getName() + " <" + author.getEmailAddress() + ">\n"
                + "AuthorDate: " + author.getWhen() + "\n"
                + "Commit:     " + committer.getName() + " <" + committer.getEmailAddress() + ">\n"
                + "CommitDate: " + committer.getWhen() + "\n";
    }

    private void saveDiff(OutputStream fos) throws IOException {
	    /* FIXME: LOCK!!! */
        if (mCommit != null) {
            String message;
            fos.write(formatCommitInfo().getBytes());
            fos.write("\n".getBytes());
            message = mCommit.getFullMessage();
            for (String line : message.split("\n", -1)) {
                fos.write(("    " + line + "\n").getBytes());
            }
            fos.write("\n".getBytes());
        }
        for (String str : mDiffStrs) {
            fos.write(str.getBytes());
        }
    }

    private File sharedDiffPathName() {
        // Should we rather use createTempFile?
        String fname = mNewCommit;
        if (mOldCommit != null)
            fname += "_" + mOldCommit;
        return new File(FsUtils.getExternalDir("diff", true), fname + ".diff");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SAVE_DIFF && resultCode == RESULT_OK) {
            Uri diffUri = data.getData();
            try {
                saveDiff(getContentResolver().openOutputStream(diffUri));
            } catch (IOException e) {
                showToastMessage(R.string.alert_file_creation_failure);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_save_diff:
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType("text/x-patch")
                        .putExtra(Intent.EXTRA_TITLE, Repo.getCommitDisplayName(mNewCommit) + ".diff");

                startActivityForResult(intent, REQUEST_SAVE_DIFF);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class CodeLoader {
        @JavascriptInterface
        public String getDiff(int index) {
            return mDiffStrs.get(index);
        }

        @JavascriptInterface
        public boolean haveCommitInfo() {
            return (mCommit != null);
        }

        @JavascriptInterface
        public String getCommitInfo() {
            if (mCommit == null) {
                return "";
            }

            return formatCommitInfo();
        }

        @JavascriptInterface
        public String getCommitMessage() {
            if (mCommit == null) {
                return "";
            }
            return mCommit.getFullMessage();
        }

        @JavascriptInterface
        public String getChangeType(int index) {
            DiffEntry diff = mDiffEntries.get(index);
            DiffEntry.ChangeType ct = diff.getChangeType();
            return ct.toString();
        }

        @JavascriptInterface
        public String getOldPath(int index) {
            DiffEntry diff = mDiffEntries.get(index);
            String op = diff.getOldPath();
            return op;
        }

        @JavascriptInterface
        public String getNewPath(int index) {
            DiffEntry diff = mDiffEntries.get(index);
            String np = diff.getNewPath();
            return np;
        }

        @JavascriptInterface
        public void getDiffEntries() {
            String oldCommit = mOldCommit != null ? mOldCommit : (mNewCommit + "^");
            CommitDiffTask diffTask = new CommitDiffTask(mRepo, oldCommit,
                    mNewCommit, new CommitDiffResult() {
                @Override
                public void pushResult(List<DiffEntry> diffEntries,
                                       List<String> diffStrs, RevCommit commit) {
                    mDiffEntries = diffEntries;
                    mDiffStrs = diffStrs;
                    mCommit = commit;
                    mLoading.setVisibility(View.GONE);
                    mDiffContent.loadUrl(CodeGuesser.wrapUrlScript("notifyEntriesReady();"));
                }
            }, mShowDescription);
            diffTask.executeTask();
        }

        @JavascriptInterface
        public int getDiffSize() {
            return mDiffEntries.size();
        }

        @JavascriptInterface
        public String getTheme() {
            return Profile.getCodeMirrorTheme(getApplicationContext());
        }
    }
}
