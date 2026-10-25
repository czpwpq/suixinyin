package org.nfcresearch.qpolllab.activation;
/** Emits a command containing test-only credentials for an independent POSIX shell execution check. */
public final class ActivationCommandFixture {
    public static void main(String[] args){
        if(args.length!=1)throw new IllegalArgumentException("Test APK path required");
        ActivationProof proof=new ActivationProof(10329,args[0],1000,"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        System.out.print(proof.command());
    }
}
