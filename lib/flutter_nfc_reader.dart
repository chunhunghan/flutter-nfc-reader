import 'dart:async';

import 'package:flutter/services.dart';

enum NFCStatus { none, reading, read, stopped, error, writing, noDevice, disable, enable }

class NfcData {
  final String id;
  final String content;
  final String error;
  final String statusMapper;

  NFCStatus status;

  NfcData({
    this.id,
    this.content,
    this.error,
    this.statusMapper,
  });

  factory NfcData.fromMap(Map data) {
    NfcData result = NfcData(
      id: data['nfcId'],
      content: data['nfcContent'],
      error: data['nfcError'],
      statusMapper: data['nfcStatus'],
    );
    switch (result.statusMapper) {
      case 'writing':
        result.status = NFCStatus.writing;
        break;
      case 'none':
        result.status = NFCStatus.none;
        break;
      case 'reading':
        result.status = NFCStatus.reading;
        break;
      case 'stopped':
        result.status = NFCStatus.stopped;
        break;
      case 'error':
        result.status = NFCStatus.error;
        break;
      case 'noDevice':
        result.status = NFCStatus.noDevice;
        break;
      case 'disable':
        result.status = NFCStatus.disable;
        break;
      case 'enable':
        result.status = NFCStatus.enable;
        break;
      default:
        result.status = NFCStatus.none;
    }
    return result;
  }
}

class FlutterNfcReader {
  static const MethodChannel _channel =
      const MethodChannel('flutter_nfc_reader');
  static const stream =
      const EventChannel('it.matteocrippa.flutternfcreader.flutter_nfc_reader');

  void _onEvent(dynamic data) {
    print("Event");
    print(data);
  }

  void _onError() {
    print("Error");
  }

  FlutterNfcReader() {
    stream.receiveBroadcastStream().listen(_onEvent, onError: _onError);
  }

  static Stream<NfcData> get read {
    final resultStream = _channel
        .invokeMethod('NfcRead')
        .asStream()
        .asyncExpand((_) => stream
            .receiveBroadcastStream()
            .map((result) => NfcData.fromMap(result)));
    return resultStream;
  }

  static Stream<NfcData> writeToCard(String stuffToWrite) {
    return _channel
        .invokeMethod('NfcWrite', {"text": stuffToWrite})
        .asStream()
        .asyncExpand((_) => stream
            .receiveBroadcastStream()
            .map((result) => NfcData.fromMap(result)));
  }

  static Stream<NfcData> get write {
    final resultStream = _channel
        .invokeMethod('NfcWrite', {"text": "Write from flutter"})
        .asStream()
        .asyncExpand((_) => stream
            .receiveBroadcastStream()
            .map((result) => NfcData.fromMap(result)));
    return resultStream;
  }

  static Future<NfcData> get stop async {
    final Map data = await _channel.invokeMethod('NfcStop');

    final NfcData result = NfcData.fromMap(data);

    return result;
  }

  static Future<NfcData> get check async {
    final Map data = await _channel.invokeMethod('NfcCheck');

    final NfcData result = NfcData.fromMap(data);

    return result;
  }
}
