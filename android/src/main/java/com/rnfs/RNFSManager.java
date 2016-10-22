package com.rnfs;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import android.os.Environment;
import android.os.StatFs;
import android.util.Base64;
import android.support.annotation.Nullable;
import android.util.SparseArray;

import java.io.File;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
 
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;

public class RNFSManager extends ReactContextBaseJavaModule {

  private static final String RNFSDocumentDirectoryPath = "RNFSDocumentDirectoryPath";
  private static final String RNFSExternalDirectoryPath = "RNFSExternalDirectoryPath";
  private static final String RNFSExternalStorageDirectoryPath = "RNFSExternalStorageDirectoryPath";
  private static final String RNFSPicturesDirectoryPath = "RNFSPicturesDirectoryPath";
  private static final String RNFSTemporaryDirectoryPath = "RNFSTemporaryDirectoryPath";
  private static final String RNFSCachesDirectoryPath = "RNFSCachesDirectoryPath";
  private static final String RNFSDocumentDirectory = "RNFSDocumentDirectory";

  private static final String RNFSFileTypeRegular = "RNFSFileTypeRegular";
  private static final String RNFSFileTypeDirectory = "RNFSFileTypeDirectory";

  private SparseArray<Downloader> downloaders = new SparseArray<Downloader>();

  public RNFSManager(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public String getName() {
    return "RNFSManager";
  }

  @ReactMethod
  public void writeFile(String filepath, String base64Content, Promise promise) {
    try {
      byte[] bytes = Base64.decode(base64Content, Base64.DEFAULT);

      FileOutputStream outputStream = new FileOutputStream(filepath, false);
      outputStream.write(bytes);
      outputStream.close();

      promise.resolve(null);
    } catch (Exception ex) {
      ex.printStackTrace();
      reject(promise, filepath, ex);
    }
  }

  @ReactMethod
  public void appendFile(String filepath, String base64Content, Promise promise) {
    try {
      byte[] bytes = Base64.decode(base64Content, Base64.DEFAULT);

      FileOutputStream outputStream = new FileOutputStream(filepath, true);
      outputStream.write(bytes);
      outputStream.close();

      promise.resolve(null);
    } catch (Exception ex) {
      ex.printStackTrace();
      reject(promise, filepath, ex);
    }
  }

  @ReactMethod
  public void exists(String filepath, Promise promise) {
    try {
      File file = new File(filepath);
      promise.resolve(file.exists());
    } catch (Exception ex) {
      ex.printStackTrace();
      reject(promise, filepath, ex);
    }
  }

  @ReactMethod
  public void readFile(String filepath, Promise promise) {
    try {
      File file = new File(filepath);

      if (file.isDirectory()) {
        rejectFileIsDirectory(promise);
        return;
      }

      if (!file.exists()) {
        rejectFileNotFound(promise, filepath);
        return;
      }

      FileInputStream inputStream = new FileInputStream(filepath);
      byte[] buffer = new byte[(int)file.length()];
      inputStream.read(buffer);

      String base64Content = Base64.encodeToString(buffer, Base64.NO_WRAP);

      promise.resolve(base64Content);
    } catch (Exception ex) {
      ex.printStackTrace();
      reject(promise, filepath, ex);
    }
  }

  @ReactMethod
  public void moveFile(String filepath, String destPath, Promise promise) {
    try {
      File inFile = new File(filepath);

      if (!inFile.renameTo(new File(destPath))) {
        copyFile(filepath, destPath);

        inFile.delete();
      }

      promise.resolve(true);
    } catch (Exception ex) {
      ex.printStackTrace();
      reject(promise, filepath, ex);
    }
  }

  @ReactMethod
  public void copyFile(String filepath, String destPath, Promise promise) {
    try {
      copyFile(filepath, destPath);

      promise.resolve(null);
    } catch (Exception ex) {
      ex.printStackTrace();
      reject(promise, filepath, ex);
    }
  }

  private void copyFile(String filepath, String destPath) throws IOException {
    InputStream in = new FileInputStream(filepath);
    OutputStream out = new FileOutputStream(destPath);

    byte[] buffer = new byte[1024];
    int length;
    while ((length = in.read(buffer)) > 0) {
      out.write(buffer, 0, length);
    }
    in.close();
    out.close();
  }

  @ReactMethod
  public void readDir(String directory, Promise promise) {
    try {
      File file = new File(directory);

      if (!file.exists()) throw new Exception("Folder does not exist");

      File[] files = file.listFiles();

      WritableArray fileMaps = Arguments.createArray();

      for (File childFile : files) {
        WritableMap fileMap = Arguments.createMap();

        fileMap.putString("name", childFile.getName());
        fileMap.putString("path", childFile.getAbsolutePath());
        fileMap.putInt("size", (int)childFile.length());
        fileMap.putInt("type", childFile.isDirectory() ? 1 : 0);

        fileMaps.pushMap(fileMap);
      }

      promise.resolve(fileMaps);
    } catch (Exception ex) {
      ex.printStackTrace();
      reject(promise, directory, ex);
    }
  }

  @ReactMethod
  public void stat(String filepath, Promise promise) {
    try {
      File file = new File(filepath);

      if (!file.exists()) throw new Exception("File does not exist");

      WritableMap statMap = Arguments.createMap();

      statMap.putInt("ctime", (int)(file.lastModified() / 1000));
      statMap.putInt("mtime", (int)(file.lastModified() / 1000));
      statMap.putInt("size", (int)file.length());
      statMap.putInt("type", file.isDirectory() ? 1 : 0);

      promise.resolve(statMap);
    } catch (Exception ex) {
      ex.printStackTrace();
      reject(promise, filepath, ex);
    }
  }

  @ReactMethod
  public void unlink(String filepath, Promise promise) {
    try {
      File file = new File(filepath);

      if (!file.exists()) throw new Exception("File does not exist");

      DeleteRecursive(file);

      promise.resolve(null);
    } catch (Exception ex) {
      ex.printStackTrace();
      reject(promise, filepath, ex);
    }
  }

  private void DeleteRecursive(File fileOrDirectory) {
    if (fileOrDirectory.isDirectory()) {
      for (File child : fileOrDirectory.listFiles()) {
        DeleteRecursive(child);
      }
    }

    fileOrDirectory.delete();
  }

  @ReactMethod
  public void mkdir(String filepath, ReadableMap options, Promise promise) {
    try {
      File file = new File(filepath);

      file.mkdirs();

      boolean exists = file.exists();

      if (!exists) throw new Exception("Directory could not be created");

      promise.resolve(null);
    } catch (Exception ex) {
      ex.printStackTrace();
      reject(promise, filepath, ex);
    }
  }

  private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
    reactContext
    .getJSModule(RCTNativeAppEventEmitter.class)
    .emit(eventName, params);
  }

