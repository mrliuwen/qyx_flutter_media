//
//  JCVideoRecordView.h
//  Pods
//
//  Created by zhengjiacheng on 2017/8/31.
//
//

#import <Foundation/Foundation.h>
#import "JCVideoRecordManager.h"
//typedef void(^JCVideoRecordViewDismissBlock)(void);
//typedef void(^JCVideoRecordViewCompletionBlock)(NSURL *fileUrl);
typedef void(^smallVideoBlock)(NSString *coverImagePath,NSString *videoPath);

@interface JCVideoRecordView : UIViewController

@property(nonatomic,copy)smallVideoBlock videoBlock;
@property(nonatomic,strong) NSURL *VIDEO_OUTPUTFILE;

@property(nonatomic,copy)NSString * seessionId;
@property(nonatomic)BOOL isDynamic;

//@property (nonatomic, copy) JCVideoRecordViewDismissBlock cancelBlock;
//@property (nonatomic, copy) JCVideoRecordViewCompletionBlock completionBlock;
//- (void)present;
// 获取视频第一帧
- (UIImage*) getVideoPreViewImage:(NSURL *)path;
@end
