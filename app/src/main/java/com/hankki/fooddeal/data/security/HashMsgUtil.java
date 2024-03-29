package com.hankki.fooddeal.data.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HashMsgUtil {
    public static String getSHA256(String str){
        String SHA = "";
        try{
            MessageDigest sh = MessageDigest.getInstance("SHA-256");
            sh.update(str.getBytes());
            byte[] byteData = sh.digest();
            StringBuilder sb = new StringBuilder();
            for (byte byteDatum : byteData) {
                sb.append(Integer.toString((byteDatum & 0xff) + 0x100, 16).substring(1));
            }
            SHA = sb.toString();
        } catch(NoSuchAlgorithmException e){
            e.printStackTrace();
            SHA = null;
        }
        return SHA;
    }

    public static String getSHARoomID(String insertDate, String roomTitle, ArrayList<String> userList) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(insertDate);
        stringBuilder.append(roomTitle);

        for(int i=0;i<userList.size();i++) stringBuilder.append(userList.get(i));

        return getSHA256(stringBuilder.toString());
    }

    public static String getSHAMessageId(Date messageDate, String messageSenderUid) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(messageSenderUid);
        stringBuilder.append(messageDate);

        return getSHA256(stringBuilder.toString());
    }
}
