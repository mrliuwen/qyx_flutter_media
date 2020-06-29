#import "QyxFlutterMediaPlugin.h"
#import "JCVideoRecordView.h"

static NSObject<FlutterPluginRegistrar> *registrarG;
@interface QyxFlutterMediaPlugin(){
}
@end

@implementation QyxFlutterMediaPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
    registrarG = registrar;
  FlutterMethodChannel* channel = [FlutterMethodChannel
      methodChannelWithName:@"qyx_flutter_media"
            binaryMessenger:[registrar messenger]];
  QyxFlutterMediaPlugin* instance = [[QyxFlutterMediaPlugin alloc] init];
  [registrar addMethodCallDelegate:instance channel:channel];
}

- (void)handleMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result {
  if ([@"getPlatformVersion" isEqualToString:call.method]) {
    result([@"iOS " stringByAppendingString:[[UIDevice currentDevice] systemVersion]]);
  } else if ([@"takeVideo" isEqualToString:call.method]) {
      [self takeVideo:^(NSDictionary *resultDic){
          result(resultDic);
      }];
  } else {
    result(FlutterMethodNotImplemented);
  }
}

-(void) takeVideo:(void(^)(NSDictionary *result))resultCallback{
     JCVideoRecordView *recordView = [[JCVideoRecordView alloc]init];
    UIViewController *vc =  [UIApplication sharedApplication].keyWindow.rootViewController;
    recordView.videoBlock = ^(NSString* coverPath, NSString* videoPath) {
        [vc dismissViewControllerAnimated:YES completion:nil];
        resultCallback(@{@"cover_path":coverPath,@"video_path":videoPath});
    };

    [vc presentViewController:recordView animated:YES completion:nil];
}

@end
