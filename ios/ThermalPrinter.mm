#import "ThermalPrinter.h"

// iOS isn't implemented yet; every call rejects so apps can feature-detect.
static void rejectUnsupported(RCTPromiseRejectBlock reject)
{
  reject(@"UNSUPPORTED", @"react-native-thermal-printer is Android-only for now", nil);
}

@implementation ThermalPrinter

- (void)print:(NSString *)printerJson
      jobJson:(NSString *)jobJson
      resolve:(RCTPromiseResolveBlock)resolve
       reject:(RCTPromiseRejectBlock)reject
{
  rejectUnsupported(reject);
}

- (void)preview:(NSString *)jobJson
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject
{
  rejectUnsupported(reject);
}

- (void)encode:(NSString *)jobJson
       resolve:(RCTPromiseResolveBlock)resolve
        reject:(RCTPromiseRejectBlock)reject
{
  rejectUnsupported(reject);
}

- (void)checkConnection:(NSString *)printerJson
                resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject
{
  rejectUnsupported(reject);
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeThermalPrinterSpecJSI>(params);
}

+ (NSString *)moduleName
{
  return @"ThermalPrinter";
}

@end
