package org.nfcresearch.qpolllab;

public final class PushReceiptTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        byte[] image=new byte[105984];image[70]=23;
        PushReceipt receipt=new PushReceipt(null);check(!receipt.matches("A1",image),"New content incorrectly completed");
        receipt.complete("A1",image);String stored=receipt.stored();check(stored.length()==64,"Missing receipt");
        check(receipt.matches("A1",image.clone()),"Equivalent render must retain completion");
        check(!receipt.matches("A0",image),"Panel identity lost");
        PushReceipt restored=new PushReceipt(stored);check(restored.matches("A1",image),"Restart lost receipt");
        byte[] pushed=image.clone();image[70]++;check(!receipt.matches("A1",image),"Changed bytes must permit a new push");
        check(!new PushReceipt("invalid").matches("A1",image),"Malformed persisted value trusted");
        restored.clear();check(!restored.matches("A1",pushed),"Explicit resend did not clear receipt");
        try{receipt.complete("A1",new byte[0]);throw new AssertionError("Invalid snapshot accepted");}catch(IllegalArgumentException expected){}
        System.out.println("PushReceiptTest PASS: content identity, panel separation, restore, explicit clear, invalid data");
    }
}