  @ReactMethod
  public void downloadFile(final ReadableMap options, final Promise promise) {
    try {
      File file = new File(options.getString("toFile"));
      URL url = new URL(options.getString("fromUrl"));
      final int jobId = options.getInt("jobId");
      ReadableMap headers = options.getMap("headers");
      int progressDivider = options.getInt("progressDivider");

      DownloadParams params = new DownloadParams();

      params.src = url;
      params.dest = file;
      params.headers = headers;
      params.progressDivider = progressDivider;

      params.onTaskCompleted = new DownloadParams.OnTaskCompleted() {
        public void onTaskCompleted(DownloadResult res) {
          if (res.exception == null) {
            WritableMap infoMap = Arguments.createMap();

            infoMap.putInt("jobId", jobId);
            infoMap.putInt("statusCode", res.statusCode);
            infoMap.putInt("bytesWritten", res.bytesWritten);

            promise.resolve(infoMap);
          } else {
            reject(promise, options.getString("toFile"), res.exception);
          }
        }
      };

      params.onDownloadBegin = new DownloadParams.OnDownloadBegin() {
        public void onDownloadBegin(int statusCode, int contentLength, Map<String, String> headers) {
          WritableMap headersMap = Arguments.createMap();

          for (Map.Entry<String, String> entry : headers.entrySet()) {
            headersMap.putString(entry.getKey(), entry.getValue());
          }

          WritableMap data = Arguments.createMap();

          data.putInt("jobId", jobId);
          data.putInt("statusCode", statusCode);
          data.putInt("contentLength", contentLength);
          data.putMap("headers", headersMap);

          sendEvent(getReactApplicationContext(), "DownloadBegin-" + jobId, data);
        }
      };

      params.onDownloadProgress = new DownloadParams.OnDownloadProgress() {
        public void onDownloadProgress(int contentLength, int bytesWritten) {
          WritableMap data = Arguments.createMap();

          data.putInt("jobId", jobId);
          data.putInt("contentLength", contentLength);
          data.putInt("bytesWritten", bytesWritten);

          sendEvent(getReactApplicationContext(), "DownloadProgress-" + jobId, data);
        }
      };

      Downloader downloader = new Downloader();

      downloader.execute(params);

      this.downloaders.put(jobId, downloader);
    } catch (Exception ex) {
      ex.printStackTrace();
      reject(promise, options.getString("toFile"), ex);
    }
  }

