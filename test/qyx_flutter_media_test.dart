import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:qyx_flutter_media/qyx_flutter_media.dart';

void main() {
  const MethodChannel channel = MethodChannel('qyx_flutter_media');

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });
}
