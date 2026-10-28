package com.kongye.floatball;
/** Two independently sampled PCM sources; mixing uses headroom instead of overflowing int16. */
final class PcmMix {
    static int mix(byte[] a,int an,byte[] b,int bn,byte[] output,int maximum){int n=Math.min(maximum,Math.max(an,bn));n-=n%2;for(int i=0;i<n;i+=2){int x=i+1<an?(short)((a[i]&255)|(a[i+1]<<8)):0,y=i+1<bn?(short)((b[i]&255)|(b[i+1]<<8)):0;int value=(x+y)/2;output[i]=(byte)value;output[i+1]=(byte)(value>>8);}return n;}
}
