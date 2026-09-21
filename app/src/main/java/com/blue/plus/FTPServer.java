package com.blue.plus;

import android.content.Context;

public class FTPServer {

  public interface Listener {
    void onUploadSuccess(String remotePath);
    void onUploadProgress(int progress);
    void onDownloadSuccess(String localPath);
    void onDataReceived(String key, String value);
    void onError(String message);
  }

  private final Context context;
  private final String host;
  private final int port;
  private final String user;
  private final String pass;
  private final String root;
  private Listener listener;

  public FTPServer(Context context, String host, int port, String user, String pass, String root) {
    this.context = context;
    this.host = host;
    this.port = port;
    this.user = user;
    this.pass = pass;
    this.root = root;
  }

  public void setListener(Listener l) {
    this.listener = l;
  }

  public void uploadFile(String scId, String localPath, String remotePath, Object cb) {
    // TODO: Implement real FTP/SFTP upload using serverId -> server profile mapping
    if (listener != null) {
      listener.onUploadProgress(100);
      listener.onUploadSuccess(remotePath);
    }
  }

  public void downloadFile(String scId, String remotePath, String localPath, Object cb) {
    // TODO: Implement real FTP/SFTP download
    if (listener != null) {
      listener.onDownloadSuccess(localPath);
    }
  }

  public void setJSONData(String scId, String key, String value, Object cb) {
    // TODO: Implement real /db/<key>.json write
    if (listener != null) {
      listener.onDataReceived(key, value);
    }
  }

  public void getJSONData(String scId, String key, Object cb) {
    // TODO: Implement real /db/<key>.json read
    if (listener != null) {
      listener.onDataReceived(key, "");
    }
  }
}
