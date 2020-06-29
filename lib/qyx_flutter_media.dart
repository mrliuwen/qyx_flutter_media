import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

class MediaInfo {
  //视频
  final File videoFile;
  final File coverFile;
  final int time;
 //照片
  final File photoFile;
  MediaInfo({this.videoFile,this.coverFile,this.time,this.photoFile});
}

class QyxFlutterMedia {
  static const MethodChannel _channel =
      const MethodChannel('qyx_flutter_media');

  /// 拍照
  static Future<MediaInfo> takeVideo() async {
    Map resultMap = await _channel.invokeMethod("takeVideo");
    print('resultMap---$resultMap');
    String coverPath = resultMap["cover_path"];
    String videoPath = resultMap["video_path"];
    String photoPath = resultMap["photo_path"];
    int time = resultMap["time"]??0;
    File coverFile= File(coverPath);
    File videoFile= File(videoPath);
    File photoFile= File(photoPath);
    return MediaInfo(coverFile: coverFile,videoFile: videoFile,time: time,photoFile: photoFile);
  }

}
