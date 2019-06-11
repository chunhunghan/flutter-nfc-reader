package it.matteocrippa.flutternfcreader

import android.Manifest
import android.content.Context
import android.nfc.*
import android.nfc.tech.Ndef
import android.os.Build
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.nio.charset.Charset


const val PERMISSION_NFC = 1007

class FlutterNfcReaderPlugin(val registrar: Registrar) : MethodCallHandler, EventChannel.StreamHandler, NfcAdapter.ReaderCallback {

    private val activity = registrar.activity()

    private var isReading = false
    private var nfcState = ""
    private var nfcAdapter: NfcAdapter? = null
    private var nfcManager: NfcManager? = null
    private var writeToChip = false

    private var eventSink: EventChannel.EventSink? = null

    private var kId = "nfcId"
    private var kContent = "nfcContent"
    private var kError = "nfcError"
    private var kStatus = "nfcStatus"
    private var kDataToWrite: String? = ""

    private var READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar): Unit {
            val messenger = registrar.messenger()
            val channel = MethodChannel(messenger, "flutter_nfc_reader")
            val eventChannel = EventChannel(messenger, "it.matteocrippa.flutternfcreader.flutter_nfc_reader")
            val plugin = FlutterNfcReaderPlugin(registrar)
            channel.setMethodCallHandler(plugin)
            eventChannel.setStreamHandler(plugin)
        }
    }

    init {
        nfcManager = activity.getSystemService(Context.NFC_SERVICE) as? NfcManager
        nfcAdapter = nfcManager?.defaultAdapter
    }

    override fun onMethodCall(call: MethodCall, result: Result): Unit {

        when (call.method) {
            "NfcWrite" -> {
                writeToChip = true
                kDataToWrite = call.argument("text")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    activity.requestPermissions(
                            arrayOf(Manifest.permission.NFC),
                            PERMISSION_NFC
                    )
                }

                startNFC()

                if (!isReading) {
                    result.error("404", "NFC Hardware not found", null)
                    return
                }

                result.success(null)
            }
            "NfcRead" -> {
                writeToChip = false
                kDataToWrite = ""
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.NFC),
                        PERMISSION_NFC
                    )
                }

                startNFC()

                if (!isReading) {
                    result.error("404", "NFC Hardware not found", null)
                    return
                }

                result.success(null)
            }
            "NfcStop" -> {
                stopNFC()
                val data = mapOf(kId to "", kContent to "", kError to "", kStatus to "stopped")
                result.success(data)
            }
            "NfcCheck" -> {
                checkNFC()
                val data = mapOf(kId to "", kContent to "", kError to "", kStatus to nfcState)
                result.success(data)
            }
            else -> {
                result.notImplemented()
            }
        }
    }
    
    // EventChannel.StreamHandler methods
    override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink?) {
      this.eventSink = eventSink
    }

    override fun onCancel(arguments: Any?) {
      eventSink = null
      stopNFC()
    }

    private fun startNFC(): Boolean {
        isReading = if (nfcAdapter?.isEnabled == true) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                nfcAdapter?.disableForegroundDispatch(registrar.activity())
                nfcAdapter?.enableReaderMode(registrar.activity(), this, READER_FLAGS, null )
            }

            true
        } else {
            false
        }
        return isReading
    }



    private fun stopNFC() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            nfcAdapter?.disableForegroundDispatch(registrar.activity())
            nfcAdapter?.disableReaderMode(registrar.activity())
        }
        isReading = false
        eventSink = null
    }

    private fun checkNFC() {
        val nfcAdapterTemp = nfcAdapter
        if (nfcAdapterTemp == null) {
            // NFC is not available for device
            nfcState = "noDevice"
        } else if (nfcAdapterTemp != null && !nfcAdapterTemp.isEnabled()) {
            // NFC is available for device but not enabled
            nfcState = "disable"
        } else {
            // NFC is enabled
            nfcState = "enable"
        }
    }

    private fun createRecord(value: String): NdefMessage {
        //val typeField = value.toByteArray(Charset.forName("UTF-8"))
        //val payload = byteArrayOf(0x00.toByte())
        //val record = NdefRecord.createTextRecord("EN", value)
        val record = NdefRecord.createMime("text/plain", value.toByteArray(Charset.forName("UTF-8")))
        //val record = NdefRecord(NdefRecord.TNF_WELL_KNOWN, typeField, null, payload)

        return NdefMessage(arrayOf(record))

    }

    // handle discovered NDEF Tags
    override fun onTagDiscovered(tag: Tag?) {
        // convert tag to NDEF tag
        val ndef = Ndef.get(tag)
        // ndef will be null if the discovered tag is not a NDEF tag
        // read NDEF message
        ndef?.connect()

        if(writeToChip) {
            ndef?.writeNdefMessage(createRecord(kDataToWrite.orEmpty()))
        }

        val message = ndef?.ndefMessage?.records?.first()?.payload?.toString(Charset.forName("UTF-8")) ?: ""
        val id = bytesToHexString(tag?.id) ?: ""
        ndef?.close()
        if (message != null) {
            val data = mapOf(kId to id, kContent to message, kError to "", kStatus to "read")
            eventSink?.success(data)
        }
    }

    private fun bytesToHexString(src: ByteArray?): String? {
        val stringBuilder = StringBuilder("0x")
        if (src == null || src.isEmpty()) {
            return null
        }

        val buffer = CharArray(2)
        for (i in src.indices) {
            buffer[0] = Character.forDigit(src[i].toInt().ushr(4).and(0x0F), 16)
            buffer[1] = Character.forDigit(src[i].toInt().and(0x0F), 16)
            stringBuilder.append(buffer)
        }

        return stringBuilder.toString()
    }
}