  @ReactMethod
  public void stopDownload(int jobId) {
    Downloader downloader = this.downloaders.get(jobId);

    if (downloader != null) {
      downloader.stop();
    }
  }

  @ReactMethod
  public void pathForBundle(String bundleNamed, Promise promise) {
    // TODO: Not sure what equilivent would be?
  }

  @ReactMethod
  public void getFSInfo(Promise promise) {
    File path = Environment.getDataDirectory();
    StatFs stat = new StatFs(path.getPath());
    long totalSpace;
    long freeSpace;
    if (android.os.Build.VERSION.SDK_INT >= 18) {
      totalSpace = stat.getTotalBytes();
      freeSpace = stat.getFreeBytes();
    } else {
      long blockSize = stat.getBlockSize();
      totalSpace = blockSize * stat.getBlockCount();
      freeSpace = blockSize * stat.getAvailableBlocks();
    }
    WritableMap info = Arguments.createMap();
    info.putDouble("totalSpace", (double)totalSpace);   // Int32 too small, must use Double
    info.putDouble("freeSpace", (double)freeSpace);
    promise.resolve(info);
  }

  private void reject(Promise promise, String filepath, Exception ex) {
    if (ex instanceof FileNotFoundException) {
      rejectFileNotFound(promise, filepath);
      return;
    }

    promise.reject(null, ex.getMessage());
  }

  private void rejectFileNotFound(Promise promise, String filepath) {
    promise.reject("ENOENT", "ENOENT: no such file or directory, open '" + filepath + "'");
  }

