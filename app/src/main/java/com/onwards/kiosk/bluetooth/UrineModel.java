package com.onwards.kiosk.bluetooth;

import java.io.Serializable;
import java.util.ArrayList;

// Serializable: 직렬화 가능
public class UrineModel implements Serializable {

    String blood;
    String billrubin;
    String urobillnogen;
    String ketones;
    String protein;
    String nitrite;
    String glucose;
    String pH;
    String sG;
    String leucoytes;
    String vitamin;

    /// 리스트로 초기화
    public ArrayList<String> urineList = new ArrayList<>();

    /// 검사 날짜 및 시간
    String date;

    @Override
    public String toString() {
        return "UrineModel{" +
                "blood='" + blood + '\'' +
                ", billrubin='" + billrubin + '\'' +
                ", urobillnogen='" + urobillnogen + '\'' +
                ", ketones='" + ketones + '\'' +
                ", protein='" + protein + '\'' +
                ", nitrite='" + nitrite + '\'' +
                ", glucose='" + glucose + '\'' +
                ", pH='" + pH + '\'' +
                ", sG='" + sG + '\'' +
                ", leucoytes='" + leucoytes + '\'' +
                ", vitamin='" + vitamin + '\'' +
                '}';
    }

    public void initialization(String sb) {
        blood = parsingResultBuffer("#A01", sb);
        urineList.add(blood);

        billrubin  = parsingResultBuffer("#A02", sb);
        urineList.add(billrubin);

        urobillnogen = parsingResultBuffer("#A03", sb);
        urineList.add(urobillnogen);

        ketones  = parsingResultBuffer("#A04", sb);
        urineList.add(ketones);

        protein = parsingResultBuffer("#A05", sb);
        urineList.add(protein);

        nitrite = parsingResultBuffer("#A06", sb);
        urineList.add(nitrite);

        glucose = parsingResultBuffer("#A07", sb);
        urineList.add(glucose);

        pH = parsingResultBuffer("#A08", sb);
        urineList.add(pH);

        sG  = parsingResultBuffer("#A09", sb);
        urineList.add(sG);

        leucoytes  = parsingResultBuffer("#A10", sb);
        urineList.add(leucoytes);

        vitamin = parsingResultBuffer("#A11", sb);
        urineList.add(vitamin);

//        date = DateFormat('yyyyMMddHHmmss').format(DateTime.now()).toString();
//        mLog.i(date);
    }

    String parsingResultBuffer(String baseStr, String sb){
        int idx = sb.replaceAll("\n","").indexOf(baseStr);
        String result = sb.substring(idx + 5, (idx + 6));

        return result;
    }
}
