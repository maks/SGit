package me.sheimi.sgit.repo.tasks;

import me.sheimi.android.utils.BasicFunctions;
import android.os.AsyncTask;

public abstract class SheimiAsyncTask<A, B, C> extends AsyncTask<A, B, C> {

    protected Throwable mException;
    protected int mErrorRes = 0;

    protected void setException(Throwable e) {
        mException = e;
    }

    protected void setErrorRes(int errorRes) {
        mErrorRes = errorRes;
    }

    protected void showError() {
        if (mException != null) {
            mException.printStackTrace();
        }
        if (mErrorRes != 0) {
            BasicFunctions.getActiveActivity().showToastMessage(mErrorRes);
        } else if (mException != null) {
            BasicFunctions.getActiveActivity().showToastMessage(
                    mException.getMessage());
        }
    }

    private boolean mIsCanceled = false;

    public void cancelTask() {
        mIsCanceled = true;
    }

    public boolean isTaskCanceled() {
        return mIsCanceled;
    }
    
    public static interface AsyncTaskPostCallback {
        public void onPostExecute(Boolean isSuccess);
    }

    public static interface AsyncTaskCallback {
        public boolean doInBackground(Void... params);
        
        public void onPreExecute();

        public void onProgressUpdate(String... progress);

        public void onPostExecute(Boolean isSuccess);
    }
}