  private void rejectFileIsDirectory(Promise promise) {
    promise.reject("EISDIR", "EISDIR: illegal operation on a directory, read");
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();

    constants.put(RNFSDocumentDirectory, 0);
    constants.put(RNFSDocumentDirectoryPath, this.getReactApplicationContext().getFilesDir().getAbsolutePath());
    constants.put(RNFSTemporaryDirectoryPath, null);
    constants.put(RNFSPicturesDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
    constants.put(RNFSCachesDirectoryPath, this.getReactApplicationContext().getCacheDir().getAbsolutePath());
    constants.put(RNFSFileTypeRegular, 0);
    constants.put(RNFSFileTypeDirectory, 1);

    File externalStorageDirectory = Environment.getExternalStorageDirectory();
    if (externalStorageDirectory != null) {
        constants.put(RNFSExternalStorageDirectoryPath, externalStorageDirectory.getAbsolutePath());
    } else {
        constants.put(RNFSExternalStorageDirectoryPath, null);
    }

    File externalDirectory = this.getReactApplicationContext().getExternalFilesDir(null);
    if (externalDirectory != null) {
      constants.put(RNFSExternalDirectoryPath, externalDirectory.getAbsolutePath());
    } else {
      constants.put(RNFSExternalDirectoryPath, null);
    }

    return constants;
  }
  // md5
  private String md5(final String s) {
    final String MD5 = "minicloud";
    try {
      // Create MD5 Hash
      MessageDigest digest = java.security.MessageDigest.getInstance(MD5);
      digest.update(s.getBytes());
      byte messageDigest[] = digest.digest();
      // Create Hex String
      StringBuilder hexString = new StringBuilder();
      for (byte aMessageDigest : messageDigest) {
        String h = Integer.toHexString(0xFF & aMessageDigest);
        while (h.length() < 2)
          h = "0" + h;
        hexString.append(h);
      }
      return hexString.toString();

    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    return "";
  }

  // return system thumbnail
  @ReactMethod
  public void thumbnail(String filePath,Promise promise) {
    try {
      ContentResolver cr = this.getContentResolver();
      File outputDir = this.getCacheDir();
      File f = new File(outputDir + "/" + this.md5(filePath) + ".png");
      if (f.exists()) {
        promise.resolve(f.path);
        return;
      }
      int _id = -1;
      String filenameArray[] = filePath.split("\\.");
      String extension = filenameArray[filenameArray.length - 1]
          .toLowerCase();
      Bitmap bitmap = null;
      if (extension.equalsIgnoreCase("mp4")) {
        //video
        _id = this.getVideoThumbnailId(filePath);
        if (_id == -1) {
          return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inDither = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        bitmap = MediaStore.Video.Thumbnails.getThumbnail(cr, _id,
            Images.Thumbnails.MICRO_KIND, options);
      }
      if (extension.equalsIgnoreCase("jpg")
          || extension.equalsIgnoreCase("jpeg")
          || extension.equalsIgnoreCase("png")) {
        //jpg/jpeg/png
        _id = this.getImageThumbnailId(filePath);
        if (_id == -1) {
          return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inDither = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        bitmap = MediaStore.Images.Thumbnails.getThumbnail(cr, _id,
            Images.Thumbnails.MICRO_KIND, options);
      }
      if (_id == -1) {
        return null;
      }
      //write to file
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      bitmap.compress(CompressFormat.PNG, 0 /* ignored for PNG */, bos);
      byte[] bitmapdata = bos.toByteArray();
      // write the bytes in file
      FileOutputStream fos = new FileOutputStream(f);
      fos.write(bitmapdata);
      fos.flush();
      fos.close();
      promise.resolve(f.path);
    } catch (Exception err) {
      err.printStackTrace();
    }
    return null;

  }

  /**
   * return image thumbail
   * 
   * @return
   */
  private int getImageThumbnailId(String imagePath) throws Exception {
    ContentResolver cr = this.getContentResolver();
    String[] projection = { MediaStore.Images.Media.DATA,
        MediaStore.Images.Media._ID, };
    String whereClause = MediaStore.Images.Media.DATA + " = '" + imagePath
        + "'";
    Cursor cursor = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection, whereClause, null, null);
    int _id = 0;
    if (cursor == null || cursor.getCount() == 0) {
      return -1;
    }
    if (cursor.moveToFirst()) {
      int _idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID);
      do {
        _id = cursor.getInt(_idColumn);
      } while (cursor.moveToNext());
    }
    return _id;
  }

  /**
   * return vedio thumbail
   * 
   * @return
   */
  private int getVideoThumbnailId(String videopath) throws Exception {
    ContentResolver cr = this.getContentResolver();
    String[] projection = { MediaStore.Video.Media.DATA,
        MediaStore.Video.Media._ID, };
    String whereClause = MediaStore.Video.Media.DATA + " = '" + videopath
        + "'";
    Cursor cursor = cr.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection, whereClause, null, null);
    int _id = 0;
    if (cursor == null || cursor.getCount() == 0) {
      return -1;
    }
    if (cursor.moveToFirst()) {
      int _idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID);
      do {
        _id = cursor.getInt(_idColumn);
      } while (cursor.moveToNext());
    }
    return _id;
  }
}
