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
    String videoPath = resultMap["video_path"];
    String photoPath = resultMap["photo_path"];
    File videoFile;
    File photoFile;
    if(videoPath!=null&&videoPath.length>0){
      videoFile= File(videoPath);
    }
    if(photoPath!=null&&photoPath.length>0){
      photoFile= File(photoPath);
    }
    return MediaInfo(videoFile: videoFile,photoFile: photoFile);
  }

}
