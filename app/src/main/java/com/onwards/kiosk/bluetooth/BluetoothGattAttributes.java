
package com.onwards.kiosk.bluetooth;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
/**
 * Bluetooth BLE 로 연결된 디바이스 UUID 클래스
 *
 * @author 박상원
 * @version 1.1
 * @see <pre>
 * << 개정이력(Modification Information) >>
 *
 *      수정일		   수정자             수정내용
 *  --------------   ---------    ----------------------------
 *   2020. 09. 21     박상원              최초 생성
 *   2020. 09. 28     박상원       Dialog-sps UUID 생성 및 수정
 *
 *
 * </pre>
 * @since 2020. 09.21
 */

public class BluetoothGattAttributes {
    public static String UUID_SERVICE                  = "0783b03e-8535-b5a0-7140-a304d2495cb7"; // Service
    public static String UUID_URINE_ANALYZER           = "0783b03e-8535-b5a0-7140-a304d2495cba"; // Write UUID
    public static String UUID_NOTIFICATION             = "0783b03e-8535-b5a0-7140-a304d2495cb8"; // notification UUID
    public static String CLIENT_CHARACTERISTIC_CONFIG  = "00002902-0000-1000-8000-00805f9b34fb"; // 클라이언트 특성구성
}
