import frontend.controller.Q1Action;
import org.junit.Test;

import static org.junit.Assert.*;

public class Q1ActionTest {
    private String encodeQuery1 = "CC Team";
    private String encodeQuery2 = "CC Team is awesome!";
    private String decodeQuery = "0x294acd760xfe823fc00x82bc20fd0xba60aeac0xbaf12ee20xbad82eeb0x82c420d00xfeaabfd70x37801e0x807624430x80146b50x8036a0750xed018e040x8032a0520xdd0106ea0x809020690x9c0116f80x1f90204a0x8db00720x1a80bfff0x688020920x1fb2aeb20x2a82ea20x4802e9e0x30a020bd0x13433fea0xf879a0250xe281253c0x386ba9e90xcb8ac96a0x3bb6b9480x3cb69f31";
    private Q1Action q1 = new Q1Action();

    @Test public void encode() throws Exception {
        assertTrue(q1.encode(encodeQuery1).equals("0xfe33fc140xd06ea2bb0x7595dbac0xaec121070xfaafe00c0xb32450xce10b7980x490419c0x2340001c0x47f844300x5315bac90x25d18c2e0xa60505140x1fd8a2"));
        assertTrue(q1.encode(encodeQuery2).equals("0xfe453fc10x106e950x4bb75aa50xdba102ec0x100907fa0xaafe00800x2338280x8aaa0600xea1228b40x4a0040x42cd56410xcea034790x81de020xff000a440x7f802b300x42b14ba50x5f85d02d0x4ee840450x42820fe0x17e00"));
    }

    @Test public void decode() throws Exception {
        assertTrue(q1.decode(decodeQuery).equals(encodeQuery2));
    }
}